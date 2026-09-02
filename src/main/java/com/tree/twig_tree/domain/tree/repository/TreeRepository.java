package com.tree.twig_tree.domain.tree.repository;

import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TreeRepository extends JpaRepository<Tree, Long> {
    boolean existsByWorkspace(Workspace workspace);
    Optional<Tree> findByWorkspace(Workspace workspace);
    List<Tree> findAllByWorkspaceIn(Collection<Workspace> workspaces);
}
