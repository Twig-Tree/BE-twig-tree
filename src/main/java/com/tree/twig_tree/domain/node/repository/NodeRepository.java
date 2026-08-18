package com.tree.twig_tree.domain.node.repository;

import com.tree.twig_tree.domain.node.entity.Node;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
