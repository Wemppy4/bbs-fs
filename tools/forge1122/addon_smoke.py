"""Verify a real Forge-discovered addon observes the original BBS lifecycle in order."""
import json
from smoke import ROOT, call


def main():
    result = call('/bbs-addon-probe', {})
    expected = ['common_discovered', 'source_packs', 'keyframe_factories', 'forms',
                'form_modifiers', 'camera_clips', 'action_clips', 'settings', 'common_ready',
                'client_discovered', 'l10n', 'model_loaders', 'form_sections', 'track_categories',
                'keybinds', 'form_renderers', 'form_editors', 'importers', 'client_ready']
    assert result['events'] == expected, result
    assert result['apiVersion'] == 2 and result['modVersion'] != 'unknown', result
    assert result['registeredAddons'] >= 2, result
    assert result['addonAssetRead'] and result['addonAssetListing'] > 0 and result['versionGuard'], result
    output = ROOT / 'build/reports/forge1122-addon-smoke.json'
    output.write_text(json.dumps(result, indent=2), encoding='utf8')
    print('PASS:', output)


if __name__ == '__main__':
    main()