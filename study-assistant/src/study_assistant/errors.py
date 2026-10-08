"""Typed errors exposed at the ingestion and indexing boundaries."""


class StudyAssistantError(Exception):
    """Base class for expected, user-actionable errors."""


class ValidationError(StudyAssistantError, ValueError):
    """A source, segment, document, or chunk failed contract validation."""


class MissingDependencyError(StudyAssistantError, RuntimeError):
    """An optional provider was requested but is not installed."""


class IngestionError(StudyAssistantError, RuntimeError):
    """A source could not be safely loaded or converted."""


class StagingError(StudyAssistantError, RuntimeError):
    """A validated ingestion result could not be written locally."""


class EmbeddingError(StudyAssistantError, RuntimeError):
    """An embedding provider failed or returned invalid vectors."""


class IndexingError(StudyAssistantError, RuntimeError):
    """The local semantic index could not complete an operation."""


class ModelMismatchError(IndexingError):
    """The requested provider cannot safely mix with the existing index."""


class IndexStateError(IndexingError):
    """The local index is not ready for the requested operation."""
