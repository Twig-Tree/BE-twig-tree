package com.tree.twig_tree.global.security.jwt;

import com.tree.twig_tree.domain.auth.exception.AuthException;
import com.tree.twig_tree.domain.auth.exception.code.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 키 구조: refresh:{memberId}:{jti} -> 상태값, TTL 은 refresh 토큰 수명과 동일.
 * TTL 을 두었으므로 만료된 항목을 따로 정리할 필요가 없다.
 * <p>
 * 값에는 회전 여부를 담는다. 회전 전은 {@code "1"}, 회전 후는 {@code "R"} 이다.
 * 이 구분이 없으면 "몇 번째 요청인지" 를 알 수 없어 유예 기간을 고정할 수 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    /** 아직 회전되지 않은 jti. */
    private static final String ACTIVE = "1";

    /** 이미 회전되어 유예 기간만 남은 jti. */
    private static final String ROTATED = "R";

    // 동시에 날아온 재발급 요청을 흡수할 만큼만 짧게 잡는다
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(10);

    private static final int SCAN_COUNT = 100;

    /**
     * 아직 회전되지 않은 키에만 유예 기간을 건다. 이미 ROTATED 인 키는 손대지 않으므로
     * 만료 시각이 최초 회전 시점 기준으로 고정된다.
     * <p>
     * GET 과 SET 을 애플리케이션에서 나눠 호출하면 그 사이에 낀 요청이 유예를 다시 늘릴 수 있다.
     * 회전은 동시 요청이 몰리는 지점이라 그 틈이 실제로 벌어지므로 스크립트로 묶어 원자적으로 처리한다.
     */
    private static final RedisScript<Long> MARK_ROTATED = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    @Override
    public void save(Long memberId, String jti) {
        execute(() -> {
            redisTemplate.opsForValue().set(
                    key(memberId, jti), ACTIVE, Duration.ofMillis(jwtProperties.refreshTokenTtl()));
            return null;
        });
    }

    @Override
    public boolean exists(Long memberId, String jti) {
        return execute(() -> Boolean.TRUE.equals(redisTemplate.hasKey(key(memberId, jti))));
    }

    @Override
    public void markRotated(Long memberId, String jti) {
        execute(() -> redisTemplate.execute(
                MARK_ROTATED,
                List.of(key(memberId, jti)),
                ACTIVE, ROTATED, String.valueOf(ROTATION_GRACE.toMillis())));
    }

    @Override
    public void delete(Long memberId, String jti) {
        execute(() -> redisTemplate.delete(key(memberId, jti)));
    }

    @Override
    public void deleteAll(Long memberId) {
        execute(() -> {
            // KEYS 는 Redis 를 블로킹하므로 커서로 훑는다
            ScanOptions options = ScanOptions.scanOptions()
                    .match(KEY_PREFIX + memberId + ":*")
                    .count(SCAN_COUNT)
                    .build();

            List<String> keys = new ArrayList<>();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            return null;
        });
    }

    private String key(Long memberId, String jti) {
        return KEY_PREFIX + memberId + ":" + jti;
    }

    /**
     * 저장소 장애를 401 로 내보내면 클라이언트가 "로그인이 풀렸다"고 오해해 재로그인을 유도하게 된다.
     * 재시도해야 할 상황이므로 503 으로 구분한다. access 토큰 검증은 이 저장소를 타지 않으므로
     * 진행 중인 세션은 영향받지 않는다.
     */
    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException e) {
            log.error("refresh 토큰 저장소 접근 실패", e);
            throw new AuthException(AuthErrorCode.TOKEN_STORE_UNAVAILABLE);
        }
    }
}
