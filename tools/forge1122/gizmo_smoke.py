"""Exercise real Gizmo picking, mouse gestures, numeric entry and space menus."""
import json
import math
from pathlib import Path
from smoke import call, wait_for, ROOT
from camera_smoke import mouse, distance
from qa_environment import checked_client, report_path


def state(data=None):
    return call('/bbs-gizmo-probe', data)


def key(name, **extra):
    call('/key', dict(key=name, **extra))
    call('/wait', {'ticks': 2})


def number(text):
    for char in str(text):
        key({'-': 'minus', '.': 'period'}.get(char, char))


def reset(mode='all', **extra):
    state(dict(reset=True, mode=mode, **extra))
    call('/wait', {'ticks': 4})
    return state({'scan': True})


def hover_handle(index):
    rendered = state({'scan': True})
    assert str(index) in rendered['scan'], ('Missing rendered handle', index, rendered)
    sample = rendered['scan'][str(index)]
    for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2)]:
        x, y = sample['x'] + dx, sample['y'] + dy
        mouse(x, y)
        if state()['pickedIndex'] == index:
            return x, y
    raise AssertionError(('Handle pixel does not match picking', index, sample, state()))


def gesture(index, dx=32, dy=-16):
    x, y = hover_handle(index)
    before = state()
    mouse(x, y, 'down')
    started = state()
    assert started['editing'] and not started['hotkey'], (index, started)
    mouse(x + dx, y + dy)
    moved = state()
    mouse(x + dx, y + dy, 'up')
    ended = state()
    assert not ended['editing'] and ended['ends'] > before['ends'], (before, moved, ended)
    assert ended['releases'] > before['releases'], ('Menu releaseTransform was bypassed', before, ended)
    assert ended['stateRestored'] and ended['glError'] == 0, ended
    return {'before': before, 'started': started, 'moved': moved, 'ended': ended}


def idle_pointer():
    area = state()['area']
    mouse(area['x'] + area['w'] * .75, area['y'] + area['h'] * .3)


def space(ordinal, expected):
    # The original choice menu assigns 1..5 to Parent, Local, Global, View, World.
    idle_pointer()
    key('q')
    key(str(ordinal))
    assert state()['space'] == expected, state()


def numeric(op, axis, value):
    idle_pointer()
    key(op)
    started = state()
    assert started['editing'] and started['hotkey'], started
    if axis and (started['axis'] != axis.upper() or started['axis2'] != 'null' or started['screenTranslate'] or started['scaleAll'] or started['viewRotate'] or started['sphereRotate']):
        key(axis)
    number(value)
    typed = state()
    assert typed['numericActive'], typed
    key('enter')
    assert not state()['editing'], state()
    return typed, state()


def near(a, b, tolerance=1e-4):
    return all(abs(x-y) < tolerance for x, y in zip(a, b))


