package com.tree.twig_tree.domain.member.dto;

import lombok.Builder;

public class MemberResDTO {

    @Builder
    public record Me(
            Long memberId,
            String email,
            String name,
            String profileImage
    ) {}
}
