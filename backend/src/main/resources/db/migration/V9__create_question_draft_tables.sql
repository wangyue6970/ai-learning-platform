ALTER TABLE import_file
    ADD COLUMN recognition_text LONGTEXT NULL AFTER error_message;

CREATE TABLE question_draft (
    id BIGINT NOT NULL AUTO_INCREMENT,
    library_id BIGINT NOT NULL,
    import_file_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'WAITING_CONFIRMATION',
    question_type VARCHAR(30) NULL,
    stem TEXT NULL,
    correct_answer JSON NULL,
    explanation TEXT NULL,
    knowledge_points JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (import_file_id) REFERENCES import_file(id) ON DELETE CASCADE,
    INDEX idx_question_draft_library_id (library_id),
    INDEX idx_question_draft_import_file_id (import_file_id),
    INDEX idx_question_draft_status (status)
);

CREATE TABLE question_draft_option (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_draft_id BIGINT NOT NULL,
    option_key VARCHAR(10) NOT NULL,
    content TEXT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (question_draft_id) REFERENCES question_draft(id) ON DELETE CASCADE,
    UNIQUE KEY uk_question_draft_option_key (question_draft_id, option_key),
    INDEX idx_question_draft_option_draft_id (question_draft_id)
);
