"""Check the real model runtime in the disposable ai_test world, without restart."""
import json
from smoke import ROOT, call, click, command, wait_for
from viewport_smoke import state, choose_pixel


def open_model_screen():
    if call('/health')['screen'] == 'ModelScreen':
        return
    if call('/health')['screen'] is not None:
        call('/key', {'key': 'escape'})
    command('tp @p 0.5 4 0.5 0 20')
    assert call('/block', {'x': 0, 'y': 4, 'z': 3})['id'] == 'bbs:model'
    call('/wait', {'ticks': 5})
    call('/key', {'key': 'use'})
    wait_for(lambda: call('/health')['screen'] == 'ModelScreen', 8)


def scan_drone(label):
    state({'open': True})
    try:
        wait_for(lambda: state()['frames'] > 4)
        call('/wait', {'ticks': 8})
        result = state({'scan': True})
        assert result['scan']['pixels'] > 1000 and len(result['scan']['ids']) > 8, result
        assert result['stateRestored'] and result['currentFormCleared'] and result['glError'] == 0, result
        choose_pixel(result['scan'])
        call('/screenshot', {'name': 'original-model-' + label + '-verified'})
        return result
    finally:
        state({'close': True})


def main():
    errors_before = call('/log?limit=2000&level=ERROR+')['entries']
    call('/release')
    config = ROOT / 'run-forge1122/config/bbs/assets/models/helper_drone/config.json'
    original = config.read_bytes()
    values = json.loads(original)
    assert not values.get('on_cpu', False), values
    records = {}
    try:
        open_model_screen()
        click(1)
        records['vao'] = scan_drone('vao')
        values['on_cpu'] = True
        config.write_text(json.dumps(values, indent=2), encoding='utf-8')
        open_model_screen()
        click(1)
        records['cpu'] = scan_drone('cpu')
        assert records['vao']['scan']['ids'] == records['cpu']['scan']['ids'], records
        assert abs(records['vao']['scan']['pixels'] - records['cpu']['scan']['pixels']) < 10, records
    finally:
        config.write_bytes(original)
        open_model_screen()
        click(1)

    # Select the shipped polygon/skinning BOBJ model through the real model list.
    call('/click', {'x': 30, 'y': 34})
    call('/type', {'text': 'emoticons/steve'})
    call('/click', {'x': 40, 'y': 56})
    wait_for(lambda: 'emoticons/steve' in call('/bbs1122').get('loadedModels', []))
    call('/wait', {'ticks': 8})
    bobj = call('/bbs1122')
    assert 'emoticons/steve' in bobj.get('form', '') and len(bobj.get('bones', {})) > 10, bobj
    assert bobj['glError'] == 0, bobj
    call('/screenshot', {'name': 'original-model-bobj-verified'})
    records['bobj'] = bobj
    click(4)
    before = call('/bbs1122')
    call('/wait', {'ticks': 6})
    animated = call('/bbs1122')
    assert before['bones'] != animated['bones'] and animated['glError'] == 0, animated
    call('/screenshot', {'name': 'original-model-bobj-animation-verified'})
    records['bobjAnimated'] = animated
    assert call('/log?limit=2000&level=ERROR+')['entries'] == errors_before
    output = ROOT / 'build/reports/forge1122-original-model-runtime-smoke.json'
    output.write_text(json.dumps({'ok': True, 'checks': [
        'Real model reload, VAO and config on_cpu paths render matching individual bone masks',
        'Both paths preserve GL, framebuffer and current form lifecycle',
        'Original helper_drone config bytes restored and model reloaded',
        'Shipped BOBJ model loads, evaluates bones and renders without new GL/runtime errors',
        'BOBJ animated bone transforms change across six actual game ticks'
    ], 'records': records}, indent=2), encoding='utf-8')
    print('PASS: native model runtime, report:', output)
    click(2)
    call('/release')


if __name__ == '__main__':
    main()
