package com.raglaw.agentadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentadmin.domain.AgentConfigEntity;
import com.raglaw.agentadmin.domain.AgentConfigRepository;
import com.raglaw.agentadmin.dto.AgentConfigCreateRequest;
import com.raglaw.agentadmin.dto.AgentConfigUpdateRequest;
import com.raglaw.agentadmin.dto.CreateAgentVersionRequest;
import com.raglaw.agentadmin.registry.AgentRegistry;
import com.raglaw.common.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentConfigServiceTest {

    @Mock
    private AgentConfigRepository repository;

    @Mock
    private AgentRegistry registry;

    private AgentConfigService service;

    @BeforeEach
    void setUp() {
        service = new AgentConfigService(repository, registry, new ObjectMapper(), false);
    }

    @Test
    void create_persistsCustomAgentAndReloads() {
        when(repository.existsByCode("MY_AGENT")).thenReturn(false);

        var dto = service.create(new AgentConfigCreateRequest(
                "my_agent",
                "我的助手",
                "dashscope:qwen-plus",
                "自定义提示词",
                List.of("risk-dimension-review"),
                List.of("rag_search"),
                List.of(),
                List.of("STATUTE_CIVIL"),
                true
        ));

        assertThat(dto.code()).isEqualTo("MY_AGENT");
        assertThat(dto.name()).isEqualTo("我的助手");
        assertThat(dto.type()).isEqualTo("GENERAL");
        assertThat(dto.skills()).containsExactly("risk-dimension-review");
        assertThat(dto.tools()).containsExactly("rag_search");
        assertThat(dto.knowledgeScopes()).containsExactly("STATUTE_CIVIL");

        ArgumentCaptor<AgentConfigEntity> captor = ArgumentCaptor.forClass(AgentConfigEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("MY_AGENT");
        assertThat(captor.getValue().getSkillsJson()).contains("risk-dimension-review");
        verify(registry).reload(any());
    }

    @Test
    void create_filtersUnknownSkills() {
        when(repository.existsByCode("MY_AGENT")).thenReturn(false);

        var dto = service.create(new AgentConfigCreateRequest(
                "MY_AGENT",
                "我的助手",
                null,
                null,
                List.of("risk-dimension-review", "unknown-skill"),
                List.of(),
                List.of(),
                List.of(),
                true
        ));

        assertThat(dto.skills()).containsExactly("risk-dimension-review");
    }

    @Test
    void update_persistsSkills() {
        AgentConfigEntity entity = new AgentConfigEntity(
                "id-1",
                "MY_AGENT",
                "我的助手",
                "GENERAL",
                true,
                "dashscope:qwen-plus",
                "[]",
                "[]",
                "[]",
                "prompt",
                "[]"
        );
        when(repository.findByCode("MY_AGENT")).thenReturn(Optional.of(entity));

        var dto = service.update("MY_AGENT", new AgentConfigUpdateRequest(
                null,
                null,
                null,
                null,
                List.of("risk-dimension-review"),
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(dto.skills()).containsExactly("risk-dimension-review");
        assertThat(entity.getSkillsJson()).contains("risk-dimension-review");
        verify(registry).reload(any());
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(repository.existsByCode("MY_AGENT")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new AgentConfigCreateRequest(
                "MY_AGENT",
                "重复",
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void create_rejectsBuiltinCode() {
        assertThatThrownBy(() -> service.create(new AgentConfigCreateRequest(
                "GENERAL",
                "通用",
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("内置");
    }

    @Test
    void delete_removesCustomAgentAndReloads() {
        AgentConfigEntity entity = new AgentConfigEntity(
                "id-1",
                "MY_AGENT",
                "我的助手",
                "GENERAL",
                true,
                "dashscope:qwen-plus",
                "[]",
                "[]",
                "[]",
                "prompt",
                "[]"
        );
        when(repository.findByCode("MY_AGENT")).thenReturn(Optional.of(entity));

        service.delete("MY_AGENT");

        verify(repository).delete(entity);
        verify(registry).reload(any());
    }

    @Test
    void delete_rejectsBuiltinAgent() {
        assertThatThrownBy(() -> service.delete("STATUTE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内置");

        verify(repository, never()).delete(any());
    }

    @Test
    void legacyCreateAlsoCreatesUnpublishedVersionWhenBridgeIsConfigured() {
        var publicationService = org.mockito.Mockito.mock(AgentPublicationService.class);
        when(repository.existsByCode("MY_AGENT")).thenReturn(false);
        var bridged = new AgentConfigService(repository, registry, new ObjectMapper(), false, publicationService);

        bridged.create(new AgentConfigCreateRequest(
                "MY_AGENT", "我的助手", "dashscope:qwen-plus", "提示词",
                List.of(), List.of("rag_search"), List.of(), List.of(), true), "operator-1");

        var request = ArgumentCaptor.forClass(CreateAgentVersionRequest.class);
        verify(publicationService).createVersion(org.mockito.ArgumentMatchers.eq("MY_AGENT"), request.capture(), org.mockito.ArgumentMatchers.eq("operator-1"));
        assertThat(request.getValue().manifest().supportedRiskLevels()).contains("HIGH", "CRITICAL");
        assertThat(request.getValue().toolPolicy().toolNames()).containsExactly("rag_search");
    }
}
