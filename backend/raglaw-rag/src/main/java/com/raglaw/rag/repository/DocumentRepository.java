package com.raglaw.rag.repository;

import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    List<DocumentEntity> findByStatusOrderByCreatedAtDesc(DocStatus status);
}
