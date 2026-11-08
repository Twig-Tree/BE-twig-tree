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

    /**
     * 트리 소유자(멤버) ID 조회.
     *
     * <p>LLM 호출처럼 느린 작업 앞에서 소유권만 확인할 때 쓴다. 엔티티를 가져오면 workspace 와 member 가
     * 지연 로딩이라 트랜잭션을 열어야 하고, 그 트랜잭션이 LLM 응답을 기다리는 동안 DB 커넥션을 점유한다.
     */
    @Query("SELECT t.workspace.member.id FROM Tree t WHERE t.id = :treeId")
    Optional<Long> findOwnerMemberIdById(@Param("treeId") Long treeId);

    // 노드 이동처럼 형제 순서를 다시 매기는 작업을 트리 단위로 직렬화하기 위한 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tree t WHERE t.id = :treeId")
    Optional<Tree> findByIdForUpdate(@Param("treeId") Long treeId);
}
