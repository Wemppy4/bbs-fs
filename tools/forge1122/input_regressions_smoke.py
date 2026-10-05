"""Native keyboard input through the original interpolation and order widgets."""
import json
import time
from PIL import Image
from smoke import call
from qa_environment import checked_client, report_path


ROUTE = '/bbs-input-regressions-probe'


def status():
    return call(ROUTE)


def main():
    report = {'ok': False, 'target': checked_client()}
    try:
        call(ROUTE, {'op': 'prepare'})
        time.sleep(.5)
        report['initial'] = state = status()
        assert state['dashboardDefault'] == 11 and state['customRPreserved'], state
        assert state['categories'] == ['category.bbs.main'], state
        assert state['interpolation'] == 'constant' and not state['contextMenu'], state
        assert state['glError'] == 0, state
        shot = call('/screenshot', {'name': 'original-input-order-colors'})
        report['screenshot'] = shot
        screenshot = Image.open(shot['path']).convert('RGB')
        sx, sy = screenshot.width / state['width'], screenshot.height / state['height']
        report['orderPixels'] = []
        for row in state['orders']:
            assert all(row[name] != 0 for name in ('xColor', 'yColor', 'zColor')), row
            pixels = list(screenshot.crop((int(row['x'] * sx), int(row['y'] * sy),
                                          int((row['x'] + row['w']) * sx),
                                          int((row['y'] + row['h']) * sy))).getdata())
            counts = [sum(p[axis] > 100 and p[axis] > p[(axis + 1) % 3] + 40
                          and p[axis] > p[(axis + 2) % 3] + 40 for p in pixels) for axis in range(3)]
            report['orderPixels'].append(counts)
            assert min(counts) > 2, (row, counts)
        # Each key travels through helper /key -> GuiScreen.keyTyped(native LWJGL)
        # -> UIScreen GLFW translation -> actual selected-key parameters/menu.
        call('/key', {'key': 't', 'shift': True})
        assert status()['contextMenu'], status()
        report['selections'] = []
        for key, modifier, expected in [('l', None, 'linear'), ('q', None, 'quad_inout'),
                                        ('q', 'shift', 'quad_in'), ('q', 'ctrl', 'quad_out'),
                                        ('t', None, 'constant'), ('p', None, 'step'),
                                        ('c', 'ctrl', 'cubic_out')]:
            event = {'key': key}
            if modifier:
                event[modifier] = True
            call('/key', event)
            state = status()
            report['selections'].append({'event': event, 'actual': state['interpolation']})
            assert state['interpolation'] == expected, (event, expected, state)
        call('/key', {'key': 'escape'})
        assert not status()['contextMenu'], status()
        # The camera clip's original context-menu consumer uses the other key helper.
        call(ROUTE, {'op': 'camera'})
        call('/key', {'key': 'e', 'shift': True})
        report['cameraMenu'] = status()
        assert report['cameraMenu']['interpolation'] == 'exp_in', report['cameraMenu']
        assert report['cameraMenu']['glError'] == 0, report['cameraMenu']
        report['ok'] = True
    finally:
        report['cleanup'] = call(ROUTE, {'op': 'cleanup'})
        output = report_path('forge1122-input-regressions-smoke.json')
        output.write_text(json.dumps(report, indent=2), encoding='utf8')
    print('PASS: native interpolation shortcuts, GPU axis colors and original key settings', output)


if __name__ == '__main__':
    main()
