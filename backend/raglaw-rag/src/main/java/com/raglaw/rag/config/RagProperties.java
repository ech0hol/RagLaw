package com.raglaw.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raglaw.rag")
public class RagProperties {

    private Postgres postgres = new Postgres();
    private Minio minio = new Minio();
    private Storage storage = new Storage();
    private Embedding embedding = new Embedding();
    private Rabbit rabbit = new Rabbit();

    public Postgres getPostgres() {
        return postgres;
    }

    public void setPostgres(Postgres postgres) {
        this.postgres = postgres;
    }

    public Minio getMinio() {
        return minio;
    }

    public void setMinio(Minio minio) {
        this.minio = minio;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public Rabbit getRabbit() {
        return rabbit;
    }

    public void setRabbit(Rabbit rabbit) {
        this.rabbit = rabbit;
    }

    public static class Postgres {
        private boolean enabled;
        private String url = "jdbc:postgresql://localhost:5432/raglaw_vector";
        private String username = "raglaw";
        private String password = "raglaw";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Minio {
        private boolean enabled;
        private String endpoint = "http://localhost:9000";
        private String accessKey = "raglaw";
        private String secretKey = "raglawsecret";
        private String bucket = "raglaw";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    public static class Storage {
        private String localTempDir = "./tmp/raglaw-uploads";

        public String getLocalTempDir() {
            return localTempDir;
        }

        public void setLocalTempDir(String localTempDir) {
            this.localTempDir = localTempDir;
        }
    }

    public static class Embedding {
        private boolean enabled;
        private String apiKey = "";
        private String model = "text-embedding-v3";
        private int dimensions = 1024;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getDimensions() {
            return dimensions;
        }

        public void setDimensions(int dimensions) {
            this.dimensions = dimensions;
        }
    }

    public static class Rabbit {
        private boolean enabled;
        private String parseQueue = "raglaw.parse";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getParseQueue() {
            return parseQueue;
        }

        public void setParseQueue(String parseQueue) {
            this.parseQueue = parseQueue;
        }
    }
}
