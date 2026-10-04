"""Production file import checks on temporary assets, with no UI or mouse input."""
import json
from smoke import ROOT, call


def main():
    result = call('/bbs-importers-probe', {'test': True})
    assert result['importerCount'] >= 10, result
    for key in ('nativeCallback', 'unicodePath', 'screenRestored', 'attachmentRestored'):
        assert result['nativeDrop'][key], (key, result)
    assert result['nativeDrop']['fileCount'] == 2, result
    for key in ('recognizesPng', 'rejectsEmpty', 'copyAndDuplicatePreserved',
                'recognizesLegacySkin', 'skinExpanded', 'skinOriginalRetained',
                'skinLimbMirrored', 'skinOverlayTransparent', 'temporaryFilesRemoved'):
        assert result[key], (key, result)
    if result['ffmpegAvailable']:
        for key in ('jpegConverted', 'gifExtracted', 'wavMono', 'aiffConverted'):
            assert result[key], (key, result)
    output = ROOT / 'build/reports/forge1122-importers-smoke.json'
    output.write_text(json.dumps(result, indent=2), encoding='utf8')
    print('PASS:', output)


if __name__ == '__main__':
    main()
