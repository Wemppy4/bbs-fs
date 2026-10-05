"""Actual thumbnails/items followed by the original structure editor; isolated QA only."""
import json
import shutil
from pathlib import Path
from smoke import call, ROOT, mc
from qa_environment import checked_client, report_path


def main():
    target = checked_client()
    report = {'target': target, 'shader': call('/bbs-optifine-probe')}
    fixture = Path(target['gameDir']) / 'saves/ai_test/structures/aihelper_ui_user123.nbt'
    source = ROOT / 'run-forge1122-obf/saves/ai_test/structures/123.nbt'
    assert not fixture.exists(), fixture
    fixture.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, fixture)
    path = report_path('forge1122-native-ui-render-smoke.json')
    try:
        probe({'open': True})
        for name, parameters in [('igloo', {}), ('reference', {'reference': True}),
                                 ('cullOffNative', {'model': '__missing__', 'reference': False}),
                                 ('cullOffReference', {'reference': True}),
                                 ('endcity', {'model': 'player/steve', 'reference': False, 'structure': 'endcity/base_floor'}),
                                 ('user123', {'structure': 'user123'})]:
            if parameters:
                probe(parameters)
            call('/wait', {'ticks': 8})
            report[name] = probe()
            report[name]['screenshot'] = call('/screenshot', {'name': 'native-ui-' + name})
        report['itemDepthMatchesReference'] = report['cullOffNative']['itemPixels'] == report['cullOffReference']['itemPixels']
        probe({'editor': ''})
        call('/wait', {'ticks': 8})
        report['editorEmpty'] = probe()
        probe({'structure': 'minecraft:aihelper_ui_user123'})
        call('/wait', {'ticks': 8})
        report['editorUser123'] = probe()
        report['editorScreenshot'] = call('/screenshot', {'name': 'native-ui-editor-user123'})
        probe({'structure': ''})
        probe({'picker': True})
        call('/wait', {'ticks': 3})
        screen = call('/screen')
        assert (screen['width'], screen['height']) == (427, 240), ('Picker click fixture requires the verified 854x480 window', screen)
        report['pickerScreenshot'] = call('/screenshot', {'name': 'native-ui-structure-picker'})
        for mode in ('down', 'up'):
            call('/ui-mouse', {'warp': False, 'x': 180, 'y': 75, 'mode': mode})
        call('/wait', {'ticks': 5})
        report['pickerSelected'] = probe()
        report['pickedScreenshot'] = call('/screenshot', {'name': 'native-ui-structure-picked'})
        for mode in ('down', 'up'):
            call('/ui-mouse', {'warp': False, 'x': 323, 'y': 10, 'mode': mode})
        call('/wait', {'ticks': 4})
        report['pickerClosed'] = probe()
        report['closedScreenshot'] = call('/screenshot', {'name': 'native-ui-structure-picked-closed'})
    finally:
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        call('/release')
        call('/bbs-extended-forms-probe', {'open': True})
        fixture.unlink()
    print(path)


def probe(parameters=None):
    result = mc.call('/bbs-native-ui-render-probe', parameters, timeout=65)
    assert 'error' not in result, result
    return result


if __name__ == '__main__':
    main()
