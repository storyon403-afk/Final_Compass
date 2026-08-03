import hmac
import os
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Header, HTTPException, UploadFile
from markitdown import MarkItDown

MAX_BYTES = int(os.getenv("MARKITDOWN_MAX_BYTES", str(20 * 1024 * 1024)))
MAX_CHARS = int(os.getenv("MARKITDOWN_MAX_CHARS", "60000"))
WORKER_TOKEN = os.getenv("MARKITDOWN_WORKER_TOKEN", "")
ALLOWED_EXTENSIONS = {
    ".pdf", ".docx", ".pptx", ".xls", ".xlsx",
    ".txt", ".md", ".csv", ".json", ".xml", ".html", ".htm",
    ".png", ".jpg", ".jpeg", ".webp", ".wav", ".mp3", ".m4a",
}

app = FastAPI(title="Finals Compass MarkItDown Worker", docs_url=None, redoc_url=None)
converter = MarkItDown(enable_plugins=False)


def authorize(token: str | None) -> None:
    if not WORKER_TOKEN:
        raise HTTPException(status_code=503, detail="worker token is not configured")
    if token is None or not hmac.compare_digest(token, WORKER_TOKEN):
        raise HTTPException(status_code=401, detail="invalid worker token")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/convert")
async def convert(
    file: UploadFile = File(...),
    x_worker_token: str | None = Header(default=None),
) -> dict[str, object]:
    authorize(x_worker_token)
    original_name = Path(file.filename or "attachment").name
    extension = Path(original_name).suffix.lower()
    if extension not in ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail="unsupported file type")

    data = await file.read(MAX_BYTES + 1)
    await file.close()
    if not data:
        raise HTTPException(status_code=400, detail="empty file")
    if len(data) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="file is too large")

    try:
        with tempfile.TemporaryDirectory(prefix="fc-markitdown-") as directory:
            local_path = Path(directory) / f"input{extension}"
            local_path.write_bytes(data)
            result = converter.convert_local(local_path)
            markdown = getattr(result, "markdown", None) or getattr(result, "text_content", "")
    except Exception as exception:
        raise HTTPException(status_code=422, detail="document conversion failed") from exception

    markdown = markdown.strip()
    truncated = len(markdown) > MAX_CHARS
    if truncated:
        markdown = markdown[:MAX_CHARS]
    return {
        "fileName": original_name,
        "contentType": file.content_type or "application/octet-stream",
        "markdown": markdown,
        "characters": len(markdown),
        "truncated": truncated,
    }
