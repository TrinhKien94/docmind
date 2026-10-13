# Real OfficeEngine (.docx + .xlsx) — Semantic Review

Replaces the stub `OfficeEngine` with a real native-office parser: Word `.docx`
via python-docx and Excel `.xlsx` via openpyxl, both isolated in a new
`app/engines/office_parser.py` module. `OfficeEngine.parse()` picks the format
from the detected MIME (filename extension as a tiebreak for generic zip/octet
MIMEs), lazy-imports the backing lib, and falls back to the pre-existing stub
blocks when the lib is missing or parsing raises. The canonical schema, router
routing tables, normalizer, and job store are untouched. All real blocks carry
the documented office field-value contract (bbox zero sentinel, confidence 1.0,
latex None, page 1, parse_engine "office").

Watch for: nothing blocking. One **possible** cosmetic nit — the `local_index`
in `parse_docx` is incremented inside each branch rather than once per item;
this is correct as written but worth an eyeball (see the ordering section). The
recorded evidence (20 passed, block excerpts, sheet metadata) is present and
matches the code.

**Verdict**: APPROVED

## High-level view

The split between `stub_engines.py` and the new `office_parser.py` is clean:
`OfficeEngine` owns format selection, the lazy-import guard, the fallback
decision, logging, and the `block_factory` that stamps ids/order/engine/version
and the office field conventions; `office_parser.py` owns pure block-building
with no schema stamping of its own. This keeps the three sibling engines and the
schema completely out of the diff, which is exactly the stated scope.

The lazy-import + graceful-fallback discipline holds end to end. `docx` and
`openpyxl` are imported only inside `load_docx()` / `load_openpyxl()` and inside
`parse_docx` / `parse_xlsx`, never at module top. `OfficeEngine.parse()` checks
the loader for `None` before touching the real path and wraps the real builder
in a broad `try/except` that returns stub blocks on any failure, so the service
boots and jobs complete with neither library installed.

The Word path preserves true reading order via `iter_inner_content()` with an
XML-walk fallback for older python-docx, maps heading styles to a running
breadcrumb, skips empty paragraphs, and renders tables to markdown/HTML/text.
The Excel path emits a sheet-name heading then a capped (200-row) table per
sheet, with sheet names, per-sheet row counts, and truncation surfaced through a
`meta` dict the caller logs. Both paths are UTF-8 clean because the libs return
`str` directly.

Blocks validate against the canonical schema: `latex=None` satisfies the
non-formula rule, `bbox` is a 4-float tuple, `page>=1`, `order>=0`,
`parse_engine` is the "office" enum value. The required evidence was recorded in
the plan's Verification section (import checks, 20 passed, DOCX/XLSX extracted
excerpts, HTTP pipeline breakdown), so no re-run was needed.

<details>
<summary>Issues (2)</summary>

1. **Row-cap semantics (non-blocking, confirmed)** — `_MAX_TABLE_ROWS` caps
   *all* rows including the header, so a sheet with ≤200 total rows is never
   truncated and the header always survives; large sheets drop body rows past
   199 data rows. Behavior is correct and documented; just not a strict
   "200 data rows" count. No action required.
2. **`local_index` increment placement (possible, cosmetic)** — in `parse_docx`
   the index increments at the end of each branch; verified monotonic and
   gap-free because every emitted block increments exactly once and skipped
   empties/empty-tables do not. No action required.

</details>

<details>
<summary>Details</summary>

### Interface preserved — router still reaches the engine

`OfficeEngine` still subclasses `_BaseEngine` (hence `EngineAdapter`), keeps
`name = ParseEngine.OFFICE.value` ("office"), and `supports()` returns True for
`PageKind.OFFICE` and `PageKind.WEB`. `router.py` is unchanged: `_OFFICE_MIMES`
still maps the docx/xlsx OOXML MIMEs to `PageKind.OFFICE`, and `engine_for`
walks the registration list returning the first engine whose `supports` matches
— `OfficeEngine` is last and also the catch-all. The integration test
(`test_office_docx_routes_through_parser_router`) confirms `classify(docx_mime,
data)` yields exactly one `PageKind.OFFICE` decision and `engine_for(OFFICE)`
returns an `OfficeEngine` instance, so the routing contract is intact.

### Lazy-import + graceful fallback

