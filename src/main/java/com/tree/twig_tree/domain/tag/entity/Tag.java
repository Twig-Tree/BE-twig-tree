package com.tree.twig_tree.domain.tag.entity;

import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tags",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tag_name", // 하나의 트리 안에서 태그 이름이 겹칠 수 없다.
                columnNames = {"tree_id", "name"}
        ))
public class Tag extends BaseEntity {

    /** 이름 최대 길이. DB 컬럼 길이이자 API·시드의 검증 기준이다. */
    public static final int NAME_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long id;

    @Column(length = NAME_MAX_LENGTH, nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tree_id", nullable = false)
    private Tree tree;

    // 도메인 함수
    public void updateName(String name) {
        this.name = name;
    }
}
