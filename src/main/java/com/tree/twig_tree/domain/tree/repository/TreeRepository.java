package com.tree.twig_tree.domain.tree.repository;

import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TreeRepository extends JpaRepository<Tree, Long> {
    boolean existsByWorkspace(Workspace workspace);
    Optional<Tree> findByWorkspace(Workspace workspace);
    List<Tree> findAllByWorkspaceIn(Collection<Workspace> workspaces);

    List<Tree> findAllByWorkspace_Member_Id(Long memberId);

    // 노드 이동처럼 형제 순서를 다시 매기는 작업을 트리 단위로 직렬화하기 위한 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tree t WHERE t.id = :treeId")
    Optional<Tree> findByIdForUpdate(@Param("treeId") Long treeId);
}
