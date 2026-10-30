package com.tree.twig_tree.global.security.cookie;

import com.tree.twig_tree.global.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RefreshTokenCookieFactoryTest {

    private final JwtProperties jwtProperties = new JwtProperties("unused", 1800000, 1209600000);

    @Test
    void productionCookieUsesSecureHostOnlyScopeAndRefreshTtl() {
        ResponseCookie cookie = factory(true).create("test-refresh-token");

        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEqualTo("test-refresh-token");
        assertThat(cookie.getPath()).isEqualTo("/auth");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14));
        assertThat(cookie.toString()).contains("Max-Age=1209600", "Secure", "HttpOnly", "SameSite=Lax");
    }

    @Test
    void clearExpiresTheSameCookieInBothEnvironments() {
        for (boolean secure : new boolean[]{true, false}) {
            ResponseCookie issued = factory(secure).create("test-refresh-token");
            ResponseCookie cleared = factory(secure).clear();

            assertThat(cleared.getName()).isEqualTo(issued.getName());
            assertThat(cleared.getPath()).isEqualTo(issued.getPath());
            assertThat(cleared.getDomain()).isEqualTo(issued.getDomain());
            assertThat(cleared.isSecure()).isEqualTo(secure);
            assertThat(cleared.isHttpOnly()).isTrue();
            assertThat(cleared.getSameSite()).isEqualTo(issued.getSameSite());
            assertThat(cleared.getValue()).isEmpty();
            assertThat(cleared.toString()).contains("Max-Age=0");
        }
    }

    @Test
    void localCookieSupportsHttpWithoutDroppingHttpOnly() {
        ResponseCookie cookie = factory(false).create("test-refresh-token");

        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    void blankTokenCannotBeIssued() {
        assertThatIllegalArgumentException().isThrownBy(() -> factory(true).create(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> factory(true).create(null));
    }

    private RefreshTokenCookieFactory factory(boolean secure) {
        return new RefreshTokenCookieFactory(new AuthCookieProperties(secure), jwtProperties);
    }
}
