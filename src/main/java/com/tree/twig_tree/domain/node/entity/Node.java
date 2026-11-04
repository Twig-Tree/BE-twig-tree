package com.tree.twig_tree.domain.node.entity;

import com.tree.twig_tree.domain.tag.entity.NodeTag;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OptimisticLock;

import java.util.ArrayList;
import java.util.List;

// 바뀐 컬럼만 UPDATE 합니다. 이름·메모 수정과 이동이 겹쳐도 상대가 바꾼 컬럼을 읽어둔 옛 값으로 덮어쓰지 않습니다.
@DynamicUpdate
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "nodes")
public class Node extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "node_id")
    private Long id;

    @Column(length = 30)
    private String name;

    @Column(length = 500)
    private String memo;

    // 이름·메모 수정 시 1씩 증가합니다. 요청이 보고 만든 version과 다르면 저장하지 않습니다.
    @Version
    private Long version;

    // orderId의 DB 제약사항을 flyway에 정의합니다.
    // orderId는 비즈니스 로직으로 결정되는 값이라 임의로 초기화하면 안 됩니다. null로 존재해야 오류 시에도 인지할 수 있습니다.
    // 이동·형제 재정렬은 트리 락으로 보호하므로 version을 올리지 않습니다. (올리면 형제 노드 수정까지 충돌로 막힙니다.)
    @OptimisticLock(excluded = true)
    @Column(name = "order_id")
    private Long orderId;

    // 연관관계 객체여야 JPA가 추적 가능합니다.
    @OptimisticLock(excluded = true)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Node parent;

    // 부모 노드가 삭제되면 자식 노드도 함께 삭제되어야 합니다.
    // 컬렉션 변경도 version을 올릴 수 있어 제외합니다. (자식·태그 변경은 이 노드의 이름·메모 충돌이 아닙니다.)
    @OptimisticLock(excluded = true)
    @OneToMany(mappedBy = "parent")
    @Builder.Default // @Builder 사용 시 필드 기본값이 무시되므로 초기화가 필요합니다.
    private List<Node> children = new ArrayList<>();

    // BatchSize: Lazy 연관관계를 하나씩 따로 조회하지 않고, 여러개를 모아서 IN 쿼리 한번으로 가져옵니다. (N+1 문제 해결)
    // 트리 조회는 재귀 CTE native query라서 JPQL join fetch를 쓰지 못해서 batch 로 해결합니다.
    @OptimisticLock(excluded = true)
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "node")
    @Builder.Default
    private List<NodeTag> nodeTags = new ArrayList<>();

    // Node가 연관관계의 주인입니다.
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

    // 노드 부모 변경 (하위 노드는 parent_id 로 연결되어 있어 함께 이동합니다.)
    public void moveTo(Node newParent) {
        this.parent = newParent;
    }

    // 형제 노드 사이의 순서 변경
    public void changeOrder(Long newOrderId) {
        this.orderId = newOrderId;
    }
}
