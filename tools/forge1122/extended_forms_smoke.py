"""Real production form draws and FBO restoration, with no OS mouse/keyboard input."""
import json
from smoke import call, ROOT
from qa_environment import checked_client, report_path


def main():
    environment = checked_client()
    call('/bbs-extended-forms-probe', {'open': True})
    call('/wait', {'ticks': 20})
    initial = call('/bbs-extended-forms-probe')
    textures = call('/bbs-extended-forms-probe', {'framebuffers': True})['framebuffers']
    picking = call('/bbs-extended-forms-probe', {'picking': True})['picking']
    matrices = call('/bbs-extended-forms-probe', {'mobMatrices': True})['mobMatrices']
    print(json.dumps({'initial': initial, 'framebuffers': textures, 'picking': picking, 'mobMatrices': matrices}, ensure_ascii=False))
    assert initial['frames'] > 0, initial
    assert not initial['errors'], initial
    assert initial['stateRestored'], initial
    assert all(initial['roundtrip']), initial
    assert initial['structureVertices'] > 100, initial
    assert initial['trailRenderer'] and initial['videoRenderer'], initial
    assert all(p > 20 for p in initial['visiblePixels']), initial
    assert all(p['pixels'] > 0 and p['unknownPixels'] == 0 for p in picking), picking
    assert all(m['head'] and m['matrixCount'] > 3 for m in matrices), matrices
    assert matrices[-1]['posedHead'] and matrices[-1]['attachment'], matrices
    assert all('head' in p['bones'] for p in picking if p['form'] == 'MobForm'), picking
    call('/screenshot', {'name': 'extended-forms-native'})
    call('/bbs-extended-forms-probe', {'mutate': True, 'invalidate': True})
    call('/wait', {'ticks': 10})
    changed = call('/bbs-extended-forms-probe')
    assert not changed['errors'] and changed['stateRestored'], changed
    assert all(p > 20 for p in changed['visiblePixels']), changed
    assert changed['visiblePixels'][3] != initial['visiblePixels'][3], (initial, changed)
    call('/screenshot', {'name': 'extended-forms-mutated'})
    report = {'ok': True, 'environment': environment, 'initial': initial, 'changed': changed, 'picking': picking, 'mobMatrices': matrices,
              'checks': ['block and fluid geometry', 'native item model', 'label Cyrillic and background',
                         'structure AO, fluids and TESR', 'spline repetition', 'nested framebuffer forms',
                         'FBO/viewport/GL restoration', 'registered form roundtrip', 'structure cache invalidation',
                         'player wide/slim', 'mob poses and attached forms', 'per-bone picking and matrix evaluation'],
              'notCovered': ['shaderpack parity', 'video decoding', 'world trail movement', 'wand physical input']}
    path = report_path('forge1122-extended-forms-smoke.json')
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS:', path)


if __name__ == '__main__':
    main()
