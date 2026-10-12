# Implementation Plan — Real OfficeEngine (.docx + .xlsx)

Replace the stub `OfficeEngine` in `doc-service-py` with a REAL parser that turns
Word (`.docx` via `python-docx`) and Excel (`.xlsx` via `openpyxl`) into canonical
`Block` objects, keeping the SAME `EngineAdapter` interface and the lazy-import +
graceful-fallback discipline. Scope is `docmind/doc-service-py` only. Do NOT change
the canonical schema, do NOT touch the Java service, do NOT add real pptx parsing.

## Design decisions (made from reading the code)

- **Where the real code lives.** Add a new module `app/engines/office_parser.py`
  holding two pure helper functions (`parse_docx(payload, ctx, block_factory) ->
  list[Block]` and `parse_xlsx(payload, ctx, block_factory) -> list[Block]`) plus a
  tiny `OfficeLibs` import helper. `OfficeEngine.parse()` in `stub_engines.py` calls
  these. Rationale: keeps `stub_engines.py` readable, isolates the two optional libs
  behind one module, and lets the sibling engines (PyMuPdf/Docling/PaddleOcr) stay
  untouched. `OfficeEngine` keeps its `_sample_blocks()` stub as the fallback path.
- **Why not reuse `_BaseEngine._try_import` / `_real_module`.** That helper imports a
  SINGLE dotted module. Office needs to pick between `docx` and `openpyxl` by MIME at
  runtime, so `OfficeEngine` sets `_real_module = ""` (unchanged) and uses its own
  per-format lazy import inside `office_parser.OfficeLibs`. Rationale: honors the
  existing lazy-import rule (import inside `parse()`, never at module top) while
  supporting two independent optional libs.
- **Format selection signal.** `ParseContext` carries `mime` and `filename`. Decide
  docx vs xlsx vs fallback primarily on `ctx.mime` (set by `detect_mime` from magic
  bytes: `...wordprocessingml.document` -> docx, `...spreadsheetml.sheet` -> xlsx),
  with `ctx.filename` extension (`.docx` / `.xlsx`) as a secondary tiebreak when the
  mime is a generic zip/ole value. Everything else (`.doc`, `.ppt`, `.pptx`, `.md`,
  `.txt`, web/html) keeps the current stub blocks. Rationale: mirrors how `router.py`
  already maps these MIMEs to `PageKind.OFFICE`; magic-byte mime is authoritative in
  this codebase (see `validation.detect_mime`).
- **Native-extraction field values (documented in code comments).** `bbox=(0.0,0.0,
  0.0,0.0)` (office formats expose no coordinates), `confidence=1.0` (native text
  extraction, not OCR), `page=ctx.page` which is always `1` for office (docx has no
  fixed page model; xlsx worksheets are not pages). `parse_engine="office"`,
  `parse_version` = `f"python-docx-{docx.__version__}"` for Word blocks and
  `f"openpyxl-{openpyxl.__version__}"` for Excel blocks (fall back to a static
  `"python-docx-?"` / `"openpyxl-?"` if the lib exposes no `__version__`).
- **Block ids & ordering.** Reuse `_block_id(ctx.document_id, ctx.page, local_index)`
  verbatim with a per-call monotonic `local_index`. Set each block's `order =
  ctx.order_start + local_index`. The normalizer re-assigns a global `order` later, so
  local order only needs to be monotonic within the call.
- **`latex` rule.** Office produces no formula blocks, so every block sets
  `latex=None` (schema `_latex_only_for_formula` requires null on non-formula types).
- **`supports()` unchanged.** `OfficeEngine.supports` keeps returning True for
  `PageKind.OFFICE` and `PageKind.WEB` so `router.engine_for(PageKind.OFFICE)` still
  resolves to it and `_OFFICE_MIMES` routing is unaffected.
- **Row cap.** Excel tables cap at `_MAX_TABLE_ROWS = 200` data rows per sheet; when a
  used range exceeds the cap, truncate and emit a trailing note row/flag plus a log
  field `truncated=True`. Rationale: bounds token/memory blow-up on large sheets, per
  the task.

## Ordered steps

- [ ] 1. Add the two runtime deps.
      Append `python-docx>=1.1.2` and `openpyxl>=3.1.5` to `requirements.txt` under a
      new comment block (e.g. `# Office native parsers (lazy-imported in OfficeEngine)`).
      These are pure-Python wheels, safe for the base install. Leave `pyproject.toml`
      as-is (it only holds pytest config; no dependency table exists there — do not
      invent one).
      Files: `requirements.txt`
      Verify: `python -m pip install -r requirements.txt` succeeds and
      `python -c "import docx, openpyxl; print(docx.__version__, openpyxl.__version__)"`
      prints two versions.

