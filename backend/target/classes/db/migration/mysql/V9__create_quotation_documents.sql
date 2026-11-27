CREATE TABLE quotation_documents (
    revision_id BIGINT PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    storage_key VARCHAR(36),
    sha256 CHAR(64),
    size_bytes BIGINT,
    created_at DATETIME(6) NOT NULL,
    generated_at DATETIME(6),
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6),
    lease_token VARCHAR(36),
    lease_until DATETIME(6),
    last_error_code VARCHAR(32),
    CONSTRAINT fk_qdoc_revision FOREIGN KEY (revision_id) REFERENCES quotation_revisions(id),
    CONSTRAINT uq_qdoc_storage UNIQUE (storage_key),
    CONSTRAINT ck_qdoc_status CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT ck_qdoc_lease CHECK (
        (status = 'PROCESSING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'PROCESSING' AND lease_token IS NULL AND lease_until IS NULL)),
    CONSTRAINT ck_qdoc_candidate CHECK (
        (storage_key IS NULL AND sha256 IS NULL AND size_bytes IS NULL)
        OR (storage_key IS NOT NULL AND sha256 IS NOT NULL AND size_bytes IS NOT NULL AND size_bytes > 0)),
    CONSTRAINT ck_qdoc_ready CHECK (
        (status = 'READY' AND generated_at IS NOT NULL AND storage_key IS NOT NULL)
        OR (status <> 'READY' AND generated_at IS NULL)),
    CONSTRAINT ck_qdoc_attempt CHECK (attempt_count >= 0)
);

CREATE INDEX idx_qdoc_retry ON quotation_documents(status, next_attempt_at, revision_id);
CREATE INDEX idx_qdoc_lease ON quotation_documents(status, lease_until, revision_id);
