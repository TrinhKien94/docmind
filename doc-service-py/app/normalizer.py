"""Normalizer: assemble engine blocks into a validated canonical Document.

Responsibilities:
  * concatenate per-page block lists in page/engine order,
  * re-assign a global monotonic 0-based ``order`` across all blocks,
  * build the ``source`` (filename, sha256, mime, pages),
  * compute ``stats.engine_breakdown`` (block count per parse_engine),
  * validate the result against ``app.schema`` (raises on contract violation).
"""

from __future__ import annotations

from collections import Counter

from app.logging_config import get_logger
from app.schema import Block, Document, Source, Stats

log = get_logger()


def normalize(
    *,
    document_id: str,
    filename: str,
    sha256: str,
    mime: str,
    pages: int,
    page_blocks: list[list[Block]],
) -> Document:
    """Build and validate a :class:`Document` from per-page block lists."""
    flat: list[Block] = []
    for blocks in page_blocks:
        flat.extend(blocks)

    # Re-assign a global monotonic reading order (0-based) across all blocks.
    normalized: list[Block] = []
    for idx, block in enumerate(flat):
        # model_copy keeps validation (incl. the formula/latex rule) intact.
        normalized.append(block.model_copy(update={"order": idx}))

    breakdown = Counter(b.parse_engine for b in normalized)
    stats = Stats(engine_breakdown=dict(breakdown))

    document = Document(
        document_id=document_id,
        source=Source(filename=filename, sha256=sha256, mime=mime, pages=pages),
        blocks=normalized,
        stats=stats,
    )

    # Explicit re-validation of the assembled object (defensive; catches any
    # drift from the canonical schema before it leaves the service).
    Document.model_validate(document.model_dump())

    log.info(
        "document normalized",
        stage="normalize",
        document_id=document_id,
        block_count=len(normalized),
        pages=pages,
        engine_breakdown=stats.engine_breakdown,
    )
    return document
