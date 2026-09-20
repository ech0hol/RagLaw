package com.raglaw.agentadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.domain.AgentVersionEntity;
import com.raglaw.agentadmin.domain.AgentVersionRepository;
import com.raglaw.agentadmin.dto.CreateAgentVersionRequest;
import com.raglaw.agentadmin.model.AgentCapabilityManifest;
import com.raglaw.agentadmin.model.AgentToolPolicy;
import com.raglaw.agentadmin.registry.AgentVersionRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AgentPublicationServiceTest {
    private AgentVersionRepository repository;
    private AgentPublicationService service;
    private AgentVersionEntity version;

    @BeforeEach
    void setUp() throws Exception {
        repository = Mockito.mock(AgentVersionRepository.class);
        version = entity("labor_expert", 2, AgentPublishStatus.SHADOW, 0.95);
        when(repository.findByAgentCodeAndVersion("labor_expert", 2)).thenReturn(Optional.of(version));
        when(repository.findTopByAgentCodeAndStatusOrderByVersionDesc("labor_expert", AgentPublishStatus.PUBLISHED)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByOrderByAgentCodeAscVersionDesc()).thenReturn(List.of());
        service = new AgentPublicationService(repository, new AgentManifestValidator(), new AgentVersionRegistry(), new ObjectMapper());
    }

    @Test
    void cannotPublishVersionBelowEvaluationGate() {
        version = entity("labor_expert", 2, AgentPublishStatus.SHADOW, 0.79);
        when(repository.findByAgentCodeAndVersion("labor_expert", 2)).thenReturn(Optional.of(version));
        assertThatThrownBy(() -> service.publishReadyVersion("labor_expert", 2, "admin"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Evaluation gate");
    }

    @Test
    void publishingShadowVersionMakesItPublished() {
        var dto = service.publishReadyVersion("labor_expert", 2, "admin");
        assertThat(dto.status()).isEqualTo(AgentPublishStatus.PUBLISHED);
        assertThat(version.getPublishedBy()).isEqualTo("admin");
    }

    @Test
    void draftCannotBePublishedBeforeValidation() {
        version = entity("labor_expert", 2, AgentPublishStatus.DRAFT, 0.95);
        when(repository.findByAgentCodeAndVersion("labor_expert", 2)).thenReturn(Optional.of(version));
        assertThatThrownBy(() -> service.publishReadyVersion("labor_expert", 2, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only shadow");
    }

    @Test
    void createVersionStartsAsDraftAndDoesNotEnterRegistry() {
        when(repository.findTopByAgentCodeOrderByVersionDesc("labor_expert")).thenReturn(Optional.empty());
        var created = service.createVersion("labor_expert", new CreateAgentVersionRequest(
                null, "dashscope:qwen-plus", "prompt", manifest(), new AgentToolPolicy(List.of(), Set.of()),
                List.of(), List.of(), List.of(), 0.0, "sha-new"), "admin");
        assertThat(created.status()).isEqualTo(AgentPublishStatus.DRAFT);
        assertThat(service.publishedCandidates()).isEmpty();
    }

    private static AgentCapabilityManifest manifest() {
        return new AgentCapabilityManifest(Set.of("LABOR_LAW"), Set.of("LEGAL_ANALYSIS"),
                Set.of("LEGAL_ANALYSIS"), Set.of("HIGH"), Set.of(), "Result");
    }

    private static AgentVersionEntity entity(String code, int version, AgentPublishStatus status, double score) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return new AgentVersionEntity("id-" + version, code, version, status, "dashscope:qwen-plus", "prompt",
                    mapper.writeValueAsString(manifest()),
                    mapper.writeValueAsString(new AgentToolPolicy(List.of(), Set.of())), "[]", "[]", "[]", score, "sha-" + version, Instant.now());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
