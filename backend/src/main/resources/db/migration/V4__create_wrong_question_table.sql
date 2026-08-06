CREATE TABLE wrong_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    library_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    consecutive_correct_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (library_id) REFERENCES learning_library(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE,
    UNIQUE KEY uk_wrong_question_library_question (library_id, question_id)
);
