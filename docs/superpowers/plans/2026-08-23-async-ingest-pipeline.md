# 异步入库完整管线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split document ingest into parse and index RabbitMQ stages with parent-child chunking and observable document status, while preserving sync fallback when RabbitMQ is disabled.

**Architecture:** `DocumentUploadService` enqueues `documentId` to `raglaw.parse`. `ParseMessageConsumer` extracts text, writes parent+child chunks to MySQL, sets status `INDEXING`, enqueues to `raglaw.index`. `IndexMessageConsumer` embeds child chunks and upserts pgvector, then sets `INDEXED` or `AWAITING_APPROVAL`. `IngestPipeline.sync()` runs both phases inline when `RABBITMQ_ENABLED=false`.

**Tech Stack:** Java 17, Spring Boot 3, Spring AMQP, RabbitMQ, MySQL 8, pgvector, existing `MarkdownChunker` / `EmbeddingService`.

**Spec:** [`docs/superpowers/specs/2026-08-23-async-ingest-pipeline-design.md`](../specs/2026-08-23-async-ingest-pipeline-design.md)

## Global Constraints

- Flyway migration **V13** for ingest stage columns (H2 PDF highlight uses **V14**)
- Queue names: `raglaw.parse`, `raglaw.index`; DLQ: `raglaw.parse.dlq`, `raglaw.index.dlq`
- Message body: `documentId` string
- `RABBITMQ_ENABLED=false` must keep current sync behavior passing `mvn test`
- Embedding: **text-embedding-v3** (1024 dim) or mock when `RAGLAW_LLM_MOCK=true`
- Retrieval targets **child** chunks only

---

## File Map

| File | Responsibility |
|------|----------------|
| `V13__document_ingest_stage.sql` | `ingest_stage`, `ingest_error` on `raglaw_document` |
| `IngestPipeline.java` | Orchestrates `parse(document)` + `index(document)` |
| `ParseMessageConsumer.java` | Calls `ingestPipeline.parse()` only |
| `IndexMessageConsumer.java` | New consumer for index queue |
| `RabbitMqConfig.java` | Index queue + DLQ bindings |
| `MarkdownChunker.java` | Parent-child chunk output |
| `DocumentUploadService.java` | Set `PENDING`, enqueue parse |
| `IngestService.java` | Delegate to `IngestPipeline` or shrink to thin wrapper |
| `IngestPipelineTest.java` | Unit tests for chunk hierarchy |

---

### Task 1: Flyway V13 — ingest stage columns

**Files:**
- Create: `backend/raglaw-server/src/main/resources/db/migration/V13__document_ingest_stage.sql`
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/domain/DocumentEntity.java`
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/domain/DocStatus.java` (if needed)

**Interfaces:**
- Produces: `DocumentEntity.ingestStage` (`String`), `DocumentEntity.ingestError` (`String`)

- [ ] **Step 1: Write migration**

```sql
ALTER TABLE raglaw_document
  ADD COLUMN ingest_stage VARCHAR(32) NULL AFTER status,
  ADD COLUMN ingest_error TEXT NULL AFTER ingest_stage;
```

- [ ] **Step 2: Add fields to `DocumentEntity`**

```java
@Column(name = "ingest_stage", length = 32)
private String ingestStage;

@Column(name = "ingest_error", columnDefinition = "TEXT")
private String ingestError;
```

- [ ] **Step 3: Run Flyway**

Run: `cd backend && mvn -q -pl raglaw-server spring-boot:run` (with Docker MySQL up)  
Expected: V13 applied, app starts without schema validation errors

- [ ] **Step 4: Commit**

```bash
git add backend/raglaw-server/src/main/resources/db/migration/V13__document_ingest_stage.sql backend/raglaw-rag/src/main/java/com/raglaw/rag/domain/DocumentEntity.java
git commit -m "feat: add document ingest_stage columns for async pipeline"
```

---

### Task 2: Extract `IngestPipeline` from `IngestService`

