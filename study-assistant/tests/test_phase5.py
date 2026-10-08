from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path

from study_assistant.generation import ArtifactGenerator
from study_assistant.persistence import StudyDatabase
from study_assistant.persistence.review_scheduler import ReviewState, schedule_review
from study_assistant.retrieval.contracts import Citation
from study_assistant.generation.contracts import Flashcard


def card() -> Flashcard:
    return Flashcard(
        card_id="card-1", front="What is a local index?", back="An index stored on this machine.",
        difficulty="easy", citations=(Citation("chunk-1", "fixture.pdf", page_start=1),),
        source_hash="hash",
    )


def test_review_scheduler_is_deterministic() -> None:
    now = datetime(2026, 1, 1, tzinfo=timezone.utc)
    initial = ReviewState(due_at=now.isoformat())
    good = schedule_review(initial, "good", now=now)
    again = schedule_review(good, "again", now=now)
    assert good.interval_days == 1
    assert good.repetitions == 1
    assert again.interval_days == 1
    assert again.repetitions == 0
    assert again.lapses == 1


def test_database_persists_cards_reviews_exports_and_backup(tmp_path: Path) -> None:
    database = tmp_path / "study.sqlite"
    with StudyDatabase(database) as db:
        assert db.save_flashcards((card(),), source_scope="fixture") == 1
        due = db.due_flashcards(limit=5)
        assert due[0]["card_id"] == "card-1"
        updated = db.review_card("card-1", "good")
        assert updated.repetitions == 1
        quiz_id = db.save_quiz(
            type("Quiz", (), {
                "title": "Fixture quiz", "provider": "fixture",
                "questions": (type("Question", (), {
                    "question_id": "q1", "question": "Is this local?", "question_type": "true_false",
                    "options": ("True", "False"), "correct_answer": "True", "explanation": "Fixture",
                    "citations": (Citation("chunk-1", "fixture.pdf", page_start=1),),
                })(),),
            })(), source_scope="fixture"
        )
        assert db.submit_quiz(quiz_id, {0: "True"}) == (1, 1)
        export = db.export_json(tmp_path / "export.json")
        backup = db.backup(tmp_path / "backup.sqlite")
    assert export.exists() and backup.exists()
    with StudyDatabase(backup) as restored:
        assert restored.due_flashcards(limit=5) == []
