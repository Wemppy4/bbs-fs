"""Production VideoForm decoder and its independent frozen palette preview."""
import json
import subprocess
from pathlib import Path
from smoke import call, ROOT


def snapshot(age, loop=False):
    call('/bbs-extended-forms-probe', {'videoAge': age, 'videoLoop': loop})
    for _ in range(8):
        call('/wait', {'ticks': 10})
        result = call('/bbs-extended-forms-probe', {'videoState': True})
        state = result['videoState']
        if all(p.get('valid') and p.get('rgb') for p in state.values()):
            wanted = age % 20 if loop else age
            if abs(state['world']['frame'] - wanted) <= 1:
                assert not result['errors'] and result['stateRestored'], result
                assert all(p > 20 for p in result['visiblePixels'][:2]), result
                return state
    raise AssertionError(result)


def main():
    call('/bbs-extended-forms-probe', {'open': True})
    fixture = call('/bbs-extended-forms-probe', {'videoFixture': True})
    path = Path(fixture['videoFile'])
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(['ffmpeg', '-v', 'error', '-n', '-f', 'lavfi', '-i', 'color=c=red:s=64x32:d=0.5:r=20',
                        '-f', 'lavfi', '-i', 'color=c=lime:s=64x32:d=0.5:r=20', '-filter_complex',
                        '[0:v][1:v]concat=n=2:v=1:a=0[out]', '-map', '[out]', '-pix_fmt', 'yuv420p', str(path)], check=True)
        call('/bbs-extended-forms-probe', {'video': fixture['videoLink']})
        red = snapshot(6)
        green = snapshot(16)
        loop = snapshot(26, True)
        assert red['world']['rgb'][0] > 220 and red['world']['rgb'][1] < 30, red
        assert green['world']['rgb'][1] > 220 and green['world']['rgb'][0] < 30, green
        assert loop['world']['rgb'][0] > 220 and loop['world']['rgb'][1] < 30, loop
        for state in (red, green, loop):
            assert state['preview']['frame'] == 0 and state['preview']['rgb'][0] > 220, state
            assert state['world']['width'] == 64 and state['world']['height'] == 32, state
        report = {'ok': True, 'red': red, 'green': green, 'loop': loop,
                  'checks': ['real VideoForm texture upload and pixels', 'entity clock seeks',
                             'loop wrap', 'separate frozen palette decoder']}
        output = ROOT / 'build/reports/forge1122-video-forms-smoke.json'
    finally:
        cleanup = call('/bbs-extended-forms-probe', {'open': True})
        print('Video cleanup:', json.dumps(cleanup.get('videoCleanup'), ensure_ascii=False))
        assert not path.exists(), 'Video decoder did not release fixture: ' + str(path)
    report['cleanup'] = cleanup.get('videoCleanup')
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS:', output)


if __name__ == '__main__':
    main()
