package com.raglaw.chat.service;

import com.raglaw.chat.domain.ConversationEntity;
import com.raglaw.chat.domain.ConversationRepository;
import com.raglaw.chat.domain.MessageEntity;
import com.raglaw.chat.domain.MessageRepository;
import com.raglaw.chat.dto.ConversationDto;
import com.raglaw.chat.dto.MessageDto;
import com.raglaw.common.util.Ids;
import com.raglaw.rag.contract.ContractAccessService;
import com.raglaw.memory.casefile.CaseService;
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
    private final ContractAccessService contractAccessService;
    private final CaseService caseService;

    public ConversationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ContractAccessService contractAccessService,
            CaseService caseService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.contractAccessService = contractAccessService;
        this.caseService = caseService;
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> listByUser(String userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ConversationDto create(String userId, String agentCode, String contextDocumentId, String caseId) {
        contractAccessService.validateOwnedContractContext(blankToNull(contextDocumentId));
        String resolvedCaseId = blankToNull(caseId);
        if (resolvedCaseId != null && !caseService.existsOwnedBy("default", userId, resolvedCaseId)) {
            throw new IllegalArgumentException("case does not belong to user");
        }
        Instant now = Instant.now();
        String resolvedAgentCode = agentCode == null || agentCode.isBlank() ? DEFAULT_AGENT_CODE : agentCode;
        ConversationEntity entity = new ConversationEntity(
                Ids.newId(),
                userId,
                DEFAULT_TITLE,
                resolvedAgentCode,
                blankToNull(contextDocumentId),
                resolvedCaseId,
                now,
                now
        );
        return toDto(conversationRepository.save(entity));
    }

    @Transactional
    public ConversationDto create(String userId, String agentCode, String contextDocumentId) {
        return create(userId, agentCode, contextDocumentId, null);
    }

    @Transactional(readOnly = true)
    public Optional<String> findContextDocumentId(String userId, String conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.getUserId().equals(userId))
                .map(ConversationEntity::getContextDocumentId)
                .filter(id -> id != null && !id.isBlank());
    }

    @Transactional(readOnly = true)
    public Optional<String> findCaseId(String userId, String conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.getUserId().equals(userId))
                .map(ConversationEntity::getCaseId)
                .filter(id -> id != null && !id.isBlank());
    }

    @Transactional(readOnly = true)
    public Optional<String> findLastUserMessageId(String userId, String conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.getUserId().equals(userId))
                .flatMap(conversation -> messageRepository
                        .findTopByConversationIdAndRoleOrderByCreatedAtDesc(conversationId, "user")
                        .map(MessageEntity::getId));
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

    @Transactional(readOnly = true)
    public Optional<String> findLastUserMessageContent(String userId, String conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.getUserId().equals(userId))
                .flatMap(conversation -> messageRepository
                        .findTopByConversationIdAndRoleOrderByCreatedAtDesc(conversationId, "user")
                        .map(MessageEntity::getContent));
    }

    @Transactional(readOnly = true)
    public Optional<String> findLastAssistantMessageId(String userId, String conversationId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.getUserId().equals(userId))
                .flatMap(conversation -> messageRepository
                        .findTopByConversationIdAndRoleOrderByCreatedAtDesc(conversationId, "assistant")
                        .map(MessageEntity::getId));
    }

    /**
     * Deletes the target assistant message and all subsequent messages, then returns the paired user content.
     */
    @Transactional
    public Optional<String> prepareRegenerateFromAssistant(
            String userId,
            String conversationId,
            String assistantMessageId
    ) {
        if (assistantMessageId == null || assistantMessageId.isBlank()) {
            return Optional.empty();
        }
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.getUserId().equals(userId))
                .flatMap(conversation -> messageRepository
                        .findByIdAndConversationId(assistantMessageId, conversationId)
                        .filter(message -> "assistant".equals(message.getRole()))
                        .flatMap(assistant -> {
                            String userContent = findPairedUserContent(conversationId, assistant).orElse(null);
                            if (userContent == null) {
                                return Optional.empty();
                            }
                            messageRepository.deleteByConversationIdAndCreatedAtGreaterThanEqual(
                                    conversationId,
                                    assistant.getCreatedAt()
                            );
                            conversation.setUpdatedAt(Instant.now());
                            conversationRepository.save(conversation);
                            return Optional.of(userContent);
                        }));
    }

    private Optional<String> findPairedUserContent(String conversationId, MessageEntity assistant) {
        List<MessageEntity> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        for (int i = 0; i < messages.size(); i++) {
            if (!messages.get(i).getId().equals(assistant.getId())) {
                continue;
            }
            for (int j = i - 1; j >= 0; j--) {
                MessageEntity candidate = messages.get(j);
                if ("user".equals(candidate.getRole())) {
                    return Optional.of(candidate.getContent());
                }
            }
            return Optional.empty();
        }
        return Optional.empty();
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
                entity.getContextDocumentId(),
                entity.getCaseId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
