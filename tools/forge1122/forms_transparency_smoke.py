"""Native overlap ordering, straight-alpha label output, and opaque structure cutout parity."""
import json
from smoke import call, ROOT
from qa_environment import checked_client, report_path

environment = checked_client()
result = call('/bbs-forms-transparency-probe')
result['environment'] = environment
path = report_path('forge1122-forms-transparency-smoke.json')
path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(result, ensure_ascii=False))
assert result['stateRestored'] and result['glError'] == 0, result
assert all(row['visible'] > 30 for row in result['order']), result
assert all(row['changedPixels'] == 0 and row['absoluteError'] == 0 for row in result['order']), result
red, green, blue, _alpha = result['halfRedOverGreen']
# Fullbright lightmap is slightly below white; original default blending does not preserve destination alpha.
assert 115 <= red <= 135 and 120 <= green <= 135 and blue <= 2, result
structures = [row for row in result['cutout'] if row['kind'] == 'structure']
assert len(structures) == 3 and all(row['visible'] > 0 and row['backgroundDependent'] == 0 for row in structures), result
# BlockForm itself deliberately enables blending in the original; do not impose Structure's cutout rule there.
print('PASS:', path)
