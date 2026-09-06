package com.tree.twig_tree.domain.workspace.repository;

import com.tree.twig_tree.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    // 본인 소유 전체 워크스페이스 (폴더 무관)
    List<Workspace> findAllByMember_IdOrderByUpdatedAtDesc(Long memberId);

    // 폴더 기준 조회
    List<Workspace> findAllByFolder_IdAndMember_IdOrderByUpdatedAtDesc(Long folderId, Long memberId);
    List<Workspace> findAllByFolderIsNullAndMember_IdOrderByUpdatedAtDesc(Long memberId);

}
