package com.tree.twig_tree.domain.node.entity;

import com.tree.twig_tree.domain.tree.entity.Tree;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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

    private String name;

    @Column(length = 500)
    private String memo;

    // orderId의 DB 제약사항을 flyway에 정의합니다.
    // orderId는 비즈니스 로직으로 결정되는 값이라 임의로 초기화하면 안 됩니다. null로 존재해야 오류 시에도 인지할 수 있습니다.
    @Column(name = "order_id")
    private Long orderId;

    // 연관관계 객체여야 JPA가 추적 가능합니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Node parent;

    // 부모 노드가 삭제되면 자식 노드도 함께 삭제되어야 합니다.
    @OneToMany(mappedBy = "parent")
    @Builder.Default // @Builder 사용 시 필드 기본값이 무시되므로 초기화가 필요합니다.
    private List<Node> children = new ArrayList<>();

    // Node가 연관관계의 주입입니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tree_id")
    private Tree tree;

    /**
     * 기본 메서드
     */
    // 노드 이름 수정
    public void updateName(String newName) {
        this.name = newName;
    }

    // 메모 수정
    public void updateMemo(String newContent) {
        this.memo = newContent;
    }
}
