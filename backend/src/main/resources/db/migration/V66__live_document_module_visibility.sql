ALTER TABLE system_module_setting DROP CHECK chk_system_module_status;
ALTER TABLE system_module_setting
  ADD CONSTRAINT chk_system_module_status CHECK(status IN ('OPEN','MAINTENANCE','CLOSED'));

INSERT INTO system_module_setting(module_key,status,maintenance_title,maintenance_content)
VALUES('LIVE_DOCUMENT','CLOSED','活文档尚未开放','活文档当前仅对管理员开放。');
