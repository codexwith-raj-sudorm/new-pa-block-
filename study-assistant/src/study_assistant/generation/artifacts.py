"""Validated extractive notes, flashcards, and quizzes."""

from __future__ import annotations

import re
from hashlib import sha256

from ..retrieval.contracts import RetrievalResult, RetrievedChunk
from .contracts import Flashcard, KeyTerm, NoteSection, QuizQuestion, QuizSet, StudyNotes


class ArtifactGenerator:
    """Generate useful, cited artifacts without requiring an LLM."""

    provider_name = "extractive-artifacts"

    def notes(self, retrieval: RetrievalResult, title: str | None = None) -> StudyNotes:
        chunks = retrieval.chunks
        citations = tuple(chunk.citation for chunk in chunks)
        summary = " ".join(_sentences(chunk.text, 2) for chunk in chunks[:2]).strip()
        sections = tuple(
            NoteSection(
                heading=_heading(chunk),
                takeaways=(_sentences(chunk.text, 1),),
                explanation=chunk.text,
                citations=(chunk.citation,),
            )
            for chunk in chunks
        )
        terms = tuple(
            KeyTerm(term=_term(chunk.text), definition=_sentences(chunk.text, 1), citations=(chunk.citation,))
            for chunk in chunks[:5]
        )
        return StudyNotes(
            title=title or f"Study notes: {retrieval.query[:80]}",
            executive_summary=summary or "No grounded evidence was retrieved.",
            key_terms=terms,
            sections=sections,
            citations=citations,
            provider=self.provider_name,
        )

    def flashcards(self, retrieval: RetrievalResult, limit: int = 10) -> tuple[Flashcard, ...]:
        cards: list[Flashcard] = []
        seen: set[str] = set()
        for chunk in retrieval.chunks:
            back = _sentences(chunk.text, 2)
            front = f"What is the main idea of the section beginning '{_term(chunk.text)}'?"
            key = re.sub(r"\W+", " ", front.casefold()).strip()
            if not back or key in seen:
                continue
            seen.add(key)
            source_hash = sha256(chunk.text.encode("utf-8")).hexdigest()
            cards.append(
                Flashcard(
                    card_id=sha256(f"{chunk.chunk_id}:flashcard".encode()).hexdigest()[:24],
                    front=front, back=back, difficulty="medium",
                    citations=(chunk.citation,), source_hash=source_hash,
                )
            )
            if len(cards) >= limit:
                break
        return tuple(cards)

    def quiz(self, retrieval: RetrievalResult, limit: int = 5) -> QuizSet:
        questions: list[QuizQuestion] = []
        for chunk in retrieval.chunks[:limit]:
            statement = _sentences(chunk.text, 1)
            question_id = sha256(f"{chunk.chunk_id}:true-false".encode()).hexdigest()[:24]
            questions.append(
                QuizQuestion(
                    question_id=question_id,
                    question=f"True or false: the source states that {statement}",
                    question_type="true_false", options=("True", "False"),
                    correct_answer="True", explanation=f"The statement is taken from the cited source text: {statement}",
                    citations=(chunk.citation,),
                )
            )
        return QuizSet(title=f"Quiz: {retrieval.query[:80]}", questions=tuple(questions), provider=self.provider_name)


def _sentences(text: str, count: int) -> str:
    pieces = re.split(r"(?<=[.!?])\s+", text.strip())
    return " ".join(pieces[:count]).strip() or text.strip()


def _term(text: str) -> str:
    words = re.findall(r"\w+", text, flags=re.UNICODE)
    return " ".join(words[:8]) or "source section"


def _heading(chunk: RetrievedChunk) -> str:
    suffix = f"page {chunk.page_start}" if chunk.page_start else (f"{chunk.start_seconds:.0f}s" if chunk.start_seconds else f"chunk {chunk.ordinal}")
    return f"{chunk.source_name} — {suffix}"
