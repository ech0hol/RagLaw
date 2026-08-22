package com.raglaw.rag.service.storage;

import com.raglaw.rag.config.RagProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(MinioDocumentStorage.class)
public class LocalDocumentStorage implements DocumentStorageService {

    private final Path baseDir;

    public LocalDocumentStorage(RagProperties ragProperties) {
        this.baseDir = Path.of(ragProperties.getStorage().getLocalTempDir());
    }

    @Override
    public String store(String documentId, String originalFilename, InputStream inputStream, long size, String contentType) {
        try {
            Path docDir = baseDir.resolve(documentId);
            Files.createDirectories(docDir);
            Path target = docDir.resolve(originalFilename);
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("本地存储失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public InputStream load(String storageKey) {
        try {
            return Files.newInputStream(Path.of(storageKey));
        } catch (IOException ex) {
            throw new IllegalStateException("本地读取失败: " + ex.getMessage(), ex);
        }
    }
}
