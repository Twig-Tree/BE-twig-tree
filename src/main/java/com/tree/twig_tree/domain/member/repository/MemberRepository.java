package com.tree.twig_tree.domain.member.repository;

import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByProviderAndProviderId(Provider provider, String providerId);
}
