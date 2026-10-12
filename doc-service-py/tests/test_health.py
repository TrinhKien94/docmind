"""Health probes: /healthz and /readyz both return 200."""

from __future__ import annotations


def test_healthz_ok(client):
    resp = client.get("/healthz")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_readyz_ok_with_optional_lib_report(client):
    resp = client.get("/readyz")
    assert resp.status_code == 200
    body = resp.json()
    assert body["ready"] is True
    # Reports importability of optional ML libs; absent in CI is fine.
    assert "optional_ml_libs" in body
    assert isinstance(body["optional_ml_libs"], dict)
    # fitz/docling/paddleocr keys are present even if False.
    assert "fitz" in body["optional_ml_libs"]
