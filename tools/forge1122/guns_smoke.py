"""Server gun mechanics and native spawn synchronization in ai_test; no real player's inventory is touched."""
import json
import time
from smoke import call
from qa_environment import checked_client, report_path


def wait(requested):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        state = call('/bbs-guns-probe', {})
        assert state['ok'], state
        if state['completed'] >= requested:
            return state
        time.sleep(.1)
    raise AssertionError('Gun server task timed out')


def main():
    target = checked_client()
    try:
        state = call('/bbs-guns-probe', {'op': 'test'})
        state = wait(state['requested'])
        for key in ('itemRegistered', 'nativeItemRenderer', 'defaultGunForm'):
            assert state[key], (key, state)
        result = state['result']
        assert result['projectileCount'] == 3, result
        for key in ('fireCommand', 'spawnPayload', 'launch', 'launchAdditive', 'bounce',
                    'impactCommand', 'stick', 'fallAfterBlockRemoved', 'fallAfterExternalMove', 'lifetime',
                    'tickAndVanishCommands', 'damage', 'knockback', 'vanishOnHit',
                    'zoomInterpolation', 'zoomRestore', 'instantZoom', 'rejectedDamageRestoresFireTimer'):
            assert result[key], (key, result)
        deadline = time.monotonic() + 10
        while len(state['clientProjectiles']) < 3 and time.monotonic() < deadline:
            time.sleep(.1)
            state = call('/bbs-guns-probe', {})
        assert len(state['clientProjectiles']) == 3, state
        state['target'] = target
        output = report_path('forge1122-guns-smoke.json')
        output.write_text(json.dumps(state, indent=2), encoding='utf8')
        print('PASS:', output)
    finally:
        state = call('/bbs-guns-probe', {'op': 'cleanup'})
        wait(state['requested'])


if __name__ == '__main__':
    main()
