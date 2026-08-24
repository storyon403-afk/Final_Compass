ALTER TABLE platform_ai_setting
  ADD COLUMN internal_test_open BOOLEAN NOT NULL DEFAULT FALSE AFTER default_provider;
