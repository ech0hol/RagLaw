package com.raglaw.rag.service.storage;

import com.raglaw.rag.config.RagProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "raglaw.rag.minio", name = "enabled", havingValue = "true")
public class MinioDocumentStorage implements DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioDocumentStorage.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioDocumentStorage(RagProperties ragProperties) {
        RagProperties.Minio minio = ragProperties.getMinio();
        this.bucket = minio.getBucket();
        this.minioClient = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
        ensureBucket();
    }

    @Override
    public String store(String documentId, String originalFilename, InputStream inputStream, long size, String contentType) {
        String key = "rag/original/" + documentId + "/" + originalFilename;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(inputStream, size, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
            return key;
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO 上传失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public InputStream load(String storageKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("MinIO 读取失败: " + ex.getMessage(), ex);
        }
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket {}", bucket);
            }
        } catch (Exception ex) {
            log.warn("MinIO bucket check failed: {}", ex.getMessage());
        }
    }
}
