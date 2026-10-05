"""Render all four model item forms through vanilla's override/perspective/TEISR path."""
import json
from smoke import call, command, ROOT
from qa_environment import checked_client, report_path

checked_client()
# World perspectives and cracks deliberately use the player's native lightmap.
# Keep their fixture above ground at noon, independent of the previous QA test.
command('time set 6000')
command('weather clear')
command('tp @p 0.5 60 0.5 0 0')
call('/wait', {'ticks': 20})
result = call('/bbs-model-item-gpu-probe')
print(json.dumps(result, ensure_ascii=False))
assert result['stateRestored'] and result['glError'] == 0, result
assert [v['texture'] for v in result['creative']] == ['assets:textures/model_block.png', 'assets:textures/icon.png'], result
assert all(v['form'] == 'BillboardForm' and v['loaded'] and v['pixels'] > 20 for v in result['creative']), result
expected = [(0,), (1,), (2,), (0, 1)]
for row, channels in zip(result['perspectives'], expected):
    assert row['pixels'] > 100, row
    for channel, value in enumerate(row['rgb']):
        assert value > 60 if channel in channels else value < 10, row
for crack in result['cracks']:
    assert crack['pixels'] > 20 and crack['stateRestored'], crack
assert result['cracks'][0]['pixels'] != result['cracks'][1]['pixels'], result['cracks']
path = report_path('forge1122-model-item-gpu-smoke.json')
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