The two optional libs are reached only through `load_docx()` /
`load_openpyxl()`, each a `try: import ... except Exception: return None`. The
module top of `office_parser.py` imports only stdlib (`html`, `io`, `typing`)
plus `app.schema`, so `import app.engines.office_parser` succeeds with neither
lib present — the plan recorded this import check passing. `parse()` gates each
real path on the loader result:

- docx path: if `load_docx() is None` → `log.info("engine falling back to stub
  (lib unavailable)", ... detail="python-docx not installed")` and returns
  `_emit(self._sample_blocks(ctx), ...)`.
- xlsx path: symmetric with `load_openpyxl()` and "openpyxl not installed".

Both real builders are additionally wrapped in `try/except Exception as exc`
that logs `"office real parse failed; using stub"` and returns stub blocks, so a
corrupt file never propagates — honoring the base-class "MUST NOT raise"
contract. The fallback test monkeypatches `office_parser.load_docx` to `None`,
feeds junk bytes, and asserts 2 stub blocks with no raise. This is the correct
seam: the engine imports `office_parser` as a module and calls
`office_parser.load_docx()` at call time, so the monkeypatch takes effect.

### Word: structure mapping and reading order

`parse_docx` iterates `document.iter_inner_content()` when available (modern
python-docx) and falls back to walking `document.element.body` children, wrapping
`<w:p>`/`<w:tbl>` elements with the `Paragraph`/`Table` classes. This is the one
correct way to interleave paragraphs and tables in document order, since
python-docx otherwise exposes them as two separate flat lists. The fallback
branch is marked `pragma: no cover` (only older libs hit it), which is honest.

Heading detection via `_heading_level`: `Title` → level 0 (resets breadcrumb to
`[text]`), `Heading N` → level N, bare `Heading` → level 1, everything else →
`None` (body text). `_apply_heading` truncates the path to `level-1` then appends,
so a Heading 2 under a Heading 1 yields `["H1", "H2"]` — the test asserts exactly
this (`["Tài liệu thử nghiệm", "Mục con"]`). Markdown uses `#*md_level`; HTML
clamps the level to 1–6. Empty/whitespace paragraphs are skipped (`if not text:
continue`), and the test confirms the empty paragraph produced no block (final
count is exactly 4). Tables read `cell.text.strip()` per cell and render through
the three helpers; the test checks `|` in markdown, `<table` in html, and the
Vietnamese cell text in plain text. Vietnamese round-trips because no manual
decode happens.

The `local_index` increments at the tail of the paragraph branch and the tail of
the table branch; a `continue` on an empty paragraph or empty table skips the
increment too, so the index stays contiguous. Each emitted block gets `order =
ctx.order_start + local_index` via the factory, so local order is monotonic as
the normalizer expects.

### Excel: per-sheet heading + capped table + observability

`parse_xlsx` opens with `read_only=True, data_only=True` (streams values, resolves
formulas to cached values) and iterates `workbook.worksheets` in sheet order. Per
sheet it emits a `heading` block (text = sheet title, `heading_path=[title]`,
`markdown="# "+title`, HTML-escaped `<h1>`) then a `table` block over
`iter_rows(values_only=True)`, stringifying `None`→`""` and other values via
`str`. The row cap keeps the first `_MAX_TABLE_ROWS` rows and, on overflow, sets
`truncated` and appends a `[truncated: showing first 200 of N rows]` note to the
plain-text rendering — satisfying "truncation noted in text + log". `order`
increments continuously across sheets through the shared `local_index`. The
`meta` dict carries `library`, `sheet_names`, `sheet_rows`, `truncated_sheets`
back to the caller, which logs them in the `"office parsed xlsx"` line. `workbook.
close()` runs in a `finally`, which matters for `read_only` mode. The test
asserts the sheet-name heading, the following table block, the second sheet
"Bảng 2" appearing as its own heading, Vietnamese cells round-tripping, and
`parse_version` starting with "openpyxl".

Note on the cap: `_MAX_TABLE_ROWS` bounds *all* collected rows (header counts as
one), so the strict count is "≤200 rows kept including header". The plan calls it
"~200 data rows"; functionally equivalent and well within the stated intent of
bounding token/memory cost. Flagged as non-blocking.

### Field-value contract and schema validity

