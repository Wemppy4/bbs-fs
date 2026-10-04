"""Real shader-world draws plus CEM pack lifecycle; an isolated running world is required."""
import json
from smoke import call, ROOT


def probe(action='state'):
    return call('/bbs-shader-world-forms-probe', {'action': action})


def main():
    assert call('/health')['inWorld'], 'An isolated ai_helper world is required'
    shader = call('/bbs-optifine-probe')
    assert shader['loaded'] and shader['isShaderPackInitialized'], shader
    report = {'shader': shader, 'before': probe()}
    path = ROOT / 'build/reports/forge1122-shader-world-forms-smoke.json'
    try:
        report['enabled'] = probe('cem-enable')
        enabled = report['enabled']
        assert enabled['cemLifecycleInstalled'] and enabled['cemCatalog'] and enabled['cemModelLoaded'], enabled
        assert enabled['assetsVersion'] > enabled['versionBeforeEnable'], enabled
        probe('start')
        call('/wait', {'ticks': 30})
        report['world'] = world = probe()
        assert world['shaderFrames'] > 0 and world['shaderFrames'] == world['worldFrames'], world
        assert not world['errors'] and world['stateRestored'], world
        assert all(value > 0 for value in world['samples']), world
        for draw in world.get('cemDraw', []):
            assert not draw.get('error') and draw.get('glError', 0) == 0, draw
            assert all(0 <= channel <= 1 for channel in draw.get('entityColor', [])), draw
        report['screenshot'] = call('/screenshot', {'name': 'shader-world-forms'})
        report['reloaded'] = probe('cem-reload')
        assert report['reloaded']['cemModelReplacedOnReload'], report['reloaded']
        call('/wait', {'ticks': 15})
        report['afterReload'] = probe()
        assert not report['afterReload']['errors'] and report['afterReload']['stateRestored'], report['afterReload']
    finally:
        probe('stop')
        report['disabled'] = probe('cem-disable')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    assert not report['disabled']['cemEnabled'], report['disabled']
    assert report['disabled']['cemCatalog'] == report['before']['cemCatalog'], report
    print('PASS: native shader-world forms, GL/program/FBO/VAO restore, CEM enable/reload/disable')
    print(path)


if __name__ == '__main__':
    main()
