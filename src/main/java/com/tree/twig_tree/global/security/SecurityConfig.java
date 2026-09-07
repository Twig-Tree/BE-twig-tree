package com.tree.twig_tree.global.security;

import com.tree.twig_tree.global.security.handler.JwtAccessDeniedHandler;
import com.tree.twig_tree.global.security.handler.JwtAuthenticationEntryPoint;
import com.tree.twig_tree.global.security.jwt.JwtAuthenticationFilter;
import com.tree.twig_tree.global.security.cookie.AuthCookieProperties;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PERMIT_ALL_PATTERNS = {
            "/auth/csrf",
            "/auth/google",
            "/auth/refresh",
            "/auth/logout",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsProperties corsProperties;
    private final AuthCookieProperties authCookieProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        // 응답 본문의 마스킹된 토큰을 그대로 헤더에 전달하는 방식이다.
                        .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(new AndRequestMatcher(
                                CsrfFilter.DEFAULT_CSRF_MATCHER,
                                PathPatternRequestMatcher.withDefaults().matcher("/auth/**"))))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                .authorizeHttpRequests(auth -> auth
                        // 오류 페이지로의 내부 디스패치까지 인가 대상이 되면, 예외의 실제 원인 대신
                        // 401 이 나가 디버깅이 불가능해진다. 외부에서 직접 부를 수 있는 경로가 아니다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
                        .anyRequest().authenticated())

                // 거부 판정 시 응답 작성자 지정
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))

                // 기본 로그인 필터 앞에 커스텀 jwt 필터 배치
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        // 운영에서는 다른 서브도메인이 Domain 쿠키로 같은 이름을 주입하지 못하게 한다.
        repository.setCookieName(authCookieProperties.secure() ? "__Host-csrf_token" : "csrf_token");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie
                .httpOnly(true)
                .secure(authCookieProperties.secure())
                .sameSite("Lax"));
        return repository;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
