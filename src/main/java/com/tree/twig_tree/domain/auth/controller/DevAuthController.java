package com.tree.twig_tree.domain.auth.controller;

import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.exception.code.AuthSuccessCode;
import com.tree.twig_tree.domain.member.converter.MemberConverter;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.security.jwt.JwtProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발용 access 토큰 발급 API.
 * 설정을 명시적으로 켠 환경에서만 빈이 등록된다. 운영에서 켜지면 누구나 아무 회원의 토큰을 받을 수 있으므로 운영에서는 꺼둔다.
 */
@Tag(name="Dev", description = "개발용 API (운영 비활성화)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev")
@Profile("!prod") // 설정값과 무관하게 운영 프로필에서는 등록하지 않는다
@ConditionalOnProperty(name = "dev.test-token.enabled", havingValue = "true")
public class DevAuthController {

    private final MemberService memberService;
    private final JwtProvider jwtProvider;

    @PostMapping("/token")
    @Operation(summary = "테스트용 Access Token 발급",
        description = "구글 로그인 없이 DB에 존재하는 회원의 Access Token을 발급합니다. Refresh Token은 발급하지 않습니다.<br>"
                        + "발급받은 토큰을 우측 상단 Authorize에 입력해 사용하세요.")
    public ResponseEntity<ApiResponse<AuthResDTO.TokenResponse>> issueTestToken(@RequestParam Long memberId) {
        Member member = memberService.getById(memberId);
        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole());
        AuthResDTO.TokenResponse data = new AuthResDTO.TokenResponse(accessToken, MemberConverter.toMe(member));
        return ApiResponse.onSuccess(AuthSuccessCode.DEV_TOKEN_OK, data);
    }

}
