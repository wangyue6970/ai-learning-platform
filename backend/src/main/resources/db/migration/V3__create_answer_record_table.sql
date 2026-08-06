CREATE TABLE answer_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    library_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    selected_answer JSON NOT NULL,
    is_correct BOOLEAN NOT NULL,
    answered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (library_id) REFERENCES learning_library(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE,
    INDEX idx_answer_record_library_id (library_id)
);
