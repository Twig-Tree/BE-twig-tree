package com.tree.twig_tree.domain.tree.repository;

import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TreeRepository extends JpaRepository<Tree, Long> {
    boolean existsByWorkspace(Workspace workspace);
}
