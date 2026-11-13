package com.tree.twig_tree.domain.member.controller;

import com.tree.twig_tree.domain.auth.service.AuthService;
import com.tree.twig_tree.domain.member.converter.MemberConverter;
import com.tree.twig_tree.domain.member.dto.MemberResDTO;
import com.tree.twig_tree.domain.member.exception.code.MemberErrorCode;
import com.tree.twig_tree.domain.member.exception.code.MemberSuccessCode;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.swagger.ApiErrorCodeExample;
import com.tree.twig_tree.global.security.cookie.RefreshTokenCookieFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member", description = "회원 정보 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;
    private final AuthService authService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "액세스 토큰의 주인(로그인한 회원) 정보를 조회합니다.")
    @ApiErrorCodeExample(MemberErrorCode.class)
    public ResponseEntity<ApiResponse<MemberResDTO.Me>> getMe(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(
                MemberSuccessCode.ME_FOUND,
                MemberConverter.toMe(memberService.getById(memberId))
        );
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴",
            description = "로그인한 회원과 그 회원의 폴더·워크스페이스·트리를 모두 즉시 삭제하고, 모든 세션의 Refresh Token을 폐기한 뒤 refresh_token 쿠키를 삭제합니다.<br>"
                    + "삭제한 데이터는 복구할 수 없습니다. 같은 구글 계정으로 다시 로그인하면 새 회원으로 가입됩니다.<br>"
                    + "이미 발급된 액세스 토큰은 만료(최대 30분)까지 남아 있지만, 회원 정보가 필요한 요청은 MEMBER404-1 로 실패합니다.<br>"
                    + "이미 탈퇴한 회원이 다시 요청해도 같은 성공 응답을 주고 쿠키를 삭제합니다(재시도 안전).")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal Long memberId) {
        authService.withdraw(memberId);
        return ResponseEntity.status(MemberSuccessCode.WITHDRAWN.getStatus())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(MemberSuccessCode.WITHDRAWN, null));
    }
}
