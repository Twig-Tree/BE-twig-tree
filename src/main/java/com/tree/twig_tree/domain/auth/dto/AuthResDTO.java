package com.tree.twig_tree.domain.auth.dto;

import com.tree.twig_tree.domain.member.dto.MemberResDTO;
import lombok.Builder;

public class AuthResDTO {

    public record Csrf(String token, String headerName) {}

    public record TokenResponse(
            String accessToken,
            MemberResDTO.Me member
    ) {}

    @Builder
    public record TokenPair(
            String accessToken,
            String refreshToken,
            MemberResDTO.Me member
    ) {}
}
