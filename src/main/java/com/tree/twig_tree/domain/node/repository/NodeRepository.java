package com.tree.twig_tree.domain.node.repository;

import com.tree.twig_tree.domain.node.entity.Node;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NodeRepository extends JpaRepository<Node, Long> {

    // 해당 treeId를 갖는 트리의 전체 노드들 조회
    @Query(value = """
        WITH RECURSIVE tree_hierarchy AS (
            -- Non-recursive term
            SELECT node_id, name, memo, parent_id, order_id, tree_id, created_at, updated_at
            FROM nodes
            WHERE tree_id = :treeId AND parent_id IS NULL

            UNION ALL

            -- Recursive term
            SELECT n.node_id, n.name, n.memo, n.parent_id, n.order_id, n.tree_id, n.created_at, n.updated_at
            FROM nodes n
            INNER JOIN tree_hierarchy th ON n.parent_id = th.node_id
        )
        SELECT * FROM tree_hierarchy
        """, nativeQuery = true)
    List<Node> findFullTreeByTreeId(@Param("treeId") Long treeId);

    // 해당 rootId를 갖는 서브트리의 노드들 조회
    @Query(value = """
        WITH RECURSIVE tree_hierarchy AS (
            -- Non-recursive term
            SELECT node_id, name, memo, parent_id, order_id, tree_id, created_at, updated_at
            FROM nodes
            WHERE node_id = :rootId

            UNION ALL

            -- Recursive term
            SELECT n.node_id, n.name, n.memo, n.parent_id, n.order_id, n.tree_id, n.created_at, n.updated_at
            FROM nodes n
            INNER JOIN tree_hierarchy th ON n.parent_id = th.node_id
        )
        SELECT * FROM tree_hierarchy
        """, nativeQuery = true)
    List<Node> findSubTreeByRootId(@Param("rootId") Long rootId);

    // nodeId가 targetId 자신이거나 targetId의 조상인지 확인 (targetId에서 루트까지 거슬러 올라가며 탐색)
    @Query(value = """
        WITH RECURSIVE ancestors AS (
            -- Non-recursive term
            SELECT node_id, parent_id
            FROM nodes
            WHERE node_id = :targetId

            UNION ALL

            -- Recursive term
            SELECT n.node_id, n.parent_id
            FROM nodes n
            INNER JOIN ancestors a ON n.node_id = a.parent_id
        )
        SELECT EXISTS (SELECT 1 FROM ancestors WHERE node_id = :nodeId)
        """, nativeQuery = true)
    boolean isSelfOrAncestor(@Param("nodeId") Long nodeId, @Param("targetId") Long targetId);

    // 같은 부모를 갖는 형제 노드들을 순서대로 조회
    List<Node> findAllByParentIdOrderByOrderIdAsc(Long parentId);

    // 형제 노드 재정렬 중에는 order_id가 잠깐 겹칠 수 있으므로 현재 트랜잭션에서 유니크 검사를 미룹니다.
    @Modifying
    @Query(value = "SET CONSTRAINTS uk_nodes_parent_order_id DEFERRED", nativeQuery = true)
    void deferParentOrderIdConstraint();

    // 미뤄둔 유니크 검사를 즉시 수행합니다. 커밋 시점이 아닌 여기서 위반이 드러나야 DUPLICATED_ORDER_ID로 매핑됩니다.
    @Modifying
    @Query(value = "SET CONSTRAINTS uk_nodes_parent_order_id IMMEDIATE", nativeQuery = true)
    void checkParentOrderIdConstraint();

}
