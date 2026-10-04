"""Validate real clip -> OptiFine uniforms, including restore after clip end/stop.

Uses only ai_helper HTTP routes; never moves the OS pointer or opens a game window.
The running isolated client must already have an initialized shader pack.
"""
import json
from smoke import call, ROOT


def state(**request):
    return call('/bbs-shader-curves-probe', request or None)


def wait():
    call('/wait', {'ticks': 5})


def option(snapshot, name):
    assert snapshot['glError'] == 0, snapshot
    return next(item for item in snapshot['options'] if item['id'] == name)


def assert_gpu(snapshot, name, expected):
    current = option(snapshot, name)
    assert abs(current['value'] - expected) < 0.001, current
    assert current['gpu'], current
    # Some shadow/composite variants may not have run again since changing the
    # fixture. At least one real linked and rendered program must track it.
    assert any(abs(sample['value'] - expected) < 0.001 for sample in current['gpu']), current
    return current


def main():
    before = state()
    assert before['loaded'] and before['sources'] > 0 and before['curves'] > 0, before
    assert before['uploads'] > 0 and before['menuCurves'] > 0, before
    candidates = [item for item in before['options'] if item['gpu'] and not item['integer']]
    assert candidates, before
    selected = next((item for item in candidates if 'EXPOSURE' in item['id']), candidates[0])
    name, default = selected['id'], selected['default']
    start, end = default + 0.1, default + 0.3
    report = {'before': before, 'selected': name}
    try:
        state(action='start', option=name, **{'from': start, 'to': end})
        wait()
        report['start'] = assert_gpu(state(), name, start)
        state(action='seek', tick=5)
        wait()
        report['middle'] = assert_gpu(state(), name, (start + end) / 2)
        state(action='seek', tick=10)
        wait()
        report['end'] = assert_gpu(state(), name, end)
        state(action='seek', tick=20)
        wait()
        report['outside'] = assert_gpu(state(), name, default)
        state(action='seek', tick=5)
        wait()
    finally:
        state(action='stop')
    wait()
    report['stopped'] = assert_gpu(state(), name, default)
    destination = ROOT / 'build/reports/forge1122-shader-curves-smoke.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS: shader curve source hooks, real GPU interpolation, clip end/stop restore, GL0')


if __name__ == '__main__':
    main()
