"""Exercise the running Forge client through the existing ai_helper Python client.

Start with: AIH_PORT=25612 python ../ai_helper/tools/mc.py launch
This uses only the isolated ai_test world and the fixture bundled with these checks.
"""
import importlib.util
import argparse
import json
import os
import re
from pathlib import Path
import shutil
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--release', action='store_true', help='test runObfClient distribution JAR')
    args = parser.parse_args()
    run_dir = ROOT / ('run-forge1122-obf' if args.release else 'run-forge1122')
    health = call("/health")
    assert health["inWorld"] and health["bbsLoaded"] and health["minecraft"] == "1.12.2", health
    assert Path(health["gameDir"]).resolve() == run_dir.resolve(), health
    # Select the disposable world explicitly even if a user changed worlds
    # after launching the helper. No block mutations precede this step.
    call("/disconnect", {})
    call("/world", {"world": "ai_test"})
    wait_for(lambda: call("/health")["inWorld"])
    target = run_dir / "config/bbs/assets/models/helper_drone"
    target.mkdir(parents=True, exist_ok=True)
    for name in ("model.bbs.json", "model.png", "config.json"):
        shutil.copyfile(ROOT / "src/forgeTest/resources/fixtures/helper_drone" / name, target / name)
    if call("/health")["screen"] is not None:
        call("/key", {"key": "escape"})
    # Deterministic empty location in the disposable test world.
    command("tp @p 0.5 4 0.5 0 15")
    command("gamerule doMobSpawning false")
    command("gamerule doDaylightCycle false")
    command("time set 6000")
    if call("/block", {"x": 0, "y": 4, "z": 3})["id"] != "minecraft:air":
        command("setblock 0 4 3 air")
    call("/key", {"key": "f6"})
    wait_for(lambda: call("/health")["screen"] == "ModelScreen")
    click(1)  # reload
    call("/click", {"x": 30, "y": 34})
    call("/type", {"text": "helper_drone"})
    call("/click", {"x": 40, "y": 56})
    wait_for(lambda: "animations" in call("/bbs1122"))
    snapshot = call("/bbs1122")
    assert len(snapshot["animations"]) == 6 and "helper_drone" in snapshot["loadedModels"], snapshot
    # BBS's real ActionsConfig defaults to "idle", so select the intended
    # action explicitly rather than assuming the alpha's empty default.
    for _ in range(len(snapshot["animations"]) + 1):
        click(4)
        if re.search(r'\bidle:\s*"fold"', call("/bbs1122")["form"]):
            break
    else:
        raise AssertionError("Could not select the embedded fold animation")
    before = call("/bbs1122")["bones"]
    call("/wait", {"ticks": 7})
    after = call("/bbs1122")["bones"]
    assert before != after, "Animation did not change the evaluated bones"
    click(8)  # first bone, antenna
    click(10)  # rotate 10 degrees
    click(5)  # save
    saved = call("/bbs1122")["form"]
    click(10)  # alter pose again
    assert call("/bbs1122")["form"] != saved
    click(6)  # restore saved form
    assert call("/bbs1122")["form"] == saved
    call("/screenshot", {"name": "forge-model-ui-verified"})
    click(7)  # place full form through the actual network packet
    wait_for(lambda: call("/block", {"x": 0, "y": 4, "z": 3})["id"] == "bbs:model")
    block = call("/block", {"x": 0, "y": 4, "z": 3})
    assert "antenna" in block["nbt"] and "helper_drone" in block["nbt"] and "fold" in block["nbt"], block
    call("/look", {"yaw": 0, "pitch": 20})
    call("/wait", {"ticks": 5})
    call("/screenshot", {"name": "forge-world-model-verified"})
    call("/key", {"key": "use"})
    wait_for(lambda: call("/health")["screen"] == "ModelScreen")
    click(8); click(10); click(7)  # edit and apply to the same tile
    call("/wait", {"ticks": 5})
    edited = call("/block", {"x": 0, "y": 4, "z": 3})
    assert edited["nbt"] != block["nbt"], "Model block edit packet did not reach server"
    call("/disconnect", {})
    assert not call("/health")["inWorld"]
    call("/world", {"world": "ai_test"})
    wait_for(lambda: call("/health")["inWorld"])
    wait_for(lambda: call("/block", {"x": 0, "y": 4, "z": 3})["id"] == "bbs:model")
    restored = call("/block", {"x": 0, "y": 4, "z": 3})
    assert restored["nbt"] == edited["nbt"], "Form changed after world reload"
    status = call("/bbs1122")
    assert status["renderEpoch"] > 0 and status["glError"] == 0 and "helper_drone" in status["loadedModels"], status
    errors = call("/log?limit=2000&level=ERROR+")
    assert not errors["entries"], errors
    result = {"ok": True, "minecraft": "1.12.2", "checks": [
        "Forge mod loaded and world entered", "external model reload and texture rendering",
        "six BBS animations and moving evaluated bones", "pose save/load through GUI",
        "complete form placed through server packet", "right-click model editor and apply packet",
        "model block survives world disconnect/rejoin", "no runtime ERROR/FATAL or OpenGL errors"
    ], "health": call("/health"), "block": restored}
    output = ROOT / ('build/reports/forge1122-release-smoke.json' if args.release else 'build/reports/forge1122-smoke.json')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
