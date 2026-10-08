"""Local embedding providers.

Sentence Transformers is optional at import time so PDF/chunk-only workflows
remain lightweight. The hash provider is intentionally marked as a fixture
provider; it gives tests a deterministic, zero-download vector source but is
not presented as a semantic model for production study search.
"""

from __future__ import annotations

import hashlib
import json
import re
from threading import Lock
from typing import Any, Sequence

import numpy as np

from ..errors import EmbeddingError, MissingDependencyError


def _as_float_matrix(value: Any, expected_rows: int | None = None) -> np.ndarray:
    if hasattr(value, "detach"):
        value = value.detach().cpu().numpy()
    try:
        matrix = np.asarray(value, dtype=np.float32)
    except (TypeError, ValueError) as exc:
        raise EmbeddingError(f"embedding provider returned non-numeric data: {exc}") from exc
    if matrix.ndim == 1:
        matrix = matrix.reshape(1, -1)
    if matrix.ndim != 2 or matrix.shape[1] < 1:
        raise EmbeddingError(f"embedding provider returned invalid shape {matrix.shape}")
    if expected_rows is not None and matrix.shape[0] != expected_rows:
        raise EmbeddingError(
            f"embedding provider returned {matrix.shape[0]} rows for {expected_rows} texts"
        )
    if not np.isfinite(matrix).all():
        raise EmbeddingError("embedding provider returned NaN or infinite values")
    return np.ascontiguousarray(matrix, dtype=np.float32)


class SentenceTransformerProvider:
    """CPU-first local Sentence Transformers adapter with model reuse."""

    _models: dict[tuple[str, str, str | None], Any] = {}
    _lock = Lock()

    def __init__(
        self,
        model_name: str = "all-MiniLM-L6-v2",
        *,
        device: str = "cpu",
        cache_folder: str | None = None,
        batch_size: int = 16,
    ) -> None:
        if not model_name.strip():
            raise ValueError("model_name cannot be empty")
        if batch_size < 1:
            raise ValueError("batch_size must be positive")
        self._model_name = model_name
        self.device = device
        self.cache_folder = cache_folder
        self.batch_size = batch_size
        self._dimension: int | None = None
        payload = {
            "provider": "sentence-transformers",
            "model_name": model_name,
            "device": device,
            "cache_folder": cache_folder,
            "normalize_embeddings": False,
        }
        self._fingerprint = hashlib.sha256(
            json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
        ).hexdigest()[:24]

    @property
    def model_name(self) -> str:
        return self._model_name

    @property
    def fingerprint(self) -> str:
        return self._fingerprint

    @property
    def dimension(self) -> int:
        if self._dimension is None:
            model = self._model()
            dimension = model.get_sentence_embedding_dimension()
            if not dimension or int(dimension) < 1:
                raise EmbeddingError("Sentence Transformer reported an invalid dimension")
            self._dimension = int(dimension)
        return self._dimension

    def _model(self) -> Any:
        key = (self._model_name, self.device, self.cache_folder)
        with self._lock:
            if key in self._models:
                return self._models[key]
            try:
                from sentence_transformers import SentenceTransformer
            except ImportError as exc:  # pragma: no cover - environment dependent
                raise MissingDependencyError(
                    "Sentence Transformer indexing requires the embeddings extra; "
                    "install sentence-transformers"
                ) from exc
            kwargs: dict[str, Any] = {"device": self.device}
            if self.cache_folder:
                kwargs["cache_folder"] = self.cache_folder
            try:
                model = SentenceTransformer(self._model_name, **kwargs)
            except Exception as exc:
                raise EmbeddingError(f"could not load local embedding model: {exc}") from exc
            self._models[key] = model
            return model

    def embed_documents(self, texts: Sequence[str]) -> np.ndarray:
        if not texts:
            return np.empty((0, self.dimension), dtype=np.float32)
        if any(not str(text).strip() for text in texts):
            raise EmbeddingError("cannot embed blank document text")
        try:
            result = self._model().encode(
                list(texts),
                batch_size=self.batch_size,
                show_progress_bar=False,
                convert_to_numpy=True,
                normalize_embeddings=False,
            )
        except Exception as exc:
            raise EmbeddingError(f"local document embedding failed: {exc}") from exc
        matrix = _as_float_matrix(result, expected_rows=len(texts))
        if matrix.shape[1] != self.dimension:
            raise EmbeddingError("embedding dimension changed during one provider session")
        return matrix

    def embed_query(self, text: str) -> np.ndarray:
        if not text.strip():
            raise EmbeddingError("cannot embed a blank query")
        return self.embed_documents([text])[0]


class HashEmbeddingProvider:
    """Deterministic fixture provider; use a real local model for semantic search."""

    def __init__(self, dimension: int = 64, model_name: str = "hash-fixture-v1") -> None:
        if dimension < 2:
            raise ValueError("hash embedding dimension must be at least 2")
        self._dimension = dimension
        self._model_name = model_name
        self._fingerprint = hashlib.sha256(
            f"{model_name}:{dimension}".encode("utf-8")
        ).hexdigest()[:24]

    @property
    def model_name(self) -> str:
        return self._model_name

    @property
    def dimension(self) -> int:
        return self._dimension

    @property
    def fingerprint(self) -> str:
        return self._fingerprint

    def embed_documents(self, texts: Sequence[str]) -> np.ndarray:
        matrix = np.vstack([self._embed_one(text) for text in texts]) if texts else np.empty((0, self.dimension), dtype=np.float32)
        return matrix

    def embed_query(self, text: str) -> np.ndarray:
        if not text.strip():
            raise EmbeddingError("cannot embed a blank query")
        return self._embed_one(text)

    def _embed_one(self, text: str) -> np.ndarray:
        if not text.strip():
            raise EmbeddingError("cannot embed blank document text")
        vector = np.zeros(self.dimension, dtype=np.float32)
        tokens = re.findall(r"\w+", text.casefold(), flags=re.UNICODE)
        for token in tokens:
            digest = hashlib.sha256(token.encode("utf-8")).digest()
            index = int.from_bytes(digest[:4], "little") % self.dimension
            sign = 1.0 if digest[4] & 1 else -1.0
            vector[index] += sign
        if not np.any(vector):
            vector[0] = 1.0
        return vector
