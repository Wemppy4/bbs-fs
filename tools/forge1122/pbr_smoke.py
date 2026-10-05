"""PBR GPU maps and native vertex attributes in the running isolated shader world."""
import json
from pathlib import Path
import re
from smoke import call, mc
from qa_environment import checked_client, report_path


def probe(action):
    result = mc.call('/bbs-shader-world-forms-probe', {'action': action}, timeout=65)
    assert result.get('ok', True), result  # build12 returned the payload without this envelope key
    return result


def main():
    target=checked_client()
    run=Path(target['gameDir'])
    pack = call('/bbs-optifine-probe')['pack']
    assert pack.startswith('Complementary') and Path(pack).name == pack, pack
    options = run / 'shaderpacks' / (pack + '.txt')
    original = options.read_bytes() if options.exists() else None
    result = {'target': target, 'pack': pack, 'modes': {}}
    path = report_path('forge1122-pbr-smoke.json')
    try:
        for mode, name in ((3, 'labPBR'), (2, 'OldPBR')):
            if call('/health')['inWorld']:
                call('/disconnect')
            settings = (original or b'').decode('iso-8859-1')
            settings = re.sub(r'^RP_MODE=.*\r?\n?', '', settings, flags=re.M)
            options.write_text(settings.rstrip() + f'\nRP_MODE={mode}\n', encoding='iso-8859-1')
            call('/bbs-optifine-probe', {'pack': pack})
            call('/world', {'world': 'ai_test'})
            call('/wait', {'ticks': 10})
            probe('pbr-start')
            call('/wait', {'ticks': 10})
            result['modes'][name] = value = probe('pbr-state')
            assert not value['pending'] and value['result']['ok'], value
            assert value['result']['at_tangent'] >= 0, value
            assert value['result']['RP_MODE'] == str(mode), value
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    finally:
        if original is None:
            options.unlink(missing_ok=True)
        else:
            options.write_bytes(original)
        try:
            if call('/health')['inWorld']:
                call('/disconnect')
            call('/bbs-optifine-probe', {'pack': pack})
            call('/world', {'world': 'ai_test'})
            call('/wait', {'ticks': 10})
        except Exception as error:
            result['restoreRuntimeError'] = str(error)
            raise
        finally:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS: raw normal/OldPBR maps, LabPBR sliders, relief, isolated materials, animated values, GPU cleanup')
    print(path)


if __name__ == '__main__':
    main()
