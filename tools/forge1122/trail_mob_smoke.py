"""Production moving ribbon, native mob animation matrices and deterministic held items."""
import json
from smoke import call, ROOT
from qa_environment import checked_client, report_path

environment = checked_client()
result = call('/bbs-trail-mob-probe')
result['environment'] = environment
print(json.dumps(result, ensure_ascii=False))
assert result['stateRestored'] and result['glError'] == 0, result
rows = {row['state']: row for row in result['mob']}
for state in ('standing', 'crouching', 'bow', 'gliding'):
    row = rows[state]
    assert row['pixels'] > 30 and row['matrices'] >= 10, row
    assert row['maxMatrixError'] < .0001 and row['attachment'], row
assert rows['gliding']['gliding'] and rows['gliding']['roll'] == 40, rows
assert rows['bow']['usingItem'] and rows['bow']['useElapsed'] == 12, rows
assert rows['modelReplacement']['refreshed'], rows
assert rows['useDoesNotConsume']['sourceCount'] == rows['useDoesNotConsume']['copyCount'] == 3, rows
assert rows['armorDye']['changedPixels'] > 30, rows
trail = result['trail']
assert trail['pixels'] > 100 and trail['samples'] == 6 and trail['rgb'] > 0, trail
assert all(trail[k] for k in ('lightRestored', 'paused', 'unlit', 'expired')), trail
path = report_path('forge1122-trail-mob-smoke.json')
path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
