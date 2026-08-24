CREATE TABLE account_number_sequence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  series_key VARCHAR(40) NOT NULL UNIQUE,
  account_prefix VARCHAR(32) NOT NULL,
  number_width INT NOT NULL DEFAULT 2,
  next_value BIGINT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO account_number_sequence(series_key,account_prefix,number_width,next_value)
SELECT 'BETA_ROUND_2','beta2-',2,
       COALESCE(MAX(CASE WHEN username REGEXP '^beta2-[0-9]+$' THEN CAST(SUBSTRING(username,7) AS UNSIGNED) END),0)+1
FROM app_user;

CREATE TABLE account_reservation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id BIGINT NOT NULL UNIQUE,
  sequence_id BIGINT NOT NULL,
  reserved_username VARCHAR(64) NOT NULL UNIQUE,
  sequence_value BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
  reserved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  consumed_at TIMESTAMP NULL,
  CONSTRAINT fk_account_reservation_request FOREIGN KEY (request_id) REFERENCES beta_access_request(id),
  CONSTRAINT fk_account_reservation_sequence FOREIGN KEY (sequence_id) REFERENCES account_number_sequence(id),
  INDEX idx_account_reservation_status (status,reserved_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
