"""Pure, deterministic SM-2-style review scheduling."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Literal

from ..errors import ValidationError

ReviewGrade = Literal["again", "hard", "good", "easy"]


@dataclass(frozen=True)
class ReviewState:
    due_at: str
    interval_days: float = 0
    ease: float = 2.5
    repetitions: int = 0
    lapses: int = 0
    last_grade: str | None = None

    def __post_init__(self) -> None:
        if self.interval_days < 0 or self.ease < 1.3 or self.repetitions < 0 or self.lapses < 0:
            raise ValidationError("invalid review state")


def schedule_review(state: ReviewState, grade: ReviewGrade, now: datetime | None = None) -> ReviewState:
    if grade not in {"again", "hard", "good", "easy"}:
        raise ValidationError(f"unsupported review grade: {grade}")
    current = now or datetime.now(timezone.utc)
    if current.tzinfo is None:
        current = current.replace(tzinfo=timezone.utc)
    ease = state.ease
    repetitions = state.repetitions
    lapses = state.lapses
    if grade == "again":
        interval = 1.0
        repetitions = 0
        lapses += 1
        ease = max(1.3, ease - 0.2)
    elif grade == "hard":
        interval = max(1.0, state.interval_days * 1.2 if state.interval_days else 1.0)
        repetitions += 1
        ease = max(1.3, ease - 0.15)
    elif grade == "good":
        interval = 1.0 if repetitions == 0 else 6.0 if repetitions == 1 else max(1.0, state.interval_days * ease)
        repetitions += 1
    else:
        interval = 4.0 if repetitions == 0 else max(1.0, state.interval_days * ease * 1.3)
        repetitions += 1
        ease += 0.15
    due = current + timedelta(days=interval)
    return ReviewState(
        due_at=due.astimezone(timezone.utc).isoformat(timespec="seconds"),
        interval_days=round(interval, 3), ease=round(ease, 3),
        repetitions=repetitions, lapses=lapses, last_grade=grade,
    )
