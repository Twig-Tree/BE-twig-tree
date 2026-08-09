package com.tree.twig_tree.domain.auth.service;

import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.service.GoogleIdTokenVerifier.GoogleUserInfo;
import com.tree.twig_tree.domain.member.converter.MemberConverter;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.global.security.jwt.JwtProvider;
import com.tree.twig_tree.global.security.jwt.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
