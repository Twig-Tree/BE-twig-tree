package com.tree.twig_tree.domain.auth.service;

import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.exception.AuthException;
import com.tree.twig_tree.domain.auth.exception.code.AuthErrorCode;
import com.tree.twig_tree.domain.auth.service.GoogleIdTokenVerifier.GoogleUserInfo;
import com.tree.twig_tree.domain.member.converter.MemberConverter;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.global.security.jwt.JwtProvider;
import com.tree.twig_tree.global.security.jwt.RefreshTokenStore;
import com.tree.twig_tree.global.security.jwt.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final MemberService memberService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;

    /**
     * 구글 로그인: ID 토큰 검증 -> 회원 조회/자동가입 -> 자체 토큰 발급.
     */
    public AuthResDTO.TokenPair googleLogin(String idToken) {
        GoogleUserInfo userInfo = googleIdTokenVerifier.verify(idToken);

        Member member = memberService.findOrCreateByGoogle(
                userInfo.providerId(),
                userInfo.email(),
                userInfo.name(),
                userInfo.profileImage()
        );

        return issueTokens(member);
    }

    /**
     * 재발급: refresh 토큰을 회전시키며 새 토큰쌍을 내준다.
     * 이미 폐기된 토큰이 다시 오면 탈취로 보고 그 회원의 모든 세션을 끊는다.
     */
    public AuthResDTO.TokenPair reissue(String refreshToken) {
        Claims claims = parseRefreshToken(refreshToken);
        Long memberId = jwtProvider.getMemberId(claims);
        String jti = claims.getId();

        if (!refreshTokenStore.exists(memberId, jti)) {
            // 서명은 멀쩡한데 저장소에 없다 = 이미 회전됐거나 로그아웃된 토큰
            log.warn("refresh 토큰 재사용 감지, 회원 전체 세션을 무효화합니다. memberId={}, jti={}", memberId, jti);
            refreshTokenStore.deleteAll(memberId);
            // 재사용을 별도 코드로 알리지 않는다. 공격자에게 "한때 유효했다"는 힌트가 된다
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // role 은 토큰이 아니라 DB 에서 다시 읽는다. refresh 에는 role 클레임이 없고, 그 사이 바뀌었을 수 있다
        Member member = memberService.getById(memberId);

        // 즉시 지우지 않고 유예 기간만 남긴다. 동시에 날아온 재발급 요청이 서로를 재사용으로 오인하는 것을 막는다
        refreshTokenStore.markRotated(memberId, jti);

        return issueTokens(member);
    }

    private AuthResDTO.TokenPair issueTokens(Member member) {
        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole());
        JwtProvider.IssuedToken refreshToken = jwtProvider.createRefreshToken(member.getId());

        refreshTokenStore.save(member.getId(), refreshToken.jti());

        return AuthResDTO.TokenPair.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.token())
                .member(MemberConverter.toMe(member))
                .build();
    }

    /**
     * 서명·만료를 검증하고 refresh 용도로 발급된 토큰인지 확인한다.
     * access 토큰으로 재발급받는 경로를 여기서 막는다.
     */
    private Claims parseRefreshToken(String refreshToken) {
        Claims claims;
        try {
            claims = jwtProvider.parseClaims(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new AuthException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (!jwtProvider.isTokenType(claims, TokenType.REFRESH) || claims.getId() == null) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        return claims;
    }
}
