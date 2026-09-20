package com.raglaw.agentadmin.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentVersionRepository extends JpaRepository<AgentVersionEntity, String> {
    List<AgentVersionEntity> findAllByOrderByAgentCodeAscVersionDesc();
    List<AgentVersionEntity> findByStatusOrderByAgentCodeAscVersionDesc(AgentPublishStatus status);
    Optional<AgentVersionEntity> findByAgentCodeAndVersion(String agentCode, int version);
    Optional<AgentVersionEntity> findTopByAgentCodeOrderByVersionDesc(String agentCode);
    List<AgentVersionEntity> findByAgentCodeOrderByVersionDesc(String agentCode);
    Optional<AgentVersionEntity> findTopByAgentCodeAndStatusOrderByVersionDesc(String agentCode, AgentPublishStatus status);
}
