package com.tree.twig_tree.domain.tag.repository;

import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.tag.entity.Tag;
import com.tree.twig_tree.domain.tree.entity.Tree;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findAllByTree(Tree tree);

    @Query("select nt.tag from NodeTag nt where nt.node = :node")
    List<Tag> findAllByNode(Node node);
}
