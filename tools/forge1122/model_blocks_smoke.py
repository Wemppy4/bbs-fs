"""Model body, NBT, replay, actual update packets and item editor persistence."""
import json
from smoke import ROOT, call, wait_for


def request(op='status'):
    result = call('/bbs-model-blocks-probe', {'op': op})
    assert not result.get('error'), result
    return result


def server(op):
    initial = request(op)
    wait_for(lambda: request()['completed'] >= initial['requested'])
    return request()


def main():
    report = {}
    try:
        server('prepare')
        wait_for(lambda: request().get('clientTile'))
        request('edit')
        call('/wait', {'ticks': 10})
        data = report['body'] = server('snapshot')
        s = data['server']
        for name in ('nbtRoundtrip','woodSound','solid','equipment','cameraIgnores','cameraCollides',
                     'nonSolid','cameraIndependent','ordinaryPick','cameraScopeRestored'):
            assert s[name], (name, data)
        assert s['light']==12 and s['hardness']==2 and abs(s['height']-1.5)<.001, data
        assert data['clientForms'] and data['clientLight']==12 and data['replayAnchor'] and data['replayShadow'], data
        assert abs(data['replayX']-data['expectedX'])<.001, data
        server('give')
        wait_for(lambda: request().get('heldModel'))
        assert request()['itemFourForms']
        request('itemEdit')
        call('/wait', {'ticks': 10})
        data = report['item'] = server('snapshot')
        for s in (data, data['server']):
            assert abs(s['itemInventoryScale']-.4)<.001 and abs(s['itemFirstPersonY']-.3)<.001, data
        request('open')
        call('/wait', {'ticks': 15})
        assert request()['panelSelected']
        call('/screenshot', {'name': 'model-block-original-panel'})
        report['ok'] = True
    finally:
        server('cleanup')
    path = ROOT/'build/reports/forge1122-model-blocks-smoke.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS:',path)


if __name__ == '__main__':
    main()
