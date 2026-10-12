"""Result cache: identical bytes on a second submit return cached:true."""

from __future__ import annotations

import time

from tests.conftest import PDF_BYTES


def _wait_succeeded(client, job_id, timeout=5.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        body = client.get(f"/v1/jobs/{job_id}").json()
        if body["status"] in ("succeeded", "failed"):
            return body
        time.sleep(0.02)
    return client.get(f"/v1/jobs/{job_id}").json()


def test_identical_bytes_second_submit_is_cached(client):
    files = {"file": ("a.bin", PDF_BYTES, "application/octet-stream")}

    first = client.post("/v1/parse", files=files)
    assert first.status_code == 202
    assert first.json()["cached"] is False
    first_body = _wait_succeeded(client, first.json()["job_id"])
    assert first_body["status"] == "succeeded"
    first_doc_id = first_body["document_id"]

    # Re-submit the exact same bytes.
    second = client.post("/v1/parse", files=files)
    assert second.status_code == 202
    second_body = second.json()
    assert second_body["cached"] is True
    # Cached hit reuses the already-parsed document id.
    assert second_body["document_id"] == first_doc_id

    # The cached job is immediately succeeded and its result matches.
    status = client.get(f"/v1/jobs/{second_body['job_id']}").json()
    assert status["status"] == "succeeded"
    assert status["cached"] is True

    r1 = client.get(f"/v1/jobs/{first.json()['job_id']}/result").json()
    r2 = client.get(f"/v1/jobs/{second_body['job_id']}/result").json()
    assert r1 == r2
