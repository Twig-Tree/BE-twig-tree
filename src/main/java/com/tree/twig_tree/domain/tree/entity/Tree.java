package com.tree.twig_tree.domain.tree.entity;

import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.global.common.entity.BaseEntity;
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
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "trees")
public class Tree extends BaseEntity {

    @Column(name = "tree_id")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "tree")
    @Builder.Default
    private List<Node> nodes = new ArrayList<>();
}