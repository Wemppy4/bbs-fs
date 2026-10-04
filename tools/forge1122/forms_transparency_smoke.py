"""Native overlap/cutout diagnostic; report exact pixel changes before asserting parity."""
import json
from smoke import call, ROOT

result = call('/bbs-forms-transparency-probe')
path = ROOT / 'build/reports/forge1122-forms-transparency-smoke.json'
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(result, ensure_ascii=False))
assert result['stateRestored'] and result['glError'] == 0, result
assert all(row['visible'] > 30 for row in result['order']), result
