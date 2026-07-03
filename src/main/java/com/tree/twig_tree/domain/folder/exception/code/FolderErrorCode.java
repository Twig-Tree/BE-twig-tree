package com.tree.twig_tree.domain.folder.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FolderErrorCode implements BaseErrorCode {

    FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, "FOLDER404-1","해당 폴더가 존재하지 않습니다."),
    PARENT_NOT_FOUND(HttpStatus.NOT_FOUND, "FOLDER404-2","부모 폴더가 존재하지 않습니다."),
    DUPLICATE_FOLDER_NAME(HttpStatus.BAD_REQUEST, "FOLDER400-2","같은 부모 아래 동일한 이름의 폴더는 만들 수 없습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;
}
