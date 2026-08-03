import importlib

from fastapi.testclient import TestClient


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
