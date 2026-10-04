"""Exercise the unchanged BBS timeline using real menu input in the isolated Forge client."""
import json
from pathlib import Path
from smoke import call, wait_for, ROOT


def state(): return call('/bbs-timeline-probe')
def point(track, index):
    s = state()
    p = dict(next(t for t in s['tracks'] if t['id'] == track)['keys'][index])
    # The probe reports row origins in dope-sheet mode, and value coordinates in graph mode.
    if not s['graph']: p['y'] += 8
    return p
def mouse(x, y, mode='move'):
    call('/ui-mouse', {'x': round(x), 'y': round(y), 'mode': mode})
    call('/wait', {'ticks': 2})
def click(p):
    mouse(p['x'], p['y'])
    call('/click', {'x': p['x'], 'y': p['y']})


def main():
    health = call('/health')
    assert Path(health['gameDir']).resolve() == (ROOT/'run-forge1122').resolve(), health
    assert health['inWorld']
    call('/bbs-timeline-probe', {'open': True})
    call('/wait', {'ticks': 3})
    original = point('x', 1)
    click(original)
    assert point('x', 1)['selected'], state()
    call('/key', {'key': 'j'})
    assert not point('x', 1)['enabled'], state()
    call('/key', {'key': 'j'})
    assert point('x', 1)['enabled']
    step = (point('x', 2)['x'] - original['x']) / 20
    mouse(original['x'], original['y'], 'down')
    mouse(original['x'] + step * 5, original['y'])
    mouse(original['x'] + step * 5, original['y'], 'up')
    moved = point('x', 1)
    assert moved['tick'] == 25 and moved['value'] == original['value'], state()
    mouse(moved['x'] + step * 5, moved['y'])
    call('/key', {'key': 'i'})
    keys = next(t for t in state()['tracks'] if t['id']=='x')['keys']
    # The standalone timeline's cursor is continuous; film timelines supply their own playhead.
    assert len(keys) == 5 and any(abs(k['tick'] - 30) < 0.1 for k in keys), state()
    call('/key', {'key': 'delete'})
    assert len(next(t for t in state()['tracks'] if t['id']=='x')['keys']) == 4, state()
    call('/screenshot', {'name': 'original-timeline-keys-verified'})
    button = state()['button']
    click({'x': button['x']+10, 'y': button['y']+10})
    assert state()['graph'], state()
    call('/wait', {'ticks': 3})
    p = point('x', 1)
    click(p)
    original_value = p['value']
    mouse(p['x'],p['y'],'down')
    mouse(p['x'],p['y']+30)
    mouse(p['x'],p['y']+30,'up')
    assert point('x',1)['tick'] == p['tick'] and point('x',1)['value'] != original_value, state()
    call('/screenshot', {'name': 'original-timeline-graph-verified'})
    click({'x': button['x']+10, 'y': button['y']+10})
    p = point('pose',1)
    mouse(p['x'],p['y'])
    call('/wait', {'ticks': 20})
    call('/screenshot', {'name': 'original-timeline-pose-preview-verified'})
    assert state()['glError'] == 0, state()
    assert not call('/log?limit=2000&level=ERROR+')['entries']
    report = {'ok': True, 'checks': ['selection', 'enabled shortcut', 'drag time with snapping',
        'insert at cursor', 'delete selected', 'graph value drag', 'pose preview', 'OpenGL and runtime logs'], 'state': state()}
    output = ROOT/'build/reports/forge1122-original-timeline-smoke.json'
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS:', output)


if __name__ == '__main__': main()
