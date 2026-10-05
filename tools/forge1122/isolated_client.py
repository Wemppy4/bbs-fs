"""Use the original ai_helper CLI with independent launch state for the QA port.

The user client retains .aihelper/runClient.pid and runClient.log. This adapter
only relocates the CLI's bookkeeping; all launches and commands use ai_helper.
"""
import importlib.util
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT.parent / 'ai_helper/tools/mc.py'
os.environ.setdefault('AIH_PORT', '25615')
PORT = int(os.environ['AIH_PORT'])
if PORT != 25615:
    raise SystemExit('The isolated Forge QA client requires AIH_PORT=25615.')

spec = importlib.util.spec_from_file_location('bbs_isolated_ai_helper', HELPER)
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


def state_dir(project):
    target = Path(project) / '.aihelper/clients' / str(PORT)
    target.mkdir(parents=True, exist_ok=True)
    return str(target)


helper.state_dir = state_dir

if __name__ == '__main__':
    raise SystemExit(helper.main())
