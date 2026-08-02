ALTER TABLE anonymous_user
  ADD COLUMN app_user_id BIGINT NULL AFTER id,
  ADD CONSTRAINT fk_anonymous_app_user FOREIGN KEY (app_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
  ADD CONSTRAINT uk_anonymous_app_user UNIQUE (app_user_id);
