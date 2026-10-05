"""Check actual OptiFine shadow matrices and light vector under a real horizontal sun curve."""
import json
from smoke import call
from qa_environment import checked_client, report_path


def snapshot():
    full = call('/bbs-shader-curves-probe')
    return {key: full[key] for key in ('sunYaw', 'shadowCameraUpdates', 'shadowLightUpdates',
                                      'shadowModelView', 'shadowModelViewInverse',
                                      'shadowLightPositionVector', 'glError')}


def main():
    target=checked_client()
    report = {'target': target}
    try:
        call('/bbs-shader-curves-probe', {'action': 'start', 'option': 'sun_horizontal_rotation', 'from': 0, 'to': 90})
        call('/wait', {'ticks': 10})
        report['base'] = base = snapshot()
        assert base['glError'] == 0 and base['shadowCameraUpdates'] > 0, base
        call('/bbs-shader-curves-probe', {'action': 'seek', 'tick': 10})
        call('/wait', {'ticks': 10})
        report['turned'] = turn = snapshot()
        assert turn['glError'] == 0 and turn['sunYaw'] == 90, turn
        assert turn['shadowCameraUpdates'] > base['shadowCameraUpdates'], turn
        assert turn['shadowLightUpdates'] > base['shadowLightUpdates'], turn
        original, rotated = base['shadowModelView'], turn['shadowModelView']
        # World-to-light camera postmultiplies inverse yaw; matrix storage is column-major.
        for row in range(3):
            assert abs(rotated[row] - original[8 + row]) < .002, (base, turn)
            assert abs(rotated[4 + row] - original[4 + row]) < .002, (base, turn)
            assert abs(rotated[8 + row] + original[row]) < .002, (base, turn)
        a, b = base['shadowLightPositionVector'], turn['shadowLightPositionVector']
        assert abs(b[0] - a[2]) < .002 and abs(b[1] - a[1]) < .002 and abs(b[2] + a[0]) < .002, (a, b)
        report['screenshot'] = call('/screenshot', {'name': 'shader-sun-yaw-90'})
    finally:
        call('/bbs-shader-curves-probe', {'action': 'stop'})
        call('/wait', {'ticks': 5})
        report['stopped'] = snapshot()
        path = report_path('forge1122-shader-sun-smoke.json')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(report, indent=2), encoding='utf-8')
    assert report['stopped']['sunYaw'] == 0 and report['stopped']['glError'] == 0, report
    print('PASS: real sun curve rotates native shadow camera and world light vector together; stop resets yaw')


if __name__ == '__main__':
    main()
