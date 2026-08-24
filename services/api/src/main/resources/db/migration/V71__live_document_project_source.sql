ALTER TABLE live_document
  ADD COLUMN source_model VARCHAR(32) NOT NULL DEFAULT 'live-project-v1' AFTER source_format,
  ADD COLUMN document_js LONGTEXT NULL AFTER document_css,
  ADD COLUMN manifest_json JSON NULL AFTER document_js;

UPDATE live_document
SET document_css=COALESCE(document_css,''),
    document_js=COALESCE(document_js,''),
    manifest_json=JSON_OBJECT(
      'format','live-project-v1',
      'entry','content.md',
      'files',JSON_ARRAY('content.md','document.css','document.js','manifest.json'),
      'runtime',JSON_OBJECT('content','markdown-hybrid','behavior','sandboxed')
    );

ALTER TABLE live_document
  MODIFY document_css TEXT NOT NULL,
  MODIFY document_js LONGTEXT NOT NULL,
  MODIFY manifest_json JSON NOT NULL;
