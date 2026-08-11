CREATE TABLE import_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    library_id BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'WAITING_RECOGNITION',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (library_id) REFERENCES learning_library(id) ON DELETE CASCADE,
    INDEX idx_import_batch_library_id (library_id)
);

CREATE TABLE import_file (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_batch_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'WAITING_RECOGNITION',
    error_message VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (import_batch_id) REFERENCES import_batch(id) ON DELETE CASCADE,
    INDEX idx_import_file_batch_id (import_batch_id),
    INDEX idx_import_file_status (status)
);
