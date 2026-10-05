"""Between-tick native keyboard delivery and original sphere-mode settings UI."""
import json
import time
from smoke import call
from camera_smoke import mouse
from qa_environment import checked_client, report_path


ROUTE = '/bbs-gizmo-probe'


def state(data=None):
    return call(ROUTE, data)


def frame_key(key):
    state({'nativeKey': key})
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        result = state()
        sample = result['nativeKey']
        assert 'error' not in sample, result
        if 'consumed' in sample:
            assert sample['consumed'] and sample['beforeHasRay'], result
            return result
        time.sleep(.03)
    raise AssertionError('Native between-tick input was not consumed')


def pointer():
    area = state()['area']
    mouse(area['x'] + area['w'] * .7, area['y'] + area['h'] * .3)


def reset():
    state({'reset': True, 'mode': 'all', 'simple': False})
    pointer()


def sphere_button():
    box = state()['sphereButton']
    x, y = box['x'] + box['w'] / 2, box['y'] + box['h'] / 2
    mouse(x, y, 'down')
    mouse(x, y, 'up')


def main():
    report = {'ok': False, 'target': checked_client()}
    try:
        state({'open': True, 'mode': 'all'})
        call('/wait', {'ticks': 3})
        report['settingBefore'] = state()
        assert report['settingBefore']['sphereSubtype'] == 'MODES', report['settingBefore']
        assert report['settingBefore'].get('sphereLabels') == 2, report['settingBefore']
        assert report['settingBefore']['sphereMode'] == 0, report['settingBefore']
        sphere_button()
        report['settingAfterClick'] = state()
        assert report['settingAfterClick']['sphereMode'] == 1, report['settingAfterClick']
        assert report['settingBefore']['sphereLabel'] != report['settingAfterClick']['sphereLabel'], report
        report['keys'] = []
        for key, expected in [('g', 'ScreenTranslateDrag'), ('s', 'UniformScaleDrag'), ('r', 'ViewRotateDrag')]:
            reset()
            result = frame_key(key)
            report['keys'].append(result)
            assert result['nativeKey']['strategy'] == expected and result['strategy'] == expected, result
            assert not result['simple'], result
            frame_key('escape')
            assert not state()['editing'], state()
        reset()
        frame_key('r')
        report['arcball'] = frame_key('r')
        assert report['arcball']['strategy'] == 'ArcballDrag', report['arcball']
        frame_key('escape')
        sphere_button()
        assert state()['sphereMode'] == 0, state()
        reset()
        frame_key('r')
        report['trackball'] = frame_key('r')
        assert report['trackball']['strategy'] == 'TrackballDrag', report['trackball']
        frame_key('escape')
        reset()
        state({'simple': True})
        report['intentionalSimple'] = frame_key('g')
        assert report['intentionalSimple']['strategy'] == 'AdditiveDrag' and report['intentionalSimple']['simple'], report
        frame_key('escape')
        report['ok'] = True
    finally:
        report['cleanup'] = state({'close': True})
        call('/release', {})
        output = report_path('forge1122-gizmo-input-regressions-smoke.json')
        output.write_text(json.dumps(report, indent=2), encoding='utf8')
    print('PASS: native between-tick G/S/R strategies and sphere setting buttons', output)


if __name__ == '__main__':
    main()
