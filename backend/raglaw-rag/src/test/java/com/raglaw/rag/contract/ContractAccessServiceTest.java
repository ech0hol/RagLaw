package com.raglaw.rag.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.auth.CurrentUserHolder;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.DocumentRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractAccessServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    private ContractAccessService service;

    @BeforeEach
    void setUp() {
        service = new ContractAccessService(documentRepository);
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    void requireOwnedContract_allowsOwner() {
        CurrentUserHolder.set("user-a");
        DocumentEntity document = new DocumentEntity("doc-1", "cat", "合同", "CONTRACT", "user-a", "key.pdf");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(document));

        DocumentEntity result = service.requireOwnedContract("doc-1");

        assertThat(result.getId()).isEqualTo("doc-1");
    }

    @Test
    void requireOwnedContract_rejectsNonOwner() {
        CurrentUserHolder.set("user-b");
        DocumentEntity document = new DocumentEntity("doc-1", "cat", "合同", "CONTRACT", "user-a", "key.pdf");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.requireOwnedContract("doc-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCodes.FORBIDDEN);
    }

    @Test
    void requireOwnedContract_rejectsNonContract() {
        CurrentUserHolder.set("user-a");
        DocumentEntity document = new DocumentEntity("doc-1", "cat", "法规", "STATUTE", "user-a", "key.md");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.requireOwnedContract("doc-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCodes.VALIDATION);
    }

    @Test
    void requireUserId_rejectsAnonymous() {
        assertThatThrownBy(() -> service.requireUserId())
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCodes.UNAUTHORIZED);
    }
}
