package com.tree.twig_tree.domain.auth.dto;

import com.tree.twig_tree.domain.member.dto.MemberResDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

public class AuthResDTO {

    public record TokenResponse(
            String accessToken,
            MemberResDTO.Me member
    ) {}

    /**
     * 구글 로그인 응답. seedFailed 는 이번 로그인에서 가입하며 테스터 시드 트리 생성에 실패했을 때만 true 다.
     */
    public record LoginResponse(
            String accessToken,
            MemberResDTO.Me member,

            @Schema(description = "신규 가입 시 테스터 시드 트리 생성에 실패했으면 true. 이 응답에서만 알려 주며 다시 시도하지 않는다.")
            boolean seedFailed
    ) {}

    public record LoginResult(
            TokenPair tokens,
            boolean seedFailed
    ) {}

    @Builder
    public record TokenPair(
            String accessToken,
            String refreshToken,
            MemberResDTO.Me member
    ) {}
}
