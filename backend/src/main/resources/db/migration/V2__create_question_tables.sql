CREATE TABLE question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    library_id BIGINT NOT NULL,
    question_type VARCHAR(30) NOT NULL,
    stem TEXT NOT NULL,
    correct_answer JSON NOT NULL,
    explanation TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (library_id) REFERENCES learning_library(id) ON DELETE CASCADE,
    INDEX idx_question_library_id (library_id)
);

CREATE TABLE question_option (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    option_key VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE,
    UNIQUE KEY uk_question_option_key (question_id, option_key),
    INDEX idx_question_option_question_id (question_id)
);
