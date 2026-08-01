package com.tree.twig_tree.domain.member.controller;

import com.tree.twig_tree.domain.member.converter.MemberConverter;
import com.tree.twig_tree.domain.member.dto.MemberResDTO;
import com.tree.twig_tree.domain.member.exception.code.MemberSuccessCode;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member", description = "회원 정보 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "액세스 토큰의 주인(로그인한 회원) 정보를 조회합니다.")
    public ApiResponse<MemberResDTO.Me> getMe(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(
                MemberSuccessCode.ME_FOUND,
                MemberConverter.toMe(memberService.getById(memberId))
        );
    }
}
