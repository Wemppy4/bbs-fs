"""Real animation packets and model-block editor visibility, isolated QA only."""
import json
import time
from dashboard_smoke import call, key, state, select_panel, dismiss_onboarding, wait
from qa_environment import checked_client, report_path
from smoke import command


def visibility(op='status'):
    return call('/bbs-film-player-visibility-probe', {'op': op})


def close_menu():
    for _ in range(6):
        if call('/health')['screen'] is None:
            return
        key('escape')
    raise AssertionError('Editor did not close')


def main():
    report = {'target': checked_client()}
    path = report_path('swing-model-block.json')
    film = block = False
    try:
        close_menu()
        call('/bbs-actions-probe', {'swings': True})
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            result = call('/bbs-actions-probe', {'swings': False})
            if result['result'].get('finished'):
                break
            time.sleep(.1)
        report['swings'] = result
        ticks = result['result']['swipeTicks']
        # Twelve native swings, two during countdown; each captured once, no idle clips.
        assert len(ticks) == len(set(ticks)) == 10, result
        assert ticks[0] >= 7 and ticks[-1] < 55, ticks
        assert all(3 <= b-a <= 5 for a, b in zip(ticks, ticks[1:])), ticks

        key(state()['dashboardKey'])
        dismiss_onboarding()
        select_panel('UIFilmPanel')
        visibility('prepare')
        film = True
        call('/bbs-model-blocks-probe', {'op': 'prepare'})
        block = True
        wait(6)
        call('/bbs-model-blocks-probe', {'op': 'edit'})
        wait(5)
        target = call('/bbs-model-blocks-probe', {'op': 'open'})
        wait(6)
        # Keep the player inside the camera frustum; an offscreen player proves nothing.
        command('tp @p %s %s %s' % (target['x']-1.5, target['y'], target['z']+.5))
        wait(8)
        for name, operation in [('plain', 'demorph'), ('morphed', 'morph')]:
            visibility(operation)
            wait(6)
            visibility('reset')
            wait(12)
            sample = report[name] = visibility()
            assert sample['shouldHide'], sample
            assert sample['localAttempts'] == sample['localPosts'] == sample['localMorphDraws'] == 0, sample
            assert sample['hasMorph'] == (name == 'morphed') and sample['glError'] == 0, sample
        report['screenshot'] = call('/screenshot', {'name': 'feedback24-model-block-after'})
        key('e')
        wait(8)
        visibility('reset')
        wait(12)
        nested = report['nestedEditor'] = visibility()
        assert nested['shouldHide'] and nested['localMorphDraws'] == nested['localAttempts'] == 0, nested
        report['nestedScreenshot'] = call('/screenshot', {'name': 'feedback24-model-block-form-after'})
        close_menu()
        visibility('third_person')
        visibility('reset')
        wait(12)
        restored = report['restored'] = visibility()
        assert not restored['shouldHide'] and restored['hasMorph'] and restored['localMorphDraws'] > 0, restored
        report['ok'] = True
    finally:
        close_menu()
        if block:
            call('/bbs-model-blocks-probe', {'op': 'cleanup'})
            wait(5)
        if film:
            visibility('cleanup')
        path.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(path)


if __name__ == '__main__':
    main()
