package com.tree.twig_tree.domain.tree.exception;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.exception.ProjectException;

public class TreeException extends ProjectException {
  public TreeException(BaseErrorCode errorCode) {
    super(errorCode);
  }
}
