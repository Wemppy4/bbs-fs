"""Regression for stationary editor camera projection changes and terrain visibility."""
import json
import math
import re
from dashboard_smoke import call, state, key, select_panel, dismiss_onboarding, wait
from qa_environment import checked_client, report_path


def terrain(**request):
    return call('/bbs-camera-probe', {'terrain': True, **request})


def count(snapshot):
    return int(re.search(r'C: (\d+)/', snapshot['renders']).group(1))


def stable_visibility():
    wait(30)
    before = terrain()
    terrain(invalidate=True)
    wait(30)
    after = terrain()
    assert count(before) == count(after), (before, after)
    assert before['loaded'] == after['loaded'], (before, after)
    if not before['viewIsPlayer']:
        assert before['chunk'] == [math.floor(x / 16) for x in before['position']], before
    return {'before': before, 'after': after}


def main():
    report = {'target': checked_client()}
    prepared = False
    try:
        if call('/health')['screen'] is not None:
            key('escape')
        call('/bbs-camera-probe', {'open': True})
        for fov in (20, 110, 35, 90):
            call('/bbs-camera-probe', {'fov': fov})
            report['fov' + str(fov)] = stable_visibility()
        assert count(report['fov110']['before']) > count(report['fov20']['before']), report
        call('/bbs-camera-probe', {'orbit': True})
        call('/bbs-camera-probe', {'toggleOrtho': True})
        report['ortho'] = stable_visibility()
        call('/bbs-camera-probe', {'toggleOrtho': True})
        report['perspective'] = stable_visibility()
        call('/bbs-camera-probe', {'close': True})
        report['restored'] = stable_visibility()
        assert report['restored']['before']['viewIsPlayer'], report
        key(state()['dashboardKey'])
        dismiss_onboarding()
        select_panel('UIFilmPanel')
        call('/bbs-film-player-visibility-probe', {'op': 'prepare'})
        prepared = True
        report['film'] = stable_visibility()
        report['screenshot'] = call('/screenshot', {'name': 'film-chunks-fixed'})
        for index in range(3):
            key('escape')
            wait(5)
            key(state()['dashboardKey'])
            select_panel('UIFilmPanel')
            report['reopen' + str(index)] = stable_visibility()
        report['ok'] = True
    finally:
        if call('/health')['screen'] is not None:
            key('escape')
        if prepared:
            call('/bbs-film-player-visibility-probe', {'op': 'cleanup'})
        report_path('chunk-visibility.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print('PASS: projection changes, chunk coordinates, film entry/reentry and camera restore')


if __name__ == '__main__':
    main()
