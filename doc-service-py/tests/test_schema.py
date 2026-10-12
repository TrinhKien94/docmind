"""Schema rules: the formula/latex invariant from the canonical contract."""

from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.schema import Block, BlockType, ParseEngine


def _base_kwargs(**overrides):
    kwargs = dict(
        block_id="11111111-1111-4111-8111-111111111111",
        page=1,
        order=0,
        type=BlockType.PARAGRAPH,
        text="x",
        markdown="x",
        html="<p>x</p>",
        latex=None,
        bbox=(0.0, 0.0, 1.0, 1.0),
        confidence=0.9,
        heading_path=[],
        parse_engine=ParseEngine.PYMUPDF,
        parse_version="stub-0.1",
    )
    kwargs.update(overrides)
    return kwargs


def test_latex_rejected_on_non_formula_block():
    with pytest.raises(ValidationError):
        Block(**_base_kwargs(type=BlockType.PARAGRAPH, latex="x^2"))


def test_latex_required_on_formula_block():
    with pytest.raises(ValidationError):
        Block(**_base_kwargs(type=BlockType.FORMULA, latex=None))


def test_formula_block_with_latex_ok():
    block = Block(**_base_kwargs(type=BlockType.FORMULA, latex=r"\frac{a}{b}"))
    assert block.latex == r"\frac{a}{b}"
    assert block.type == "formula"


def test_parse_engine_rejects_mineru():
    # MinerU is intentionally deferred and not a valid parse_engine value.
    with pytest.raises(ValidationError):
        Block(**_base_kwargs(parse_engine="mineru"))
