import asyncio
import hmac
import os
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Header, HTTPException, UploadFile
from markitdown import MarkItDown
from .guards import validate_archive, validate_audio, validate_pdf, validate_signature

MAX_BYTES = int(os.getenv("MARKITDOWN_MAX_BYTES", str(20 * 1024 * 1024)))
MAX_CHARS = int(os.getenv("MARKITDOWN_MAX_CHARS", "60000"))
MAX_CONCURRENCY = max(1, int(os.getenv("MARKITDOWN_MAX_CONCURRENCY", "2")))
MAX_PDF_PAGES = int(os.getenv("MARKITDOWN_MAX_PDF_PAGES", "80"))
MAX_AUDIO_SECONDS = int(os.getenv("MARKITDOWN_MAX_AUDIO_SECONDS", "600"))
MAX_ARCHIVE_ENTRIES = int(os.getenv("MARKITDOWN_MAX_ARCHIVE_ENTRIES", "2000"))
MAX_UNPACKED_BYTES = int(os.getenv("MARKITDOWN_MAX_UNPACKED_BYTES", str(100 * 1024 * 1024)))
MAX_COMPRESSION_RATIO = int(os.getenv("MARKITDOWN_MAX_COMPRESSION_RATIO", "200"))
WORKER_TOKEN = os.getenv("MARKITDOWN_WORKER_TOKEN", "")
ALLOWED_EXTENSIONS = {
    ".pdf", ".docx", ".pptx", ".xls", ".xlsx",
    ".txt", ".md", ".csv", ".json", ".xml", ".html", ".htm",
    ".png", ".jpg", ".jpeg", ".webp", ".wav", ".mp3", ".m4a",
}

app = FastAPI(title="Finals Compass MarkItDown Worker", docs_url=None, redoc_url=None)
conversion_slots = asyncio.Semaphore(MAX_CONCURRENCY)


def convert_local(path: Path):
    # A converter instance is request-scoped because third-party parsers are not
    # guaranteed to be thread-safe when multiple conversion slots are enabled.
    return MarkItDown(enable_plugins=False).convert_local(path)


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

    validate_signature(extension, data)
    validate_archive(extension, data, MAX_ARCHIVE_ENTRIES, MAX_UNPACKED_BYTES, MAX_COMPRESSION_RATIO)
    if extension == ".pdf":
        validate_pdf(data, MAX_PDF_PAGES)

    try:
        with tempfile.TemporaryDirectory(prefix="fc-markitdown-") as directory:
            local_path = Path(directory) / f"input{extension}"
            local_path.write_bytes(data)
            if extension in {".wav", ".mp3", ".m4a"}:
                validate_audio(local_path, MAX_AUDIO_SECONDS)
            try:
                await asyncio.wait_for(conversion_slots.acquire(), timeout=0.05)
            except TimeoutError:
                raise HTTPException(status_code=429, detail="worker is busy; retry later")
            try:
                result = await asyncio.to_thread(convert_local, local_path)
            finally:
                conversion_slots.release()
            markdown = getattr(result, "markdown", None) or getattr(result, "text_content", "")
    except HTTPException:
        raise
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
