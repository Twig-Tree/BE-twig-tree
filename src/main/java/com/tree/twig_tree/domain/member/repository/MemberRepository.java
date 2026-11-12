package com.tree.twig_tree.domain.member.repository;

import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByProviderAndProviderId(Provider provider, String providerId);

    // 같은 회원의 폴더 계층을 바꾸는 요청들을 직렬화하기 위한 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);
}
