package com.tree.twig_tree.domain.tag.entity;

import com.tree.twig_tree.domain.node.entity.Node;
import jakarta.persistence.*;
import lombok.Cleanup;
import lombok.Getter;

@Getter
@Entity
public class NodeTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "node_tag_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id")
    private Node node;
}
