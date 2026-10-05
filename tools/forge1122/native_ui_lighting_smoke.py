"""Original GUI light uniforms and immediate/deferred slime lighting on the real GPU."""
import json
from pathlib import Path
from PIL import Image, ImageChops
from smoke import call
from qa_environment import checked_client, report_path
from native_ui_render_smoke import probe


def main():
    report = {'target': checked_client()}
    path = report_path('forge1122-native-ui-lighting-smoke.json')
    try:
        probe({'open': True})
        call('/wait', {'ticks': 6})
        report['players'] = probe()
        report['playerScreenshot'] = call('/screenshot', {'name': 'native-ui-player-lighting'})
        probe({'viewportMob': 'minecraft:slime', 'queue': False})
        call('/wait', {'ticks': 6})
        report['immediate'] = probe()
        report['immediateScreenshot'] = call('/screenshot', {'name': 'native-ui-slime-immediate'})
        probe({'queue': True})
        call('/wait', {'ticks': 6})
        report['deferred'] = probe()
        report['deferredScreenshot'] = call('/screenshot', {'name': 'native-ui-slime-deferred'})
        immediate, deferred = report['immediate'], report['deferred']
        assert immediate['glError'] == deferred['glError'] == 0, report
        assert immediate['queuedDraws'] == 0 and deferred['queuedDraws'] > 0, report
        for sample in (immediate, deferred):
            assert sample['nativeUniforms']['Diffuse'] == [1.0], sample
        assert immediate['nativeUniforms']['Light0'] == deferred['nativeUniforms']['Light0'], report
        assert immediate['nativeUniforms']['Light1'] == deferred['nativeUniforms']['Light1'], report
        a = Image.open(report['immediateScreenshot']['path']).convert('RGB')
        b = Image.open(report['deferredScreenshot']['path']).convert('RGB')
        # The viewport occupies the bottom half; independent animated thumbnails stay out.
        region = (16, a.height // 2, a.width - 16, a.height - 16)
        difference = ImageChops.difference(a.crop(region), b.crop(region))
        report['viewportDifference'] = {'maxChannelError': max(high for low, high in difference.getextrema()),
                                       'changedPixels': sum(pixel != (0, 0, 0) for pixel in difference.getdata())}
        assert report['viewportDifference']['maxChannelError'] <= 1, report['viewportDifference']
    finally:
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        call('/bbs-extended-forms-probe', {'open': True})
    print(path)


if __name__ == '__main__':
    main()
