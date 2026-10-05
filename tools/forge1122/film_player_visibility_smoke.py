"""Actual film panel lifecycle and world render callbacks, including a player-shaped replay."""
import json
from dashboard_smoke import (
    call, checked_client, click, dismiss_onboarding, key, select_panel, state, wait,
)
from qa_environment import report_path


def probe(op='status'):
    return call('/bbs-film-player-visibility-probe', {'op': op})


def capture(name):
    probe('reset')
    wait(12)
    result = probe()
    result['dashboard'] = state()
    result['screenshot'] = call('/screenshot', {'name': 'film-player-' + name})
    return result


def main():
    report = {'target': checked_client(), 'shader': call('/bbs-optifine-probe')}
    active = False
    call('/bbs-depth-state-probe', {'op': 'start', 'limit': 100000})
    try:
        if call('/health')['screen'] is not None:
            key('escape')
        assert call('/health')['screen'] is None
        key(state()['dashboardKey'])
        dismiss_onboarding()
        select_panel('UIFilmPanel')
        report['prepare'] = probe('prepare')
        active = True
        wait(8)
        plain = report['unmorphed'] = capture('film-unmorphed')
        assert not plain['hasMorph'] and plain['hidePlayer'] and not plain['cameraIsPlayer'], plain
        assert plain['shouldHide'] and plain['localAttempts'] == plain['localMorphDraws'] == 0, plain
        assert plain['localPosts'] == 0 and plain['previewPosts'] > 0 and plain['glError'] == 0, plain

        probe('morph')
        wait(8)
        morphed = report['morphed'] = capture('film-morphed')
        assert morphed['hasMorph'] and morphed['hidePlayer'], morphed
        assert morphed['shouldHide'] and morphed['localAttempts'] == morphed['localMorphDraws'] == 0, morphed
        assert morphed['localPosts'] == 0 and morphed['previewPosts'] > 0 and morphed['glError'] == 0, morphed

        select_panel('UIMorphingPanel')
        report['otherPanel'] = probe()
        assert not report['otherPanel']['hidePlayer'], report['otherPanel']
        select_panel('UIFilmPanel')
        report['reentered'] = capture('film-reentered')
        assert report['reentered']['hidePlayer'] and report['reentered']['previewPosts'] > 0
        key('escape')
        assert call('/health')['screen'] is None
        probe('demorph')
        wait(8)
        probe('third_person')
        restored = report['restored'] = capture('world-restored')
        assert not restored['hidePlayer'] and not restored['hasMorph'], restored
        assert restored['localPosts'] > 0 and restored['localCanceled'] == 0 and restored['glError'] == 0, restored
        report['ok'] = True
    finally:
        if active:
            if call('/health')['screen'] is not None:
                key('escape')
            report['cleanup'] = probe('cleanup')
            wait(5)
        depth = call('/bbs-depth-state-probe', {'op': 'stop'})
        report_path('forge1122-film-player-depth-states.json').write_text(
            json.dumps(depth, ensure_ascii=False, indent=2), encoding='utf-8')
        path = report_path('forge1122-film-player-visibility-smoke.json')
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(path)


if __name__ == '__main__':
    main()
