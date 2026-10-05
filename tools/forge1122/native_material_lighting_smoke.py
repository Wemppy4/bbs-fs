"""Actual thumbnails and native inventory icons: original GUI lighting semantics."""
import json
from smoke import call
from qa_environment import checked_client, report_path
from native_ui_render_smoke import probe


def main():
    report = {'target': checked_client()}
    path = report_path('forge1122-native-material-lighting-smoke.json')
    try:
        probe({'open': True, 'materials': True})
        call('/wait', {'ticks': 6})
        report['frame'] = frame = probe()
        report['screenshot'] = call('/screenshot', {'name': 'native-material-lighting'})
        assert frame['glError'] == 0, frame
        for sample in frame['samples']:
            assert 'error' not in sample and sample['pixels']['visible'] > 20, sample
        for item in frame['inventory']:
            assert item['pixels']['visible'] > 20, item
        # Original model.json/gun.json use gui_light:front. A white front-facing quad
        # saturates the two-light .6 diffuse + .4 ambient equation, without world shading.
        reference = frame['inventory'][-1]
        assert reference['pixels']['max'] >= 245, reference
        matrix = reference['textureUniforms']['NormalMatrix']
        assert max(abs(v - (1 if i in (0, 4, 8) else 0)) for i, v in enumerate(matrix)) < 1e-4, matrix
        preview = frame['samples'][0]['textureUniforms']
        assert preview['Light0'] != reference['textureUniforms']['Light0'], frame
        report['status'] = 'PASS; cow facing requires screenshot inspection'
    finally:
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        call('/bbs-extended-forms-probe', {'open': True})
    print(path)


if __name__ == '__main__':
    main()
