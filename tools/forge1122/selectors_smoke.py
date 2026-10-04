"""Exercise selector capability, native entity draw and the original Dashboard overlay in ai_test."""
import json
from smoke import ROOT, call


def click(area):
    args = {'x': area['x'] + area['w'] / 2, 'y': area['y'] + area['h'] / 2,
            'button': 0, 'warp': False}
    for mode in ('down', 'up'):
        call('/ui-mouse', dict(args, mode=mode))
    call('/wait', {'ticks': 2})


def main():
    result = call('/bbs-selectors-probe', {'test': True})
    for key in ('entityNameNbtMatch', 'compoundAndSiblingMismatch', 'nameMismatch', 'disabledMismatch',
                'firstRuleWins', 'repositoryRoundtrip', 'reloadReplaces', 'nativeCapability',
                'independentForm', 'nativeTick', 'nbtUpdatesSelection', 'nbtRestoresSelection',
                'sameTickEdits', 'clearRestoresVanilla', 'dashboardPinned'):
        assert result[key], (key, result)
    for key in ('nativeRender', 'recursiveMobRender'):
        assert result[key]['pixels'] > 20 and result[key]['replaced'] == 1, result
        assert result[key]['recursionGuard'], result
    assert result['glError'] == 0, result
    try:
        call('/bbs-selectors-probe', {'op': 'begin_ui'})
        call('/wait', {'ticks': 4})
        ui = call('/bbs-selectors-probe', {})
        assert ui['panel'] == 'UISelectorsOverlayPanel' and ui['rules'] == 1, ui
        assert ui['entityText'] == 'minecraft:pig', ui
        click(ui['name'])
        call('/key', {'key': 'a', 'ctrl': True})
        call('/type', {'text': 'BBS selectors UI fixture'})
        call('/wait', {'ticks': 2})
        ui = call('/bbs-selectors-probe', {})
        assert ui['nameText'] == 'BBS selectors UI fixture', ui
        click(ui['enabled'])
        click(ui['close'])
        result['ui'] = ui
    finally:
        saved = call('/bbs-selectors-probe', {'op': 'finish_ui'})
    assert saved['savedName'] == 'BBS selectors UI fixture' and not saved['savedEnabled'], saved
    result['uiSaved'] = saved
    output = ROOT / 'build/reports/forge1122-selectors-smoke.json'
    output.write_text(json.dumps(result, indent=2), encoding='utf8')
    print('PASS:', output)


if __name__ == '__main__':
    main()
