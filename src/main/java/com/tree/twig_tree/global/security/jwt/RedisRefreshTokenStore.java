package com.tree.twig_tree.global.security.jwt;

import com.tree.twig_tree.domain.auth.exception.AuthException;
import com.tree.twig_tree.domain.auth.exception.code.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 키 구조: refresh:{memberId}:{jti} -> "1", TTL 은 refresh 토큰 수명과 동일.
 * TTL 을 두었으므로 만료된 항목을 따로 정리할 필요가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    // 동시에 날아온 재발급 요청을 흡수할 만큼만 짧게 잡는다
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(10);

    private static final int SCAN_COUNT = 100;

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    @Override
    public void save(Long memberId, String jti) {
        execute(() -> {
            redisTemplate.opsForValue().set(
                    key(memberId, jti), "1", Duration.ofMillis(jwtProperties.refreshTokenTtl()));
            return null;
        });
    }

    @Override
    public boolean exists(Long memberId, String jti) {
        return execute(() -> Boolean.TRUE.equals(redisTemplate.hasKey(key(memberId, jti))));
    }

    @Override
    public void markRotated(Long memberId, String jti) {
        execute(() -> redisTemplate.expire(key(memberId, jti), ROTATION_GRACE));
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
