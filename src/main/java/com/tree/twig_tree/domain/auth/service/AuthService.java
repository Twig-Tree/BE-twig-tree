package com.tree.twig_tree.domain.auth.service;

import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.service.GoogleIdTokenVerifier.GoogleUserInfo;
import com.tree.twig_tree.domain.member.converter.MemberConverter;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final MemberService memberService;
    private final JwtProvider jwtProvider;

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

        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtProvider.createRefreshToken(member.getId());
        // TODO Phase 5: refreshToken의 jti를 Redis에 저장 (로테이션/로그아웃 지원)

        return AuthResDTO.TokenPair.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .member(MemberConverter.toMe(member))
                .build();
    }
}
