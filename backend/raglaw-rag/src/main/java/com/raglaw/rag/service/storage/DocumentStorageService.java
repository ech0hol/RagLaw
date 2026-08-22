package com.raglaw.rag.service.storage;

import java.io.InputStream;

public interface DocumentStorageService {

    String store(String documentId, String originalFilename, InputStream inputStream, long size, String contentType);

    InputStream load(String storageKey);
}
