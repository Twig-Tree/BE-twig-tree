package com.tree.twig_tree.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthReqDTO {

    public record GoogleLogin(
            @NotBlank
            String idToken
    ) {}

    public record Reissue(
            @NotBlank
            String refreshToken
    ) {}
}