- [ ] 2. Create `app/engines/office_parser.py` with the lazy-import helper + builders.
      Contents:
      - Module docstring matching the house style (explains lazy import + fallback,
        the field-value conventions above, and that pptx/doc stay stub).
      - `_MAX_TABLE_ROWS = 200`.
      - `class OfficeLibs` (or two functions `load_docx()` / `load_openpyxl()`): each
        does `try: import docx / import openpyxl except Exception: return None`,
        returning the module or `None`. NEVER import at module top level.
      - Pure helpers that build canonical `Block`s via a passed-in `block_factory`
        callable (so `OfficeEngine` owns id/order/engine/version assignment and this
        module stays schema-light):
        - `_md_table(rows: list[list[str]]) -> str` → GitHub-style markdown table
          (header row + `--- | ---` separator + body rows; pipes/newlines in cells
          escaped).
        - `_html_table(rows) -> str` → `<table><tr><th>…</th></tr><tr><td>…</td>…` with
          `html.escape` on every cell.
        - `_text_table(rows) -> str` → plain-text flatten, cells joined by ` | `, rows
          by `\n`.
      - `parse_docx(payload: bytes, mk) -> list[Block]`:
        - `doc = docx.Document(io.BytesIO(payload))`.
        - Iterate the body in reading order. python-docx exposes `doc.paragraphs` and
          `doc.tables` separately; to preserve order, walk `doc.element.body` children
          and map each `<w:p>` to the matching `Paragraph` and each `<w:tbl>` to the
          matching `Table` (use `docx.text.paragraph.Paragraph` /
          `docx.table.Table` wrappers over the element, matching python-docx’s own
          `iter_inner_content()` when available; if the installed version lacks it,
          fall back to indexing `doc.paragraphs` / `doc.tables` in document order).
        - Paragraph mapping:
          - style name starting with `Heading` (e.g. `Heading 1`..`Heading 9`) or `Title`
            → `type="heading"`. Maintain a running `heading_path` breadcrumb keyed by the
            heading level (level N truncates the path to N-1 then appends the text);
            `Title` → level 0 (resets path to `[title]`). `markdown = "#"*level + " " +
            text`, `html = f"<h{level}>{escaped}</h{level}>"` (clamp level 1–6 for html).
          - otherwise non-empty paragraph → `type="paragraph"`, carrying the current
            `heading_path`; `markdown = text`, `html = f"<p>{escaped}</p>"`.
          - empty/whitespace-only paragraph → skipped.
        - Table mapping → `type="table"`: read `table.rows` → `cell.text`; build
          `markdown`/`html`/`text` via the three helpers; `heading_path` = current
          breadcrumb.
        - Return the list (ids/order/engine/version applied by `mk`).
      - `parse_xlsx(payload: bytes, mk) -> list[Block]`:
        - `wb = openpyxl.load_workbook(io.BytesIO(payload), read_only=True,
          data_only=True)`.
        - For each worksheet (`wb.worksheets`, preserving sheet order): emit one
          `type="heading"` block (text = sheet title, `heading_path=[sheet_title]`,
          `markdown="# "+title`, `html="<h1>…</h1>"`) THEN one `type="table"` block over
          the used range (`ws.iter_rows(values_only=True)`), stringifying `None`→`""` and
          numbers/dates via `str`. Cap at `_MAX_TABLE_ROWS` data rows; if exceeded, keep
          the first `_MAX_TABLE_ROWS` and set a `truncated` flag the caller logs. The
          table block’s `heading_path=[sheet_title]`.
        - `order` increments continuously across sheets (handled by the caller’s running
          index).
      - Each builder is UTF-8 safe (python-docx/openpyxl already return `str`); no manual
        decode so Vietnamese round-trips.
      Files: `app/engines/office_parser.py`
      Verify: `python -c "import app.engines.office_parser"` imports with no error even
      when the optional libs are absent (helpers must not import at top level).

