package com.tree.twig_tree.domain.chat.dto;

import com.tree.twig_tree.domain.chat.client.LlmProvider;

public record ChatReqDTO(String message, LlmProvider provider) {
}
