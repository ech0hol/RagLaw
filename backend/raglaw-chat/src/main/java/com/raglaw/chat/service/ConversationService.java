package com.raglaw.chat.service;

import com.raglaw.chat.domain.ConversationEntity;
import com.raglaw.chat.domain.ConversationRepository;
import com.raglaw.chat.domain.MessageEntity;
import com.raglaw.chat.domain.MessageRepository;
import com.raglaw.chat.dto.ConversationDto;
import com.raglaw.chat.dto.MessageDto;
import com.raglaw.common.util.Ids;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private static final String DEFAULT_TITLE = "新对话";
    private static final String DEFAULT_AGENT_CODE = "GENERAL";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> listByUser(String userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ConversationDto create(String userId, String agentCode) {
        Instant now = Instant.now();
        String resolvedAgentCode = agentCode == null || agentCode.isBlank() ? DEFAULT_AGENT_CODE : agentCode;
        ConversationEntity entity = new ConversationEntity(
                Ids.newId(),
                userId,
                DEFAULT_TITLE,
                resolvedAgentCode,
                now,
                now
        );
        return toDto(conversationRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public Optional<ConversationDto> get(String userId, String conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.getUserId().equals(userId))
                .map(this::toDto);
    }

    @Transactional
    public Optional<ConversationDto> updateTitle(String userId, String conversationId, String title) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.getUserId().equals(userId))
                .map(conversation -> {
                    conversation.setTitle(title);
                    conversation.setUpdatedAt(Instant.now());
                    return toDto(conversationRepository.save(conversation));
                });
    }

    @Transactional
    public boolean delete(String userId, String conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.getUserId().equals(userId))
                .map(conversation -> {
                    conversationRepository.delete(conversation);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<List<MessageDto>> listMessages(String userId, String conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.getUserId().equals(userId))
                .map(conversation -> messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                        .map(this::toDto)
                        .toList());
    }

    @Transactional
    public Optional<MessageDto> appendMessage(
            String userId,
            String conversationId,
            String role,
            String content,
            String citationsJson
    ) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.getUserId().equals(userId))
                .map(conversation -> {
                    Instant now = Instant.now();
                    MessageEntity message = new MessageEntity(
                            Ids.newId(),
                            conversationId,
                            role,
                            content,
                            citationsJson,
                            now
                    );
                    messageRepository.save(message);

                    if ("user".equals(role)
                            && messageRepository.countByConversationIdAndRole(conversationId, "user") == 1) {
                        conversation.setTitle(truncateTitle(content));
                        conversation.setUpdatedAt(now);
                        conversationRepository.save(conversation);
                    } else {
                        conversation.setUpdatedAt(now);
                        conversationRepository.save(conversation);
                    }

                    return toDto(message);
                });
    }

    private static String truncateTitle(String content) {
        String trimmed = content.strip();
        if (trimmed.isEmpty()) {
            return DEFAULT_TITLE;
        }
        return trimmed.length() <= 30 ? trimmed : trimmed.substring(0, 30);
    }

    private ConversationDto toDto(ConversationEntity entity) {
        return new ConversationDto(
                entity.getId(),
                entity.getUserId(),
                entity.getTitle(),
                entity.getAgentCode(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private MessageDto toDto(MessageEntity entity) {
        return new MessageDto(
                entity.getId(),
                entity.getConversationId(),
                entity.getRole(),
                entity.getContent(),
                entity.getCitationsJson(),
                entity.getCreatedAt()
        );
    }
}
