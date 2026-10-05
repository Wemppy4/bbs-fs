"""Explicit target selection for QA while the normal Minecraft client belongs to the user."""
import os
from pathlib import Path
from smoke import call, ROOT


def checked_client():
    port = os.environ.get('AIH_PORT')
    directory = os.environ.get('AIH_GAME_DIR')
    if not port or not directory:
        raise RuntimeError('Set AIH_PORT and AIH_GAME_DIR for the isolated QA client before running this probe')
    if port == '25612':
        raise RuntimeError('Port 25612 belongs to the user; these probes require the isolated QA client')
    expected = Path(directory).resolve()
    if expected == (ROOT / 'run-forge1122-obf').resolve():
        raise RuntimeError('run-forge1122-obf belongs to the user; select a separate QA game directory')
    health = call('/health')
    assert Path(health['gameDir']).resolve() == expected, ('Wrong Minecraft directory', health, str(expected))
    assert health['inWorld'] and health['bbsLoaded'] and health['minecraft'] == '1.12.2', health
    # Hidden clients must never warp the operating-system cursor during UI tests.
    os.environ['AIH_DIRECT_UI'] = '1'
    return {'port': int(port), 'gameDir': str(expected)}


def report_path(name):
    directory = Path(os.environ.get('AIH_REPORT_DIR', str(ROOT / 'build/reports/qa')))
    directory.mkdir(parents=True, exist_ok=True)
    return directory / name
