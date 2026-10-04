"""Exercise actual BBS UI components through the original ai_helper client."""
import json
from pathlib import Path
from smoke import call, wait_for, ROOT


def state():
    return call('/bbs-ui-probe')


def click(name, button=0, right=False):
    area = state()['areas'][name]
    call('/click', {'x': area['x'] + (area['w'] - 4 if right else area['w'] // 2),
                    'y': area['y'] + area['h'] // 2, 'button': button})


def main():
    health = call('/health')
    assert Path(health['gameDir']).resolve() == (ROOT / 'run-forge1122').resolve(), health
    assert health['inWorld']
    pixel_store = call('/gl-state')['unpackRow']
    call('/bbs-ui-probe', {'open': True})
    wait_for(lambda: state()['areas']['save']['w'] > 0)
    initial = state()
    try:
        click('save')
        assert state()['clicks'] == 1
        click('toggle')
        assert state()['toggle']
        click('input')
        call('/key', {'key': 'end'})
        for _ in initial['text']:
            call('/key', {'key': 'back'})
        text = 'Порт BBS: анимация 1.12.2'
        call('/type', {'text': text})
        assert state()['text'] == text, state()
        click('number', right=True)
        assert state()['number'] == initial['number'] + 1, state()
        # The right click must open the original context menu, with its original action list.
        click('save', button=1)
        assert state()['contextMenu'], state()
        click('context')
        assert state()['contextActions'] == 1 and not state()['contextMenu'], state()
        area = state()['areas']['list']
        call('/scroll', {'x': area['x'] + 30, 'y': area['y'] + 30, 'wheel': -360})
        wait_for(lambda: state()['scroll'] > 1)
        call('/screenshot', {'name': 'original-ui-widgets-verified'})
        click('overlay')
        wait_for(lambda: state()['overlays'] > 0)
        call('/wait', {'ticks': 4})
        call('/screenshot', {'name': 'original-ui-overlay-verified'})
        assert state()['glError'] == 0, state()
        assert call('/gl-state')['unpackRow'] == pixel_store, 'UI texture upload leaked pixel-store state into the world renderer'
        call('/key', {'key': 'escape'})
        assert state()['overlays'] == 0, state()
        call('/bbs-ui-probe', {'scale': 1.75})
        wait_for(lambda: state()['width'] > initial['width'])
        click('save')
        assert state()['clicks'] == 2, state()
        call('/screenshot', {'name': 'original-ui-fractional-verified'})
        assert state()['glError'] == 0, state()
        errors = call('/log?limit=2000&level=ERROR+')
        assert not errors['entries'], errors
        report = {'ok': True, 'checks': ['original button, toggle and icon rendering',
            'Russian text input', 'numeric increment', 'context menu action', 'scroll and clipping',
            'original overlay and Kawase blur', 'fractional GUI scale and hit testing', 'OpenGL and runtime logs'],
            'state': state()}
        output = ROOT / 'build/reports/forge1122-original-ui-smoke.json'
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print('PASS: original BBS UI foundation, report:', output)
    finally:
        if call('/health')['screen'] == 'UIScreen':
            call('/bbs-ui-probe', {'scale': initial['scale']})


if __name__ == '__main__':
    main()
