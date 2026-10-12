"""Pydantic models for the DocMind canonical block schema.

This module is a VERBATIM implementation of the shared contract defined in
``docmind/docs/canonical-schema.md`` (the single source of truth). The Java
orchestrator (api-java) consumes exactly these field names / enum values, so
any change here is a contract change that must be mirrored on both sides.

Conventions (from the contract):
  * all keys are snake_case,
  * bbox is [x0, y0, x1, y1] with a top-left origin,
  * ``latex`` is a non-null string ONLY when ``type == formula`` (else null),
  * ``parse_engine`` is one of paddleocr|docling|pymupdf|office
    (MinerU is intentionally DEFERRED and is NOT valid),
  * consumers ignore unknown keys.
"""

from __future__ import annotations

from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field, model_validator


class BlockType(str, Enum):
    """Exact spellings from canonical-schema.md §2.1."""

    HEADING = "heading"
    PARAGRAPH = "paragraph"
    TABLE = "table"
    FORMULA = "formula"
    KEY_VALUE = "key_value"
    CHECKBOX = "checkbox"
    IMAGE = "image"
    LIST = "list"


class ParseEngine(str, Enum):
    """Exact spellings from canonical-schema.md §2.2.

    MinerU is intentionally DEFERRED (license to be verified) and is therefore
    NOT a member of this enum.
    """

    PADDLEOCR = "paddleocr"
    DOCLING = "docling"
    PYMUPDF = "pymupdf"
    OFFICE = "office"


class JobState(str, Enum):
    """In-memory job lifecycle states (see app/jobs.py)."""

    QUEUED = "queued"
    RUNNING = "running"
    SUCCEEDED = "succeeded"
    FAILED = "failed"


class Block(BaseModel):
    """One unit of parsed content (canonical-schema.md §2)."""

    model_config = {"use_enum_values": True}

    block_id: str = Field(..., description="UUID, unique within the document.")
    page: int = Field(..., ge=1, description="1-based page number.")
    order: int = Field(..., ge=0, description="0-based reading order (monotonic).")
    type: BlockType
    text: str = Field(..., description='Plain-text rendering (may be "" for image).')
    markdown: str
    html: str
    latex: Optional[str] = Field(
        default=None,
        description="Non-null LaTeX ONLY when type == formula; null otherwise.",
    )
    bbox: tuple[float, float, float, float] = Field(
        ..., description="[x0, y0, x1, y1] top-left origin, page/user units."
    )
    confidence: float = Field(..., ge=0.0, le=1.0)
    heading_path: list[str] = Field(default_factory=list)
    parse_engine: ParseEngine
    parse_version: str

    @model_validator(mode="after")
    def _latex_only_for_formula(self) -> "Block":
        """Enforce the formula -> latex rule from §2.3.

        * type == formula  -> latex MUST be a non-null string.
        * type != formula  -> latex MUST be null.
        """
        # self.type is a str here because of use_enum_values=True.
        is_formula = self.type == BlockType.FORMULA.value
        if is_formula:
            if self.latex is None:
                raise ValueError("formula block requires a non-null 'latex'")
        else:
            if self.latex is not None:
                raise ValueError(
                    "'latex' must be null on non-formula blocks "
                    f"(type={self.type!r})"
                )
        return self


class Source(BaseModel):
    """Provenance of the uploaded file (canonical-schema.md §1.1)."""

    filename: str
    sha256: str = Field(..., description="SHA-256 hex of the raw bytes.")
    mime: str = Field(..., description="Detected MIME, e.g. application/pdf.")
    pages: int = Field(..., ge=1)


class Stats(BaseModel):
    """Parse statistics (canonical-schema.md §1.2)."""

    engine_breakdown: dict[str, int] = Field(
        default_factory=dict,
        description="parse_engine value -> number of blocks it produced.",
    )


class Document(BaseModel):
    """Top-level parsed document (canonical-schema.md §1)."""

    document_id: str = Field(..., description="UUID stable id for this document.")
    source: Source
    blocks: list[Block] = Field(default_factory=list)
    stats: Stats


class Chunk(BaseModel):
    """Retrieval chunk (canonical-schema.md §3).

    Chunks are produced by the Java service by grouping blocks; the parser does
    not emit them. The model is defined here so both sides share the shape and
    for documentation/tests. A chunk stays within a single page.
    """

    document_id: str
    heading_path: list[str] = Field(default_factory=list)
    block_ids: list[str] = Field(default_factory=list)
    page: int = Field(..., ge=1)
    bbox: tuple[float, float, float, float]
