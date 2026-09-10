"""Safety rails: per-minute rate limiting + daily LLM spend cap."""

import time
from collections import deque

from memory import store


class RateLimiter:
    """Sliding-window limiter. Single-user MVP: one global instance in app.py."""

    def __init__(self, max_calls=20, window_sec=60, clock=None):
        self.max_calls = max_calls
        self.window_sec = window_sec
        self.clock = clock or time.monotonic
        self.hits = deque()

    def allow(self):
        now = self.clock()
        while self.hits and self.hits[0] <= now - self.window_sec:
            self.hits.popleft()
        if len(self.hits) >= self.max_calls:
            return False
        self.hits.append(now)
        return True


def budget_ok(cap_usd):
    """True if today's spend is under the cap (cap<=0 disables the cap)."""
    try:
        cap = float(cap_usd or 0)
    except (TypeError, ValueError):
        return True
    if cap <= 0:
        return True
    return store.day_spend() < cap
