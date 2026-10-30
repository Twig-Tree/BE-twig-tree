package com.tree.twig_tree.domain.member.service;

import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.domain.member.exception.MemberException;
import com.tree.twig_tree.domain.member.exception.code.MemberErrorCode;
import com.tree.twig_tree.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    /**
     * 구글 sub(providerId) 기준으로 회원 조회, 없으면 자동 가입.
     * 기존 회원이면 구글 프로필(이름/사진)을 최신값으로 갱신한다.
     */
    @Transactional
    public Member findOrCreateByGoogle(String providerId, String email, String name, String profileImage) {
        return memberRepository.findByProviderAndProviderId(Provider.GOOGLE, providerId)
                .map(member -> {
                    member.updateProfile(name, profileImage);
                    return member;
                })
                .orElseGet(() -> memberRepository.save(
                        Member.builder()
                                .email(email)
                                .name(name)
                                .profileImage(profileImage)
                                .provider(Provider.GOOGLE)
                                .providerId(providerId)
                                .role(Role.ROLE_USER)
                                .build()
                ));
    }

    public Member getById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
