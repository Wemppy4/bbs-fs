"""Native user-font rendering, layout, atlas disposal and optional filesystem watchers."""
import argparse
import json
from pathlib import Path
from smoke import call, ROOT


def state():
    return call('/bbs-font-probe')


def wait_for(predicate):
    for _ in range(12):
        call('/wait', {'ticks': 5})
        snapshot = state()
        if predicate(snapshot):
            return snapshot
    raise AssertionError(snapshot)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--watch', action='store_true', help='Also require the integrated BBSResources filesystem watcher')
    args = parser.parse_args()
    health = call('/health')
    assert Path(health['gameDir']).resolve() == (ROOT / 'run-forge1122').resolve(), health
    call('/bbs-font-probe', {'open': True})
    call('/wait', {'ticks': 25})
    initial = state()
    assert initial['loaded'] and initial['hasCyrillic'], initial
    assert initial['width1'] == initial['width3'] > 0, initial
    assert initial['oversample1'] == 1 and initial['oversample3'] == 3, initial
    assert initial['height'] > 0 and initial['lineHeight'] > initial['height'], initial
    assert initial['sameRoundedKey'] and initial['formatWidth'] and initial['boldWider'], initial
    assert initial['atlasPages'] > 0 and len(initial['wrapped']) > 1, initial
    assert all(line.startswith('\u00a7a') for line in initial['wrapped']), initial
    assert initial['glError'] == 0, initial
    call('/screenshot', {'name': 'original-native-fonts-verified'})
    rebuilt = call('/bbs-font-probe', {'action': 'invalidate'})
    assert rebuilt['previousClosed'] and rebuilt['loaded'], rebuilt
    call('/wait', {'ticks': 5})
    assert state()['atlasPages'] > 0 and state()['glError'] == 0, state()
    if args.watch:
        call('/bbs-font-probe', {'action': 'touch'})
        wait_for(lambda s: s['previousClosed'] and s['loaded'])
        call('/bbs-font-probe', {'action': 'delete'})
        wait_for(lambda s: s['previousClosed'] and not s['loaded'])
        call('/bbs-font-probe', {'action': 'restore'})
        wait_for(lambda s: s['loaded'] and s['atlasPages'] > 0)
    final = state()
    assert final['loaded'] and final['glError'] == 0, final
    assert not call('/log?limit=2000&level=ERROR+')['entries']
    report = {'ok': True, 'watchVerified': args.watch, 'initial': initial, 'final': final,
        'checks': ['TTF Cyrillic glyphs', 'raw SFNT metrics', 'oversampling invariant width',
                   'formatting and wrap', 'OpenGL atlases', 'invalidation disposal/reload']}
    output = ROOT / 'build/reports/forge1122-native-fonts-smoke.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS:', output)


if __name__ == '__main__':
    main()
