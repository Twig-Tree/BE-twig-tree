package com.tree.twig_tree.domain.auth.dto;

public class AuthReqDTO {

    public record GoogleLogin(
            String idToken
    ) {}
}
