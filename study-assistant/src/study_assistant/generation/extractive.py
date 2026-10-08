"""No-LLM grounded fallback: expose evidence without fabricating answers."""

from __future__ import annotations

from ..retrieval.contracts import RetrievalResult
from .contracts import GroundedAnswer


class ExtractiveGenerator:
    """Transparent fallback used when no local language model is available."""

    provider_name = "extractive-evidence"

    def answer(self, retrieval: RetrievalResult) -> GroundedAnswer:
        if not retrieval.chunks:
            return GroundedAnswer(
                answer="The indexed study material does not contain enough evidence to answer this question.",
                confidence="missing",
                provider=self.provider_name,
            )
        evidence = "\n\n".join(
            f"Evidence {index}: {chunk.text}" for index, chunk in enumerate(retrieval.chunks, start=1)
        )
        return GroundedAnswer(
            answer=(
                "A local language model is not configured, so I will not invent a synthesized answer. "
                "The most relevant indexed evidence is:\n\n" + evidence
            ),
            citations=tuple(chunk.citation for chunk in retrieval.chunks),
            confidence="weak" if retrieval.confidence != "missing" else "missing",
            retrieved_chunk_ids=tuple(chunk.chunk_id for chunk in retrieval.chunks),
            provider=self.provider_name,
        )
