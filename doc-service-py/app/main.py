"""FastAPI application factory for doc-service-py.

Importing this module MUST NOT pull in any heavy ML library — all of those are
lazy-imported deep inside the engines. ``import app.main`` therefore succeeds in
a venv that only has the base requirements installed.
"""

from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI

from app import __version__
from app.api import router
from app.config import get_settings
from app.logging_config import configure_logging, get_logger


@asynccontextmanager
async def _lifespan(app: FastAPI):
    """Initialize JSON logging on startup."""
    settings = get_settings()
    configure_logging(settings.docmind_log_level)
    log = get_logger()
    log.info(
        "doc-service starting",
        stage="startup",
        version=__version__,
        host=settings.host,
        port=settings.port,
        persist_artifacts=settings.docmind_persist_artifacts,
    )
    yield
    log.info("doc-service stopping", stage="shutdown")


def create_app() -> FastAPI:
    """Build and return the configured FastAPI application."""
    # Configure logging eagerly too, so logs emitted before the first request
    # (and during tests that don't trigger lifespan) are still structured JSON.
    settings = get_settings()
    configure_logging(settings.docmind_log_level)

    app = FastAPI(
        title="DocMind doc-service-py",
        version=__version__,
        description=(
            "Document parser producing canonical blocks for the DocMind RAG "
            "pipeline. CPU-safe stub engines; real ML libs lazy-imported."
        ),
        lifespan=_lifespan,
    )
    app.include_router(router)
    return app


# ASGI entrypoint: uvicorn app.main:app
app = create_app()
