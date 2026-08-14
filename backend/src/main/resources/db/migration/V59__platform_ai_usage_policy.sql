ALTER TABLE platform_ai_setting
  ADD COLUMN qualified_user_limits_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER internal_test_open,
  ADD COLUMN calls_per_minute INT NOT NULL DEFAULT 6 AFTER qualified_user_limits_enabled,
  ADD COLUMN platform_daily_calls INT NOT NULL DEFAULT 20 AFTER calls_per_minute,
  ADD COLUMN platform_monthly_tokens INT NOT NULL DEFAULT 100000 AFTER platform_daily_calls;