The `mk` factory in `stub_engines.py` stamps every real block with
`bbox=(0.0,0.0,0.0,0.0)`, `confidence=1.0`, `latex=None`, `page=ctx.page`
(always 1 for office), `parse_engine=self.name` ("office"), and
`order=ctx.order_start+local_index`. `parse_version` is derived per lib
(`python-docx-<ver>` / `openpyxl-<ver>`, degrading to `-?`). The schema's
`_latex_only_for_formula` validator requires `latex is None` on non-formula
blocks — satisfied for every heading/paragraph/table block. `bbox` validates as a
`tuple[float,float,float,float]`. The normalization test round-trips the real
blocks through `normalize(...)`, asserts the global `order` is re-assigned
`0..n-1` monotonically, and that `engine_breakdown["office"]` equals the block
count — proving the blocks are schema-valid end to end.

### Legacy types stay stub, pptx not attempted

`_format()` returns "docx"/"xlsx" only for the two OOXML MIMEs (or those plus the
matching filename suffix when the MIME is generic zip/octet-stream); everything
else — `.doc`, `.ppt`, `.pptx`, md, txt, web — returns "stub" and logs `"office
stub path (real parsing out of scope for this type)"` before returning
`_sample_blocks`. There is no pptx/doc parsing code anywhere in
`office_parser.py` (grep of the module shows only `docx`/`openpyxl`), confirming
real pptx parsing was not attempted, per scope.

### Logging and correlation

All real-path logging goes through the module `log = get_logger()` already in
`stub_engines.py`, inside the existing `with stage_context(engine=self.name,
page=ctx.page):` block, so the correlation chain (job/document/correlation ids
bound upstream) is preserved. The real paths log `"office parsed docx"` (with
`library`, `block_count`) and `"office parsed xlsx"` (with `library`,
`sheet_names`, `sheet_rows`, `truncated_sheets`, `block_count`); fallback and
stub paths log their own distinct lines; and `_emit` still fires the shared
`"parsed page"` summary with mean confidence and block count. Real-lib-vs-stub is
distinguishable from the log line emitted. This matches the requested logging
depth.

### Evidence discipline

The plan's Verification section records the required runs: `python -c "import
app.main"` → "import ok", `python -c "import app.engines.office_parser"` →
succeeds, and `python -m pytest -q` → **20 passed, 1 warning** (15 existing + 5
new). It includes concrete extracted-block excerpts for a real DOCX (4 blocks,
heading breadcrumb, Vietnamese paragraph, markdown table) and XLSX (sheet
headings + tables, `sheet_names=['Bảng 1','Bảng 2']`, `sheet_rows={'Bảng 1':2,
'Bảng 2':1}`, `truncated_sheets=[]`), plus an HTTP TestClient run showing
`engine_breakdown={'office': 2}` and the resolved docx MIME. This evidence is
specific and consistent with the code I read directly; no articulable doubt
remained, so I did not re-run the suite or install anything.

</details>

<details>
<summary>File map</summary>

- `app/engines/office_parser.py` — NEW. Lazy loaders (`load_docx`,
  `load_openpyxl`), table renderers (`_md_table`/`_html_table`/`_text_table`),
  Word parser (`parse_docx`) with heading breadcrumb + reading-order walk, Excel
  parser (`parse_xlsx`) with per-sheet heading+table, 200-row cap, and `meta`
  observability.
- `app/engines/stub_engines.py` — `OfficeEngine` rewritten: `_format()` MIME/ext
  selection, `_make_block_factory()` stamping the office field contract, `parse()`
  with lazy-import guard + try/except stub fallback + structured logging. Sibling
  engines, `_BaseEngine`, `_block_id`, `_emit` unchanged. `_sample_blocks` kept.
- `tests/test_office_engine.py` — NEW. 5 tests: docx canonical blocks, docx router
  integration, xlsx canonical blocks, stub fallback via monkeypatch, normalize
  into a valid Document.
- `requirements.txt` — added `python-docx>=1.1.2`, `openpyxl>=3.1.5` under a
  commented Office block.
- `.agents/tasks/office-parser-plan.md` — plan + appended Verification section.

Schema (`app/schema.py`), `app/router.py`, `app/normalizer.py`, `app/engines/base.py`,
job store: unchanged.

</details>
