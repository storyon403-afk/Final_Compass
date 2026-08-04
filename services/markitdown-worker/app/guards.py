import io
import zipfile
from pathlib import Path

from fastapi import HTTPException
from mutagen import File as MutagenFile
from pypdf import PdfReader

OFFICE_ROOTS = {
    ".docx": "word/",
    ".pptx": "ppt/",
    ".xlsx": "xl/",
}
TEXT_EXTENSIONS = {".txt", ".md", ".csv", ".json", ".xml", ".html", ".htm"}


def _reject(detail: str, status: int = 400) -> None:
    raise HTTPException(status_code=status, detail=detail)


def validate_signature(extension: str, data: bytes) -> None:
    """Reject extension/content mismatches before a parser sees attacker-controlled bytes."""
    if extension == ".pdf" and not data.startswith(b"%PDF-"):
        _reject("file signature does not match PDF")
    elif extension in OFFICE_ROOTS and not data.startswith(b"PK\x03\x04"):
        _reject("file signature does not match Office Open XML")
    elif extension == ".xls" and not data.startswith(b"\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1"):
        _reject("file signature does not match legacy Excel")
    elif extension == ".png" and not data.startswith(b"\x89PNG\r\n\x1a\n"):
        _reject("file signature does not match PNG")
    elif extension in {".jpg", ".jpeg"} and not data.startswith(b"\xff\xd8\xff"):
        _reject("file signature does not match JPEG")
    elif extension == ".webp" and not (data.startswith(b"RIFF") and data[8:12] == b"WEBP"):
        _reject("file signature does not match WebP")
    elif extension == ".wav" and not (data.startswith(b"RIFF") and data[8:12] == b"WAVE"):
        _reject("file signature does not match WAV")
    elif extension == ".mp3" and not (data.startswith(b"ID3") or data[:2] in {b"\xff\xfb", b"\xff\xf3", b"\xff\xf2"}):
        _reject("file signature does not match MP3")
    elif extension == ".m4a" and not (len(data) >= 12 and data[4:8] == b"ftyp"):
        _reject("file signature does not match M4A")
    elif extension in TEXT_EXTENSIONS:
        if b"\x00" in data[:8192]:
            _reject("text file contains binary data")
        try:
            data.decode("utf-8")
        except UnicodeDecodeError:
            _reject("text file must use UTF-8 encoding")


def validate_archive(extension: str, data: bytes, max_entries: int, max_unpacked_bytes: int,
                     max_compression_ratio: int) -> None:
    if extension not in OFFICE_ROOTS:
        return
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            entries = archive.infolist()
            if len(entries) > max_entries:
                _reject("Office document contains too many archive entries", 413)
            total_unpacked = 0
            has_expected_root = False
            for entry in entries:
                if entry.flag_bits & 0x1:
                    _reject("encrypted Office archives are not supported")
                total_unpacked += entry.file_size
                if total_unpacked > max_unpacked_bytes:
                    _reject("Office document expands beyond the allowed size", 413)
                if entry.file_size > 0 and entry.compress_size == 0:
                    _reject("invalid Office archive compression metadata")
                if entry.compress_size and entry.file_size / entry.compress_size > max_compression_ratio:
                    _reject("Office document compression ratio is unsafe", 413)
                if entry.filename.startswith(OFFICE_ROOTS[extension]):
                    has_expected_root = True
            if not has_expected_root or "[Content_Types].xml" not in archive.namelist():
                _reject("file content does not match its Office extension")
    except zipfile.BadZipFile:
        _reject("invalid Office archive")


def validate_pdf(data: bytes, max_pages: int) -> None:
    try:
        reader = PdfReader(io.BytesIO(data), strict=False)
        if reader.is_encrypted:
            _reject("encrypted PDF files are not supported")
        if len(reader.pages) > max_pages:
            _reject(f"PDF exceeds the {max_pages}-page limit", 413)
    except HTTPException:
        raise
    except Exception:
        _reject("invalid PDF document")


def validate_audio(path: Path, max_seconds: int) -> None:
    try:
        audio = MutagenFile(path)
        duration = float(audio.info.length) if audio is not None and audio.info is not None else 0
    except Exception:
        _reject("invalid audio file")
    if duration <= 0:
        _reject("audio duration could not be determined")
    if duration > max_seconds:
        _reject(f"audio exceeds the {max_seconds}-second limit", 413)
