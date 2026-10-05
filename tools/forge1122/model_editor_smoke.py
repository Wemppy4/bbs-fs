"""Create/edit/undo/save/reopen a model through the original Dashboard UI.

Read-only helper snapshots locate widgets. Mutations use the same mouse and
keyboard paths as a person, inside the isolated ai_test client. No OS cursor.
"""
import json
import os
import time
from pathlib import Path

os.environ['AIH_DIRECT_UI'] = '1'
from dashboard_smoke import state, key, click, mouse, wait, dismiss_onboarding
from dashboard_smoke import select_panel, manager, enter_prompt, close_manager, replace_text
from smoke import call, wait_for, ROOT
from qa_environment import checked_client, report_path


def model():
    return state()['modelEditor']


def scenario():
    health = call('/health')
    assert health['inWorld'], health
    run = Path(health['gameDir']).resolve()
    while call('/health')['screen'] is not None:
        key('escape')
    key(state()['dashboardKey'])
    dismiss_onboarding()
    select_panel('UIModelEditorPanel')
    name = 'editor_qa_' + str(time.time_ns())
    folder = run / 'config/bbs/assets/models' / name
    assert not folder.exists(), folder
    click(manager()['add'])
    enter_prompt(name)
    close_manager()
    wait_for(lambda: state().get('dataId') == name and model()['loaded'])
    dismiss_onboarding()
    click(model()['editors'][1])
    assert model()['editor'] == 'MODEL', model()
    before = model()
    assert len(before['groups']) == 1 and len(before['groups'][0]['cubes']) == 1, before

    # Add a bone and cube, rename it, and edit its position using actual fields.
    click(model()['tools'][0])
    wait_for(lambda: len(model()['groups']) == 2)
    group_name = model()['selected']
    assert group_name, model()
    click(model()['addCube'])
    wait_for(lambda: sum(len(g['cubes']) for g in model()['groups']) == 2)
    replace_text(model()['name'], 'Куб проверки')
    key('enter')
    field = model()['cubeX']
    # The native /click path emits down+up within one frame. Delaying between
    # them would intentionally begin the trackpad's 150 ms drag gesture.
    call('/click', {'x': round(field['x'] + field['w'] / 2), 'y': round(field['y'] + field['h'] / 2)})
    key('a', ctrl=True)
    call('/type', {'text': '3.25'})
    key('enter')
    # Enter commits numeric input. Point at the preview for shared undo keys.
    preview = model()['preview']
    mouse(preview['x'] + 10, preview['y'] + 10)
    edited = model()
    cube = next(g for g in edited['groups'] if g['id'] == group_name)['cubes'][0]
    assert cube['name'] == 'Куб проверки' and abs(cube['x'] - 3.25) < 1e-5, edited
    key('z', ctrl=True)
    undone = model()
    assert undone['groups'] != edited['groups'], undone
    key('y', ctrl=True)
    assert model()['groups'] == edited['groups'], model()
    key('s', ctrl=True)
    wait_for(lambda: (folder / 'model.bbs.json').is_file() and not model()['dirty'])
    saved = (folder / 'model.bbs.json').read_text(encoding='utf-8')
    assert 'Куб проверки' in saved and '3.25' in saved, saved
    call('/screenshot', {'name': 'original-model-editor-saved'})

    # A normal Dashboard close and reopen must keep the saved geometry.
    key('escape')
    key(state()['dashboardKey'])
    dismiss_onboarding()
    select_panel('UIModelEditorPanel')
    wait_for(lambda: model()['loaded'])
    assert state()['dataId'] == name and model()['groups'] == edited['groups'], state()
    assert state()['glError'] == 0, state()
    result = {'ok': True, 'model': str(folder), 'before': before, 'edited': edited,
              'undoRedo': True, 'reopened': model()}
    output = report_path('forge1122-model-editor-smoke.json')
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS:', output)


def main():
    checked_client()
    call('/ui-window', {'width': 1280, 'height': 720})
    try:
        scenario()
    finally:
        call('/ui-window', {'restore': True})


if __name__ == '__main__':
    main()
