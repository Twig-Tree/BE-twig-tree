package com.tree.twig_tree.domain.folder.repository;

import com.tree.twig_tree.domain.folder.dto.FolderProjection;
import com.tree.twig_tree.domain.folder.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {
    // null이 아닌 어떤 parent_id를 갖는 폴더들 조회
    List<Folder> findAllByParentAndMember_Id(Folder parent, Long member_id);

    // 최상위 루트에 있는 폴더들 조회
    List<Folder> findAllByParentIsNullAndMember_Id(Long memberId);

    // 해당 폴더 (자신 포함) + 상위 폴더들 목록을 한번에 조회
    @Query(value = """
    WITH RECURSIVE folder_ancestors AS (
        SELECT folder_id, name, folder_parent_id, 0 AS depth
        FROM folders
        WHERE folder_id = :folderId

        UNION ALL

        SELECT f.folder_id, f.name, f.folder_parent_id, fa.depth + 1
        FROM folders f
        INNER JOIN folder_ancestors fa ON f.folder_id = fa.folder_parent_id
    )
    SELECT folder_id, name
    FROM folder_ancestors
    ORDER BY depth DESC
    """, nativeQuery = true)
    List<FolderProjection.FolderAncestorProjection> findAncestorPathRaw(@Param("folderId") Long folderId);

    // folderId가 targetId 자신이거나 targetId의 조상인지 확인 (targetId에서 루트까지 거슬러 올라가며 탐색)
    @Query(value = """
    WITH RECURSIVE ancestors AS (
        SELECT folder_id, folder_parent_id
        FROM folders
        WHERE folder_id = :targetId

        UNION ALL

        SELECT f.folder_id, f.folder_parent_id
        FROM folders f
        INNER JOIN ancestors a ON f.folder_id = a.folder_parent_id
    )
    SELECT EXISTS (SELECT 1 FROM ancestors WHERE folder_id = :folderId)
    """, nativeQuery = true)
    boolean isSelfOrAncestor(@Param("folderId") Long folderId, @Param("targetId") Long targetId);

}
