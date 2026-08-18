package com.tree.twig_tree.domain.auth.service;

import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.exception.AuthException;
import com.tree.twig_tree.domain.auth.exception.code.AuthErrorCode;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.global.security.jwt.JwtProperties;
import com.tree.twig_tree.global.security.jwt.JwtProvider;
import com.tree.twig_tree.global.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 재발급·회전·재사용 탐지·로그아웃 로직을 Redis 없이 검증한다.
 * 저장소를 인터페이스로 분리해 둔 덕에 인메모리 대역으로 갈아끼울 수 있다.
 */
class AuthServiceTokenLifecycleTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("test-secret-key-for-jwt-provider-unit-test-48bytes!!".getBytes());

    private static final Long MEMBER_ID = 42L;

    private final Member member = Member.builder()
            .id(MEMBER_ID)
            .email("user@example.com")
            .name("테스터")
            .provider(Provider.GOOGLE)
            .providerId("google-sub-1")
            .role(Role.ROLE_USER)
            .build();

    private JwtProvider jwtProvider;
    private FakeRefreshTokenStore store;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(new JwtProperties(SECRET, 60_000L, 1_000_000L));
        store = new FakeRefreshTokenStore();

        // MemberService 는 재발급 시 role 을 다시 읽는 용도로만 쓰인다
        MemberService memberService = new MemberService(null) {
            @Override
            public Member getById(Long memberId) {
                return member;
            }
        };

        authService = new AuthService(null, memberService, jwtProvider, store);
    }

    /** 로그인 직후 상태, 즉 저장소에 등록된 유효한 refresh 토큰을 만든다. */
    private String issuedRefreshToken() {
        JwtProvider.IssuedToken issued = jwtProvider.createRefreshToken(MEMBER_ID);
        store.save(MEMBER_ID, issued.jti());
        return issued.token();
    }

    @Test
    void 유효한_리프레시_토큰이면_새_토큰쌍이_발급된다() {
        String refreshToken = issuedRefreshToken();

        AuthResDTO.TokenPair reissued = authService.reissue(refreshToken);

        assertThat(reissued.accessToken()).isNotBlank();
        assertThat(reissued.refreshToken()).isNotBlank().isNotEqualTo(refreshToken);
        assertThat(reissued.member().memberId()).isEqualTo(MEMBER_ID);
    }

    @Test
    void 재발급된_토큰은_저장소에_등록된다() {
        AuthResDTO.TokenPair reissued = authService.reissue(issuedRefreshToken());

        String newJti = jwtProvider.parseClaims(reissued.refreshToken()).getId();
        assertThat(store.exists(MEMBER_ID, newJti)).isTrue();
    }

    @Test
    void 회전_유예_기간_안에서는_같은_토큰으로_한_번_더_재발급된다() {
        String refreshToken = issuedRefreshToken();
        authService.reissue(refreshToken);

        // 여러 탭이 동시에 재발급을 요청하는 상황. 여기서 실패하면 사용자가 스스로를 로그아웃시키게 된다
        assertThat(authService.reissue(refreshToken)).isNotNull();
    }

    @Test
    void 유예_기간이_지나면_회전된_토큰은_거부된다() {
        String refreshToken = issuedRefreshToken();
        authService.reissue(refreshToken);
        store.elapseGrace();

        assertThatThrownBy(() -> authService.reissue(refreshToken))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void 재사용이_감지되면_회원의_다른_세션도_모두_끊긴다() {
        String laptop = issuedRefreshToken();
        String desktop = issuedRefreshToken();
        String desktopJti = jwtProvider.parseClaims(desktop).getId();

        authService.reissue(laptop);
        store.elapseGrace();

        assertThatThrownBy(() -> authService.reissue(laptop)).isInstanceOf(AuthException.class);

        // 탈취 정황이므로 관련 없어 보이는 다른 기기 세션도 함께 무효화되어야 한다
        assertThat(store.exists(MEMBER_ID, desktopJti)).isFalse();
    }

    @Test
    void 회전_유예는_반복_재사용으로_연장되지_않는다() {
        String stolen = issuedRefreshToken();
        authService.reissue(stolen);                            // 최초 회전. 여기서부터 유예 10초

        store.elapse(Duration.ofSeconds(9));
        assertThat(authService.reissue(stolen)).isNotNull();    // 아직 유예 안이므로 통과한다

        // 위 재발급이 만료를 19초로 미뤄서는 안 된다. 유예는 최초 회전 시점 기준으로 끝나야 한다
        store.elapse(Duration.ofSeconds(2));

        assertThatThrownBy(() -> authService.reissue(stolen))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void 반복_재사용해도_결국_탐지되어_회원_세션이_모두_끊긴다() {
        String stolen = issuedRefreshToken();
        String desktop = issuedRefreshToken();
        String desktopJti = jwtProvider.parseClaims(desktop).getId();

        authService.reissue(stolen);

        // 유예가 끊기기 전에 계속 들이밀어 만료를 미루려는 시도
        for (int i = 0; i < 3; i++) {
            store.elapse(Duration.ofSeconds(2));
            authService.reissue(stolen);
        }
        store.elapse(Duration.ofSeconds(5));                    // 최초 회전으로부터 11초

        assertThatThrownBy(() -> authService.reissue(stolen)).isInstanceOf(AuthException.class);
        assertThat(store.exists(MEMBER_ID, desktopJti)).isFalse();
    }

    @Test
    void 액세스_토큰으로는_재발급되지_않는다() {
        String accessToken = jwtProvider.createAccessToken(MEMBER_ID, Role.ROLE_USER);

        assertThatThrownBy(() -> authService.reissue(accessToken))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void 만료된_리프레시_토큰은_전용_코드로_거부된다() {
        JwtProvider expiredProvider = new JwtProvider(new JwtProperties(SECRET, 60_000L, -120_000L));
        String expired = expiredProvider.createRefreshToken(MEMBER_ID).token();

        assertThatThrownBy(() -> authService.reissue(expired))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.EXPIRED_REFRESH_TOKEN));
    }

    @Test
    void 로그아웃하면_해당_세션의_재발급이_거부된다() {
        String refreshToken = issuedRefreshToken();

        authService.logout(refreshToken);

        assertThatThrownBy(() -> authService.reissue(refreshToken)).isInstanceOf(AuthException.class);
    }

    @Test
    void 로그아웃은_다른_기기_세션을_건드리지_않는다() {
        String laptop = issuedRefreshToken();
        String desktop = issuedRefreshToken();
        String desktopJti = jwtProvider.parseClaims(desktop).getId();

        authService.logout(laptop);

        assertThat(store.exists(MEMBER_ID, desktopJti)).isTrue();
    }

    @Test
    void 알아볼_수_없는_토큰으로_로그아웃해도_예외가_나지_않는다() {
        // 클라이언트는 어차피 토큰을 버리므로 멱등하게 성공 처리한다
        authService.logout("not-a-jwt");
    }

    /**
     * 인메모리 대역. 실제 Redis 처럼 키마다 만료 시각을 들고 있고, 가상 시계를 앞으로 돌려
     * 유예 기간이 흐른 상황을 만든다. 회전을 단순한 플래그로 두면 "만료를 다시 미루는지"
     * 를 표현할 수 없어 유예 연장 버그가 대역에서 재현되지 않는다.
     */
    private static class FakeRefreshTokenStore implements RefreshTokenStore {

        /** 실제 구현의 ROTATION_GRACE 와 같은 값. */
        private static final Duration GRACE = Duration.ofSeconds(10);

        private static final Duration REFRESH_TTL = Duration.ofDays(14);

        private final Map<String, Long> expiresAt = new HashMap<>();
        private final Set<String> rotated = new HashSet<>();

        private long now = 0L;

        @Override
        public void save(Long memberId, String jti) {
            expiresAt.put(key(memberId, jti), now + REFRESH_TTL.toMillis());
        }

        @Override
        public boolean exists(Long memberId, String jti) {
            Long deadline = expiresAt.get(key(memberId, jti));
            return deadline != null && deadline > now;
        }

        @Override
        public void markRotated(Long memberId, String jti) {
            String key = key(memberId, jti);
            if (!exists(memberId, jti)) {
                return;
            }
            if (!rotated.add(key)) {
                // 이미 회전된 jti. 유예는 최초 회전 시점 기준이므로 만료를 다시 미루지 않는다
                return;
            }
            expiresAt.put(key, now + GRACE.toMillis());
        }

        @Override
        public void delete(Long memberId, String jti) {
            String key = key(memberId, jti);
            expiresAt.remove(key);
            rotated.remove(key);
        }

        @Override
        public void deleteAll(Long memberId) {
            String prefix = memberId + ":";
            expiresAt.keySet().removeIf(k -> k.startsWith(prefix));
            rotated.removeIf(k -> k.startsWith(prefix));
        }

        /** 가상 시계를 앞으로 돌린다. */
        void elapse(Duration duration) {
            now += duration.toMillis();
        }

        /** 회전 유예 기간이 만료된 상황을 만든다. */
        void elapseGrace() {
            elapse(GRACE.plusMillis(1));
        }

        private String key(Long memberId, String jti) {
            return memberId + ":" + jti;
        }
    }
}
