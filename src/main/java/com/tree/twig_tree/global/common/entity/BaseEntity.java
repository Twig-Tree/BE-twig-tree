package com.tree.twig_tree.global.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.OptimisticLock;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class) // 특정 이벤트(저장, 수정 등)가 발생했을 때, 이를 감지
@Getter
public abstract class BaseEntity {

    @CreatedDate // 저장 이벤트가 발생 시 현재 시각으로 저장
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate // 수정 이벤트가 발생 시 현재 시각으로 저장
    // 수정 시각은 어떤 필드가 바뀌어도 갱신되므로 @Version 증가 판단에서 제외합니다.
    // 제외하지 않으면 version 대상이 아닌 필드(예: Node.orderId)만 바뀌어도 이 값 때문에 version이 올라갑니다.
    @OptimisticLock(excluded = true)
    @Column(nullable = false)
    private Instant updatedAt;

}
