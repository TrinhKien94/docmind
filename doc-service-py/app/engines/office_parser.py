"""Real Office (.docx / .xlsx) parsing helpers for :class:`OfficeEngine`.

This module isolates the two OPTIONAL native-office libraries behind a single
place so the sibling engines (PyMuPdf/Docling/PaddleOcr) and ``stub_engines``
stay untouched:

    * Word  (.docx)  -> python-docx (imports as ``docx``)
    * Excel (.xlsx)  -> openpyxl

Lazy-import discipline (same rule as the sibling engines): the two libraries are
imported INSIDE the loader helpers, never at module top level. ``import
app.engines.office_parser`` therefore succeeds even when neither library is
installed, and the caller (:class:`OfficeEngine`) falls back to its deterministic
stub blocks when a loader returns ``None``.

Field-value conventions for office formats (documented here once; the caller's
``block_factory`` stamps them onto every :class:`Block`):

    * ``bbox = (0.0, 0.0, 0.0, 0.0)`` — Office formats expose NO page coordinates,
      so there is no meaningful bounding box; a zero box is the agreed sentinel.
    * ``confidence = 1.0`` — native text extraction (NOT OCR); the characters are
      read verbatim from the file, so confidence is maximal.
    * ``page = 1`` — a ``.docx`` has no fixed page model (pagination is a render
      concern) and ``.xlsx`` worksheets are not pages; everything maps to page 1.
    * ``latex = None`` — Office parsing never produces formula blocks.
    * ``parse_engine = "office"``; ``parse_version`` is the backing lib version,
      e.g. ``"python-docx-1.1.2"`` / ``"openpyxl-3.1.5"``.

Vietnamese / UTF-8: python-docx and openpyxl already return Python ``str``, so no
manual decode is performed and accented text round-trips unchanged.

Scope: legacy ``.doc`` / ``.ppt`` and OOXML ``.pptx`` are intentionally NOT parsed
here — the caller keeps emitting stub blocks for those.
"""

from __future__ import annotations

import html as _html
import io
from typing import Callable

from app.schema import Block, BlockType

# Cap data rows per Excel table block so a huge sheet cannot explode into tens of
# thousands of rows (bounds token/memory cost); truncation is logged by caller.
_MAX_TABLE_ROWS = 200

# A ``block_factory`` closure owned by the caller. It assigns block_id / order /
# parse_engine / version / the office field-value conventions above, so this
# module stays schema-light and the caller owns the running reading-order index.
BlockFactory = Callable[..., Block]


# --------------------------------------------------------------------------- #
# Lazy loaders (NEVER import at module top level)
# --------------------------------------------------------------------------- #
def load_docx() -> object | None:
    """Lazy-import python-docx (module ``docx``); return module or ``None``."""
    try:
        import docx  # noqa: PLC0415 - intentional lazy import
    except Exception:  # ImportError or any load-time failure
        return None
    return docx


def load_openpyxl() -> object | None:
    """Lazy-import openpyxl; return the module or ``None``."""
    try:
        import openpyxl  # noqa: PLC0415 - intentional lazy import
    except Exception:  # ImportError or any load-time failure
        return None
    return openpyxl


def _lib_version(module: object, prefix: str) -> str:
    """Return ``"<prefix>-<version>"``, degrading to ``"<prefix>-?"``."""
    version = getattr(module, "__version__", None)
    return f"{prefix}-{version}" if version else f"{prefix}-?"


# --------------------------------------------------------------------------- #
# Table rendering helpers (markdown / html / plain text)
# --------------------------------------------------------------------------- #
def _cell_md(value: str) -> str:
    """Escape a cell for a GitHub-style markdown table (pipes + newlines)."""
    return value.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "<br>")


def _md_table(rows: list[list[str]]) -> str:
    """Render ``rows`` as a GitHub-style markdown table (first row = header)."""
    if not rows:
        return ""
    header = rows[0]
    width = len(header)
    sep = ["---"] * width
    lines = [
        "| " + " | ".join(_cell_md(c) for c in header) + " |",
        "| " + " | ".join(sep) + " |",
    ]
    for row in rows[1:]:
        # Pad/truncate so every body row matches the header column count.
        cells = (list(row) + [""] * width)[:width]
        lines.append("| " + " | ".join(_cell_md(c) for c in cells) + " |")
    return "\n".join(lines)


