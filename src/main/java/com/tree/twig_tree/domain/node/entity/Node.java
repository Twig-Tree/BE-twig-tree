package com.tree.twig_tree.domain.node.entity;

import com.tree.twig_tree.domain.tree.entity.Tree;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "nodes")
public class Node {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "node_id")
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    /**
     * TODO: orderId는 겹치면 안된다는 제약조건을 걸어야합니다.
     */
    @Column(name = "order_id")
    private Long orderId;

    private String name;
    private String memo = null;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tree_id")
    private Tree tree;

    public void updateTitle(String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("노드 제목은 공백일 수 없습니다.");
        }
        this.name = newName;
    }



}
