-- Existing local learning data is preserved by assigning it to this development-only demo account.
-- Its password is BCrypt-hashed; the raw password is only shown in local setup guidance.
ALTER TABLE learning_library
    ADD COLUMN owner_id BIGINT NULL AFTER id;

INSERT INTO app_user (username, password_hash)
VALUES ('demo-user', '$2a$10$O57QGiI7RLcRrkDmhtxLiudSpHXKy95LKB3C/3gRT0whRxzBYP78i')
ON DUPLICATE KEY UPDATE username = VALUES(username);

UPDATE learning_library
SET owner_id = (SELECT id FROM app_user WHERE username = 'demo-user')
WHERE owner_id IS NULL;

ALTER TABLE learning_library
    MODIFY COLUMN owner_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_learning_library_owner
        FOREIGN KEY (owner_id) REFERENCES app_user(id),
    ADD INDEX idx_learning_library_owner_id (owner_id);
