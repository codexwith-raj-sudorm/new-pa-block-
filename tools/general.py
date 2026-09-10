"""General tools: time + calculator. Fully offline, testable anywhere."""

import ast
import math
import operator
import re
from datetime import datetime

try:
    from zoneinfo import ZoneInfo
except Exception:  # pragma: no cover
    ZoneInfo = None

GET_TIME_SCHEMA = {
    "type": "function",
    "function": {
        "name": "get_time",
        "description": "Get the current date and time. Defaults to the user's timezone.",
        "parameters": {
            "type": "object",
            "properties": {
                "timezone": {
                    "type": "string",
                    "description": "IANA timezone, e.g. Asia/Kolkata, UTC. Default: Asia/Kolkata.",
                }
            },
        },
    },
}

CALCULATE_SCHEMA = {
    "type": "function",
    "function": {
        "name": "calculate",
        "description": "Evaluate a math expression. Supports + - * / ** % //, parentheses, "
        "sqrt/abs/round/min/max/pow, ^ for power, and phrases like '15% of 200'.",
        "parameters": {
            "type": "object",
            "properties": {
                "expression": {"type": "string", "description": "Math expression, e.g. '2+2*3'."}
            },
            "required": ["expression"],
        },
    },
}


def get_time(timezone: str = "Asia/Kolkata") -> str:
    tz = None
    if ZoneInfo is not None:
        try:
            tz = ZoneInfo(timezone)
        except Exception:
            return (
                f"Unknown timezone '{timezone}'. "
                "Examples that work: Asia/Kolkata, UTC, America/New_York."
            )
    now = datetime.now(tz) if tz else datetime.utcnow()
    return now.strftime("%A, %d %B %Y, %I:%M %p %Z")


_BINOPS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.Mod: operator.mod,
    ast.Pow: operator.pow,
    ast.FloorDiv: operator.floordiv,
}
_FUNCS = {
    "abs": abs,
    "round": round,
    "min": min,
    "max": max,
    "pow": pow,
    "sqrt": math.sqrt,
}


def _eval(node):
    if isinstance(node, ast.Expression):
        return _eval(node.body)
    if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
        return node.value
    if isinstance(node, ast.BinOp) and type(node.op) in _BINOPS:
        return _BINOPS[type(node.op)](_eval(node.left), _eval(node.right))
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
        val = _eval(node.operand)
        return val if isinstance(node.op, ast.UAdd) else -val
    if (
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Name)
        and node.func.id in _FUNCS
    ):
        return _FUNCS[node.func.id](*[_eval(a) for a in node.args])
    raise ValueError(f"unsupported expression ({type(node).__name__})")


def _humanize(expr: str) -> str:
    expr = expr.strip().rstrip("?").strip()
    # "15% of 240" -> "(15/100*240)"
    expr = re.sub(r"(\d+(?:\.\d+)?)\s*%\s*of\s*(\d+(?:\.\d+)?)", r"(\1/100*\2)", expr)
    # ^ means power for humans
    expr = expr.replace("^", "**")
    return expr


def calculate(expression: str) -> str:
    try:
        result = _eval(ast.parse(_humanize(expression), mode="eval"))
    except Exception as e:
        return f"Error: couldn't calculate that ({e}). Try plain math like 2+2*3 or sqrt(16)."
    return str(result)
