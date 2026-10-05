"""Original form/state world hotkeys through the native Forge event and real packet relay."""
import json
import time
from smoke import call
from qa_environment import checked_client, report_path


def wait_for(predicate):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        state = call('/bbs-hotkeys-probe')
        if predicate(state):
            return state
        time.sleep(.1)
    raise AssertionError(state)


def request(op, **data):
    state = call('/bbs-hotkeys-probe', {'op': op, **data})
    task = state['requested']
    return wait_for(lambda state: state['completed'] == task)


def main():
    report = {'ok': False, 'target': checked_client()}
    try:
        request('prepare')
        report['prepared'] = wait_for(lambda s: s['morph'] == 'qa_morph' and s['main'] == 'qa_main' and s['off'] == 'qa_off')
        assert report['prepared']['nativeL'] == 38 and report['prepared']['storedL'] == 76, report['prepared']
        for mode in ({'up': True}, {'repeat': True}):
            state = request('key', key='l', **mode)
            assert (state['morphStates'], state['mainStates'], state['offStates']) == (0, 0, 0), state
        request('gui')
        report['guiIgnored'] = request('key', key='l')
        assert report['guiIgnored']['morphStates'] == 0 and report['guiIgnored']['screen'].startswith('GuiChat'), report['guiIgnored']
        request('close_gui')
        request('key', key='l')
        report['morphPriority'] = wait_for(lambda s: s['morphStates'] == 2)
        assert report['morphPriority']['mainStates'] == report['morphPriority']['offStates'] == 0
        request('main'); request('key', key='l')
        report['mainModelPriority'] = wait_for(lambda s: s['mainStates'] == 2)
        assert report['mainModelPriority']['morphStates'] == report['mainModelPriority']['offStates'] == 0
        request('off'); request('key', key='l')
        report['offhandGun'] = wait_for(lambda s: s['offStates'] == 2)
        assert report['offhandGun']['morphStates'] == report['offhandGun']['mainStates'] == 0
        request('key', key='k')
        report['recentPriority'] = wait_for(lambda s: s['morph'] == 'qa_recent')
        request('user'); request('key', key='k')
        report['userCategoryOrder'] = wait_for(lambda s: s['morph'] == 'qa_user_first')
        report['networkCheck'] = 'Scoped self-tracking observer: each state plays locally, then through Client->Server->Client relay'
        report['ok'] = True
    finally:
        report['cleanup'] = request('cleanup')
        report_path('forge1122-hotkeys-smoke.json').write_text(json.dumps(report, indent=2), encoding='utf8')
    print('PASS: native hotkeys, original priority, repeat/GUI suppression, model/gun state relay and form selection')


if __name__ == '__main__':
    main()
