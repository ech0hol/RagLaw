package com.raglaw.rag.contract;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.auth.CurrentUserHolder;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.DocumentRepository;
import org.springframework.stereotype.Service;

@Service
public class ContractAccessService {

    private static final String CONTRACT_DOC_TYPE = "CONTRACT";

    private final DocumentRepository documentRepository;

    public ContractAccessService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public String requireUserId() {
        String userId = CurrentUserHolder.get();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "未登录");
        }
        return userId;
    }

    public DocumentEntity requireOwnedContract(String documentId) {
        String userId = requireUserId();
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
        if (!CONTRACT_DOC_TYPE.equals(document.getDocType())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "仅支持合同文档");
        }
        if (!userId.equals(document.getUploaderId())) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "无权访问该合同");
        }
        return document;
    }

    public void validateOwnedContractContext(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }
        requireOwnedContract(documentId);
    }
}
