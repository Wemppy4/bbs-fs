"""Exercise the transformed ItemStack and WorldServer action capture boundaries in ai_test."""
import json
import time
from smoke import ROOT, call


def main():
    state = call('/bbs-actions-probe', {'test': True})
    requested = state['requested']
    deadline = time.monotonic() + 20
    while state['completed'] < requested and time.monotonic() < deadline:
        time.sleep(.1)
        state = call('/bbs-actions-probe', {})
    assert state['ok'] and state['completed'] == requested, state
    result = state['result']
    for key in ('useBlockCaptured', 'breakProgressCaptured', 'itemSnapshot', 'handAndHit',
                'recordedTick', 'breakProgressAndPosition', 'heldItemRestored', 'recorderReleased'):
        assert result.get(key), (key, result)
    output = ROOT / 'build/reports/forge1122-actions-smoke.json'
    output.write_text(json.dumps(state, indent=2), encoding='utf8')
    print('PASS:', output)


if __name__ == '__main__':
    main()