"""EngineAdapter interface + PageKind classification enum.

An ``EngineAdapter`` turns a single logical unit of input (one page, or a whole
office/web document) into a list of canonical ``Block`` objects. The concrete
adapters live in ``stub_engines.py``; each lazy-imports its real backing library
inside ``parse()`` and falls back to deterministic sample blocks when the import
is unavailable, so nothing heavy is required to run the service.
"""

from __future__ import annotations

import abc
from dataclasses import dataclass, field
from enum import Enum

from app.schema import Block


class PageKind(str, Enum):
    """How a page/document should be parsed (drives routing)."""

    TEXT_SIMPLE = "text_simple"   # digital PDF, clean text layer -> PyMuPDF
    TEXT_COMPLEX = "text_complex"  # multi-column / tables / layout -> Docling
    SCANNED = "scanned"           # image-only page -> PaddleOCR / PP-Structure
    FORMULA = "formula"           # math-heavy page -> formula-capable path
    OFFICE = "office"             # docx/pptx/xlsx/md/html -> native office parser
    WEB = "web"                   # fetched HTML / web content -> office/html path


@dataclass
class ParseContext:
    """Correlation + provenance passed to each engine's ``parse()``.

    Carries the ids needed for the logging correlation chain plus the page the
    engine is working on, the detected mime, and the heading breadcrumb so far.
    """

    job_id: str
    document_id: str
    correlation_id: str | None
    filename: str
    mime: str
    page: int = 1
    heading_path: list[str] = field(default_factory=list)
    order_start: int = 0  # global reading-order index to start assigning from


class EngineAdapter(abc.ABC):
    """Abstract parser engine.

    Implementations declare a ``name`` (which MUST be a valid ``parse_engine``
    enum value from the canonical schema) and a ``version`` tag, advertise which
    :class:`PageKind` values they handle via :meth:`supports`, and produce
    canonical :class:`Block` objects via :meth:`parse`.
    """

    #: Must equal a canonical ParseEngine value: paddleocr|docling|pymupdf|office
    name: str
    #: Version tag recorded on every block as ``parse_version``.
    version: str

    @abc.abstractmethod
    def supports(self, page_kind: PageKind) -> bool:
        """Return True if this engine can handle ``page_kind``."""

    @abc.abstractmethod
    def parse(self, payload: bytes, ctx: ParseContext) -> list[Block]:
        """Parse ``payload`` for one page/document into canonical blocks.

        Implementations MUST NOT raise on a missing optional ML library: they
        lazy-import inside this method and fall back to deterministic stub
        blocks so the service always makes progress.
        """
