package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.client.LlmClient;
import com.tree.twig_tree.domain.chat.dto.ChatReqDTO;
import com.tree.twig_tree.domain.chat.dto.ChatResDTO;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final List<LlmClient> llmClients;

    public Mono<ChatResDTO> chat(ChatReqDTO req) {
        LlmClient client = llmClients.stream()
            .filter(c -> c.getProvider() == req.provider())
            .findFirst()
            .orElseThrow(() -> new ChatException(ChatErrorCode.UNSUPPORTED_PROVIDER));

        return client.chat(req.message())
            .map(reply -> new ChatResDTO(reply, req.provider()));
    }
}
