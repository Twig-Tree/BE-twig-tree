-- 트리당 루트 노드는 하나만 존재
CREATE UNIQUE INDEX uk_nodes_root_per_tree
    ON nodes (tree_id)
    WHERE parent_id IS NULL;

-- 일반 노드 (parent_id IS NOT NULL)
CREATE UNIQUE INDEX uk_nodes_parent_order_id
    ON nodes (parent_id, order_id)
    WHERE parent_id IS NOT NULL;