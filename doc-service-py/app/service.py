"""Service layer: orchestrates a full parse job end to end.

Pipeline (each stage logs with the correlation chain):
    validate -> detect-mime -> classify (per page) -> route+parse (per page)
    -> normalize -> store result (+ cache by sha256).

``submit_parse`` is the synchronous entrypoint called by the API: it validates,
checks the sha256 cache (returning a cached Document immediately), creates the
job, and schedules ``process_parse_job`` on a background task so the HTTP POST
can return 202 right away.
"""

from __future__ import annotations

import uuid

from app.config import get_settings
from app.engines.base import ParseContext
from app.jobs import JobStore, get_job_store
from app.logging_config import bind_context, get_logger, stage_context
from app.normalizer import normalize
from app.router import ParserRouter
from app.schema import Document, JobState
from app.validation import detect_mime, enforce_size, sha256_hex

log = get_logger()

_router = ParserRouter()


class SubmitResult:
    """Return value of :func:`submit_parse`."""

    def __init__(self, job_id: str, document_id: str, cached: bool) -> None:
        self.job_id = job_id
        self.document_id = document_id
        self.cached = cached


def submit_parse(
    *,
    data: bytes,
    filename: str,
    correlation_id: str | None,
    store: JobStore | None = None,
) -> SubmitResult:
    """Validate + register a parse job. Returns ids and a cache flag.

    When the sha256 of the bytes is already cached, the job is created and
    completed immediately from the cache (no engines run) and ``cached=True``.
    Otherwise the caller is expected to schedule :func:`process_parse_job`.
    """
    store = store or get_job_store()

    job_id = str(uuid.uuid4())
    bind_context(correlation_id=correlation_id, job_id=job_id)

    # --- validate (size + hash) -------------------------------------------
    with stage_context():
        enforce_size(data)
        digest = sha256_hex(data)
        log.info("upload validated", stage="validate", sha256=digest, size=len(data))

    # --- cache check (sha256 dedup) ---------------------------------------
    cached_doc = store.cache_get(digest)
    if cached_doc is not None:
        document_id = cached_doc.document_id
        bind_context(document_id=document_id)
        store.create(
            job_id=job_id,
            document_id=document_id,
            filename=filename,
            sha256=digest,
            correlation_id=correlation_id,
        )
        store.set_result(job_id, cached_doc, cached=True)
        log.info(
            "served from cache",
            stage="cache",
            cached=True,
            document_id=document_id,
        )
        return SubmitResult(job_id=job_id, document_id=document_id, cached=True)

    # --- fresh job --------------------------------------------------------
    document_id = str(uuid.uuid4())
    bind_context(document_id=document_id)
    store.create(
        job_id=job_id,
        document_id=document_id,
        filename=filename,
        sha256=digest,
        correlation_id=correlation_id,
    )
    log.info("job queued", stage="validate", cached=False, document_id=document_id)
    return SubmitResult(job_id=job_id, document_id=document_id, cached=False)


def process_parse_job(
    *,
    job_id: str,
    data: bytes,
    filename: str,
    correlation_id: str | None,
    store: JobStore | None = None,
) -> Document:
    """Run the full parse pipeline for an already-registered job.

    Intended to run inside a FastAPI BackgroundTask. Updates per-page progress
    and job status, writes the result + populates the sha256 cache, and honors
    DOCMIND_PERSIST_ARTIFACTS.
    """
    store = store or get_job_store()
    settings = get_settings()
    job = store.get(job_id)
    assert job is not None, "process_parse_job called for unknown job"

    bind_context(
        correlation_id=correlation_id,
        job_id=job_id,
        document_id=job.document_id,
    )

    try:
        store.set_status(job_id, JobState.RUNNING)

        # --- detect-mime (magic bytes, NOT extension) ---------------------
        mime = detect_mime(data)

        # --- classify per page --------------------------------------------
        decisions = _router.classify(mime, data)
        store.init_pages(job_id, [(d.page, d.kind.value) for d in decisions])

        # --- route + parse per page ---------------------------------------
        page_blocks = []
        order_cursor = 0
        for decision in decisions:
            store.set_page_progress(job_id, decision.page, "parsing")
            engine = _router.engine_for(decision.kind)
            ctx = ParseContext(
                job_id=job_id,
                document_id=job.document_id,
                correlation_id=correlation_id,
                filename=filename,
                mime=mime,
                page=decision.page,
                order_start=order_cursor,
            )
            blocks = engine.parse(data, ctx)
            order_cursor += len(blocks)
            page_blocks.append(blocks)
            store.set_page_progress(
                job_id, decision.page, "done", block_count=len(blocks)
            )
            store.bump_engine_breakdown(job_id, engine.name, len(blocks))
            _persist_artifacts(settings, job_id, decision.page, len(blocks))

        # --- normalize -> validated Document ------------------------------
        document = normalize(
            document_id=job.document_id,
            filename=filename,
            sha256=job.sha256,
            mime=mime,
            pages=len(decisions),
            page_blocks=page_blocks,
        )

        # --- store + cache ------------------------------------------------
        store.set_result(job_id, document, cached=False)
        store.cache_put(job.sha256, document)
        log.info("job succeeded", stage="normalize", block_count=len(document.blocks))
        return document

    except Exception as exc:  # keep the job observable on failure
        log.error("job failed", stage="error", error=str(exc))
        store.set_status(job_id, JobState.FAILED, error=str(exc))
        raise


def _persist_artifacts(settings, job_id: str, page: int, block_count: int) -> None:
    """Write placeholder debug artifacts when DOCMIND_PERSIST_ARTIFACTS=true."""
    if not settings.docmind_persist_artifacts:
        return
    try:
        out_dir = settings.artifacts_dir / job_id
        out_dir.mkdir(parents=True, exist_ok=True)
        # Stub placeholders: a per-page marker standing in for rendered images /
        # raw per-engine output. Real pipeline would dump page PNGs + JSON here.
        (out_dir / f"page-{page:04d}.txt").write_text(
            f"stub artifact: job={job_id} page={page} blocks={block_count}\n",
            encoding="utf-8",
        )
        log.info("artifact persisted", stage="artifact", page=page, dir=str(out_dir))
    except Exception as exc:  # never fail the job over debug artifacts
        log.warning("artifact persist failed", stage="artifact", page=page, error=str(exc))
