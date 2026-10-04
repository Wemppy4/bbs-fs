"""Real UIClips input against disposable Film data; never edits a user project."""
import json
from pathlib import Path
from smoke import call, ROOT


def state():
    return call('/bbs-clips-probe')


def mouse(x, y, mode='move', button=0):
    x, y = round(x), round(y)
    # LWJGL/Windows cursor warps are observed by rendering after the HTTP event.
    # Synchronize the physical pointer before starting/releasing a drag; otherwise
    # a stale native mouse position may move the selection during a click.
    for _ in range(6):
        call('/ui-mouse', {'x': x, 'y': y, 'mode': 'move', 'button': button})
        call('/wait', {'ticks': 3})
        position = state()
        if abs(position['mouseX'] - x) <= 1 and abs(position['mouseY'] - y) <= 1:
            break
    else:
        raise AssertionError({'input': [x, y, mode], 'mouseNotSettled': position})
    if mode != 'move':
        call('/ui-mouse', {'x': x, 'y': y, 'mode': mode, 'button': button})
        call('/wait', {'ticks': 3})


def click(point, button=0):
    mouse(point['x'], point['y'])
    mouse(point['x'], point['y'], 'down', button)
    mouse(point['x'], point['y'], 'up', button)


def center(area):
    return {'x': area['x'] + area['w'] / 2, 'y': area['y'] + area['h'] / 2}


def tick_x(tick, snapshot=None):
    snapshot = snapshot or state()
    return round(snapshot['tick0X'] + (snapshot['tick100X'] - snapshot['tick0X']) * tick / 100)


def selected():
    return next(c for c in state()['clips'] if c['selected'])


def named(title):
    return next(c for c in state()['clips'] if c['title'] == title)


