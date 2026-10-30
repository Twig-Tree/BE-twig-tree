package com.tree.twig_tree.domain.folder.dto;

// Projection: 리포지토리에서 쿼리 결과를 받는 용도
public class FolderProjection {

    public interface FolderAncestorProjection {
        Long getFolderId();
        String getName();
    }
}
