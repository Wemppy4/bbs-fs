"""Original BBS commands through the real server dispatcher and native client packets."""
import json
import argparse
import time
from smoke import ROOT, call


def request(op):
    state = call('/bbs-commands-probe', {'op': op})
    task = state['requested']
    deadline = time.monotonic() + 45
    while state['completed'] != task and time.monotonic() < deadline:
        time.sleep(.1)
        state = call('/bbs-commands-probe')
    assert state['completed'] == task, state
    return state


def wait_for(predicate):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        state = call('/bbs-commands-probe')
        if predicate(state):
            return state
        time.sleep(.1)
    raise AssertionError(state)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--camera', action='store_true', help='also verify native HUD hiding and frame-overlay pixels')
    args = parser.parse_args()
    report = {'ok': False}
    try:
        report['server'] = request('prepare')
        for name in ('setForm', 'stateCompletion', 'permissionRejected', 'onHead', 'morphEntity',
                     'equipmentRestored', 'morphServer', 'config', 'cheatsOff', 'cheatsOn',
                     'damageControlRestore', 'damageControlShutdown', 'structureCommand',
                     'explosionNative', 'filmCompletion'):
            assert report['server']['checks'].get(name), (name, report['server'])
        report['morph'] = wait_for(lambda state: state['clientMorph'] and state['clientModel'])
        request('state')
        report['state'] = wait_for(lambda state: state['clientState'])
        wait_for(lambda state: state['capturedModel'])
        request('refresh')
        report['refresh'] = wait_for(lambda state: state['clientRefreshed'])
        request('play')
        report['play'] = wait_for(lambda state: state['clientPlaying'] and state['serverPlaying'] and state['serverActors'])
        request('stop')
        report['stop'] = wait_for(lambda state: not state['clientPlaying'] and not state['serverPlaying'])
        if args.camera:
            assert report['play']['worldHudVisible'] > 0 and not report['play']['camera'], report['play']
            request('camera')
            report['camera'] = wait_for(lambda state: state['clientPlaying'] and state['camera']
                                       and state['cameraHudHidden'] > 0 and state.get('subtitlePixels', 0) > 30
                                       and state.get('imagePixels', 0) > 100)
            assert report['camera']['glError'] == 0, report['camera']
            call('/screenshot', {'name': 'commands-world-camera-overlays'})
            request('stop')
            report['cameraStopped'] = wait_for(lambda state: not state['clientPlaying'] and not state['camera'])
        request('demorph')
        report['demorph'] = wait_for(lambda state: state['clientDemorph'])
        report['ok'] = True
    finally:
        report['cleanup'] = request('cleanup')
        output = ROOT / 'build/reports/forge1122-commands-smoke.json'
        output.write_text(json.dumps(report, indent=2), encoding='utf8')
    print('PASS: original commands, permissions, native packets and restoration', output)


if __name__ == '__main__':
    main()
