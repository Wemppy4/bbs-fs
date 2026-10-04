"""Exercise original flight/orbit controls against the real Forge world camera.

Run after the isolated run-forge1122 client has entered its disposable world.
The fixture is in src/aihelper only and never writes a film or world block.
"""
import json
import os
import math
from pathlib import Path
from smoke import call, wait_for, ROOT


def state():
    return call('/bbs-camera-probe')


def mouse(x, y, mode='move', button=0):
    call('/ui-mouse', {'x': round(x), 'y': round(y), 'mode': mode, 'button': button,
                       'warp': os.environ.get('AIH_DIRECT_UI') != '1'})
    call('/wait', {'ticks': 2})


def drag(x, y, dx, dy, button=0):
    mouse(x, y)
    mouse(x, y, 'down', button)
    mouse(x + dx, y + dy, button=button)
    mouse(x + dx, y + dy, 'up', button)


def distance(a, b):
    return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))


def main():
    health = call('/health')
    assert Path(health['gameDir']).resolve() == (ROOT / 'run-forge1122').resolve(), health
    assert health['inWorld'], health
    call('/release', {})
    call('/wait', {'ticks': 3})
    errors_before = call('/log?limit=2000&level=ERROR+')['entries']
    call('/bbs-camera-probe', {'open': True})
    wait_for(lambda: state()['active'] and not state()['viewIsPlayer'])
    call('/wait', {'ticks': 4})
    initial = state()
    checks = []
    try:
        assert initial['perspective'] == 0, initial
        assert abs(initial['projectionM23'] + 1) < 1e-5 and abs(initial['projectionM33']) < 1e-5, initial
        checks.append('native render-view entity and perspective matrix')

        # Input travels through UIBaseMenu and the original OrbitCamera key dispatch.
        call('/bbs-camera-probe', {'key': 'W', 'pressed': True})
        call('/wait', {'ticks': 8})
        call('/bbs-camera-probe', {'key': 'W', 'pressed': False})
        call('/wait', {'ticks': 3})
        moved = state()
        assert distance(initial['position'], moved['position']) > 0.1, (initial, moved)
        call('/wait', {'ticks': 4})
        stopped = state()
        assert distance(moved['position'], stopped['position']) < 1e-4, (moved, stopped)
        assert distance(initial['player'], stopped['player']) < 1e-4, (initial, stopped)
        assert initial['playerYaw'] == stopped['playerYaw'] and initial['playerPitch'] == stopped['playerPitch'], (initial, stopped)
        checks.append('W flight, key release, player position and rotation remain unchanged')

        x, y = initial['width'] // 2, initial['height'] // 2
        drag(x, y, 35, 18)
        turned = state()
        assert abs(turned['yaw'] - stopped['yaw']) > 1 and abs(turned['pitch'] - stopped['pitch']) > 1, (stopped, turned)
        drag(x, y, 45, 0, button=1)
        rolled = state()
        assert abs(rolled['roll'] - turned['roll']) > 1, (turned, rolled)
        drag(x, y, 0, 45, button=2)
        widened = state()
        assert abs(widened['fov'] - rolled['fov']) > 1, (rolled, widened)
        call('/scroll', {'x': x, 'y': y, 'wheel': 120})
        assert state()['speed'] == initial['speed'] + 1, state()
        checks.append('mouse yaw/pitch, right-button roll, middle-button FOV, wheel flight speed')
        call('/screenshot', {'name': 'original-camera-flight-verified'})

        call('/bbs-camera-probe', {'freeLook': True})
        call('/wait', {'ticks': 3})
        captured = state()
        assert captured['freeLook'] and captured['grabbed'] and not captured['clipMouse'], captured
        # Absolute input beyond the window detects LWJGL2's default clipping bug.
        mouse(initial['width'] + 180, y)
        outside = state()
        assert outside['mouseX'] > outside['width'], outside
        assert abs(outside['yaw'] - captured['yaw']) > 1, (captured, outside)
        call('/bbs-camera-probe', {'freeLook': False})
        call('/wait', {'ticks': 2})
        released = state()
        assert not released['grabbed'] and released['clipMouse'] == initial['clipMouse'], released
        checks.append('free look beyond window edge and restored cursor clipping')

        call('/bbs-camera-probe', {'orbit': True})
        call('/wait', {'ticks': 3})
        orbit = state()
        drag(x, y, 35, 15)
        orbited = state()
        assert distance(orbit['position'], orbited['position']) > 0.1 and abs(orbit['yaw'] - orbited['yaw']) > 1, (orbit, orbited)
        drag(x, y, 25, 15, button=2)
        panned = state()
        assert distance(orbited['position'], panned['position']) > 0.01, (orbited, panned)
        assert abs(orbited['yaw'] - panned['yaw']) < 1e-4, (orbited, panned)
        call('/scroll', {'x': x, 'y': y, 'wheel': 240})
        call('/wait', {'ticks': 3})
        zoomed = state()
        assert distance(panned['position'], zoomed['position']) > 0.01, (panned, zoomed)
        checks.append('original viewport orbit, plane pan and distance zoom')

        call('/bbs-camera-probe', {'toggleOrtho': True})
        call('/wait', {'ticks': 3})
        ortho = state()
        assert ortho['ortho'] and abs(ortho['projectionM23']) < 1e-5 and abs(ortho['projectionM33'] - 1) < 1e-5, ortho
        assert ortho['fogPasses'] > 0 and abs(ortho['fogProjectionM23']) < 1e-5 and abs(ortho['fogProjectionM33'] - 1) < 1e-5, ortho
        assert not ortho['culling'], ortho
        assert ortho['glError'] == 0, ortho
        call('/screenshot', {'name': 'original-camera-ortho-near-ground'})
        # At this low angle the near plane straddles the ground. Raise the real
        # orbit camera for a second visual check with every view ray above it.
        drag(x, y, 0, 68)
        call('/scroll', {'x': x, 'y': y, 'wheel': -120})
        call('/wait', {'ticks': 3})
        ortho_above = state()
        assert 45 < ortho_above['pitch'] < 89 and ortho_above['position'][1] > initial['player'][1] + 1.5, ortho_above
        assert ortho_above['ortho'] and abs(ortho_above['projectionM23']) < 1e-5 and abs(ortho_above['projectionM33'] - 1) < 1e-5, ortho_above
        assert distance(initial['player'], ortho_above['player']) < 1e-4 and ortho_above['glError'] == 0, ortho_above
        call('/screenshot', {'name': 'original-camera-ortho-verified'})
        call('/bbs-camera-probe', {'toggleOrtho': True})
        call('/wait', {'ticks': 3})
        perspective = state()
        assert not perspective['ortho'] and abs(perspective['projectionM23'] + 1) < 1e-5, perspective
        assert perspective['culling'] == initial['previousCulling'], perspective
        checks.append('orthographic world projection and chunk culling restoration')

        call('/bbs-camera-probe', {'close': True})
        wait_for(lambda: state()['viewRestored'])
        final = state()
        assert not final['active'] and final['viewRestored'], final
        assert final['perspective'] == initial['previousPerspective'], (initial, final)
        assert final['culling'] == initial['previousCulling'] and not final['ortho'], final
        assert final['glError'] == 0, final
        errors_after = call('/log?limit=2000&level=ERROR+')['entries']
        assert errors_after == errors_before, errors_after
        checks.append('close restores render view, perspective and culling; no new runtime/GL errors')
        output = ROOT / 'build/reports/forge1122-original-camera-smoke.json'
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps({'ok': True, 'checks': checks, 'initial': initial, 'flight': widened, 'orbit': orbit,
                                      'orbited': orbited, 'panned': panned, 'zoomed': zoomed, 'ortho': ortho,
                                      'orthoAboveGround': ortho_above, 'perspective': perspective, 'final': final},
                                     ensure_ascii=False, indent=2), encoding='utf-8')
        print('PASS: original BBS camera controls, report:', output)
    finally:
        if state()['open']:
            call('/bbs-camera-probe', {'close': True})


if __name__ == '__main__':
    main()
