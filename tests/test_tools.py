"""Zero-dependency tests. Run: python3 tests/test_tools.py"""

import asyncio
import sys

sys.path.insert(0, ".")

from agent.runner import run_with_tools
from tools.demo_router import detect_demo_tool
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


print("general tools:")
check("get_time works", "2026" in execute_tool("get_time", {}))
check("get_time bad tz", "Unknown timezone" in execute_tool("get_time", {"timezone": "Mars/Olympus"}))
check("calc basic", execute_tool("calculate", {"expression": "2+2*3"}) == "8")
check("calc percent-of", execute_tool("calculate", {"expression": "15% of 240"}) == "36.0")
check("calc caret power", execute_tool("calculate", {"expression": "2^3"}) == "8")
check("calc sqrt", execute_tool("calculate", {"expression": "sqrt(16)"}) == "4.0")
check(
    "calc blocks evil",
    "Error" in execute_tool("calculate", {"expression": "__import__('os').system('x')"}),
)
check("unknown tool", "unknown tool" in execute_tool("nope", {}))

print("web tools (degraded in sandbox, must not crash):")
check("web_search returns str", isinstance(execute_tool("web_search", {"query": "hi"}), str))
check(
    "fetch blocks localhost",
    "blocked" in execute_tool("fetch_page", {"url": "http://localhost:8000/"}).lower(),
)
check(
    "fetch rejects bad url",
    "Error" in execute_tool("fetch_page", {"url": "not-a-url"}),
)

print("demo router:")
check("time", (detect_demo_tool("what time is it?") or [None])[0] == "get_time")
check("calc cmd", detect_demo_tool("calc 2+2") == ("calculate", {"expression": "2+2"}))
check("what-is math", (detect_demo_tool("What is 12*12?") or [None])[0] == "calculate")
check("what-is words ignored", detect_demo_tool("What is the capital of France?") is None)
check("search", (detect_demo_tool("search for monsoon updates") or [None])[0] == "web_search")
check("fetch", (detect_demo_tool("fetch https://example.com") or [None])[0] == "fetch_page")
check("plain chat ignored", detect_demo_tool("hello there") is None)

print("runner loop (fake LLM):")


def fake_llm(messages):
    fake_llm.n = getattr(fake_llm, "n", 0) + 1
    if fake_llm.n == 1:
        return {
            "content": "",
            "tool_calls": [{"id": "1", "name": "calculate", "arguments": '{"expression": "7*6"}'}],
        }
    last = messages[-1]["content"]
    assert last == "42", f"tool result not fed back: {last!r}"
    return {"content": "7 times 6 is 42.", "tool_calls": []}


seen = []


async def on_tool(name, args, result):
    seen.append((name, result))


out = asyncio.run(run_with_tools([{"role": "user", "content": "hi"}], fake_llm, on_tool))
check("final text", out == "7 times 6 is 42.", repr(out))
check("tool executed", seen == [("calculate", "42")], repr(seen))


def fake_unknown(messages):
    fake_unknown.n = getattr(fake_unknown, "n", 0) + 1
    if fake_unknown.n == 1:
        return {"content": "", "tool_calls": [{"id": "1", "name": "nope", "arguments": "{}"}]}
    return {"content": f"handled:{'unknown tool' in messages[-1]['content']}", "tool_calls": []}


out2 = asyncio.run(run_with_tools([{"role": "user", "content": "x"}], fake_unknown))
check("unknown tool handled", out2 == "handled:True", repr(out2))

print(f"\n{passed} passed, {failed} failed")
sys.exit(1 if failed else 0)
