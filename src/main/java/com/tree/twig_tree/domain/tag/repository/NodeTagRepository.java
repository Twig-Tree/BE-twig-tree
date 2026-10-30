package com.tree.twig_tree.domain.tag.repository;

import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.tag.entity.NodeTag;
import com.tree.twig_tree.domain.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NodeTagRepository extends JpaRepository<NodeTag, Long> {
    Optional<NodeTag> findByNodeAndTag(Node node, Tag tag);

    // 노드당 태그 개수 카운트
    long countByNodeId(Long nodeId);
}