def main():
    health = call('/health')
    assert Path(health['gameDir']).resolve() == (ROOT / 'run-forge1122').resolve(), health
    assert health['inWorld'], health
    call('/bbs-clips-probe', {'open': True})
    call('/wait', {'ticks': 8})
    start = state()
    assert len(start['clips']) == 3 and start['waveform'], start
    assert abs(start['waveDuration'] - 4) < 0.01 and start['roundtrip'], start
    call('/screenshot', {'name': 'original-clips-waveform-verified'})

    # Real selection and clip enable key.
    clip = named('Перемещение')
    grab = {'x': (clip['x'] + clip['right']) / 2 - 2, 'y': clip['y']}
    click(grab)
    assert selected()['title'] == 'Перемещение', state()
    call('/key', {'key': 'j'})
    assert not selected()['enabled'], state()
    call('/key', {'key': 'j'})
    assert selected()['enabled'], state()

    assert selected()['tick'] == 10 and selected()['duration'] == 20, state()
    # Refresh geometry after selection so a failed gesture cannot reuse stale coordinates.
    clip = selected()
    grab = {'x': (clip['x'] + clip['right']) / 2 - 2, 'y': clip['y']}
    # Aim one tick short of the film marker: the original snap logic must finish at 30.
    step = (start['tick100X'] - start['tick0X']) / 100
    mouse(grab['x'], grab['y'], 'down')
    mouse(grab['x'] + step * 19, grab['y'])
    mouse(grab['x'] + step * 19, grab['y'], 'up')
    moved = selected()
    assert moved['tick'] == 30 and moved['duration'] == 20, state()

    # Drag the right edge five ticks left, preserving the left edge.
    mouse(moved['right'] - 2, moved['y'], 'down')
    mouse(moved['right'] - 2 - step * 5, moved['y'])
    mouse(moved['right'] - 2 - step * 5, moved['y'], 'up')
    trimmed = selected()
    assert trimmed['tick'] == 30 and trimmed['duration'] == 15, state()

    # Clipboard actions go through actual Ctrl+C/Ctrl+V and the original factory.
    call('/key', {'key': 'c', 'ctrl': True})
    mouse(tick_x(90), trimmed['y'])
    call('/key', {'key': 'v', 'ctrl': True})
    assert len(state()['clips']) == 4, state()
    pasted = selected()
    assert pasted['tick'] == 90 and pasted['duration'] == 15, state()
    old_layer = pasted['layer']
    call('/key', {'key': 'up', 'alt': True})
    assert selected()['layer'] == old_layer + 1, state()

    # Shift+A opens the real factory menu, selecting its first row creates a clip.
    snapshot = state()
    layer = next(l for l in snapshot['layers'] if l['layer'] == 2)
    mouse(tick_x(110, snapshot), layer['y'])
    call('/key', {'key': 'a', 'shift': True})
    call('/wait', {'ticks': 3})
    menu = state()
    assert menu['contextMenu'] and menu.get('menu'), menu
    click(menu['menu'][0])
    assert len(state()['clips']) == 5 and selected()['tick'] == 110, state()
    call('/key', {'key': 'delete'})
    assert len(state()['clips']) == 4, state()

    # Marker drag uses the ruler controller, committing Value data on release.
    marker = state()['markers'][0]
    mouse(marker['x'], marker['y'], 'down')
    mouse(tick_x(45), marker['y'])
    mouse(tick_x(45), marker['y'], 'up')
    assert state()['markers'][0]['tick'] == 45, state()

    # Add a marker through its context menu, enter Cyrillic, and close its real overlay.
    snapshot = state()
    click({'x': tick_x(75, snapshot), 'y': snapshot['area']['y'] + 5}, button=1)
    menu = state()
    assert menu.get('menu'), menu
    click(menu['menu'][0])
    editor = state()
    assert len(editor['markers']) == 2 and 'markerEditor' in editor, editor
    click(center(editor['markerEditor']['title']))
    call('/key', {'key': 'a', 'ctrl': True})
    call('/type', {'text': 'Новая метка'})
    editor = state()
    assert any(m['title'] == 'Новая метка' for m in editor['markers']), editor
    call('/screenshot', {'name': 'original-clips-marker-editor-verified'})
    click(center(editor['markerEditor']['close']))
    marker = next(m for m in state()['markers'] if m['title'] == 'Новая метка')
    click(marker, button=1)
    menu = state()
    assert len(menu.get('menu', [])) == 2, menu
    click(menu['menu'][1])
    assert len(state()['markers']) == 1, state()

    # Fill enough lanes to exceed this window's viewport using the actual layer key.
    # The default 20 lanes fit entirely in a tall window, so there is nothing to scroll.
    snapshot = state()
    pasted = next(c for c in snapshot['clips'] if c['tick'] == 90)
    click({'x': (pasted['x'] + pasted['right']) / 2, 'y': pasted['y']})
    extra_layers = max(1, int(snapshot['area']['h'] / snapshot['layerHeight']) + 5 - pasted['layer'])
    for _ in range(extra_layers):
        call('/key', {'key': 'up', 'alt': True})
    call('/wait', {'ticks': 3})
    # Shift-wheel is lane scroll; ordinary wheel zooms the timeline under the mouse.
    before = state()
    area = center(before['area'])
    mouse(area['x'], area['y'])
    call('/scroll', {'x': round(area['x']), 'y': round(area['y']), 'wheel': -120, 'shift': True})
    call('/wait', {'ticks': 10})
    scrolled = state()
    assert scrolled['verticalScroll'] != before['verticalScroll'], scrolled
    call('/scroll', {'x': round(area['x']), 'y': round(area['y']), 'wheel': 120, 'shift': True})
    call('/wait', {'ticks': 10})
    for _ in range(extra_layers):
        call('/key', {'key': 'down', 'alt': True})
    before_zoom = state()['zoom']
    call('/scroll', {'x': round(area['x']), 'y': round(area['y']), 'wheel': 120})
    call('/wait', {'ticks': 10})
    assert state()['zoom'] != before_zoom, state()
    call('/screenshot', {'name': 'original-clips-edits-verified'})
    final = state()
    assert final['changes'] > 0 and final['fills'] > 0, final
    assert final['roundtrip'] and final['waveform'] and final['glError'] == 0, final
    assert not call('/log?limit=2000&level=ERROR+')['entries']
    report = {'ok': True, 'checks': ['selection', 'clip enable key', 'drag and film-marker snap',
        'duration trim', 'clipboard copy/paste', 'layer key', 'create factory menu', 'delete',
        'marker drag', 'marker add/edit/remove', 'Cyrillic input', 'lane scroll', 'time zoom',
        'WAV waveform and envelope', 'Film data roundtrip', 'OpenGL/runtime logs'], 'state': final}
    output = ROOT / 'build/reports/forge1122-original-clips-smoke.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS:', output)


if __name__ == '__main__':
    main()
