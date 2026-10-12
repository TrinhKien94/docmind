"""In-memory job lifecycle store + sha256-keyed result cache.

Thread-safe enough for a single-process dev service (a lock guards mutations).
Statuses: queued -> running -> succeeded | failed.

TODO (production): move the JobStore to Postgres (job table) and the parse
queue to a real broker (e.g. Redis/RQ or a durable queue); move the result
cache to a shared store keyed by sha256 so dedup survives restarts and scales
across workers. The in-memory versions here are deliberately simple.
"""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field

from app.logging_config import get_logger
from app.schema import Document, JobState

log = get_logger()


@dataclass
class PageProgress:
    """Per-page progress for a running job."""

    page: int
    kind: str
    status: str = "pending"  # pending | parsing | done
    block_count: int = 0


@dataclass
class Job:
    """A parse job and its evolving state."""

    job_id: str
    document_id: str
    filename: str
    sha256: str
    correlation_id: str | None = None
    state: JobState = JobState.QUEUED
    pages_total: int = 0
    pages_done: int = 0
    page_progress: list[PageProgress] = field(default_factory=list)
    engine_breakdown: dict[str, int] = field(default_factory=dict)
    result: Document | None = None
    error: str | None = None
    cached: bool = False
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)


class JobStore:
    """In-memory job registry + sha256 -> Document result cache."""

    def __init__(self) -> None:
        self._jobs: dict[str, Job] = {}
        self._cache: dict[str, Document] = {}  # sha256 -> parsed Document
        self._lock = threading.RLock()

    # -- job lifecycle ------------------------------------------------------

    def create(
        self,
        *,
        job_id: str,
        document_id: str,
        filename: str,
        sha256: str,
        correlation_id: str | None = None,
    ) -> Job:
        with self._lock:
            job = Job(
                job_id=job_id,
                document_id=document_id,
                filename=filename,
                sha256=sha256,
                correlation_id=correlation_id,
            )
            self._jobs[job_id] = job
            return job

    def get(self, job_id: str) -> Job | None:
        with self._lock:
            return self._jobs.get(job_id)

    def set_status(self, job_id: str, state: JobState, error: str | None = None) -> None:
        with self._lock:
            job = self._jobs.get(job_id)
            if job is None:
                return
            job.state = state
            if error is not None:
                job.error = error
            job.updated_at = time.time()

    def init_pages(self, job_id: str, pages: list[tuple[int, str]]) -> None:
        """Seed per-page progress from (page_number, kind) pairs."""
        with self._lock:
            job = self._jobs.get(job_id)
            if job is None:
                return
            job.pages_total = len(pages)
            job.pages_done = 0
            job.page_progress = [
                PageProgress(page=p, kind=k, status="pending") for p, k in pages
            ]
            job.updated_at = time.time()

    def set_page_progress(
        self, job_id: str, page: int, status: str, block_count: int = 0
    ) -> None:
        with self._lock:
            job = self._jobs.get(job_id)
            if job is None:
                return
            for pp in job.page_progress:
                if pp.page == page:
                    pp.status = status
                    pp.block_count = block_count
                    break
            if status == "done":
                job.pages_done = sum(1 for pp in job.page_progress if pp.status == "done")
            job.updated_at = time.time()

    def bump_engine_breakdown(self, job_id: str, engine: str, count: int) -> None:
        """Accumulate engine_breakdown-so-far while the job runs."""
        with self._lock:
            job = self._jobs.get(job_id)
            if job is None:
                return
            job.engine_breakdown[engine] = job.engine_breakdown.get(engine, 0) + count
            job.updated_at = time.time()

    def set_result(self, job_id: str, document: Document, cached: bool = False) -> None:
        with self._lock:
            job = self._jobs.get(job_id)
            if job is None:
                return
            job.result = document
            job.cached = cached
            job.engine_breakdown = dict(document.stats.engine_breakdown)
            job.pages_total = document.source.pages
            job.pages_done = document.source.pages
            job.state = JobState.SUCCEEDED
            job.updated_at = time.time()

    # -- result cache (sha256 dedup) ---------------------------------------

    def cache_get(self, sha256: str) -> Document | None:
        with self._lock:
            doc = self._cache.get(sha256)
        if doc is not None:
            log.info(
                "result cache hit",
                stage="cache",
                sha256=sha256,
                document_id=doc.document_id,
                cached=True,
            )
        return doc

    def cache_put(self, sha256: str, document: Document) -> None:
        with self._lock:
            self._cache[sha256] = document


# Process-wide singleton (dev scope). TODO: inject/replace for scale-out.
_store = JobStore()


def get_job_store() -> JobStore:
    return _store
