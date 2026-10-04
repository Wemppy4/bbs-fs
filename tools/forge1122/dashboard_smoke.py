"""Exercise the real F6 Dashboard via native mouse/key routes and read-only inspection.

Only the isolated run-forge1122/ai_test world is used. No alternate probe menu is opened.
The uniquely named saved film is retained for review; its duplicate is renamed/deleted via UI.
"""
import json
import time
from pathlib import Path
from smoke import call, ROOT, wait_for


def state():
    return call('/bbs-dashboard-probe')


def wait(ticks=3):
    call('/wait', {'ticks': ticks})


def key(value, **modifiers):
    call('/key', {'key': value, **modifiers})
    wait()


def mouse(x, y, mode='move'):
    x, y = round(x), round(y)
    for _ in range(8):
        call('/ui-mouse', {'x': x, 'y': y, 'mode': 'move'})
        wait()
        current = state()
        if abs(current['mouseX'] - x) <= 1 and abs(current['mouseY'] - y) <= 1:
            break
    else:
        raise AssertionError({'mouseNotSettled': current, 'target': [x, y]})
    if mode != 'move':
        call('/ui-mouse', {'x': x, 'y': y, 'mode': mode, 'button': 0})
        wait()


def click(area):
    assert area['visible'] and area['enabled'] and area['w'] > 0 and area['h'] > 0, area
    x, y = area['x'] + area['w'] / 2, area['y'] + area['h'] / 2
    mouse(x, y, 'down')
    mouse(x, y, 'up')


def overlay(expected=None):
    value = state()
    assert value['overlays'], value
    current = value['overlays'][-1]
    if expected:
        assert current['type'] == expected, current
    return current


def replace_text(area, value):
    click(area)
    key('a', ctrl=True)
    call('/type', {'text': value})
    wait()


def dismiss_onboarding():
    for _ in range(24):
        current = state()
        if current['overlays'] and current['overlays'][-1]['type'] == 'UIWelcomeOverlayPanel':
            key('escape')
        elif current['tours']:
            click(current['tours'][-1])
        else:
            return
    raise AssertionError({'onboardingNotClosed': state()})


def select_panel(name):
    target = next(p for p in state()['panels'] if p['type'] == name)
    click(target['button'])
    dismiss_onboarding()
    assert state()['selectedPanel'] == name, state()


def manager():
    current = state()
    if current['overlays'] and 'names' in current['overlays'][-1]:
        return current['overlays'][-1]
    key('n')
    panel = overlay()
    assert 'names' in panel and 'add' in panel, panel
    return panel


def close_manager():
    current = state()
    if current['overlays'] and 'names' in current['overlays'][-1]:
        click(current['overlays'][-1]['close'])
    dismiss_onboarding()


def enter_prompt(text):
    prompt = overlay('UIPromptOverlayPanel')
    assert len(prompt['fields']) == 1, prompt
    replace_text(prompt['fields'][0], text)
    key('enter')
    assert not state()['overlays'] or state()['overlays'][-1]['type'] != 'UIPromptOverlayPanel', state()


