package com.raglaw.rag.repository;

import com.raglaw.rag.domain.ContractRiskEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRiskRepository extends JpaRepository<ContractRiskEntity, String> {

    List<ContractRiskEntity> findByDocumentIdOrderByCreatedAtAsc(String documentId);

    void deleteByDocumentId(String documentId);
}
