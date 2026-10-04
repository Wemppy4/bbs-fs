"""Validate original structure data and caches against native Forge NBT and blocks."""
import json
from pathlib import Path
from smoke import ROOT, call


def main():
    health = call('/health')
    assert Path(health['gameDir']).resolve() == (ROOT / 'run-forge1122').resolve(), health
    result = call('/bbs-structures-probe', {})
    for key in ('listedAssets', 'listedLegacy', 'listedGenerated', 'globalPriority', 'geometry',
                'tileNbt', 'legacyLoad', 'generatedLoad', 'cached', 'invalidated',
                'previewSurvivesInvalidation', 'uniquePreviewIds', 'pathBoundary'):
        assert result[key], (key, result)
    for key, expected in {'emitterLight': 15, 'adjacentLight': 14, 'opaqueLight': 0,
                          'skyLight': 15, 'distantLight': 0}.items():
        assert result[key] == expected, (key, result)
    output = ROOT / 'build/reports/forge1122-structures-smoke.json'
    output.write_text(json.dumps(result, indent=2), encoding='utf8')
    print('PASS:', output)


if __name__ == '__main__':
    main()
