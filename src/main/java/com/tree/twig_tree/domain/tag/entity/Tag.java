package com.tree.twig_tree.domain.tag.entity;

import com.tree.twig_tree.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "tags")
public class Tag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long id;

    @Column(length = 30, nullable = false)
    private String name;

    // 도메인 함수
    public void updateName(String name) {
        this.name = name;
    }
}