def _html_table(rows: list[list[str]]) -> str:
    """Render ``rows`` as an HTML table (first row = ``<th>`` header)."""
    if not rows:
        return "<table></table>"
    parts = ["<table>"]
    header = rows[0]
    width = len(header)
    parts.append(
        "<tr>" + "".join(f"<th>{_html.escape(c)}</th>" for c in header) + "</tr>"
    )
    for row in rows[1:]:
        cells = (list(row) + [""] * width)[:width]
        parts.append(
            "<tr>" + "".join(f"<td>{_html.escape(c)}</td>" for c in cells) + "</tr>"
        )
    parts.append("</table>")
    return "".join(parts)


def _text_table(rows: list[list[str]]) -> str:
    """Flatten ``rows`` to plain text: cells joined by ' | ', rows by newline."""
    return "\n".join(" | ".join(row) for row in rows)


def _stringify(value: object) -> str:
    """Stringify a cell value; ``None`` -> empty string, everything else str()."""
    return "" if value is None else str(value)


# --------------------------------------------------------------------------- #
# Word (.docx)
# --------------------------------------------------------------------------- #
def _heading_level(style_name: str) -> int | None:
    """Return the heading level for a style name, or ``None`` if not a heading.

    ``Title`` -> level 0 (resets the breadcrumb). ``Heading 1``..``Heading 9``
    -> their numeric level. Anything else -> ``None`` (treated as body text).
    """
    if not style_name:
        return None
    name = style_name.strip()
    if name.lower() == "title":
        return 0
    if name.lower().startswith("heading"):
        tail = name[len("heading"):].strip()
        if tail.isdigit():
            return int(tail)
        return 1  # bare "Heading" with no number -> level 1
    return None


def _apply_heading(path: list[str], level: int, text: str) -> list[str]:
    """Return a NEW breadcrumb after placing ``text`` at ``level``.

    * level 0 (Title) resets the path to ``[text]``.
    * level N truncates the path to N-1 entries then appends ``text`` so a
      Heading 2 under a Heading 1 yields ``["H1", "H2"]``.
    """
    if level <= 0:
        return [text]
    truncated = path[: level - 1]
    return [*truncated, text]


def parse_docx(payload: bytes, mk: BlockFactory) -> list[Block]:
    """Parse Word ``.docx`` bytes into canonical blocks in reading order.

    Heading styles become ``heading`` blocks maintaining a running
    ``heading_path`` breadcrumb; normal paragraphs become ``paragraph`` blocks;
    empty paragraphs are skipped; tables become ``table`` blocks (markdown +
    html + plain-text flatten). ``mk`` stamps id/order/engine/version/bbox/etc.
    """
    docx = load_docx()
    assert docx is not None, "parse_docx called without python-docx available"
    from docx.table import Table  # noqa: PLC0415 - lazy, only on the real path
    from docx.text.paragraph import Paragraph  # noqa: PLC0415

    document = docx.Document(io.BytesIO(payload))
    parse_version = _lib_version(docx, "python-docx")

    blocks: list[Block] = []
    heading_path: list[str] = []
    local_index = 0

    # Iterate the body in TRUE reading order. python-docx exposes paragraphs and
    # tables separately, so we walk the XML body children and wrap each <w:p> and
    # <w:tbl> element with the matching python-docx wrapper. Newer python-docx
    # offers Document.iter_inner_content(); if absent we fall back to the XML walk.
    inner = getattr(document, "iter_inner_content", None)
    if callable(inner):
        items = list(inner())
    else:  # pragma: no cover - exercised only on older python-docx
        items = []
        body = document.element.body
        for child in body.iterchildren():
            tag = child.tag.rsplit("}", 1)[-1]
            if tag == "p":
                items.append(Paragraph(child, document))
            elif tag == "tbl":
                items.append(Table(child, document))

    for item in items:
        if isinstance(item, Paragraph):
            text = item.text.strip()
            if not text:
                continue  # skip empty / whitespace-only paragraphs
            style_name = item.style.name if item.style is not None else ""
            level = _heading_level(style_name)
            if level is not None:
                heading_path = _apply_heading(heading_path, level, text)
                md_level = max(1, level) if level > 0 else 1
                html_level = min(max(md_level, 1), 6)
                blocks.append(
                    mk(
                        local_index,
                        type=BlockType.HEADING,
                        text=text,
                        markdown=f"{'#' * md_level} {text}",
                        html=f"<h{html_level}>{_html.escape(text)}</h{html_level}>",
                        heading_path=list(heading_path),
                        parse_version=parse_version,
                    )
                )
            else:
                blocks.append(
                    mk(
                        local_index,
                        type=BlockType.PARAGRAPH,
                        text=text,
                        markdown=text,
                        html=f"<p>{_html.escape(text)}</p>",
                        heading_path=list(heading_path),
                        parse_version=parse_version,
                    )
                )
            local_index += 1
        elif isinstance(item, Table):
            rows = [[cell.text.strip() for cell in row.cells] for row in item.rows]
            if not rows:
                continue
            blocks.append(
                mk(
                    local_index,
                    type=BlockType.TABLE,
                    text=_text_table(rows),
                    markdown=_md_table(rows),
                    html=_html_table(rows),
                    heading_path=list(heading_path),
                    parse_version=parse_version,
                )
            )
            local_index += 1

    return blocks


