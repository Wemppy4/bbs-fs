"""Forge movement event, remapped controls and a real Dashboard overlay; no OS input."""
import json
from smoke import call, ROOT

result = call('/bbs-film-control-input-probe')
print(json.dumps(result, ensure_ascii=False))
assert all(result.values()), result
path = ROOT / 'build/reports/forge1122-film-control-input-smoke.json'
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
