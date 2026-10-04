"""Actual ModelForm color overlay in a shader pack, plus restoration to the neutral draw."""
import json
from smoke import call, ROOT


def probe(action='state', **values):
    return call('/bbs-shader-world-forms-probe', {'action': action, **values})


def main():
    assert call('/health')['inWorld']
    assert call('/bbs-optifine-probe')['loaded']
    report = {}
    path = ROOT / 'build/reports/forge1122-model-overlay-smoke.json'
    try:
        probe('cem-enable')
        probe('start')
        call('/wait', {'ticks': 25})
        for name, color, hurt, expected in (
                ('base', [0, 0, 0, 0], False, [0, 0, 0, 0]),
                ('blue', [0, 0, 1, 0.8], False, [0, 0, 1, 0.8]),
                ('hurt', [0, 0, 1, 0.8], True, [1, 0, 0, 77/255]),
                ('reset', [0, 0, 0, 0], False, [0, 0, 0, 0])):
            probe('cem-material', overlay=color, hurt=hurt)
            call('/wait', {'ticks': 10})
            report[name] = state = probe()
            assert not state['errors'] and state['stateRestored'] and state['samples'][7] > 0, state
            assert state['cemDraw'], state
            for draw in state['cemDraw']:
                assert not draw.get('error') and draw.get('glError', 0) == 0, draw
                actual = draw['entityColor']
                assert all(abs(a-b) < 0.006 for a, b in zip(actual, expected)), (actual, expected)
            report[name]['image'] = call('/screenshot', {'name': 'model-overlay-' + name})
    finally:
        try:
            probe('stop')
            probe('cem-disable')
        finally:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS: ModelForm native entityColor uses the overlay and returns to neutral; inspect screenshots')
    print(path)


if __name__ == '__main__':
    main()