def main():
    checked_client()
    errors = call('/log?limit=2000&level=ERROR+')['entries']
    call('/release', {})
    state({'open': True})
    report = {'ok': False, 'records': {}}
    output = report_path('forge1122-original-gizmo-smoke.json')
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        wait_for(lambda: state()['frames'] > 4)
        initial = state({'scan': True})
        report['initial'] = initial
        assert initial['framebufferStatus'] == 0x8CD5 and initial['stateRestored'] and initial['glError'] == 0, initial
        for mode, required in [('move', [1, 2, 3, 4, 5, 6, 18]), ('scale', [7, 8, 9, 10, 11, 12, 19]), ('rotate', [13, 14, 15, 17])]:
            rendered = reset(mode)
            assert all(str(index) in rendered['scan'] for index in required), (mode, rendered)
            report['records'][mode + '_geometry'] = rendered
            call('/screenshot', {'name': 'original-gizmo-' + mode + '-verified'})

        reset('move')
        moved = gesture(1)
        assert abs(moved['ended']['translate'][0]) > .01 and near(moved['ended']['translate'][1:], [0, 0]), moved
        report['records']['move_x'] = moved
        reset('move')
        plane = gesture(4)
        assert abs(plane['ended']['translate'][1]) < 1e-4 and distance(plane['ended']['translate'], [0, 0, 0]) > .01, plane
        report['records']['move_xz'] = plane
        reset('move')
        screen = gesture(18)
        assert screen['started']['screenTranslate'] and distance(screen['ended']['translate'], [0, 0, 0]) > .01, screen
        report['records']['screen_translate'] = screen

        reset('scale')
        scaled = gesture(7)
        assert abs(scaled['ended']['scale'][0] - 1) > .01 and near(scaled['ended']['scale'][1:], [1, 1]), scaled
        report['records']['scale_x'] = scaled
        reset('scale')
        uniform = gesture(19, 45, -20)
        factors = uniform['ended']['scale']
        assert uniform['started']['scaleAll'] and abs(factors[0]-1) > .01 and near(factors, [factors[0]]*3), uniform
        report['records']['scale_all'] = uniform

        reset('rotate')
        x, y = hover_handle(15)
        center = state()['center']
        tangent = [-(y-center[1]), x-center[0]]
        factor = 35 / max(1, math.hypot(*tangent))
        rotated = gesture(15, tangent[0]*factor, tangent[1]*factor)
        assert abs(rotated['ended']['rotate'][2]) > .02 and near(rotated['ended']['rotate'][:2], [0, 0], .005), rotated
        report['records']['rotate_z'] = rotated
        reset('rotate')
        viewed = gesture(17, 25, -20)
        assert viewed['started']['viewRotate'] and distance(viewed['ended']['rotate'], [0, 0, 0]) > .02, viewed
        report['records']['view_rotate'] = viewed

        reset('rotate')
        rendered = state()
        cx, cy = rendered['center']; radius = rendered['radius']
        sphere = None
        for fx, fy in [(.3, .2), (-.3, .2), (.4, -.3), (-.4, -.3), (0, .55)]:
            x, y = cx + radius*fx, cy + radius*fy
            mouse(x, y)
            if state()['sphereHovered']:
                mouse(x, y, 'down')
                mouse(x+12, y+8)
                mouse(x+32, y+22)
                sphere = state()
                assert sphere['editing'] and sphere['sphereRotate'], sphere
                mouse(x+32, y+22, 'up')
                break
        assert sphere is not None and distance(state()['rotate'], [0, 0, 0]) > .01, state()
        report['records']['sphere'] = {'moved': sphere, 'ended': state()}

        reset()
        space(1, 'PARENT')
        typed, accepted = numeric('g', 'x', '1.25')
        assert near(accepted['translate'], [1.25, 0, 0]), accepted
        report['records']['numeric_translate'] = {'typed': typed, 'accepted': accepted}
        idle_pointer(); key('g'); key('x'); number('5')
        cancelled_value = state()
        key('escape')
        assert not state()['editing'] and near(state()['translate'], accepted['translate']), (cancelled_value, state())
        report['records']['numeric_cancel'] = {'typed': cancelled_value, 'cancelled': state()}

        reset()
        space(2, 'LOCAL')
        _, accepted = numeric('r', 'z', '90')
        assert abs(accepted['rotate'][2]-math.pi/2) < 1e-4, accepted
        _, local = numeric('g', 'x', '1')
        assert near(local['translate'], [0, 1, 0]), local
        report['records']['local_rotated_move'] = local
        reset(parentYaw=30)
        space(3, 'GLOBAL')
        _, global_move = numeric('g', 'x', '1')
        assert near(global_move['translate'], [math.cos(math.pi/6), 0, math.sin(math.pi/6)]), global_move
        report['records']['global_rotated_parent_move'] = global_move
        space(4, 'VIEW'); space(5, 'WORLD'); space(1, 'PARENT')

        reset()
        idle_pointer(); key('q', shift=True)
        assert state()['rotationMode'] == 'QUATERNION', state()
        _, quat = numeric('r', 'z', '45')
        assert quat['rotationMode'] == 'QUATERNION' and abs(quat['rotate'][2]-math.pi/4) < 1e-4, quat
        report['records']['quaternion_numeric_rotate'] = quat
        assert state()['stateRestored'] and state()['glError'] == 0, state()
        assert call('/log?limit=2000&level=ERROR+')['entries'] == errors
        report['final'] = state(); report['ok'] = True
        print('PASS: original Gizmo, UI transform gestures, numeric input and spaces:', output)
    finally:
        report['last'] = state()
        output.write_text(json.dumps(report, indent=2), encoding='utf-8')
        state({'close': True})


if __name__ == '__main__':
    main()
