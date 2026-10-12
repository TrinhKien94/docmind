"""DocMind doc-service-py: FastAPI document-processing service.

Produces canonical ``Document`` objects (see docmind/docs/canonical-schema.md)
consumed by the Java orchestrator. Heavy ML engines are lazy + stubbed so the
service boots on CPU with only the base requirements installed.
"""

__version__ = "0.1.0"
