package com.tree.twig_tree.domain.member.event;

/**
 * 회원이 새로 가입했을 때 발행된다.
 *
 * <p>가입 트랜잭션 안에서 동기로 발행되므로, {@code @EventListener} 로 받은 쪽의 작업은
 * 가입과 함께 커밋되거나 함께 롤백된다.
 */
public record MemberRegisteredEvent(Long memberId) {
}
