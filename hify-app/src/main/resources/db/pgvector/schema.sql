-- Hify vector schema (PostgreSQL 18 + pgvector)
-- Apply to the dedicated vector database, for example:
--   psql -U postgres -d hify_vector -f hify-app/src/main/resources/db/pgvector/schema.sql
-- document_id and knowledge_base_id reference MySQL rows at the application layer.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunk (
                                id                BIGSERIAL    PRIMARY KEY,
                                knowledge_base_id BIGINT       NOT NULL,   -- 冗余，检索时免 JOIN
                                document_id       BIGINT       NOT NULL,
                                chunk_index       INT          NOT NULL,
                                content           TEXT         NOT NULL,
                                embedding         vector(1536) NOT NULL,   -- text-embedding-3-small
                                token_count       INT          NOT NULL DEFAULT 0,
                                deleted           SMALLINT     NOT NULL DEFAULT 0,
                                created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chunk_kb ON document_chunk (knowledge_base_id) WHERE deleted = 0;

-- The initial data volume is small, so retrieval uses exact cosine distance.
-- Add an HNSW vector_cosine_ops index only after production measurements justify it.
