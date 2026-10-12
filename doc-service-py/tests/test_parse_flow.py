"""End-to-end parse flow: POST 202 -> job succeeds -> schema-valid result."""

from __future__ import annotations

import time

from app.schema import Document
from tests.conftest import PDF_BYTES


def _poll_until_done(client, job_id, timeout=5.0):
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        resp = client.get(f"/v1/jobs/{job_id}")
        assert resp.status_code == 200
        last = resp.json()
        if last["status"] in ("succeeded", "failed"):
            return last
        time.sleep(0.02)
    return last


def test_parse_returns_202_and_succeeds_with_schema_valid_result(client):
    files = {"file": ("notes.txt", PDF_BYTES, "application/octet-stream")}
    resp = client.post("/v1/parse", files=files)
    assert resp.status_code == 202
    body = resp.json()
    assert body["cached"] is False
    job_id = body["job_id"]
    assert body["document_id"]

    status = _poll_until_done(client, job_id)
    assert status is not None
    assert status["status"] == "succeeded", status
    # per-page progress populated and all done
    assert status["pages_total"] >= 1
    assert status["pages_done"] == status["pages_total"]
    assert status["engine_breakdown"]  # non-empty
    for pp in status["page_progress"]:
        assert pp["status"] == "done"
        assert pp["block_count"] >= 1

    # result conforms to the canonical schema
    rresp = client.get(f"/v1/jobs/{job_id}/result")
    assert rresp.status_code == 200
    doc = Document.model_validate(rresp.json())  # raises if non-conformant
    assert doc.document_id == body["document_id"]
    assert doc.source.sha256 and doc.source.mime == "application/pdf"
    assert doc.source.pages == status["pages_total"]
    assert len(doc.blocks) >= 1

    # global reading order is monotonic 0..n-1
    orders = [b.order for b in doc.blocks]
    assert orders == list(range(len(doc.blocks)))

    # engine_breakdown sums to the block count
    assert sum(doc.stats.engine_breakdown.values()) == len(doc.blocks)

    # every block carries the required canonical fields
    for b in doc.blocks:
        assert b.block_id
        assert b.page >= 1
        assert 0.0 <= b.confidence <= 1.0
        assert b.parse_engine in {"paddleocr", "docling", "pymupdf", "office"}
        assert b.parse_version


def test_result_404_before_completion(client):
    # Unknown job id -> 404
    resp = client.get("/v1/jobs/does-not-exist/result")
    assert resp.status_code == 404
