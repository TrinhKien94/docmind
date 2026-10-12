"""HTTP API: /v1 parse-job endpoints + health probes.

Routes:
  POST /v1/parse            -> 202 {job_id, document_id, cached}
  GET  /v1/jobs/{job_id}         -> status + per-page progress + engine_breakdown
  GET  /v1/jobs/{job_id}/result  -> canonical Document JSON (404 until succeeded)
  GET  /healthz             -> liveness (always 200 while the process is up)
  GET  /readyz              -> readiness (200; reports optional ML lib importability)

Every handler reads the X-Correlation-Id header (set by the Java orchestrator)
and binds it + ids onto the logging context so all lines share the chain.
"""

from __future__ import annotations

import importlib.util

from fastapi import APIRouter, BackgroundTasks, File, Header, Request, UploadFile
from fastapi.responses import JSONResponse

from app.config import get_settings
from app.jobs import JobState, get_job_store
from app.logging_config import bind_context, clear_context, get_logger
from app.service import process_parse_job, submit_parse

log = get_logger()

router = APIRouter()

# Optional ML libraries whose importability /readyz reports.
_OPTIONAL_ML_LIBS = [
    "fitz",            # PyMuPDF
    "docling",
    "paddleocr",
    "paddle",          # paddlepaddle
    "sentence_transformers",
    "pdf_inspector",   # firecrawl pdf-inspector style classifier
]


def _correlation_from(header_value: str | None) -> str | None:
    return header_value or None


@router.post("/v1/parse", status_code=202)
async def parse(
    request: Request,
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    x_correlation_id: str | None = Header(default=None),
) -> JSONResponse:
    """Accept a multipart file; return 202 with job ids immediately."""
    correlation_id = _correlation_from(x_correlation_id)
    bind_context(correlation_id=correlation_id)
    try:
        data = await file.read()
        filename = file.filename or "upload.bin"

        store = get_job_store()
        result = submit_parse(
            data=data,
            filename=filename,
            correlation_id=correlation_id,
            store=store,
        )

        # Only schedule background parsing when it was NOT a cache hit.
        if not result.cached:
            background_tasks.add_task(
                process_parse_job,
                job_id=result.job_id,
                data=data,
                filename=filename,
                correlation_id=correlation_id,
                store=store,
            )

        return JSONResponse(
            status_code=202,
            content={
                "job_id": result.job_id,
                "document_id": result.document_id,
                "cached": result.cached,
            },
        )
    finally:
        clear_context()


@router.get("/v1/jobs/{job_id}")
async def get_job(
    job_id: str,
    x_correlation_id: str | None = Header(default=None),
) -> JSONResponse:
    """Return job status + per-page progress + engine_breakdown-so-far."""
    bind_context(correlation_id=_correlation_from(x_correlation_id), job_id=job_id)
    try:
        job = get_job_store().get(job_id)
        if job is None:
            return JSONResponse(status_code=404, content={"error": "job not found"})

        bind_context(document_id=job.document_id)
        payload = {
            "job_id": job.job_id,
            "document_id": job.document_id,
            "status": job.state.value if isinstance(job.state, JobState) else job.state,
            "filename": job.filename,
            "sha256": job.sha256,
            "cached": job.cached,
            "pages_total": job.pages_total,
            "pages_done": job.pages_done,
            "page_progress": [
                {
                    "page": pp.page,
                    "kind": pp.kind,
                    "status": pp.status,
                    "block_count": pp.block_count,
                }
                for pp in job.page_progress
            ],
            "engine_breakdown": job.engine_breakdown,
            "error": job.error,
        }
        log.info("job status served", stage="jobs", status=payload["status"])
        return JSONResponse(status_code=200, content=payload)
    finally:
        clear_context()


@router.get("/v1/jobs/{job_id}/result")
async def get_job_result(
    job_id: str,
    x_correlation_id: str | None = Header(default=None),
) -> JSONResponse:
    """Return the canonical Document JSON; 404 until the job has succeeded."""
    bind_context(correlation_id=_correlation_from(x_correlation_id), job_id=job_id)
    try:
        job = get_job_store().get(job_id)
        if job is None:
            return JSONResponse(status_code=404, content={"error": "job not found"})
        if job.result is None or job.state != JobState.SUCCEEDED:
            return JSONResponse(
                status_code=404,
                content={
                    "error": "result not ready",
                    "status": job.state.value
                    if isinstance(job.state, JobState)
                    else job.state,
                },
            )
        bind_context(document_id=job.document_id)
        log.info("job result served", stage="result", cached=job.cached)
        # mode="json" renders enums/tuples to their JSON-safe forms.
        return JSONResponse(status_code=200, content=job.result.model_dump(mode="json"))
    finally:
        clear_context()


@router.get("/healthz")
async def healthz() -> JSONResponse:
    """Liveness: always 200 while the process is up."""
    return JSONResponse(status_code=200, content={"status": "ok"})


@router.get("/readyz")
async def readyz() -> JSONResponse:
    """Readiness: 200 (stubs make us ready) + optional ML lib importability."""
    optional = {
        name: importlib.util.find_spec(name) is not None
        for name in _OPTIONAL_ML_LIBS
    }
    settings = get_settings()
    return JSONResponse(
        status_code=200,
        content={
            "status": "ready",
            "ready": True,  # stub engines always keep the service usable
            "optional_ml_libs": optional,
            "persist_artifacts": settings.docmind_persist_artifacts,
        },
    )
