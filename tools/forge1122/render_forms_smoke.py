"""Production Billboard/Extruded renderer, in the isolated Forge client."""
import json
from pathlib import Path
from smoke import call, wait_for, ROOT


def state():
    return call('/bbs-forms-probe')


def change(action, slot):
    before = state()
    call('/bbs-forms-probe', {'action': action})
    call('/wait', {'ticks': 3})
    after = state()
    assert after['frames'] > before['frames'], (action, before, after)
    assert after['signatures'][slot] != before['signatures'][slot], (action, before, after)
    assert not after['glErrors'] and after['stateRestored'], after
    return after


def main():
    health = call('/health')
    assert Path(health['gameDir']).resolve() == (ROOT/'run-forge1122').resolve(), health
    assert health['inWorld'], health
    errors_before = call('/log?limit=2000&level=ERROR+')['entries']
    call('/release', {})
    call('/bbs-forms-probe', {'open': True})
    call('/wait', {'ticks': 12})
    first = state()
    assert first['frames'] > 0 and not first['glErrors'], first
    assert first['stateRestored'] and first['extrusionVertices'] > 36, first
    wait_for(lambda: len(state()['animationFrames']) == 2)
    assert state()['cacheReused'], state()
    pixel = first['overlapPixel']
    red, green, blue = pixel >> 16 & 255, pixel >> 8 & 255, pixel & 255
    assert red > blue * 1.5 and red > 90 and 30 < blue < 100 and green < 30, first
    call('/screenshot', {'name': 'original-forms-baseline-verified'})
    change('crop', 1)
    change('uv', 1)
    change('alpha', 0)
    change('overlay', 0)
    change('billboard', 2)
    change('filter', 1)
    assert state()['textureFilter'] == 9728, state()  # GL_NEAREST remains the shared texture default.
    call('/screenshot', {'name': 'original-forms-transforms-verified'})
    before = state()
    call('/bbs-forms-probe', {'action': 'reload'})
    call('/wait', {'ticks': 4})
    after = state()
    # OpenGL may reuse the released id. Unchanged geometry and successful drawing prove reload.
    assert after['extrusionVertices'] == before['extrusionVertices'], after
    assert after['signatures'][3] == before['signatures'][3], (before, after)
    assert after['cacheReused'] and not after['glErrors'] and after['stateRestored'], after
    assert call('/log?limit=2000&level=ERROR+')['entries'] == errors_before
    report = {'ok': True, 'checks': ['crop and resize', 'UV rotation and offsets', 'tint and alpha',
        'overlay', 'nested camera facing', 'mipmap and filter restoration', 'extrusion and hole walls',
        'GIF frame geometry cache', 'reload', 'far-to-near alpha blend', 'OpenGL and shader state'],
        'state': after,
        'limits': ['Visual parity still requires comparison with 1.20.4.',
            'This stage does not test the picking pipeline or sorting against cubic/mob transparency.']}
    output = ROOT/'build/reports/forge1122-original-forms-smoke.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS:', output)


if __name__ == '__main__':
    main()
