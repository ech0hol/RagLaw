package com.raglaw.rag.repository;

import com.raglaw.rag.domain.CaseStatuteRefEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseStatuteRefRepository extends JpaRepository<CaseStatuteRefEntity, String> {

    List<CaseStatuteRefEntity> findByCaseDocId(String caseDocId);

    List<CaseStatuteRefEntity> findByStatuteId(String statuteId);
}
