"""Validated grounded answer and study-artifact records."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal

from ..errors import ValidationError
from ..retrieval.contracts import Citation

Confidence = Literal["grounded", "weak", "missing"]
Difficulty = Literal["easy", "medium", "hard"]
QuestionType = Literal["multiple_choice", "true_false", "short_answer"]


def _validate_citations(citations: tuple[Citation, ...], allowed: set[str] | None = None) -> None:
    if not citations:
        raise ValidationError("generated item must contain at least one citation")
    if allowed is not None:
        unknown = {citation.chunk_id for citation in citations} - allowed
        if unknown:
            raise ValidationError(f"citation refers to a chunk outside the supplied context: {unknown}")


@dataclass(frozen=True)
class GroundedAnswer:
    answer: str
    citations: tuple[Citation, ...] = ()
    confidence: Confidence = "missing"
    retrieved_chunk_ids: tuple[str, ...] = ()
    provider: str = "none"

    def __post_init__(self) -> None:
        if not self.answer.strip():
            raise ValidationError("answer cannot be empty")
        if self.confidence not in {"grounded", "weak", "missing"}:
            raise ValidationError("invalid answer confidence")
        if self.confidence != "missing":
            _validate_citations(self.citations, set(self.retrieved_chunk_ids))


@dataclass(frozen=True)
class KeyTerm:
    term: str
    definition: str
    citations: tuple[Citation, ...]

    def __post_init__(self) -> None:
        if not self.term.strip() or not self.definition.strip():
            raise ValidationError("key term fields cannot be empty")
        _validate_citations(self.citations)


@dataclass(frozen=True)
class NoteSection:
    heading: str
    takeaways: tuple[str, ...]
    explanation: str
    citations: tuple[Citation, ...]

    def __post_init__(self) -> None:
        if not self.heading.strip() or not self.explanation.strip() or not self.takeaways:
            raise ValidationError("note sections require heading, takeaways, and explanation")
        if any(not takeaway.strip() for takeaway in self.takeaways):
            raise ValidationError("note takeaways cannot be empty")
        _validate_citations(self.citations)


@dataclass(frozen=True)
class StudyNotes:
    title: str
    executive_summary: str
    key_terms: tuple[KeyTerm, ...]
    sections: tuple[NoteSection, ...]
    citations: tuple[Citation, ...]
    provider: str = "extractive"

    def __post_init__(self) -> None:
        if not self.title.strip() or not self.executive_summary.strip():
            raise ValidationError("notes require a title and executive summary")
        if not self.sections:
            raise ValidationError("notes require at least one section")
        _validate_citations(self.citations)

    def to_markdown(self) -> str:
        lines = [f"# {self.title}", "", "## Executive summary", "", self.executive_summary, ""]
        if self.key_terms:
            lines.extend(["## Key terms", ""])
            for term in self.key_terms:
                lines.append(f"- **{term.term}:** {term.definition} [{', '.join(c.chunk_id for c in term.citations)}]")
            lines.append("")
        for section in self.sections:
            lines.extend([f"## {section.heading}", ""])
            lines.extend(f"- {takeaway}" for takeaway in section.takeaways)
            lines.extend(["", section.explanation, "", f"Sources: {', '.join(c.label() for c in section.citations)}", ""])
        return "\n".join(lines).strip() + "\n"


@dataclass(frozen=True)
class Flashcard:
    card_id: str
    front: str
    back: str
    difficulty: Difficulty
    citations: tuple[Citation, ...]
    source_hash: str

    def __post_init__(self) -> None:
        if not self.card_id.strip() or not self.front.strip() or not self.back.strip():
            raise ValidationError("flashcards require IDs, front, and back")
        if self.difficulty not in {"easy", "medium", "hard"}:
            raise ValidationError("invalid flashcard difficulty")
        _validate_citations(self.citations)
        if not self.source_hash.strip():
            raise ValidationError("flashcard source_hash cannot be empty")


@dataclass(frozen=True)
class QuizQuestion:
    question_id: str
    question: str
    question_type: QuestionType
    options: tuple[str, ...]
    correct_answer: str
    explanation: str
    citations: tuple[Citation, ...]

    def __post_init__(self) -> None:
        if not self.question_id.strip() or not self.question.strip():
            raise ValidationError("quiz question ID and question cannot be empty")
        if self.question_type == "multiple_choice":
            if len(self.options) < 2 or len(set(self.options)) != len(self.options):
                raise ValidationError("multiple-choice options must be distinct and contain at least two items")
        elif self.question_type == "true_false":
            if self.options != ("True", "False"):
                raise ValidationError("true/false options must be exactly True and False")
        elif self.question_type == "short_answer" and not self.correct_answer.strip():
            raise ValidationError("short-answer questions require an accepted answer")
        if self.question_type != "short_answer" and self.correct_answer not in self.options:
            raise ValidationError("quiz answer must be one of the options")
        if not self.explanation.strip():
            raise ValidationError("quiz explanation cannot be empty")
        _validate_citations(self.citations)


@dataclass(frozen=True)
class QuizSet:
    title: str
    questions: tuple[QuizQuestion, ...]
    provider: str = "extractive"

    def __post_init__(self) -> None:
        if not self.title.strip() or not self.questions:
            raise ValidationError("quiz sets require a title and questions")
        normalized = [question.question.casefold().strip() for question in self.questions]
        if len(normalized) != len(set(normalized)):
            raise ValidationError("quiz questions must be unique")
