"""Shared AI Helper transport for the Forge 1.12.2 runtime checks."""
import importlib.util
import os
from pathlib import Path
import time

ROOT = Path(__file__).resolve().parents[2]
os.environ.setdefault("AIH_PORT", "25612")
spec = importlib.util.spec_from_file_location("ai_helper_mc", ROOT.parent / "ai_helper/tools/mc.py")
mc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mc)


def call(route, data=None):
    result = mc.call(route, data, timeout=65)
    assert result.get("ok"), (route, result)
    return result


def wait_for(predicate, seconds=45):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if predicate():
            return
        time.sleep(0.2)
    raise AssertionError("Timed out waiting for the tested state")


def click(button_id):
    button = next(v for v in call("/screen")["widgets"] if v["id"] == button_id)
    call("/click", {"x": button["x"] + button["width"] // 2,
                    "y": button["y"] + button["height"] // 2})


def command(text):
    result = call("/cmd", {"cmd": text})
    assert result["result"] > 0, result
