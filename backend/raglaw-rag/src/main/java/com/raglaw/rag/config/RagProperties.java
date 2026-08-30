package com.raglaw.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raglaw.rag")
public class RagProperties {

    private Elasticsearch elasticsearch = new Elasticsearch();
    private Outbox outbox = new Outbox();
    private Minio minio = new Minio();
    private Storage storage = new Storage();
    private Embedding embedding = new Embedding();
    private Rabbit rabbit = new Rabbit();
    private Retrieval retrieval = new Retrieval();
    private KnowledgeSearch knowledgeSearch = new KnowledgeSearch();
    private Contract contract = new Contract();

    public Elasticsearch getElasticsearch() {
        return elasticsearch;
    }

    public void setElasticsearch(Elasticsearch elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox;
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

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval) {
        this.retrieval = retrieval;
    }

    public KnowledgeSearch getKnowledgeSearch() {
        return knowledgeSearch;
    }

    public void setKnowledgeSearch(KnowledgeSearch knowledgeSearch) {
        this.knowledgeSearch = knowledgeSearch;
    }

    public Contract getContract() {
        return contract;
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public static class Contract {
        private boolean enabled = true;
        private String model = "qwen-max";
        private int chunksPerBatch = 4;
        private int maxRisksPerDocument = 30;
        private int ragHitLimit = 8;
        private int chatContextMaxChars = 12_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getChunksPerBatch() {
            return chunksPerBatch;
        }

        public void setChunksPerBatch(int chunksPerBatch) {
            this.chunksPerBatch = chunksPerBatch;
        }

        public int getMaxRisksPerDocument() {
            return maxRisksPerDocument;
        }

        public void setMaxRisksPerDocument(int maxRisksPerDocument) {
            this.maxRisksPerDocument = maxRisksPerDocument;
        }

        public int getRagHitLimit() {
            return ragHitLimit;
        }

        public void setRagHitLimit(int ragHitLimit) {
            this.ragHitLimit = ragHitLimit;
        }

        public int getChatContextMaxChars() {
            return chatContextMaxChars;
        }

        public void setChatContextMaxChars(int chatContextMaxChars) {
            this.chatContextMaxChars = chatContextMaxChars;
        }
    }

    public static class Retrieval {
        private int displayExcerptMaxChars = 320;
        private int llmContextMaxChars = 800;
        private int maxChunksPerDocument = 2;
        private int candidateMultiplier = 4;
        private double vectorMinSimilarity = 0.40;
        private double fulltextMinRatio = 0.30;
        private boolean funnelEnabled = true;
        private double documentConfidenceThreshold = 0.55;
        private double topicConfidenceThreshold = 0.45;
        private int funnelTopDocuments = 3;
        private int funnelTopTopics = 3;
        private int funnelMaxDocumentCandidates = 50;

        public int getDisplayExcerptMaxChars() {
            return displayExcerptMaxChars;
        }

        public void setDisplayExcerptMaxChars(int displayExcerptMaxChars) {
            this.displayExcerptMaxChars = displayExcerptMaxChars;
        }

        public int getLlmContextMaxChars() {
            return llmContextMaxChars;
        }

        public void setLlmContextMaxChars(int llmContextMaxChars) {
            this.llmContextMaxChars = llmContextMaxChars;
        }

        public int getMaxChunksPerDocument() {
            return maxChunksPerDocument;
        }

        public void setMaxChunksPerDocument(int maxChunksPerDocument) {
            this.maxChunksPerDocument = maxChunksPerDocument;
        }

        public int getCandidateMultiplier() {
            return candidateMultiplier;
        }

        public void setCandidateMultiplier(int candidateMultiplier) {
            this.candidateMultiplier = candidateMultiplier;
        }

        public double getVectorMinSimilarity() {
            return vectorMinSimilarity;
        }

        public void setVectorMinSimilarity(double vectorMinSimilarity) {
            this.vectorMinSimilarity = vectorMinSimilarity;
        }

        public double getFulltextMinRatio() {
            return fulltextMinRatio;
        }

        public void setFulltextMinRatio(double fulltextMinRatio) {
            this.fulltextMinRatio = fulltextMinRatio;
        }

        public boolean isFunnelEnabled() {
            return funnelEnabled;
        }

        public void setFunnelEnabled(boolean funnelEnabled) {
            this.funnelEnabled = funnelEnabled;
        }

        public double getDocumentConfidenceThreshold() {
            return documentConfidenceThreshold;
        }

        public void setDocumentConfidenceThreshold(double documentConfidenceThreshold) {
            this.documentConfidenceThreshold = documentConfidenceThreshold;
        }

        public double getTopicConfidenceThreshold() {
            return topicConfidenceThreshold;
        }

        public void setTopicConfidenceThreshold(double topicConfidenceThreshold) {
            this.topicConfidenceThreshold = topicConfidenceThreshold;
        }

        public int getFunnelTopDocuments() {
            return funnelTopDocuments;
        }

        public void setFunnelTopDocuments(int funnelTopDocuments) {
            this.funnelTopDocuments = funnelTopDocuments;
        }

        public int getFunnelTopTopics() {
            return funnelTopTopics;
        }

        public void setFunnelTopTopics(int funnelTopTopics) {
            this.funnelTopTopics = funnelTopTopics;
        }

        public int getFunnelMaxDocumentCandidates() {
            return funnelMaxDocumentCandidates;
        }

        public void setFunnelMaxDocumentCandidates(int funnelMaxDocumentCandidates) {
            this.funnelMaxDocumentCandidates = funnelMaxDocumentCandidates;
        }
    }

    public static class KnowledgeSearch {
        private boolean useFunnel = true;

        public boolean isUseFunnel() {
            return useFunnel;
        }

        public void setUseFunnel(boolean useFunnel) {
            this.useFunnel = useFunnel;
        }
    }

    public static class Elasticsearch {
        private boolean enabled;
        private String uri = "http://localhost:9200";
        private String indexName = "raglaw_chunks";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }
    }

    public static class Outbox {
        private boolean enabled = true;
        private long pollIntervalMs = 30_000;
        private int maxAttempts = 5;
        private int batchSize = 50;
        private long failedRetryDelayMs = 60_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getFailedRetryDelayMs() {
            return failedRetryDelayMs;
        }

        public void setFailedRetryDelayMs(long failedRetryDelayMs) {
            this.failedRetryDelayMs = failedRetryDelayMs;
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
        private String indexQueue = "raglaw.index";
        private String parseDlq = "raglaw.parse.dlq";
        private String indexDlq = "raglaw.index.dlq";

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

        public String getIndexQueue() {
            return indexQueue;
        }

        public void setIndexQueue(String indexQueue) {
            this.indexQueue = indexQueue;
        }

        public String getParseDlq() {
            return parseDlq;
        }

        public void setParseDlq(String parseDlq) {
            this.parseDlq = parseDlq;
        }

        public String getIndexDlq() {
            return indexDlq;
        }

        public void setIndexDlq(String indexDlq) {
            this.indexDlq = indexDlq;
        }
    }
}
