"""Real Forge film packets, actor lifecycle, morphology and damage-control hook."""
import json
from smoke import call, wait_for, ROOT


def state():
    return call('/bbs-film-backend-probe')


def operation(name):
    sent = call('/bbs-film-backend-probe', {'op': name})
    request = sent['requested']
    wait_for(lambda: state()['completed'] >= request)
    value = state()
    assert 'error' not in value['server'], value
    return value


def main():
    health = call('/health')
    assert health['inWorld'], health
    call('/release', {})
    baseline = state()
    wait_for(lambda: state()['handshake'])
    report = {}
    try:
        report['prepare'] = operation('prepare')
        assert report['prepare']['server']['prepared'] and report['prepare']['server']['serverStorage'], report
        report['damage'] = operation('damage')
        for check in ['sharedOwnerRetainsChange', 'worldHookRestoredBlock', 'worldHookRestoredNbt']:
            assert report['damage']['server'][check], report['damage']
        report['morph'] = operation('morph')
        assert report['morph']['server']['morphCapabilityRoundTrip'], report['morph']
        wait_for(lambda: state()['clientMorph'] and abs(state()['clientWidth'] - .9) < .001)
        call('/bbs-film-backend-probe', {'op': 'play'})
        wait_for(lambda: state().get('clientPlaying') and state().get('clientActor') and state().get('clientActorForm'))
        report['playing'] = operation('snapshot')
        assert report['playing']['server']['serverPlaying'] and report['playing']['server']['serverActorCount'] == 1, report['playing']
        assert report['playing']['clientActorId'] == report['playing']['server']['serverActorId'], report['playing']
        first = report['playing']['server']['serverTick']
        call('/wait', {'ticks': 8})
        assert operation('snapshot')['server']['serverTick'] > first
        call('/bbs-film-backend-probe', {'op': 'pause'})
        wait_for(lambda: state().get('clientPaused'))
        report['paused'] = operation('snapshot')
        assert report['paused']['server']['serverPaused'], report['paused']
        stopped_at = report['paused']['server']['serverTick']
        call('/wait', {'ticks': 5})
        assert operation('snapshot')['server']['serverTick'] == stopped_at
        call('/bbs-film-backend-probe', {'op': 'pause'})
        wait_for(lambda: not state().get('clientPaused', True))
        call('/wait', {'ticks': 5})
        assert operation('snapshot')['server']['serverTick'] > stopped_at
        call('/bbs-film-backend-probe', {'op': 'stop'})
        wait_for(lambda: not state().get('clientPlaying'))
        report['stopped'] = operation('snapshot')
        assert not report['stopped']['server']['serverPlaying'] and report['stopped']['server']['serverActorCount'] == 0, report['stopped']
        assert report['stopped']['glError'] == 0, report['stopped']
    finally:
        report['cleanup'] = operation('cleanup')
        wait_for(lambda: abs(state()['clientWidth'] - baseline['clientWidth']) < .001)
    report['ok'] = True
    output = ROOT / 'build/reports/forge1122-original-film-backend-smoke.json'
    output.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print('PASS: film packets / actor lifecycle / morph capability / World damage hook:', output)


if __name__ == '__main__':
    main()
