"""Structured JSON logging with a correlation chain.

Every log line is a single JSON object on one line carrying (where known):

    ts, level, stage, correlation_id, document_id, job_id, page, block_id,
    engine, duration_ms, confidence, msg

The correlation chain is kept in a ``contextvars``-backed structlog contextvar
so that once a request binds ``correlation_id`` / ``job_id`` / ``document_id``
(and a stage binds ``page`` / ``block_id`` / ``engine``), every subsequent log
line in that async context inherits those fields automatically — this is the
full correlation chain the Java side (X-Correlation-Id) threads through.

Usage::

    from app.logging_config import configure_logging, get_logger, bind_context

    configure_logging("INFO")           # once, at startup
    log = get_logger()
    bind_context(correlation_id=..., job_id=..., document_id=...)
    log.info("message", stage="parse", engine="pymupdf", duration_ms=12.3)
"""

from __future__ import annotations

import logging
import sys
from contextlib import contextmanager
from typing import Any, Iterator

import structlog
from structlog.contextvars import (
    bind_contextvars,
    clear_contextvars,
    merge_contextvars,
    reset_contextvars,
)

# Canonical ordering of correlation / telemetry keys for readable JSON lines.
_KEY_ORDER = [
    "ts",
    "level",
    "stage",
    "correlation_id",
    "document_id",
    "job_id",
    "page",
    "block_id",
    "engine",
    "duration_ms",
    "confidence",
    "msg",
]


def _event_to_msg(_logger: Any, _method: str, event_dict: dict) -> dict:
    """Rename structlog's ``event`` key to ``msg`` to match the contract."""
    if "event" in event_dict:
        event_dict["msg"] = event_dict.pop("event")
    return event_dict


def _order_keys(_logger: Any, _method: str, event_dict: dict) -> dict:
    """Emit known keys first (stable order), then any extras alphabetically."""
    ordered: dict[str, Any] = {}
    for key in _KEY_ORDER:
        if key in event_dict:
            ordered[key] = event_dict.pop(key)
    for key in sorted(event_dict):
        ordered[key] = event_dict[key]
    return ordered


def configure_logging(level: str = "INFO") -> None:
    """Configure structlog to emit one JSON object per line to stdout."""
    log_level = getattr(logging, str(level).upper(), logging.INFO)

    # Route stdlib logging (uvicorn, etc.) through a plain stdout handler at the
    # same level; our app logs go through structlog's JSON renderer below.
    logging.basicConfig(
        format="%(message)s",
        stream=sys.stdout,
        level=log_level,
        force=True,
    )

    structlog.configure(
        processors=[
            merge_contextvars,  # pull in bound correlation fields
            structlog.processors.add_log_level,
            structlog.processors.TimeStamper(fmt="iso", key="ts"),
            _event_to_msg,
            _order_keys,
            structlog.processors.JSONRenderer(),
        ],
        wrapper_class=structlog.make_filtering_bound_logger(log_level),
        logger_factory=structlog.PrintLoggerFactory(file=sys.stdout),
        cache_logger_on_first_use=True,
    )


def get_logger(name: str = "docmind.doc-service") -> structlog.stdlib.BoundLogger:
    """Return a bound structlog logger."""
    return structlog.get_logger(name)


def bind_context(**fields: Any) -> None:
    """Bind correlation fields onto the current context (async-safe).

    Only non-None values are bound so a stage can set ``page``/``block_id``
    without clobbering request-level ``correlation_id``.
    """
    clean = {k: v for k, v in fields.items() if v is not None}
    if clean:
        bind_contextvars(**clean)


def clear_context() -> None:
    """Clear all bound correlation fields (call at end of a request)."""
    clear_contextvars()


@contextmanager
def stage_context(**fields: Any) -> Iterator[None]:
    """Temporarily bind extra correlation fields for the duration of a stage.

    Fields set here (e.g. ``page``, ``engine``, ``block_id``) are reset when the
    ``with`` block exits, leaving the request-level context intact.
    """
    clean = {k: v for k, v in fields.items() if v is not None}
    tokens = bind_contextvars(**clean) if clean else {}
    try:
        yield
    finally:
        if tokens:
            reset_contextvars(**tokens)
