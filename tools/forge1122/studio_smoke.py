"""Author a scene through the native studio UI using the existing ai_helper client."""
import json
import time
from pathlib import Path
from smoke import call, command, wait_for, ROOT


def button(text):
    widgets = call('/screen')['widgets']
    widget = next(w for w in widgets if w['text'] == text)
    call('/click', {'x': widget['x'] + widget['width'] // 2, 'y': widget['y'] + widget['height'] // 2})


def field(label, text):
    f = next(f for f in call('/screen')['fields'] if f['label'] == label)
    call('/click', {'x': f['x'] + 4, 'y': f['y'] + f['height'] // 2})
    call('/key', {'key': 'end'})
    for _ in f['text']:
        call('/key', {'key': 'back'})
    call('/type', {'text': str(text)})
    call('/key', {'key': 'enter'})


def choose(text):
    assert call('/health')['screen'] == 'StudioDialog'
    field('Input', text)
    wait_for(lambda: call('/health')['screen'] == 'StudioScreen')


def main():
    health = call('/health')
    assert health['inWorld'] and health['bbsLoaded'], health
    assert Path(health['gameDir']).resolve() == (ROOT / 'run-forge1122').resolve(), health
    call('/disconnect', {})
    call('/world', {'world': 'ai_test'})
    wait_for(lambda: call('/health')['inWorld'])
    if call('/health')['screen'] is not None:
        call('/key', {'key': 'escape'})
    command('tp @p 0.5 4 0.5 0 15')
    command('gamerule doDaylightCycle false')
    command('time set 6000')
    call('/key', {'key': 'f6'})
    wait_for(lambda: call('/health')['screen'] == 'StudioScreen')
    button('New')
    name = 'studio_test_' + str(int(time.time()))
    choose(name)
    button('+ Actor')
    choose('helper_drone')
    state = call('/bbs1122')
    assert len(state['actors']) == 1 and 'helper_drone' in state['film'], state
    start = state['actors'][0]['position']
    field('Tick', 20)
    field('x (key)', start[0] + 4)
    field('Tick', 10)
    state = call('/bbs1122')
    assert abs(state['actors'][0]['position'][0] - start[0] - 2) < 0.01, state
    button('Pose')
    button('Bone: antenna')
    choose('antenna')
    button('Add pose key')
    field('Rotation X', 30)
    button('Save')
    saved = call('/bbs1122')['film']
    button('+ Camera')
    field('Duration', 60)
    button('Camera')
    assert call('/bbs1122')['camera']
    call('/wait', {'ticks': 4})
    assert call('/bbs1122')['viewEntity'] == 'CameraEntity'
    button('Camera: on')
    assert call('/bbs1122')['viewEntity'] != 'CameraEntity'
    button('Save')
    saved = call('/bbs1122')['film']
    button('Open')
    choose(name)
    assert call('/bbs1122')['film'] == saved
    call('/screenshot', {'name': 'forge-studio-first-pass'})
    state = call('/bbs1122')
    assert not state['errors'] and state['glError'] == 0, state
    report = {'ok': True, 'film': name, 'actorStart': start, 'state': state}
    (ROOT / 'build/reports/forge1122-studio-smoke.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'ok': True, 'film': name}, ensure_ascii=False))


if __name__ == '__main__':
    main()
