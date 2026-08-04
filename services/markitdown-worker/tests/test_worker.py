import importlib
import io
import wave
import zipfile

from fastapi.testclient import TestClient
from pypdf import PdfWriter


def load_client(monkeypatch):
    monkeypatch.setenv("MARKITDOWN_WORKER_TOKEN", "test-worker-token")
    from app import main
    importlib.reload(main)
    return TestClient(main.app)


def test_health_does_not_expose_configuration(monkeypatch):
    client = load_client(monkeypatch)
    assert client.get("/health").json() == {"status": "ok"}


def test_conversion_requires_internal_token(monkeypatch):
    client = load_client(monkeypatch)
    response = client.post("/convert", files={"file": ("note.txt", b"hello", "text/plain")})
    assert response.status_code == 401


def test_converts_text_and_rejects_unknown_extension(monkeypatch):
    client = load_client(monkeypatch)
    headers = {"X-Worker-Token": "test-worker-token"}
    converted = client.post("/convert", headers=headers,
                            files={"file": ("note.txt", b"# Hello\n\nFinals Compass", "text/plain")})
    assert converted.status_code == 200
    assert "Finals Compass" in converted.json()["markdown"]

    rejected = client.post("/convert", headers=headers,
                           files={"file": ("payload.exe", b"no", "application/octet-stream")})
    assert rejected.status_code == 400


def test_rejects_extension_signature_mismatch(monkeypatch):
    client = load_client(monkeypatch)
    response = client.post(
        "/convert",
        headers={"X-Worker-Token": "test-worker-token"},
        files={"file": ("fake.pdf", b"this is not a PDF", "application/pdf")},
    )
    assert response.status_code == 400
    assert "signature" in response.json()["detail"]


def test_rejects_office_archive_with_unsafe_expansion(monkeypatch):
    monkeypatch.setenv("MARKITDOWN_MAX_UNPACKED_BYTES", "32")
    client = load_client(monkeypatch)
    content = io.BytesIO()
    with zipfile.ZipFile(content, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", "x" * 20)
        archive.writestr("word/document.xml", "x" * 20)
    response = client.post(
        "/convert",
        headers={"X-Worker-Token": "test-worker-token"},
        files={"file": ("large.docx", content.getvalue(), "application/vnd.openxmlformats-officedocument.wordprocessingml.document")},
    )
    assert response.status_code == 413
    assert "expands" in response.json()["detail"]


def test_rejects_pdf_over_page_limit(monkeypatch):
    monkeypatch.setenv("MARKITDOWN_MAX_PDF_PAGES", "1")
    client = load_client(monkeypatch)
    content = io.BytesIO()
    writer = PdfWriter()
    writer.add_blank_page(width=100, height=100)
    writer.add_blank_page(width=100, height=100)
    writer.write(content)
    response = client.post(
        "/convert",
        headers={"X-Worker-Token": "test-worker-token"},
        files={"file": ("long.pdf", content.getvalue(), "application/pdf")},
    )
    assert response.status_code == 413
    assert "page limit" in response.json()["detail"]


def test_rejects_audio_over_duration_limit(monkeypatch):
    monkeypatch.setenv("MARKITDOWN_MAX_AUDIO_SECONDS", "1")
    client = load_client(monkeypatch)
    content = io.BytesIO()
    with wave.open(content, "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(8000)
        audio.writeframes(b"\x00\x00" * 16000)
    response = client.post(
        "/convert",
        headers={"X-Worker-Token": "test-worker-token"},
        files={"file": ("long.wav", content.getvalue(), "audio/wav")},
    )
    assert response.status_code == 413
    assert "second limit" in response.json()["detail"]
