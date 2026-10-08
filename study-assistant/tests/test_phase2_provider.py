from __future__ import annotations

import numpy as np

from study_assistant.embeddings.providers import HashEmbeddingProvider


def test_fixture_provider_returns_unormalized_but_finite_vectors() -> None:
    provider = HashEmbeddingProvider(dimension=8)
    matrix = provider.embed_documents(["local study material", "another passage"])
    assert matrix.shape == (2, 8)
    assert np.isfinite(matrix).all()
    assert provider.fingerprint
    assert provider.model_name == "hash-fixture-v1"
