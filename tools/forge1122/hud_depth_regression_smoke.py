"""Real wand HUD must not leave GL_ALWAYS in later model-block/world frames."""
import json
from dashboard_smoke import key, state, wait
from smoke import call, command, wait_for
from qa_environment import checked_client, report_path


def main():
    report = {'target': checked_client()}
    prepared = False
    try:
        if call('/health')['screen'] is not None:
            key('escape')
        key(state()['dashboardKey'])
        key('escape')
        report['before'] = call('/gl-state')
        assert report['before']['depthFunc'] == 515, report
        start = call('/bbs-model-blocks-probe', {'op': 'prepare'})
        prepared = True
        wait_for(lambda: call('/bbs-model-blocks-probe')['completed'] >= start['requested'])
        wait_for(lambda: call('/bbs-model-blocks-probe').get('clientTile'))
        report['fixture'] = call('/bbs-model-blocks-probe', {'op': 'edit', 'model': 'helper_drone'})
        wait(8)
        call('/bbs-depth-state-probe', {'op': 'start', 'limit': 100000})
        command('replaceitem entity @p slot.weapon.mainhand bbs:structure_wand')
        wait(15)
        report['wand'] = call('/gl-state')
        report['wandScreenshot'] = call('/screenshot', {'name': 'depth-wand-fixed'})
        command('replaceitem entity @p slot.weapon.mainhand minecraft:air')
        wait(15)
        report['afterWand'] = call('/gl-state')
        report['draws'] = call('/bbs-depth-state-probe', {'op': 'stop'})
        for boundary in ('wand', 'afterWand'):
            assert report[boundary]['depthFunc'] == 515 and report[boundary]['glError'] == 0, report
        model_states = [v for v in report['draws']['states']
                        if v['form'] == 'ModelForm' and v['type'] == 'MODEL_BLOCK']
        assert sum(v['count'] for v in model_states) >= 10, report
        assert all(v['func'] == 515 and v['test'] and v['write'] and v['bits'] > 0
                   and v['near'] == 0 and v['far'] == 1 for v in model_states), model_states
        # The ordinary editor still owns its ALWAYS UI mode, and its model picker works.
        call('/bbs-model-blocks-probe', {'op': 'open'})
        wait(8)
        report['editorScreenshot'] = call('/screenshot', {'name': 'depth-model-editor-fixed'})
        assert call('/gl-state')['glError'] == 0
        key('escape')
        report['ok'] = True
    finally:
        call('/bbs-depth-state-probe', {'op': 'stop'})
        if prepared:
            cleanup = call('/bbs-model-blocks-probe', {'op': 'cleanup'})
            wait_for(lambda: call('/bbs-model-blocks-probe')['completed'] >= cleanup['requested'])
        report_path('forge1122-hud-depth-regression-smoke.json').write_text(
            json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS:', report_path('forge1122-hud-depth-regression-smoke.json'))


if __name__ == '__main__':
    main()
