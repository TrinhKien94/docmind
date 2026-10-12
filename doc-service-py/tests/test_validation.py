"""Validation: deterministic sha256 + magic-byte MIME (NOT extension)."""

from __future__ import annotations

from app.validation import detect_mime, enforce_size, sha256_hex
from tests.conftest import MD_BYTES, PDF_BYTES, PNG_BYTES


def test_sha256_is_deterministic():
    a = sha256_hex(PDF_BYTES)
    b = sha256_hex(PDF_BYTES)
    assert a == b
    assert len(a) == 64
    # Different content -> different digest.
    assert sha256_hex(PNG_BYTES) != a


def test_detect_mime_pdf_by_magic_bytes():
    assert detect_mime(PDF_BYTES) == "application/pdf"


def test_detect_mime_png_by_magic_bytes():
    assert detect_mime(PNG_BYTES) == "image/png"


def test_detect_mime_ignores_extension_uses_bytes():
    # Content is a PDF; a filename like "notes.txt" must NOT change detection.
    # detect_mime only sees bytes — proving extension is irrelevant.
    assert detect_mime(PDF_BYTES) == "application/pdf"


def test_detect_mime_markdown_text():
    assert detect_mime(MD_BYTES) in ("text/markdown", "text/plain")


def test_enforce_size_rejects_empty():
    try:
        enforce_size(b"")
    except ValueError as exc:
        assert "empty" in str(exc)
    else:  # pragma: no cover
        raise AssertionError("empty upload should raise")
