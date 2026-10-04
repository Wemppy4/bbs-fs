"""Offscreen smoke of real BBS particle parser, simulation, repository and native rendering."""
import json
from pathlib import Path
from smoke import ROOT, call


def main():
    result = call('/bbs-particles-probe', {'test': True})
    for key in ('roundtrip', 'repositoryRoundtrip', 'finite', 'motion', 'lifetime', 'pause', 'resume', 'modernNameAlias', 'blockArguments'):
        assert result[key], (key, result)
    assert result['componentCount'] >= 10, result
    assert result['curveCount'] >= 1, result
    assert 0 < result['spawned'] <= 500, result
    assert result['vanillaCount'] == 20, result
    assert result['render']['bedrockPixels'] > 25, result
    assert result['render']['vanillaPixels'] > 25, result
    assert result['glError'] == 0, result
    output = ROOT / 'build/reports/forge1122-particles-smoke.json'
    output.write_text(json.dumps(result, indent=2), encoding='utf8')
    print('PASS:', output)


if __name__ == '__main__':
    main()
