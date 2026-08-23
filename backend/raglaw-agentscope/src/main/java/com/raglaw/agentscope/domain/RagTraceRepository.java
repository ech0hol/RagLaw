package com.raglaw.agentscope.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RagTraceRepository extends JpaRepository<RagTraceEntity, String> {

    List<RagTraceEntity> findTop50ByOrderByCreatedAtDesc();
}
