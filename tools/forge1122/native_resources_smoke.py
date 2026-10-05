"""Native resource packs, original chroma blocks and fractional UI GPU sampling."""
import json
from smoke import call, ROOT
from qa_environment import checked_client, report_path

checked_client()
result = call('/bbs-native-resources-probe')
print(json.dumps(result, ensure_ascii=False))
assert len(result['chroma']) == 8, result
for block in result['chroma']:
    assert block['registered'] and block['item'] and block['tab'] and block['itemModel'], block
    assert block['hardness'] == -1 and block['drops'] == 0, block
    assert block['quads'] == 6 and not block['diffuse'] and not block['ambientOcclusion'], block
assert result['minecraftRoot'] > 5 and result['minecraftTextures'] > 100, result
assert result['steveListed'] and result['stevePng'], result
assert result['nativePixelShader'] > 0, result
pixels = result['pixelart']
assert pixels['glError'] == 0, pixels
assert pixels['seams_1.5_0'] == 0 and pixels['seams_1.5_1'] > 30, pixels
assert pixels['seams_2.0_0'] == 0 and pixels['seams_2.0_1'] == 0, pixels
assert pixels['changed_1.5'] > 100, pixels
path = report_path('forge1122-native-resources-smoke.json')
path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
print('PASS:', path)
