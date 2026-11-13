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
     * 기존 회원이면 구글 프로필(이름/사진)을 최신값으로 갱신한다. 신규 가입 여부를 함께 돌려준다.
     */
    @Transactional
    public FindOrCreateResult findOrCreateByGoogle(String providerId, String email, String name, String profileImage) {
        return memberRepository.findByProviderAndProviderId(Provider.GOOGLE, providerId)
                .map(member -> {
                    member.updateProfile(name, profileImage);
                    return new FindOrCreateResult(member, false);
                })
                .orElseGet(() -> new FindOrCreateResult(memberRepository.save(Member.builder()
                        .email(email)
                        .name(name)
                        .profileImage(profileImage)
                        .provider(Provider.GOOGLE)
                        .providerId(providerId)
                        .role(Role.ROLE_USER)
                        .build()), true));
    }

    public record FindOrCreateResult(Member member, boolean created) {
    }

    public Member getById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * 회원 행에 쓰기 락을 걸어, 그 회원의 폴더 계층을 바꾸는 요청들을 직렬화한다.
     *
     * <p>폴더 트리에는 노드의 트리처럼 계층 전체를 대표하는 행이 없고, 순환 검사는 조상 경로 전체를
     * 읽으므로 폴더 두 개만 잠가서는 막을 수 없다. 폴더는 모두 한 회원 소유라 회원 행이 가장 작은
     * 공통 잠금 대상이다. 호출자의 트랜잭션에 합류해야 하므로 반드시 쓰기 트랜잭션 안에서 부른다.
     */
    @Transactional
    public Member lockById(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
