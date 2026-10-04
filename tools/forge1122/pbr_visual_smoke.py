"""Capture actual CEM material changes under LabPBR; inspect the resulting images."""
import json
import re
from pathlib import Path
from smoke import call, ROOT


def probe(action, **values):
    return call('/bbs-shader-world-forms-probe', {'action': action, **values})


def main():
    health = call('/health')
    run = Path(health['gameDir']).resolve()
    assert health['inWorld'] and run == (ROOT / 'run-forge1122-obf').resolve(), health
    pack = call('/bbs-optifine-probe')['pack']
    assert pack.startswith('Complementary') and Path(pack).name == pack, pack
    options = run / 'shaderpacks' / (pack + '.txt')
    original = options.read_bytes() if options.exists() else None
    report = {'pack': pack}
    path = ROOT / 'build/reports/forge1122-pbr-visual-smoke.json'
    try:
        call('/disconnect')
        settings = re.sub(r'^RP_MODE=.*\r?\n?', '', (original or b'').decode('iso-8859-1'), flags=re.M)
        options.write_text(settings.rstrip() + '\nRP_MODE=3\n', encoding='iso-8859-1')
        call('/bbs-optifine-probe', {'pack': pack})
        call('/world', {'world': 'ai_test'})
        call('/wait', {'ticks': 10})
        probe('pbr-start')
        call('/wait', {'ticks': 10})
        report['pbr'] = pbr = probe('pbr-state')['result']
        assert pbr['ok'] and pbr['RP_MODE'] == '3', pbr
        probe('cem-enable')
        probe('start')
        call('/wait', {'ticks': 25})
        report['baseState'] = state = probe('state')
        assert not state['errors'] and state['stateRestored'] and state['samples'][7] > 0, state
        report['base'] = call('/screenshot', {'name': 'pbr-cem-base'})
        probe('cem-material', emission=1, smoothness=0, metallic=0, relief=0)
        call('/wait', {'ticks': 15})
        report['emission'] = call('/screenshot', {'name': 'pbr-cem-emission'})
        probe('cem-material', emission=0, smoothness=1, metallic=1, relief=0.8)
        call('/wait', {'ticks': 15})
        report['metal'] = call('/screenshot', {'name': 'pbr-cem-metal'})
        report['endState'] = state = probe('state')
        assert not state['errors'] and state['stateRestored'], state
    finally:
        if original is None:
            options.unlink(missing_ok=True)
        else:
            options.write_bytes(original)
        try:
            probe('stop')
            probe('cem-disable')
            if call('/health')['inWorld']:
                call('/disconnect')
            call('/bbs-optifine-probe', {'pack': pack})
            call('/world', {'world': 'ai_test'})
            call('/wait', {'ticks': 10})
        finally:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Captured actual base / emissive / metallic CEM materials; visual inspection required')
    print(path)


if __name__ == '__main__':
    main()
