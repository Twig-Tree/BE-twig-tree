package com.tree.twig_tree.domain.workspace.repository;

import com.tree.twig_tree.domain.workspace.entity.Workspace;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    // 폴더 기준 조회
    List<Workspace> findAllByFolder_IdAndMember_IdOrderByUpdatedAtDesc(Long folderId, Long memberId);
    List<Workspace> findAllByFolderIsNullAndMember_IdOrderByUpdatedAtDesc(Long memberId);

    // 전체 워크스페이스 목록 최신순 조회 (폴더 무관)
    /**
     * 첫 페이지: 커서 없이 최신순 상위 N개
     */
    @Query("""
          SELECT w FROM Workspace w
          WHERE w.member.id = :memberId
          ORDER BY w.updatedAt DESC, w.id DESC
          """)
    List<Workspace> findRecentFirstPage(@Param("memberId") Long memberId, Pageable pageable);

    /**
     * 다음 페이지: 커서(updatedAt, id)보다 뒤 항목 N개
     * updatedAt 먼저 비교, 같으면 workspaceId로 비교
     */
    @Query("""
        SELECT w
        FROM Workspace w
        WHERE w.member.id = :memberId
          AND (
              w.updatedAt < :cursorUpdatedAt
              OR (
                  w.updatedAt = :cursorUpdatedAt
                  AND w.id < :cursorId
              )
          )
        ORDER BY w.updatedAt DESC, w.id DESC
    """)
    List<Workspace> findRecentAfterCursor(@Param("memberId") Long memberId,
                                          @Param("cursorUpdatedAt") Instant cursorUpdatedAt,
                                          @Param("cursorId") Long cursorId,
                                          Pageable pageable);

}
