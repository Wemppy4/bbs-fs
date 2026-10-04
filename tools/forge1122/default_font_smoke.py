"""Exact 1.20.4 default bitmap and Unihex providers through the real BBS renderer."""
import json
from pathlib import Path
from smoke import call, ROOT


def state():
    return call('/bbs-default-font-probe')


def main():
    health = call('/health')
    assert Path(health['gameDir']).resolve() == (ROOT / 'run-forge1122').resolve(), health
    call('/bbs-default-font-probe', {'open': True})
    for _ in range(180):
        call('/wait', {'ticks': 5})
        initial = state()
        assert initial['state'] != 'ERROR', initial
        if initial['state'] == 'READY' and initial.get('atlasPages', 0) > 0:
            break
    else:
        raise AssertionError(initial)
    assert initial['glyphCount'] > 100000, initial
    assert initial['cyrillicWidth'] == 67 and initial['boldCyrillicWidth'] == 78, initial
    assert initial['boldUnihexWidth'] == 19, initial
    assert initial['glError'] == 0, initial
    glyphs = {g['codepoint']: g for g in initial['glyphs']}
    for cp in 'ЁёЙй':
        assert glyphs[ord(cp)]['provider'] == 'minecraft:font/accented.png', initial
        assert glyphs[ord(cp)]['top'] == -3, initial
    assert glyphs[ord('中')]['provider'] == 'unihex', initial
    assert glyphs[ord('中')]['boldOffset'] == glyphs[ord('中')]['shadowOffset'] == 0.5, initial
    call('/screenshot', {'name': 'original-default-bitmap-font-verified'})
    reloaded = call('/bbs-default-font-probe', {'reload': True})
    assert reloaded['previousClosed'], reloaded
    call('/wait', {'ticks': 10})
    final = state()
    assert final['atlasPages'] > 0 and final['glError'] == 0, final
    assert initial['sampleWidths'] == final['sampleWidths'], final
    assert not call('/log?limit=2000&level=ERROR+')['entries']
    report = {'ok': True, 'initial': initial, 'final': final,
              'checks': ['official PNG and Unihex metrics', 'Cyrillic width67', 'accented glyph baseline',
                         'half-pixel Unihex bold/shadow', 'GL atlases and disposal', 'formatting visual fixture']}
    path = ROOT / 'build/reports/forge1122-default-font-smoke.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS:', path)


if __name__ == '__main__':
    main()
