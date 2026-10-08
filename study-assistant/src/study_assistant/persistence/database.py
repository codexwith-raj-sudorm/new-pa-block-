"""Repository-style SQLite persistence for study state and generated artifacts."""

from __future__ import annotations

import json
import sqlite3
from dataclasses import asdict
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
from typing import Iterable

from ..contracts import IngestionDocument, SourceRef
from ..errors import PersistenceError
from ..generation.contracts import Flashcard, QuizSet, StudyNotes
from ..retrieval.contracts import Citation
from .review_scheduler import ReviewState, schedule_review


class StudyDatabase:
    """A local SQLite repository with forward-compatible schema versioning."""

    SCHEMA_VERSION = 1

    def __init__(self, path: str | Path) -> None:
        self.path = Path(path).expanduser()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(str(self.path))
        self.connection.row_factory = sqlite3.Row
        self.connection.execute("PRAGMA foreign_keys = ON")
        self.connection.execute("PRAGMA journal_mode = WAL")
        self.connection.execute("PRAGMA synchronous = NORMAL")
        self._migrate()

    def close(self) -> None:
        self.connection.close()

    def __enter__(self) -> "StudyDatabase":
        return self

    def __exit__(self, _type: object, _value: object, _traceback: object) -> None:
        self.close()

    def register_source(self, source: SourceRef, *, title: str = "", language: str | None = None, status: str = "ready", page_count: int | None = None, duration_seconds: float | None = None) -> None:
        now = _now()
        try:
            with self.connection:
                self.connection.execute(
                    """
                    INSERT INTO sources(source_id, source_type, uri, display_name, checksum, title,
                                       language, page_count, duration_seconds, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(source_id) DO UPDATE SET
                      source_type=excluded.source_type, uri=excluded.uri,
                      display_name=excluded.display_name, checksum=excluded.checksum,
                      title=excluded.title, language=excluded.language,
                      page_count=excluded.page_count, duration_seconds=excluded.duration_seconds,
                      status=excluded.status, updated_at=excluded.updated_at
                    """,
                    (source.source_id, source.source_type, source.original_uri, source.display_name,
                     source.checksum, title or source.display_name, language, page_count,
                     duration_seconds, status, now, now),
                )
        except sqlite3.Error as exc:
            raise PersistenceError(f"could not register source: {exc}") from exc

    def register_document(self, document: IngestionDocument, *, status: str = "ready") -> None:
        self.register_source(
            document.source, title=document.title, language=document.language,
            status=status, page_count=document.page_count, duration_seconds=document.duration_seconds,
        )

    def list_sources(self) -> list[dict[str, object]]:
        return [dict(row) for row in self.connection.execute("SELECT * FROM sources ORDER BY updated_at DESC")]

    def save_notes(self, notes: StudyNotes, *, source_scope: str = "library") -> str:
        note_id = sha256(f"{notes.title}:{notes.to_markdown()}".encode("utf-8")).hexdigest()[:24]
        citations = json.dumps([citation.as_dict() for citation in notes.citations], sort_keys=True)
        try:
            with self.connection:
                self.connection.execute(
                    """
                    INSERT INTO notes(note_id, source_scope, title, markdown, structured_json, provider, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(note_id) DO UPDATE SET markdown=excluded.markdown,
                      structured_json=excluded.structured_json, provider=excluded.provider
                    """,
                    (note_id, source_scope, notes.title, notes.to_markdown(),
                     json.dumps(_notes_dict(notes), ensure_ascii=False, sort_keys=True), notes.provider, _now()),
                )
        except sqlite3.Error as exc:
            raise PersistenceError(f"could not save notes: {exc}") from exc
        return note_id

    def list_notes(self, source_scope: str | None = None) -> list[dict[str, object]]:
        if source_scope:
            rows = self.connection.execute("SELECT * FROM notes WHERE source_scope = ? ORDER BY created_at DESC", (source_scope,))
        else:
            rows = self.connection.execute("SELECT * FROM notes ORDER BY created_at DESC")
        return [dict(row) for row in rows]

    def save_flashcards(self, cards: Iterable[Flashcard], *, source_scope: str = "library") -> int:
        card_list = tuple(cards)
        try:
            with self.connection:
                for card in card_list:
                    citation_json = json.dumps([citation.as_dict() for citation in card.citations], sort_keys=True)
                    self.connection.execute(
                        """
                        INSERT INTO flashcards(card_id, source_scope, front, back, difficulty,
                                               citations_json, source_hash, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(card_id) DO UPDATE SET front=excluded.front, back=excluded.back,
                          difficulty=excluded.difficulty, citations_json=excluded.citations_json
                        """,
                        (card.card_id, source_scope, card.front, card.back, card.difficulty,
                         citation_json, card.source_hash, _now()),
                    )
                    self.connection.execute(
                        """
                        INSERT INTO reviews(card_id, due_at, interval_days, ease, repetitions, lapses, last_grade)
                        VALUES (?, ?, 0, 2.5, 0, 0, NULL)
                        ON CONFLICT(card_id) DO NOTHING
                        """,
                        (card.card_id, _now()),
                    )
        except sqlite3.Error as exc:
            raise PersistenceError(f"could not save flashcards: {exc}") from exc
        return len(card_list)

    def due_flashcards(self, *, limit: int = 20, now: str | None = None) -> list[dict[str, object]]:
        if limit < 1:
            raise PersistenceError("review limit must be positive")
        current = now or _now()
        rows = self.connection.execute(
            """
            SELECT f.*, r.due_at, r.interval_days, r.ease, r.repetitions, r.lapses, r.last_grade
            FROM flashcards f JOIN reviews r ON r.card_id = f.card_id
            WHERE r.due_at <= ? ORDER BY r.due_at ASC LIMIT ?
            """, (current, limit)
        )
        return [dict(row) for row in rows]

    def review_card(self, card_id: str, grade: str) -> ReviewState:
        row = self.connection.execute("SELECT * FROM reviews WHERE card_id = ?", (card_id,)).fetchone()
        if row is None:
            raise PersistenceError(f"unknown flashcard: {card_id}")
        state = ReviewState(
            due_at=row["due_at"], interval_days=float(row["interval_days"]), ease=float(row["ease"]),
            repetitions=int(row["repetitions"]), lapses=int(row["lapses"]), last_grade=row["last_grade"],
        )
        try:
            updated = schedule_review(state, grade)  # type: ignore[arg-type]
            with self.connection:
                self.connection.execute(
                    """
                    UPDATE reviews SET due_at=?, interval_days=?, ease=?, repetitions=?, lapses=?, last_grade=?
                    WHERE card_id=?
                    """, (updated.due_at, updated.interval_days, updated.ease,
                           updated.repetitions, updated.lapses, updated.last_grade, card_id),
                )
            return updated
        except (sqlite3.Error, ValueError) as exc:
            raise PersistenceError(f"could not record review: {exc}") from exc

    def save_quiz(self, quiz: QuizSet, *, source_scope: str = "library") -> str:
        quiz_id = sha256(f"{quiz.title}:{len(quiz.questions)}:{quiz.provider}".encode()).hexdigest()[:24]
        try:
            with self.connection:
                self.connection.execute(
                    "INSERT OR REPLACE INTO quiz_sessions(quiz_id, source_scope, title, score, total, provider, created_at) VALUES (?, ?, ?, NULL, ?, ?, ?)",
                    (quiz_id, source_scope, quiz.title, len(quiz.questions), quiz.provider, _now()),
                )
                self.connection.execute("DELETE FROM quiz_questions WHERE quiz_id = ?", (quiz_id,))
                for index, question in enumerate(quiz.questions):
                    self.connection.execute(
                        "INSERT INTO quiz_questions(quiz_id, ordinal, question_json, selected_answer, is_correct) VALUES (?, ?, ?, NULL, NULL)",
                        (quiz_id, index, json.dumps(_question_dict(question), ensure_ascii=False, sort_keys=True)),
                    )
        except sqlite3.Error as exc:
            raise PersistenceError(f"could not save quiz: {exc}") from exc
        return quiz_id

    def submit_quiz(self, quiz_id: str, answers: dict[int, str]) -> tuple[int, int]:
        rows = self.connection.execute(
            "SELECT ordinal, question_json FROM quiz_questions WHERE quiz_id = ? ORDER BY ordinal",
            (quiz_id,),
        ).fetchall()
        if not rows:
            raise PersistenceError(f"unknown quiz: {quiz_id}")
        score = 0
        try:
            with self.connection:
                for row in rows:
                    question = json.loads(row["question_json"])
                    selected = answers.get(int(row["ordinal"]))
                    correct = selected == question["correct_answer"]
                    score += int(correct)
                    self.connection.execute(
                        "UPDATE quiz_questions SET selected_answer = ?, is_correct = ? WHERE quiz_id = ? AND ordinal = ?",
                        (selected, int(correct), quiz_id, row["ordinal"]),
                    )
                self.connection.execute("UPDATE quiz_sessions SET score = ? WHERE quiz_id = ?", (score, quiz_id))
        except (sqlite3.Error, json.JSONDecodeError) as exc:
            raise PersistenceError(f"could not submit quiz: {exc}") from exc
        return score, len(rows)

    def export_json(self, output_path: str | Path) -> Path:
        target = Path(output_path).expanduser()
        payload = {
            "sources": self.list_sources(),
            "notes": self.list_notes(),
            "flashcards": [dict(row) for row in self.connection.execute("SELECT * FROM flashcards")],
            "reviews": [dict(row) for row in self.connection.execute("SELECT * FROM reviews")],
            "quizzes": [dict(row) for row in self.connection.execute("SELECT * FROM quiz_sessions")],
        }
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(payload, ensure_ascii=False, indent=2, default=str) + "\n", encoding="utf-8")
        return target

    def backup(self, output_path: str | Path) -> Path:
        target = Path(output_path).expanduser()
        target.parent.mkdir(parents=True, exist_ok=True)
        destination = sqlite3.connect(str(target))
        try:
            self.connection.backup(destination)
        finally:
            destination.close()
        return target

    def delete_source(self, source_id: str) -> None:
        try:
            with self.connection:
                self.connection.execute("DELETE FROM sources WHERE source_id = ?", (source_id,))
                self.connection.execute("DELETE FROM notes WHERE source_scope = ?", (source_id,))
                self.connection.execute("DELETE FROM flashcards WHERE source_scope = ?", (source_id,))
                self.connection.execute("DELETE FROM quiz_sessions WHERE source_scope = ?", (source_id,))
        except sqlite3.Error as exc:
            raise PersistenceError(f"could not delete source state: {exc}") from exc

    def _migrate(self) -> None:
        with self.connection:
            self.connection.execute("CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)")
            current = self.connection.execute("SELECT MAX(version) AS version FROM schema_migrations").fetchone()["version"] or 0
            if current < 1:
                self.connection.executescript(
                    """
                    CREATE TABLE IF NOT EXISTS sources (
                      source_id TEXT PRIMARY KEY, source_type TEXT NOT NULL, uri TEXT NOT NULL,
                      display_name TEXT NOT NULL, checksum TEXT, title TEXT NOT NULL,
                      language TEXT, page_count INTEGER, duration_seconds REAL,
                      status TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL
                    );
                    CREATE TABLE IF NOT EXISTS notes (
                      note_id TEXT PRIMARY KEY, source_scope TEXT NOT NULL, title TEXT NOT NULL,
                      markdown TEXT NOT NULL, structured_json TEXT NOT NULL, provider TEXT NOT NULL,
                      created_at TEXT NOT NULL
                    );
                    CREATE TABLE IF NOT EXISTS flashcards (
                      card_id TEXT PRIMARY KEY, source_scope TEXT NOT NULL, front TEXT NOT NULL,
                      back TEXT NOT NULL, difficulty TEXT NOT NULL, citations_json TEXT NOT NULL,
                      source_hash TEXT NOT NULL, created_at TEXT NOT NULL
                    );
                    CREATE TABLE IF NOT EXISTS reviews (
                      card_id TEXT PRIMARY KEY REFERENCES flashcards(card_id) ON DELETE CASCADE,
                      due_at TEXT NOT NULL, interval_days REAL NOT NULL, ease REAL NOT NULL,
                      repetitions INTEGER NOT NULL, lapses INTEGER NOT NULL, last_grade TEXT
                    );
                    CREATE TABLE IF NOT EXISTS quiz_sessions (
                      quiz_id TEXT PRIMARY KEY, source_scope TEXT NOT NULL, title TEXT NOT NULL,
                      score INTEGER, total INTEGER NOT NULL, provider TEXT NOT NULL, created_at TEXT NOT NULL
                    );
                    CREATE TABLE IF NOT EXISTS quiz_questions (
                      quiz_id TEXT NOT NULL REFERENCES quiz_sessions(quiz_id) ON DELETE CASCADE,
                      ordinal INTEGER NOT NULL, question_json TEXT NOT NULL,
                      selected_answer TEXT, is_correct INTEGER, PRIMARY KEY(quiz_id, ordinal)
                    );
                    CREATE INDEX IF NOT EXISTS reviews_due_idx ON reviews(due_at);
                    CREATE INDEX IF NOT EXISTS notes_scope_idx ON notes(source_scope);
                    CREATE INDEX IF NOT EXISTS flashcards_scope_idx ON flashcards(source_scope);
                    INSERT INTO schema_migrations(version, applied_at) VALUES (1, CURRENT_TIMESTAMP);
                    """
                )


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _citation_dict(citation: Citation) -> dict[str, object]:
    return citation.as_dict()


def _notes_dict(notes: StudyNotes) -> dict[str, object]:
    return {
        "title": notes.title, "executive_summary": notes.executive_summary,
        "key_terms": [
            {"term": term.term, "definition": term.definition, "citations": [_citation_dict(c) for c in term.citations]}
            for term in notes.key_terms
        ],
        "sections": [
            {"heading": section.heading, "takeaways": list(section.takeaways), "explanation": section.explanation,
             "citations": [_citation_dict(c) for c in section.citations]}
            for section in notes.sections
        ],
        "citations": [_citation_dict(c) for c in notes.citations], "provider": notes.provider,
    }


def _question_dict(question: object) -> dict[str, object]:
    return {"question_id": question.question_id, "question": question.question, "question_type": question.question_type,
            "options": list(question.options), "correct_answer": question.correct_answer,
            "explanation": question.explanation, "citations": [_citation_dict(c) for c in question.citations]}
