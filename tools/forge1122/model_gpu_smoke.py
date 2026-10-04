"""Exercise original model GLSL, integer bone picking, and native GPU state scopes."""
import json
from pathlib import Path
from smoke import call, ROOT


def main():
    health = call('/health')
    assert health['inWorld'] and Path(health['gameDir']).resolve() in (
        (ROOT / 'run-forge1122').resolve(), (ROOT / 'run-forge1122-obf').resolve()), health
    errors = call('/log?limit=2000&level=ERROR+')['entries']
    state = call('/bbs-model-gpu-probe', {'run': True})
    checks = ['modelLinked', 'pickerLinked', 'immutableBuilderReuse', 'uploadStateRestored',
              'scopedStateRestored', 'outerStateRestored', 'nestedShaderRestored',
              'explicitLightmapOverride', 'nativeLightmapRestored', 'alphaDiscard']
    for check in checks:
        assert state[check], (check, state)
    assert state['framebufferStatus'] == 0x8CD5 and state['glError'] == 0, state
    assert state['integerUV2Location'] == 4 and state['vertexStride'] == 64, state
    assert state['defaultOverlayRow'] == 10 and state['defaultLightU'] == 240, state
    assert state['pickerActual'] == state['pickerExpected'], state
    assert state['largeTargetUniform'] == state['largeTargetExpected'], state
    assert state['largePickerActual'] == state['largePickerExpected'], state
    normal, hurt, light = state['normal'], state['hurt'], state['coloredLight']
    assert all(abs(a-b) <= 2 for a, b in zip(normal, [51, 102, 204, 255])), state
    assert hurt[0] > normal[0] + 40 and hurt[1] < normal[1] and hurt[2] < normal[2], state
    assert all(abs(a-b) <= 2 for a, b in zip(light, [13, 102, 102, 255])), state
    assert call('/log?limit=2000&level=ERROR+')['entries'] == errors
    output = ROOT / 'build/reports/forge1122-original-model-gpu-smoke.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(state, indent=2), encoding='utf-8')
    print('PASS: original model GPU / integer bone picking / native state scopes:', output)


if __name__ == '__main__':
    main()