- [ ] 3. Wire `OfficeEngine.parse()` in `stub_engines.py` to the real path with fallback.
      - Keep `name = ParseEngine.OFFICE.value`, `version` default (used only by the stub
        path), `supports()` unchanged, `_sample_blocks()` unchanged.
      - In `parse()`, inside the existing `with stage_context(engine=self.name,
        page=ctx.page):` block:
        - Decide format from `ctx.mime` (+ `ctx.filename` suffix fallback):
          docx mime `...wordprocessingml.document` → docx path; xlsx mime
          `...spreadsheetml.sheet` → xlsx path; otherwise stub.
        - For the chosen real path, lazy-import via `office_parser` helpers. If the lib
          is `None`, `log.info("engine falling back to stub (lib unavailable)",
          stage="parse", engine=self.name, page=ctx.page, detail="python-docx not
          installed" / "openpyxl not installed")` and `return self._emit(
          self._sample_blocks(ctx), ctx, started)` (same shape as the sibling engines).
        - For `.doc`/`.ppt`/`.pptx`/md/txt/web → `log.info("office stub path (real
          parsing out of scope for this type)", stage="parse", engine=self.name,
          page=ctx.page, mime=ctx.mime)` and return stub blocks.
        - On the real path, pass a `block_factory` closure
          `mk(local_index, *, type, text, markdown, html, heading_path, parse_version)`
          that constructs a `Block` with
          `block_id=_block_id(ctx.document_id, ctx.page, local_index)`,
          `page=ctx.page`, `order=ctx.order_start + local_index`,
          `latex=None`, `bbox=(0.0,0.0,0.0,0.0)`, `confidence=1.0`,
          `parse_engine=self.name`. The factory assigns the engine’s running
          `local_index`.
        - Wrap the real builder call in `try/except Exception as exc:` — on ANY failure
          (corrupt file, unexpected lib error) `log.warning("office real parse failed;
          using stub", stage="parse", engine=self.name, page=ctx.page, error=str(exc))`
          and return `self._emit(self._sample_blocks(ctx), ctx, started)`. The engine
          MUST NOT raise (honors the base-class contract).
        - On success, `return self._emit(blocks, ctx, started)` so the existing
          `parsed page` summary line fires with the real `block_count`/`confidence`.
      Files: `app/engines/stub_engines.py`
      Verify: `python -c "import app.main"` still succeeds (no heavy import at import
      time); existing suite still green: `python -m pytest -q` → all 15 pass.

- [ ] 4. Add structured logging for the real path (within the existing parse stage).
      - Word: after building blocks, `log.info("office parsed docx", stage="parse",
        engine=self.name, page=ctx.page, library=parse_version, block_count=len(blocks))`.
      - Excel: `log.info("office parsed xlsx", stage="parse", engine=self.name,
        page=ctx.page, library=parse_version, sheet_names=[…], sheet_rows={sheet: n, …},
        truncated_sheets=[…], block_count=len(blocks))`.
      - Keep the fallback/stub log lines from step 3. All logging goes through the module
        `log = get_logger()` already present in `stub_engines.py`; no new logger.
      Files: `app/engines/stub_engines.py` (and small return values from
      `office_parser.py` to surface sheet names / per-sheet row counts / truncation to
      the caller — e.g. have `parse_xlsx` return `(blocks, meta)` or accept a mutable
      `meta` dict).
      Verify: run a one-off script that parses an in-memory `.xlsx` through
      `OfficeEngine().parse(...)` and confirm the JSON log line contains `sheet_names`
      and `sheet_rows` (eyeball) — folded into step 6’s pytest, not a separate command.

