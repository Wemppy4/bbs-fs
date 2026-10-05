"""Real editor regression: native held WASD, camera interpolation, orbit and local body."""
import json
from pathlib import Path
from dashboard_smoke import call, state, key, select_panel, dismiss_onboarding, wait
from qa_environment import checked_client, report_path


def probe(op):
    return call('/bbs-film-player-visibility-probe', {'op': op})


def main():
    report = {'target': checked_client()}
    path = report_path('editor-feedback.json')
    prepared = False
    try:
        if call('/health')['screen'] is not None:
            key('escape')
        key(state()['dashboardKey'])
        dismiss_onboarding()
        select_panel('UIFilmPanel')
        report['prepare'] = probe('prepare')
        prepared = True
        wait(8)
        report['orbit'] = probe('orbit')
        wait(8)
        report['orbitScreenshot'] = call('/screenshot', {'name': 'feedback23-orbit-after'})
        report['control'] = probe('control')
        wait(8)
        report['beforeWalk'] = probe('status')
        probe('walk')
        wait(28)
        report['afterWalk'] = probe('status')
        before, after = report['beforeWalk'], report['afterWalk']
        assert after['inputSamples'] == 20 and after['inputForward'] == 1 and abs(after['moveForward'] - .98) < 1e-5, after
        distance = ((after['playerX']-before['playerX'])**2+(after['playerZ']-before['playerZ'])**2)**.5
        assert distance > 1, distance
        assert abs(after['playerX']-after['serverX']) < .2 and abs(after['playerZ']-after['serverZ']) < .2, after
        report['distance'] = distance
        probe('control')
        probe('morph')
        wait(5)
        select_panel('UIMorphingPanel')
        report['morphEdit'] = probe('morph_edit')
        wait(4)
        probe('reset')
        wait(14)
        report['paused'] = probe('status')
        report['morphScreenshot'] = call('/screenshot', {'name': 'feedback23-morph-after'})
        paused = report['paused']
        frames = paused['frames']
        assert paused['paused'] and len(frames) >= 20, paused
        report['pausedCameraRange'] = {axis: max(f['camera'+axis] for f in frames)-min(f['camera'+axis] for f in frames) for axis in ('X','Y','Z')}
        assert max(report['pausedCameraRange'].values()) < 1e-7, report['pausedCameraRange']
        assert paused['shouldHide'] and paused['hasMorph'], paused
        assert paused['localAttempts'] == paused['localPosts'] == paused['localMorphDraws'] == 0, paused
        report['ok'] = True
    finally:
        path.write_text(json.dumps(report, indent=2), encoding='utf-8')
        if prepared:
            if call('/health')['screen'] is not None:
                key('escape')
            probe('cleanup')
    print(path)


if __name__ == '__main__':
    main()
