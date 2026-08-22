package com.raglaw.agentadmin.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentConfigRepository extends JpaRepository<AgentConfigEntity, String> {

    List<AgentConfigEntity> findAllByOrderByCodeAsc();

    List<AgentConfigEntity> findByEnabledTrue();

    boolean existsByCode(String code);

    java.util.Optional<AgentConfigEntity> findByCode(String code);
}
