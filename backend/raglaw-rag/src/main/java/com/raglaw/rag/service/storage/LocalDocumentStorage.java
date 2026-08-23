package com.raglaw.rag.service.storage;

import com.raglaw.rag.config.RagProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
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

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(storageKey);
            if (path.toFile().isDirectory()) {
                deleteDirectory(path);
            } else if (path.toFile().exists()) {
                Files.delete(path);
                Path parent = path.getParent();
                if (parent != null && parent.toFile().isDirectory() && isDirectoryEmpty(parent)) {
                    Files.delete(parent);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("本地删除失败: " + ex.getMessage(), ex);
        }
    }

    private static boolean isDirectoryEmpty(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.findFirst().isEmpty();
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!dir.toFile().exists()) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ex) {
                    throw new IllegalStateException("本地删除失败: " + ex.getMessage(), ex);
                }
            });
        }
    }
}
