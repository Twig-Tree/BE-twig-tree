package com.tree.twig_tree.domain.workspace.repository;

import com.tree.twig_tree.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    // 본인 소유 전체 워크스페이스 (폴더 무관)
    List<Workspace> findAllByMember_IdOrderByUpdatedAtDesc(Long memberId);

    // 폴더 기준 조회
    List<Workspace> findAllByFolder_IdAndMember_IdOrderByUpdatedAtDesc(Long folderId, Long memberId);
    List<Workspace> findAllByFolderIsNullAndMember_IdOrderByUpdatedAtDesc(Long memberId);

    // 하위 리소스(트리, 노드, 메모) 변경 시 워크스페이스 updatedAt 갱신
    // workspaceId로 바로 갱신
    @Modifying
    @Query("update Workspace w set w.updatedAt = :now where w.id = :workspaceId")
    void touchById(@Param("workspaceId") Long workspaceId, @Param("now") Instant now);

    // 트리가 속한 워크스페이스를 찾아 워크스페이스 updatedAt 갱신
    @Modifying
    @Query("update Workspace w set w.updatedAt = :now "
            + "where w.id = (select t.workspace.id from Tree t where t.id = :treeId)")
    void touchByTreeId(@Param("treeId") Long treeId, @Param("now") Instant now);

}