def main():
    health = call('/health')
    run_dir = (ROOT / 'run-forge1122').resolve()
    assert Path(health['gameDir']).resolve() == run_dir, health
    assert health['inWorld'], health
    call('/disconnect', {})
    call('/world', {'world': 'ai_test'})
    wait_for(lambda: call('/health')['inWorld'], seconds=90)
    wait_for(lambda: call('/health')['screen'] != 'GuiDownloadTerrain', seconds=90)
    for _ in range(8):
        if call('/health')['screen'] is None:
            break
        key('escape')
    assert call('/health')['screen'] is None, call('/health')
    key('f6')
    start = state()
    assert start['menu'] == 'UIDashboard' and start['built'], start
    required = {'UIFilmPanel', 'UITextureManagerPanel', 'UIAudioEditorPanel'}
    assert required <= {p['type'] for p in start['panels']}, start
    dismiss_onboarding()
    call('/screenshot', {'name': 'original-dashboard-verified'})

    # Actual settings button and actual text field; Cyrillic input changes the live filter.
    click(state()['settings'])
    settings = overlay('UISettingsOverlayPanel')
    replace_text(settings['search'], 'интерфейс')
    assert overlay('UISettingsOverlayPanel')['filter'] == 'интерфейс', state()
    call('/screenshot', {'name': 'original-dashboard-settings-verified'})
    replace_text(overlay()['search'], '')
    options = overlay()['options']
    call('/scroll', {'x': options['x'] + options['w'] // 2, 'y': options['y'] + options['h'] // 2, 'wheel': -120})
    wait()
    click(overlay()['close'])
    key('s', ctrl=True, alt=True)
    assert overlay()['type'] == 'UISettingsOverlayPanel', state()
    click(overlay()['close'])

    # The original panel bar must switch genuine editors and return to the film.
    for name in ('UITextureManagerPanel', 'UIAudioEditorPanel', 'UIFilmPanel'):
        select_panel(name)
        assert state()['glError'] == 0, state()
        call('/screenshot', {'name': 'original-dashboard-' + name.lower() + '-verified'})

    name = 'dashboard_qa_' + str(time.time_ns())
    dupe, renamed = name + '_copy', name + '_renamed'
    folder = run_dir / 'saves/ai_test/bbs/films'
    original_file, dupe_file, renamed_file = (folder / (n + '.dat') for n in (name, dupe, renamed))
    assert not any(p.exists() for p in (original_file, dupe_file, renamed_file))
    click(manager()['add'])
    enter_prompt(name)
    assert state()['dataId'] == name and state()['dataType'] == 'films', state()
    close_manager()
    key('s', ctrl=True)
    wait_for(lambda: original_file.is_file())
    assert original_file.stat().st_size > 0

    # Duplicate, rename and delete use the same original repository-backed controls.
    current = manager()
    assert current['selected'] == name, current
    click(current['dupe'])
    enter_prompt(dupe)
    assert state()['dataId'] == dupe, state()
    wait_for(lambda: dupe_file.is_file())
    click(manager()['rename'])
    enter_prompt(renamed)
    assert state()['dataId'] == renamed, state()
    wait_for(lambda: renamed_file.is_file() and not dupe_file.exists())
    call('/screenshot', {'name': 'original-dashboard-crud-verified'})
    # Renaming preserves the document data; pick its list row again if selection moved.
    current = manager()
    if current['selected'] != renamed:
        replace_text(current['search'], renamed)
        current = manager()
        point = {**current['list'], 'x': current['list']['x'] + 20, 'y': current['list']['y'] + 2, 'w': 12, 'h': 12}
        click(point)
        assert manager()['selected'] == renamed, state()
    click(manager()['remove'])
    overlay('UIConfirmOverlayPanel')
    key('enter')
    wait_for(lambda: not renamed_file.exists())
    assert original_file.is_file(), original_file
    close_manager()

    # Reopen the saved original through the CRUD filter and ordinary list confirmation.
    current = manager()
    replace_text(current['search'], name)
    current = manager()
    assert name in current['names'], current
    row = {**current['list'], 'x': current['list']['x'] + 20, 'y': current['list']['y'] + 2, 'w': 12, 'h': 12}
    click(row)
    # Data lists may require their normal double-click before opening a document.
    if state().get('dataId') != name:
        click(row)
    assert state()['dataId'] == name, state()
    close_manager()
    final = state()
    assert final['glError'] == 0, final
    call('/screenshot', {'name': 'original-dashboard-film-reopened-verified'})
    report = {'ok': True, 'menu': final['menu'], 'panels': final['panels'],
              'savedFilm': str(original_file), 'created': name, 'duplicated': dupe,
              'renamed': renamed, 'deletedDuplicate': not renamed_file.exists(), 'snapshot': final}
    output = ROOT / 'build/reports/forge1122-dashboard-smoke.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
