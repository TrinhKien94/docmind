"""Runtime configuration for doc-service-py.

Uses pydantic-settings so every value is overridable by an environment
variable. Names and defaults mirror the shared docker-compose contract
(DOCMIND_LOG_LEVEL, DOCMIND_PERSIST_ARTIFACTS) and FEAT-001's env style.
"""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Service settings (env-var backed, ${ENV:default} style)."""

    model_config = SettingsConfigDict(env_prefix="", case_sensitive=False)

    # Logging verbosity for the structured JSON logger.
    docmind_log_level: str = "INFO"

    # When true, process_parse_job writes debug artifacts (rendered page
    # images / raw per-engine output placeholders) under artifacts/<job_id>.
    docmind_persist_artifacts: bool = False

    # Bind host/port. Compose publishes 8091; keep that as the default.
    host: str = "0.0.0.0"
    port: int = 8091

    # Directory for persisted debug artifacts (relative to the service root).
    artifacts_dir: Path = Path("artifacts")

    # Upload size guard (bytes). 50 MiB default; validation enforces it.
    max_upload_bytes: int = 50 * 1024 * 1024

    # Model names reserved for later real-engine wiring (not used by stubs).
    paddleocr_lang: str = "en"
    embedding_model: str = "sentence-transformers/all-MiniLM-L6-v2"

    # HTTP header the Java orchestrator sets so logs share one correlation id.
    correlation_header: str = "X-Correlation-Id"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return a process-wide cached Settings instance."""
    return Settings()
