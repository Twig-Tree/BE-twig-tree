package com.tree.twig_tree.domain.auth.service;

import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.exception.AuthException;
import com.tree.twig_tree.domain.auth.exception.code.AuthErrorCode;
import com.tree.twig_tree.domain.auth.service.GoogleIdTokenVerifier.GoogleUserInfo;
import com.tree.twig_tree.domain.member.converter.MemberConverter;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.domain.seed.service.TesterSeedService;
import com.tree.twig_tree.global.security.jwt.JwtProvider;
import com.tree.twig_tree.global.security.jwt.RefreshTokenStore;
import com.tree.twig_tree.global.security.jwt.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final MemberService memberService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    /** tester-seed.enabled=true 일 때만 빈이 있다 */
    private final ObjectProvider<TesterSeedService> testerSeedService;

    /**
     * 구글 로그인: ID 토큰 검증 -> 회원 조회/자동가입 -> (신규면) 테스터 시드 트리 생성 -> 자체 토큰 발급.
     * 시드는 가입이 커밋된 뒤 만들며, 실패해도 로그인은 성공시키고 seedFailed 로 알린다.
     */
    public AuthResDTO.LoginResult googleLogin(String idToken) {
        GoogleUserInfo userInfo = googleIdTokenVerifier.verify(idToken);

        MemberService.FindOrCreateResult result = memberService.findOrCreateByGoogle(
                userInfo.providerId(),
                userInfo.email(),
                userInfo.name(),
                userInfo.profileImage()
        );

        boolean seedFailed = false;
        if (result.created()) {
            TesterSeedService seeder = testerSeedService.getIfAvailable();
            seedFailed = seeder != null && !seeder.seed(result.member().getId());
        }

        return new AuthResDTO.LoginResult(issueTokens(result.member()), seedFailed);
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

    /**
     * 로그아웃: 해당 세션의 refresh 토큰만 폐기한다.
     * 이미 발급된 access 토큰은 만료까지 유효하다.
     */
    public void logout(String refreshToken) {
        Claims claims;
        try {
            claims = parseRefreshToken(refreshToken);
        } catch (AuthException e) {
            // 알아볼 수 없는 토큰이면 지울 것도 없다. 클라이언트는 어차피 토큰을 버리므로 성공으로 응답한다
            return;
        }

        refreshTokenStore.delete(jwtProvider.getMemberId(claims), claims.getId());
    }

    /**
     * 회원 탈퇴: 회원과 소유 데이터를 삭제한 뒤 모든 refresh 토큰을 폐기한다.
     *
     * <p>DB 삭제가 커밋된 다음에 토큰을 지운다. 순서를 바꾸면 삭제가 실패했을 때 로그아웃만 된 채로 남는다.
     * 토큰 폐기가 실패해도 탈퇴는 성공으로 본다. 남은 refresh 토큰은 재발급 때 회원 조회에서 막힌다.
     * 이미 발급된 access 토큰은 만료까지 유효하지만, 회원을 조회하는 API 에서 404 가 난다.
     */
    public void withdraw(Long memberId) {
        memberService.delete(memberId);

        try {
            refreshTokenStore.deleteAll(memberId);
        } catch (RuntimeException e) {
            // DB 삭제는 이미 커밋됐으므로 어떤 예외든 탈퇴 실패로 올리지 않는다
            log.warn("탈퇴 회원의 refresh 토큰 폐기 실패, 재발급 시 회원 조회로 차단됩니다. memberId={}", memberId, e);
        }
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
