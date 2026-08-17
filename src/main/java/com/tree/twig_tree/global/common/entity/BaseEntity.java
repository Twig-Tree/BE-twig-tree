package com.tree.twig_tree.global.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class) // 특정 이벤트(저장, 수정 등)가 발생했을 때, 이를 감지
public abstract class BaseEntity {

    @CreatedDate // 저장 이벤트가 발생 시 현재 시걱으로 저장
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate // 수정 이벤트가 발생 시 현재 시각으로 저장
    @Column(nullable = false)
    private LocalDateTime updatedAt;

}
