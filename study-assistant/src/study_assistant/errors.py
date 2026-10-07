"""Typed errors exposed at the ingestion boundary."""


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
