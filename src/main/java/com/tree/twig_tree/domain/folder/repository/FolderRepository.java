package com.tree.twig_tree.domain.folder.repository;

import com.tree.twig_tree.domain.folder.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {
    // null이 아닌 어떤 parent_id를 갖는 폴더들 조회
    List<Folder> findAllByParent(Folder parent);

    // 최상위 루트에 있는 폴더들 조회
    List<Folder> findAllByParentIsNull();
}
