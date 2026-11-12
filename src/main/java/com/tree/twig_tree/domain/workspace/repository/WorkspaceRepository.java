package com.tree.twig_tree.domain.workspace.repository;

import com.tree.twig_tree.domain.workspace.entity.Workspace;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    /**
     * 워크스페이스 소유자(멤버) ID 조회.
     *
     * <p>LLM 호출처럼 느린 작업 앞에서 소유권만 확인할 때 쓴다. 엔티티를 가져오면 member 가 지연 로딩이라
     * 트랜잭션을 열어야 하고, 그 트랜잭션이 LLM 응답을 기다리는 동안 DB 커넥션을 점유한다.
     */
    @Query("SELECT w.member.id FROM Workspace w WHERE w.id = :workspaceId")
    Optional<Long> findOwnerMemberIdById(@Param("workspaceId") Long workspaceId);

    // 같은 워크스페이스를 대상으로 하는 트리 생성 요청을 직렬화하기 위한 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Workspace w WHERE w.id = :workspaceId")
    Optional<Workspace> findByIdForUpdate(@Param("workspaceId") Long workspaceId);

}
