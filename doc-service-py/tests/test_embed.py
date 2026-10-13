"""POST /v1/embed + embedding provider tests.

Runs with ONLY the base requirements (stub provider): the batch/empty-list/
determinism tests pass without ``sentence_transformers``. The semantic sanity
check is guarded by ``pytest.importorskip`` so it only runs when the real model
is installed in the environment.
"""

from __future__ import annotations

import math

import pytest

from app.embeddings import EMBEDDING_DIM, _StubProvider


def _cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na == 0 or nb == 0:
        return 0.0
    return dot / (na * nb)


def test_embed_batch_returns_384_vectors_in_order(client):
    texts = ["đạo hàm của hàm số", "công thức tính đạo hàm", "giá cà phê hôm nay"]
    resp = client.post("/v1/embed", json={"texts": texts})
    assert resp.status_code == 200
    body = resp.json()
    assert body["dim"] == EMBEDDING_DIM == 384
    assert isinstance(body["model"], str) and body["model"]
    assert len(body["embeddings"]) == len(texts)
    for vec in body["embeddings"]:
        assert len(vec) == 384
        assert all(isinstance(x, (int, float)) for x in vec)

    # Order preserved: re-embedding the same inputs yields identical vectors.
    resp2 = client.post("/v1/embed", json={"texts": texts})
    assert resp2.status_code == 200
    assert resp2.json()["embeddings"] == body["embeddings"]


def test_embed_empty_list_returns_empty_embeddings(client):
    resp = client.post("/v1/embed", json={"texts": []})
    assert resp.status_code == 200
    body = resp.json()
    assert body["dim"] == 384
    assert body["embeddings"] == []


def test_stub_provider_is_deterministic_384_dim():
    """Stub fallback yields deterministic, L2-normalized 384-dim vectors."""
    stub = _StubProvider()
    assert stub.name == "stub"
    assert stub.dim == 384

    a = stub.embed(["đạo hàm của hàm số", ""])
    b = stub.embed(["đạo hàm của hàm số", ""])
    assert a == b  # deterministic
    assert len(a) == 2
    for vec in a:
        assert len(vec) == 384
        # L2-normalized (empty-text seed path also normalizes to unit length).
        assert math.isclose(sum(x * x for x in vec), 1.0, abs_tol=1e-6)


def test_real_model_semantic_sanity_check():
    """When the real model is installed, paraphrases cluster closer than noise.

    Skipped automatically when ``sentence_transformers`` is not importable so
    the suite stays green on a base-only install.
    """
    pytest.importorskip("sentence_transformers")

    from app.embeddings import _RealSentenceTransformerProvider

    provider = _RealSentenceTransformerProvider()
    vecs = provider.embed(
        ["đạo hàm của hàm số", "công thức tính đạo hàm", "giá cà phê hôm nay"]
    )
    assert len(vecs) == 3
    assert all(len(v) == 384 for v in vecs)

    c01 = _cosine(vecs[0], vecs[1])  # two calculus phrases
    c02 = _cosine(vecs[0], vecs[2])  # calculus vs coffee price
    c12 = _cosine(vecs[1], vecs[2])  # calculus vs coffee price
    assert c01 > c02
    assert c01 > c12


def test_externalized_settings_override_embed_and_cors_config(monkeypatch):
    """Externalized env vars override model names, dimensions, batch size, and device."""
    from app.config import Settings

    monkeypatch.setenv("EMBEDDING_MODEL", "custom-multilingual-embed-v2")
    monkeypatch.setenv("EMBEDDING_DIM", "512")
    monkeypatch.setenv("EMBEDDING_MAX_BATCH", "128")
    monkeypatch.setenv("EMBEDDING_DEVICE", "cuda")
    monkeypatch.setenv("CORRELATION_HEADER", "X-Request-Id")

    settings = Settings()
    assert settings.embedding_model == "custom-multilingual-embed-v2"
    assert settings.embedding_dim == 512
    assert settings.embedding_max_batch == 128
    assert settings.embedding_device == "cuda"
    assert settings.correlation_header == "X-Request-Id"


