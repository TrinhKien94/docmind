# DocMind — Canonical Block Schema (Shared Contract)

This document is the **single source of truth** for the canonical JSON that flows
between the two DocMind services:

- **`doc-service-py`** (FastAPI parser) **produces** `Document` objects whose
  `blocks[]` conform to the `Block` shape below.
- **`api-java`** (Spring Boot 4 orchestrator) **consumes** that exact shape,
  persists it, derives `Chunk`s from it, and uses it for retrieval + chat.

Both services implement these field names, enum values, and rules **verbatim**.
Any change here is a contract change and must be mirrored on both sides in the
same release. The parser is the only writer of blocks; the Java chat path never
calls the Python service — it reads already-normalized data.

> Conventions: all keys are `snake_case`. Coordinates are in PDF/user space.
> `null` is allowed for optional string fields that do not apply to a block
> type (see per-field notes). Unknown keys MUST be ignored by consumers.

---

## 1. `Document`

The top-level object returned by the parser for one source file.

| Field         | Type              | Required | Notes |
|---------------|-------------------|----------|-------|
| `document_id` | string (UUID)     | yes      | Stable id for this parsed document. Chunks inherit it. |
| `source`      | `Source` object   | yes      | Provenance of the uploaded file (see §1.1). |
| `blocks`      | array of `Block`  | yes      | Ordered content blocks (see §2). May be empty. |
| `stats`       | `Stats` object    | yes      | Parse statistics incl. engine breakdown (see §1.2). |

### 1.1 `Source`

| Field      | Type            | Required | Notes |
|------------|-----------------|----------|-------|
| `filename` | string          | yes      | Original uploaded filename. |
| `sha256`   | string (hex)     | yes      | SHA-256 of the raw bytes (dedup / integrity). |
| `mime`     | string          | yes      | Detected MIME type, e.g. `application/pdf`. |
| `pages`    | integer         | yes      | Total page count (`1` for single-page / office docs without pages). |

### 1.2 `Stats`

| Field               | Type                     | Required | Notes |
|---------------------|--------------------------|----------|-------|
| `engine_breakdown`  | object<string, integer>  | yes      | Map of `parse_engine` value -> number of blocks that engine produced. Keys are drawn from the `parse_engine` enum (§2). Example: `{"pymupdf": 10, "paddleocr": 3}`. |

---

## 2. `Block`

One unit of parsed content. The order within `Document.blocks` plus the `order`
field define reading order.

| Field          | Type                 | Required | Notes |
|----------------|----------------------|----------|-------|
| `block_id`     | string (UUID)        | yes      | Unique within the document. |
| `page`         | integer              | yes      | 1-based page number the block lives on. |
| `order`        | integer              | yes      | 0-based reading order within the document (monotonic). |
| `type`         | enum (see below)     | yes      | Block kind. |
| `text`         | string               | yes      | Plain-text rendering (may be `""` for `image`). |
| `markdown`     | string               | yes      | Markdown rendering of this block. |
| `html`         | string               | yes      | HTML rendering of this block. |
| `latex`        | string \| null       | conditional | **Present (non-null) only when `type == "formula"`.** For all other types this field is `null`. |
| `bbox`         | array[4] of number   | yes      | Bounding box `[x0, y0, x1, y1]` (top-left origin: `x0,y0` = top-left corner, `x1,y1` = bottom-right corner), in page/user units. |
| `confidence`   | number (0.0–1.0)     | yes      | Parser/OCR confidence for this block. |
| `heading_path` | array of string      | yes      | Ancestor heading titles from document root to this block (breadcrumb). Empty array if none. |
| `parse_engine` | enum (see below)     | yes      | Engine that produced this block. |
| `parse_version`| string               | yes      | Version tag of the engine/pipeline that produced the block (e.g. `pymupdf-1.24.0`, `stub-0.1`). |

### 2.1 `type` enum (exact spellings)

```
heading | paragraph | table | formula | key_value | checkbox | image | list
```

### 2.2 `parse_engine` enum (exact spellings)

```
paddleocr | docling | pymupdf | office
```

