ALTER TABLE import_file
    ADD COLUMN total_chunk_count INT NOT NULL DEFAULT 0 AFTER recognition_text,
    ADD COLUMN completed_chunk_count INT NOT NULL DEFAULT 0 AFTER total_chunk_count,
    ADD COLUMN generated_draft_count INT NOT NULL DEFAULT 0 AFTER completed_chunk_count,
    ADD COLUMN estimated_question_count INT NOT NULL DEFAULT 0 AFTER generated_draft_count;