- [ ] 5. Add tests under `tests/` for the real Office engine.
      Create `tests/test_office_engine.py` following the existing style (module docstring,
      `from __future__ import annotations`, plain `def test_*`, build inputs in memory):
      - Helpers to build tiny files in memory:
        - `_docx_bytes()`: `import docx; d = docx.Document()`; add a `Heading 1`
          ("Tài liệu thử nghiệm"), a `Heading 2` ("Mục con"), a normal paragraph with
          Vietnamese text ("Nội dung tiếng Việt có dấu."), an empty paragraph, and a
          2x2 table; `d.save(buf)` → `buf.getvalue()`.
        - `_xlsx_bytes()`: `import openpyxl; wb = openpyxl.Workbook()`; name sheet
          "Bảng 1", write a header row + 2 data rows (include Vietnamese); add a second
          sheet "Bảng 2" with one row; `wb.save(buf)` → bytes.
        - Guard both helpers with `pytest.importorskip("docx")` /
          `pytest.importorskip("openpyxl")` so the suite still passes if a dev lacks the
          libs (the engine’s own fallback is covered separately — see below).
      - `ParseContext` built directly with `mime` set to the docx/xlsx OOXML mime,
        `page=1`, `order_start=0`, a fixed `document_id`.
      - Assertions (docx): blocks produced (>=4); first block `type=="heading"` with
        `heading_path==["Tài liệu thử nghiệm"]`; a `type=="paragraph"` block exists and
        its `text` contains "Nội dung tiếng Việt có dấu." (Vietnamese round-trips, exact
        equality); a `type=="table"` block exists whose `markdown` contains `|` and
        whose `html` contains `<table`; empty paragraph did NOT create a block; every
        block has `parse_engine=="office"`, `bbox==(0.0,0.0,0.0,0.0)`, `confidence==1.0`,
        `latex is None`, `page==1`, `parse_version` starts with `"python-docx"`.
      - Assertions (xlsx): first block per sheet is `type=="heading"` with text=="Bảng 1"
        and `heading_path==["Bảng 1"]`; the following block is `type=="table"`; a block
        exists mentioning the second sheet "Bảng 2"; `parse_version` starts with
        `"openpyxl"`; Vietnamese cell text round-trips in the table block’s `text`.
      - Fallback test (no importorskip): monkeypatch `office_parser.load_docx`
        (or `OfficeLibs`) to return `None`, call `OfficeEngine().parse(docx_mime_bytes,
        ctx)`, and assert it returns the stub blocks (len==2, first `type=="heading"`)
        and does NOT raise.
      - Router/pipeline test: build real `.docx` bytes, run them through the full
        pipeline the way `test_parse_flow.py` does but via `ParserRouter`:
        `router.classify(docx_mime, data)` yields one `PageKind.OFFICE` decision, then
        `router.engine_for(OFFICE).parse(data, ctx)` returns the real blocks. (Going
        through the HTTP `TestClient` is optional and heavier; the router-level check is
        the required integration path. If adding an HTTP-level case, feed real docx bytes
        and assert `source.mime == ...wordprocessingml.document` and
        `stats.engine_breakdown["office"] >= 1`.)
      - Every constructed `Block`/`Document` must pass `Document.model_validate`
        (round-trip through `normalizer.normalize` in at least one test to prove global
        `order` re-assignment stays monotonic).
      Files: `tests/test_office_engine.py`
      Verify: `python -m pytest -q tests/test_office_engine.py` passes.

- [ ] 6. Full verification pass.
      Run from `docmind/doc-service-py`:
      - Create/activate venv if not present:
        `python -m venv .venv; .\.venv\Scripts\Activate.ps1` (PowerShell on Windows).
      - `python -m pip install -r requirements.txt` (now includes python-docx + openpyxl).
      - `python -c "import app.main"` — service imports with no heavy-lib error.
      - `python -m pytest -q` — expected: all tests pass, count >= 15 existing + the new
        Office tests (target >= 20 total). Fix any failure before finishing.
      - Optional sanity: `python -c "from app.engines.stub_engines import OfficeEngine;
        print(OfficeEngine().supports.__doc__ is not None)"` to confirm the class still
        imports cleanly.
      Files: none (verification only).
      Verify: `python -m pytest -q` reports 0 failures; test count >= 20.

## Guardrails / notes for the implementer

- Do NOT edit `app/schema.py`, `app/normalizer.py` contract behavior, `router.py`
  routing tables, or anything under `api-java`/the Java service.
- Do NOT add `mineru` or any new `parse_engine` value; office blocks use `"office"`.
- Keep imports of `docx`/`openpyxl` strictly inside the parse path (lazy). `import
  app.main` and `import app.engines.office_parser` must succeed with neither lib
  installed.
- Match the existing code voice: module docstrings explaining intent, `from __future__
  import annotations`, structured `log.info(..., stage="parse", engine=..., page=...)`
  calls, and the `_block_id` / `_emit` / `stage_context` helpers used verbatim.
