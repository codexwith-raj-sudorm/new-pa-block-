"""Phase 2 local embedding providers and exact semantic index."""

from .contracts import EmbeddingProvider, IndexProgress, SearchResult, VectorRecord
from .index import SemanticIndex, load_staged_chunks
from .providers import HashEmbeddingProvider, SentenceTransformerProvider

__all__ = [
    "EmbeddingProvider",
    "HashEmbeddingProvider",
    "IndexProgress",
    "SearchResult",
    "SemanticIndex",
    "SentenceTransformerProvider",
    "VectorRecord",
    "load_staged_chunks",
]
