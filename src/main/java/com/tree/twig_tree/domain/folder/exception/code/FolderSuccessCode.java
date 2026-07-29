package com.tree.twig_tree.domain.folder.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FolderSuccessCode implements BaseSuccessCode {

    FOLDER_FOUND(HttpStatus.OK, "FOLDER200-1", "성공적으로 폴더를 조회했습니다."),
    FOLDERS_FOUND(HttpStatus.OK, "FOLDER200-2", "성공적으로 폴더 목록을 조회했습니다."),
    FOLDERS_PATH_FOUND(HttpStatus.OK, "FOLDER200-3", "성공적으로 폴더 상위 경로를 조회했습니다."),
    FOLDER_CREATED(HttpStatus.CREATED, "FOLDER201-1", "성공적으로 폴더를 추가했습니다."),
    FOLDER_UPDATED(HttpStatus.OK, "FOLDER200-3", "성공적으로 폴더 정보를 수정했습니다."),
    FOLDER_DELETED(HttpStatus.NO_CONTENT, "FOLDER204-1", "성공적으로 폴더를 삭제했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
