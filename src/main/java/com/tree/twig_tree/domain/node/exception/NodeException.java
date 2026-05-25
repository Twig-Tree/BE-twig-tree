package com.tree.twig_tree.domain.node.exception;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.exception.ProjectException;

public class NodeException extends ProjectException {
  public NodeException(BaseErrorCode errorCode) {
    super(errorCode);
  }
}