- `paddleocr` — OCR / PP-Structure path for scanned pages and images.
- `docling` — Docling structural parsing (text-based complex PDFs).
- `pymupdf` — PyMuPDF text extraction (text-based simple PDFs).
- `office`   — native parsers for `.docx` / `.pptx` / `.xlsx` / `.md` / `.html`.

> MinerU is intentionally **deferred** (license to be verified) and therefore is
> **not** a valid `parse_engine` value in this contract.

### 2.3 `formula` → `latex` rule

- When `type == "formula"`, `latex` MUST be a non-null string holding the LaTeX
  representation of the formula; `text`/`markdown`/`html` still carry best-effort
  renderings. Formula handling targets **LaTeX output**.
- When `type != "formula"`, `latex` MUST be `null`. Consumers treat a non-null
  `latex` on a non-formula block as a contract violation.

---

## 3. `Chunk` (derived, inherits from blocks)

Chunks are produced by the Java service by grouping blocks for retrieval. A chunk
**inherits** the following from the block(s) it was built from:

| Field          | Inherited from | Notes |
|----------------|----------------|-------|
| `document_id`  | `Document`     | Same value as the parent document. |
| `heading_path` | `Block`        | The heading breadcrumb of the chunk's blocks. |
| `block_ids`    | `Block.block_id` (array) | The source block ids that compose this chunk. |
| `page`         | `Block.page`   | Page of the chunk (first block's page; a chunk stays within a page). |
| `bbox`         | `Block.bbox`   | Bounding box covering the chunk's blocks (union of block bboxes). |

This inheritance is what makes citations possible: a chat answer can point back
to `document_id` + `page` + `bbox` + the originating `block_ids`.

---

## 4. Worked example

A 2-page document with a heading, a paragraph, and one formula block:

```json
{
  "document_id": "7b2f6d9e-3c41-4a1b-9f0e-1d2c3b4a5e6f",
  "source": {
    "filename": "calculus-notes.pdf",
    "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "mime": "application/pdf",
    "pages": 2
  },
  "blocks": [
    {
      "block_id": "11111111-1111-4111-8111-111111111111",
      "page": 1,
      "order": 0,
      "type": "heading",
      "text": "Derivatives",
      "markdown": "# Derivatives",
      "html": "<h1>Derivatives</h1>",
      "latex": null,
      "bbox": [72.0, 90.0, 480.0, 118.0],
      "confidence": 0.99,
      "heading_path": ["Derivatives"],
      "parse_engine": "pymupdf",
      "parse_version": "pymupdf-1.24.0"
    },
    {
      "block_id": "22222222-2222-4222-8222-222222222222",
      "page": 1,
      "order": 1,
      "type": "paragraph",
      "text": "The derivative measures the instantaneous rate of change.",
      "markdown": "The derivative measures the instantaneous rate of change.",
      "html": "<p>The derivative measures the instantaneous rate of change.</p>",
      "latex": null,
      "bbox": [72.0, 130.0, 523.0, 172.0],
      "confidence": 0.97,
      "heading_path": ["Derivatives"],
      "parse_engine": "pymupdf",
      "parse_version": "pymupdf-1.24.0"
    },
    {
      "block_id": "33333333-3333-4333-8333-333333333333",
      "page": 2,
      "order": 2,
      "type": "formula",
      "text": "f'(x) = lim (h->0) (f(x+h) - f(x)) / h",
      "markdown": "$$f'(x) = \\lim_{h \\to 0} \\frac{f(x+h) - f(x)}{h}$$",
      "html": "<p>f&#39;(x) = lim<sub>h&rarr;0</sub> (f(x+h) &minus; f(x)) / h</p>",
      "latex": "f'(x) = \\lim_{h \\to 0} \\frac{f(x+h) - f(x)}{h}",
      "bbox": [100.0, 210.0, 410.0, 260.0],
      "confidence": 0.92,
      "heading_path": ["Derivatives"],
      "parse_engine": "docling",
      "parse_version": "docling-2.0.0"
    }
  ],
  "stats": {
    "engine_breakdown": { "pymupdf": 2, "docling": 1 }
  }
}
```

Note how the `formula` block is the only one with a non-null `latex`, and how
`engine_breakdown` sums per-engine block counts (`pymupdf: 2`, `docling: 1`).
