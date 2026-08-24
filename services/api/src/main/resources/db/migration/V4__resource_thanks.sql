CREATE TABLE resource_thank (
  resource_id BIGINT NOT NULL,
  anonymous_user_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (resource_id, anonymous_user_id),
  CONSTRAINT fk_thank_resource FOREIGN KEY (resource_id) REFERENCES resource(id) ON DELETE CASCADE,
  CONSTRAINT fk_thank_user FOREIGN KEY (anonymous_user_id) REFERENCES anonymous_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