# --------------------------------------------------------------------------- #
# Excel (.xlsx)
# --------------------------------------------------------------------------- #
def parse_xlsx(payload: bytes, mk: BlockFactory, meta: dict | None = None) -> list[Block]:
    """Parse Excel ``.xlsx`` bytes into canonical blocks (one group per sheet).

    Per worksheet: one ``heading`` block with the sheet name, then one ``table``
    block over the used range (markdown + html + plain-text flatten), capped at
    :data:`_MAX_TABLE_ROWS` data rows. Observability facts (sheet names, per-sheet
    row counts, truncated sheets) are written into the optional ``meta`` dict so
    the caller can log them. ``order`` increments continuously across sheets via
    ``mk``'s running index.
    """
    openpyxl = load_openpyxl()
    assert openpyxl is not None, "parse_xlsx called without openpyxl available"

    workbook = openpyxl.load_workbook(io.BytesIO(payload), read_only=True, data_only=True)
    parse_version = _lib_version(openpyxl, "openpyxl")

    sheet_names: list[str] = []
    sheet_rows: dict[str, int] = {}
    truncated_sheets: list[str] = []

    blocks: list[Block] = []
    local_index = 0
    try:
        for worksheet in workbook.worksheets:
            title = worksheet.title
            sheet_names.append(title)

            # Heading block for the sheet.
            blocks.append(
                mk(
                    local_index,
                    type=BlockType.HEADING,
                    text=title,
                    markdown=f"# {title}",
                    html=f"<h1>{_html.escape(title)}</h1>",
                    heading_path=[title],
                    parse_version=parse_version,
                )
            )
            local_index += 1

            # Read the used range, stringifying cells. Cap data rows at the limit.
            rows: list[list[str]] = []
            total_rows = 0
            truncated = False
            for raw in worksheet.iter_rows(values_only=True):
                total_rows += 1
                if len(rows) >= _MAX_TABLE_ROWS:
                    truncated = True
                    continue
                rows.append([_stringify(v) for v in raw])

            sheet_rows[title] = total_rows
            if truncated:
                truncated_sheets.append(title)

            table_text = _text_table(rows)
            if truncated:
                note = (
                    f"[truncated: showing first {_MAX_TABLE_ROWS} of "
                    f"{total_rows} rows]"
                )
                table_text = f"{table_text}\n{note}" if table_text else note

            blocks.append(
                mk(
                    local_index,
                    type=BlockType.TABLE,
                    text=table_text,
                    markdown=_md_table(rows),
                    html=_html_table(rows),
                    heading_path=[title],
                    parse_version=parse_version,
                )
            )
            local_index += 1
    finally:
        workbook.close()

    if meta is not None:
        meta["library"] = parse_version
        meta["sheet_names"] = sheet_names
        meta["sheet_rows"] = sheet_rows
        meta["truncated_sheets"] = truncated_sheets

    return blocks
