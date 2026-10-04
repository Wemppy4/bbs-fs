"""Verify original UIModelRenderer and native form/picking framebuffer integration."""
import json
import math
from pathlib import Path
from smoke import call, wait_for, ROOT
from camera_smoke import mouse, drag, distance


def state(data=None):
    return call('/bbs-viewport-probe', data)


def choose_pixel(scan):
    x, y = scan['sampleX'], scan['sampleY']
    # Pixel centres round to GUI coordinates at fractional scales. Try a small
    # neighbourhood so a transparent silhouette edge never decides the check.
    for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2)]:
        mouse(x + dx, y + dy)
        found = state()
        if found['pickedForm']:
            if found['kind'] == 'model':
                assert found['pickedIndex'] in scan['ids'], found
            else:
                assert found['pickedIndex'] == found['indexOf'], found
            return found
    raise AssertionError(('Visible rendered form pixel could not be picked', scan, state()))


def main():
    health = call('/health')
    assert Path(health['gameDir']).resolve() == (ROOT / 'run-forge1122').resolve() and health['inWorld'], health
    errors_before = call('/log?limit=2000&level=ERROR+')['entries']
    pixel_store = call('/gl-state')
    call('/release', {})
    state({'open': True})
    records = {}
    checks = []
    try:
        wait_for(lambda: state()['frames'] > 3)
        initial = state({'scan': True})
        assert initial['framebufferStatus'] == 0x8CD5, initial
        assert initial['fboWidth'] == math.floor(initial['area']['w'] * initial['scale'] + .5), initial
        assert initial['fboHeight'] == math.floor(initial['area']['h'] * initial['scale'] + .5), initial
        checks.append('Native RGBA/depth-stencil FBO is complete and matches the viewport size')
        for kind in ('model', 'billboard', 'extruded', 'mob'):
            state({'kind': kind, 'reset': True, 'highlight': False})
            call('/wait', {'ticks': 5})
            rendered = state({'scan': True})
            assert rendered['scan']['pixels'] > 100 and rendered['scan']['ids'], rendered
            assert all(index >= 20 for index in rendered['scan']['ids']), rendered
            if kind == 'model':
                assert len(rendered['scan']['ids']) > 1, rendered
            else:
                assert rendered['indexOf'] in rendered['scan']['ids'], rendered
            picked = choose_pixel(rendered['scan'])
            area = picked['area']
            mouse(area['x'] + 3, area['y'] + 3)
            assert state()['pickedIndex'] == 0, state()
            state({'highlight': True})
            call('/wait', {'ticks': 3})
            highlighted = state()
            assert highlighted['stateRestored'] and highlighted['currentFormCleared'] and highlighted['glError'] == 0, highlighted
            call('/screenshot', {'name': 'original-viewport-' + kind + '-verified'})
            records[kind] = {'rendered': rendered, 'picked': picked, 'highlighted': highlighted}
        checks.append('Model resolves individual bone IDs; billboard alpha mask, extrusion and mob resolve their form ID')
        checks.append('Empty background is not pickable; GLSL highlight preserves projection, view, framebuffer and shader')

        state({'kind': 'model', 'reset': True, 'highlight': False})
        call('/wait', {'ticks': 3})
        before = state()
        area = before['area']; x, y = area['x'] + area['w'] // 2, area['y'] + area['h'] // 2
        drag(x, y, 35, 18)
        turned = state()
        assert abs(turned['cameraYaw'] - before['cameraYaw']) > .05 and abs(turned['cameraPitch'] - before['cameraPitch']) > .05, (before, turned)
        drag(x, y, 25, 16, button=2)
        panned = state()
        assert distance(turned['pivot'], panned['pivot']) > .01, (turned, panned)
        call('/scroll', {'x': x, 'y': y, 'wheel': 240})
        call('/wait', {'ticks': 3})
        zoomed = state()
        assert distance(panned['camera'], zoomed['camera']) > .05, (panned, zoomed)
        assert zoomed['stateRestored'] and zoomed['currentFormCleared'] and zoomed['glError'] == 0, zoomed
        checks.append('Original UIModelRenderer orbit, plane pan and proportional wheel zoom')
        assert call('/gl-state')['unpackRow'] == pixel_store['unpackRow'], 'Viewport corrupted texture pixel-store state'
        assert call('/log?limit=2000&level=ERROR+')['entries'] == errors_before
        checks.append('No new runtime/GL errors or leaked texture pixel-store state')
        output = ROOT / 'build/reports/forge1122-original-viewport-smoke.json'
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps({'ok': True, 'checks': checks, 'initial': initial, 'forms': records, 'final': zoomed}, indent=2), encoding='utf-8')
        print('PASS: original 3D viewport and form picking, report:', output)
    finally:
        state({'close': True})


if __name__ == '__main__':
    main()
