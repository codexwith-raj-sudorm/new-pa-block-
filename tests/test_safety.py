"""Safety tests: rate limiter, spend cap, search fallback. Run: python3 tests/test_safety.py"""

import os
import sys
import tempfile

tmp = tempfile.mkdtemp()
os.environ["JARVIS_DB"] = os.path.join(tmp, "test.db")
sys.path.insert(0, ".")

from agent.limits import RateLimiter, budget_ok
from memory import store
from tools.registry import execute_tool

passed = failed = 0


def check(name, cond, info=""):
    global passed, failed
    if cond:
        passed += 1
        print(f"  ✅ {name}")
    else:
        failed += 1
        print(f"  ❌ {name} {info}")


print("rate limiter:")
now = [1000.0]
rl = RateLimiter(max_calls=3, window_sec=60, clock=lambda: now[0])
check("allows burst", rl.allow() and rl.allow() and rl.allow())
check("blocks over limit", not rl.allow())
now[0] += 61
check("refills after window", rl.allow())

print("spend cap:")
check("fresh day zero", store.day_spend() == 0.0)
store.log_spend(0.5)
store.log_spend(1.0)
check("accumulates", abs(store.day_spend() - 1.5) < 1e-9, store.day_spend())
check("under cap ok", budget_ok(2.0))
check("over cap blocked", not budget_ok(1.0))
check("cap disabled at 0", budget_ok(0))
check("ignores junk", store.day_spend("2099-01-01") == 0.0)

print("search fallback:")
os.environ.pop("TAVILY_API_KEY", None)
check("ddg path graceful", isinstance(execute_tool("web_search", {"query": "x"}), str))
os.environ["TAVILY_API_KEY"] = "fake-key-for-test"
try:
    check("tavily failure falls back", isinstance(execute_tool("web_search", {"query": "x"}), str))
finally:
    os.environ.pop("TAVILY_API_KEY", None)

print(f"\n{passed} passed, {failed} failed")
sys.exit(1 if failed else 0)