**Files:**
- Create: `backend/raglaw-rag/src/main/java/com/raglaw/rag/service/IngestPipeline.java`
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/service/IngestService.java`

**Interfaces:**
- Consumes: `DocumentRepository`, `DocumentChunkRepository`, `DocumentTextExtractor`, `EmbeddingService`, `VectorStoreService`
- Produces:
  - `void parse(DocumentEntity document)` — extract, chunk, write MySQL, set `ingest_stage=PARSED`
  - `void index(DocumentEntity document)` — embed children, upsert vectors, set final status
  - `void sync(DocumentEntity document)` — `parse` then `index`

- [ ] **Step 1: Write failing test**

Create `backend/raglaw-rag/src/test/java/com/raglaw/rag/service/IngestPipelineTest.java`:

```java
@Test
void sync_setsIndexedStatus() {
    // mock repos + extractor returning fixture markdown
    pipeline.sync(document);
    assertEquals(DocStatus.INDEXED, document.getStatus());
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `cd backend && mvn -q -pl raglaw-rag test -Dtest=IngestPipelineTest`  
Expected: FAIL — class not found

- [ ] **Step 3: Move parse/index logic from `IngestService.ingest()` into `IngestPipeline`**

Split at embedding boundary: `parse()` stops before `embeddingService.embed()`; `index()` starts there.

- [ ] **Step 4: `IngestService.ingest()` delegates to `ingestPipeline.sync()`**

- [ ] **Step 5: Run tests**

Run: `cd backend && mvn -q -pl raglaw-rag test`  
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git commit -m "refactor: extract IngestPipeline parse/index phases"
```

---

### Task 3: Parent-child `MarkdownChunker`

**Files:**
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/ingest/MarkdownChunker.java`
- Create: `backend/raglaw-rag/src/test/java/com/raglaw/rag/ingest/MarkdownChunkerTest.java`
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/service/IngestPipeline.java`

**Interfaces:**
- Produces: `record ChunkDraft(String content, String parentId, int orderIndex)` list
- STATUTE: `##` / `###` headers → parent; paragraphs → child with `parentId`

- [ ] **Step 1: Write failing test**

```java
@Test
void statuteMarkdown_createsParentChildChunks() {
    String md = "## 第一章\n\n第一段。\n\n第二段。\n\n## 第二章\n\n第三段。";
    List<ChunkDraft> chunks = MarkdownChunker.chunkHierarchy(md);
    long parents = chunks.stream().filter(c -> c.parentId() == null).count();
    long children = chunks.stream().filter(c -> c.parentId() != null).count();
    assertTrue(parents >= 2);
    assertTrue(children >= 3);
}
```

- [ ] **Step 2: Run test — expect FAIL**

- [ ] **Step 3: Implement `chunkHierarchy()` per spec rules**

PDF/DOCX fallback: virtual parent every 5 paragraphs (MVP).

- [ ] **Step 4: `IngestPipeline.parse()` persists `parent_id` on `DocumentChunkEntity`**

- [ ] **Step 5: Run tests — expect PASS**

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: parent-child markdown chunking for ingest pipeline"
```

---

### Task 4: Index queue + `IndexMessageConsumer`

**Files:**
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/messaging/RabbitMqConfig.java`
- Create: `backend/raglaw-rag/src/main/java/com/raglaw/rag/messaging/IndexMessageConsumer.java`
- Modify: `backend/raglaw-server/src/main/resources/application.yml`
- Modify: `.env.example`

**Interfaces:**
- Config: `raglaw.rag.rabbit.index-queue: raglaw.index`
- Produces: `IndexMessageConsumer.onIndexJob(String documentId)` → `ingestPipeline.index(document)`

- [ ] **Step 1: Add index queue bean + DLQ in `RabbitMqConfig`**

Mirror parse queue pattern with `raglaw.index` and `raglaw.index.dlq`.

- [ ] **Step 2: Create `IndexMessageConsumer`**

```java
@RabbitListener(queues = "${raglaw.rag.rabbit.index-queue}")
public void onIndexJob(String documentId) {
    documentRepository.findById(documentId).ifPresent(doc -> {
        try {
            ingestPipeline.index(doc);
        } catch (Exception ex) {
            doc.setIngestStage("FAILED");
            doc.setIngestError(ex.getMessage());
            documentRepository.save(doc);
            throw ex;
        }
    });
}
```

- [ ] **Step 3: `application.yml` + `.env.example` document `index-queue`**

- [ ] **Step 4: Manual verify with RabbitMQ enabled**

Upload fixture → observe parse then index logs → `INDEXED` status.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add raglaw.index queue and IndexMessageConsumer"
```

---

### Task 5: Refactor `ParseMessageConsumer` + upload flow

**Files:**
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/messaging/ParseMessageConsumer.java`
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/service/DocumentUploadService.java`
- Modify: `backend/raglaw-rag/src/main/java/com/raglaw/rag/service/IngestPipeline.java`

**Interfaces:**
- `ParseMessageConsumer` calls `ingestPipeline.parse()` then publishes to index queue
- `DocumentUploadService` sets `status=PENDING`, `ingest_stage=PENDING`, enqueues parse

- [ ] **Step 1: Update `ParseMessageConsumer`**

Replace `ingestService.ingest(document)` with:
1. `ingestPipeline.parse(document)`
2. `rabbitTemplate.convertAndSend(indexQueue, documentId)`

- [ ] **Step 2: Update `DocumentUploadService` async branch**

On upload when Rabbit enabled: save `PENDING`, send to parse queue (not full ingest).

- [ ] **Step 3: Sync fallback**

When `RABBITMQ_ENABLED=false`, `DocumentUploadService` or admin ingest API calls `ingestPipeline.sync()`.

- [ ] **Step 4: CASE approval gate**

In `index()`: if `docType=CASE` and not approved, set `AWAITING_APPROVAL` and skip vector upsert (per spec).

- [ ] **Step 5: Run full backend tests**

Run: `cd backend && mvn -q test`  
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: two-stage async ingest with PENDING upload response"
```

---

### Task 6: Health + acceptance verification

**Files:**
- Modify: `backend/raglaw-server/src/main/java/com/raglaw/server/web/HealthController.java`

- [ ] **Step 1: Extend health payload with `rabbitIndexQueue` name when Rabbit enabled**

- [ ] **Step 2: Acceptance checklist**

1. Upload PDF with `RABBITMQ_ENABLED=true` → status `PENDING` → `INDEXED`
2. Fixture statute doc has `parent_id` non-null on child chunks
3. `RABBITMQ_ENABLED=false` sync path still indexes
4. Force index failure → message lands in DLQ after 3 retries

- [ ] **Step 3: Commit**

```bash
git commit -m "chore: health check for async ingest pipeline"
```

---

## Self-Review

| Spec requirement | Task |
|------------------|------|
| Two queues parse→index | Task 4, 5 |
| Parent-child chunks | Task 3 |
| DLQ on failure | Task 4 |
| Sync fallback | Task 2, 5 |
| PENDING upload response | Task 5 |
| CASE approval gate | Task 5 |
