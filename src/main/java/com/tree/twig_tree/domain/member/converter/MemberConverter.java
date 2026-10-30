package com.tree.twig_tree.domain.member.converter;

import com.tree.twig_tree.domain.member.dto.MemberResDTO;
import com.tree.twig_tree.domain.member.entity.Member;

public class MemberConverter {

    public static MemberResDTO.Me toMe(Member member) {
        return MemberResDTO.Me.builder()
                .memberId(member.getId())
                .email(member.getEmail())
                .name(member.getName())
                .profileImage(member.getProfileImage())
                .build();
    }
}
