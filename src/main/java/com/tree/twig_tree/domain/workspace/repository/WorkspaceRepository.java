package com.tree.twig_tree.domain.workspace.repository;

import com.tree.twig_tree.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
}
