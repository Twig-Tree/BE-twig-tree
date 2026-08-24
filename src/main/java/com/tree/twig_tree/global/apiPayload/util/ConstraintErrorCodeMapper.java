package com.tree.twig_tree.global.apiPayload.util;

import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.node.exception.code.NodeErrorCode;
import com.tree.twig_tree.domain.tree.exception.code.TreeErrorCode;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.code.GeneralErrorCode;

import java.util.Map;

public final class ConstraintErrorCodeMapper {

    // 객체 생성 방지
    private ConstraintErrorCodeMapper() {
        // Utility class
    }

    private static final Map<String, BaseErrorCode> CONSTRAINT_ERROR_CODE_MAP = Map.of(
            "uk_nodes_root_per_tree", NodeErrorCode.ONE_ROOT_PER_TREE,
            "uk_nodes_parent_order_id", NodeErrorCode.DUPLICATED_ORDER_ID,
            "uk_folder_root_name", FolderErrorCode.DUPLICATE_FOLDER_NAME,
            "uk_folder_parent_name", FolderErrorCode.DUPLICATE_FOLDER_NAME,
            "uk_workspace_root_name", WorkspaceErrorCode.DUPLICATE_WORKSPACE_NAME,
            "uk_workspace_folder_name", WorkspaceErrorCode.DUPLICATE_WORKSPACE_NAME,
            "uk_trees_workspace", TreeErrorCode.TREE_ALREADY_EXISTS
            // 새 도메인 제약조건 추가될 때 여기만 추가
    );

    public static BaseErrorCode getErrorCode(Throwable throwable) {
        String message = throwable.getMessage();

        if (message == null) {
            return GeneralErrorCode.BAD_REQUEST;
        }

        return CONSTRAINT_ERROR_CODE_MAP.entrySet().stream()
                .filter(entry -> message.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(GeneralErrorCode.BAD_REQUEST); // 매핑 없으면 여기서 바로 BAD_REQUEST 에러
    }
}
