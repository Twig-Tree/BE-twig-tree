package com.tree.twig_tree.domain.tag.entity;

import com.tree.twig_tree.domain.node.entity.Node;
import jakarta.persistence.*;
import lombok.Cleanup;
import lombok.Getter;

@Getter
@Entity
@Table(name = "node_tags",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_node_tag", // 같은 노드에 같은 태그를 두 번 붙일 수 없다.
                columnNames = {"node_id", "tag_id"}
        ))
public class NodeTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "node_tag_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;
}