- `.agents/tasks/` is created by this plan file; it is a planning artifact and should
  not be committed into application source if the repo excludes it — follow repo
  convention, but it does not affect the build.

## Verification (first iteration — executed 2026-10-12)

All commands run from `docmind/doc-service-py` with the existing PowerShell venv
(`.\.venv\Scripts\python.exe`).

### Environment / deps
- `pip install -r requirements.txt` (now includes `python-docx>=1.1.2`,
  `openpyxl>=3.1.5`) — succeeded.
- `python -c "import docx, openpyxl; print(docx.__version__, openpyxl.__version__)"`
  → `1.2.0 3.1.5`.
- **Note on `pyproject.toml`:** left unchanged. It contains ONLY
  `[tool.pytest.ini_options]` (no PEP 621 `[project]` dependency table). Adding a
  dependency table would invent build structure that does not exist, so the two deps
  live in `requirements.txt` (the file CI installs), consistent with the plan's
  design decision in step 1. Runtime deps remain lazy-imported.

### Imports (lazy discipline holds)
- `python -c "import app.main"` → `import ok` (no heavy import at import time).
- `python -c "import app.engines.office_parser"` → succeeds (loaders never import
  `docx`/`openpyxl` at module top level).

### Test suite
- `python -m pytest -q` → **20 passed, 1 warning** (15 existing + 5 new in
  `tests/test_office_engine.py`). The lone warning is the pre-existing Starlette
  `TestClient`/httpx deprecation, unrelated to this change.
- New tests cover: docx heading/paragraph/table blocks + breadcrumb + Vietnamese
  round-trip + office field-value contract; docx routing through `ParserRouter`
  (`PageKind.OFFICE` → `OfficeEngine`, `supports(OFFICE) is True`); xlsx sheet-name
  heading + table + second sheet + Vietnamese cells + `openpyxl` version; stub
  fallback when `load_docx()` is monkeypatched to `None` (no raise, 2 stub blocks);
  normalization into a valid `Document` with monotonic global `order`.

### Direct engine evidence (one-off driver, removed after capture)
Driving `OfficeEngine().parse(...)` over a tiny real `.docx` and `.xlsx` produced:

DOCX (`library=python-docx-1.2.0`, block_count=4):
```
order=0 type=heading   path=['Chương 2']               text='Chương 2'
order=1 type=heading   path=['Chương 2','Đạo hàm']     text='Đạo hàm'
order=2 type=paragraph path=['Chương 2','Đạo hàm']     text='Nội dung tiếng Việt có dấu.'
order=3 type=table     path=['Chương 2','Đạo hàm']     md='| Khoá | Giá trị |\n| --- | --- |\n| Alpha | 1 |'
```
All blocks: `parse_engine=office`, `bbox=(0,0,0,0)`, `confidence=1.0`, `latex=None`,
`page=1`, `parse_version=python-docx-1.2.0`. Vietnamese round-trips exactly.

XLSX (`library=openpyxl-3.1.5`, block_count=4, `sheet_names=['Bảng 1','Bảng 2']`,
`sheet_rows={'Bảng 1':2,'Bảng 2':1}`, `truncated_sheets=[]`):
```
order=0 type=heading path=['Bảng 1'] text='Bảng 1'
order=1 type=table   path=['Bảng 1'] md='| Tên | Điểm |\n| --- | --- |\n| Học viên A | 9 |'
order=2 type=heading path=['Bảng 2'] text='Bảng 2'
order=3 type=table   path=['Bảng 2'] text='Ghi chú'
```
The structured log line `office parsed xlsx` carried `sheet_names`, `sheet_rows`,
and `truncated_sheets` as required.

### HTTP pipeline evidence (FastAPI TestClient, one-off driver, removed after capture)
POST a real `.docx` (named `baitap.docx`, generic octet-stream mime so detection is
by magic bytes) to `/v1/parse` → `202 Accepted`; polling `/v1/jobs/{id}` →
`succeeded` with `engine_breakdown={'office': 2}`; `/v1/jobs/{id}/result` →
`source.mime == application/vnd.openxmlformats-officedocument.wordprocessingml.document`
and `stats.engine_breakdown == {'office': 2}`. (A live `uvicorn` run was deemed
unnecessary since the TestClient exercises the identical ASGI app and background
job pipeline.)
