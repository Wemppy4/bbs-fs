"""Use the original F4/F6 bindings to record the live world and a film in ai_test.

--resize-window also checks the opt-in native window resize/restore; run this only
on the isolated hidden desktop. Input uses ai_helper and never moves the mouse.
"""
import argparse
import json
import math
from pathlib import Path
import struct
import subprocess
import time
import uuid
import wave
from smoke import call
from qa_environment import checked_client, report_path
from dashboard_smoke import dismiss_onboarding
from export_smoke import executable


def status():
    state = call('/bbs-world-export-probe')
    assert state['glError'] == 0 and not state['failure'], state
    return state


def wait_for(predicate, seconds=60):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        state = status()
        if predicate(state):
            return state
        time.sleep(.1)
    raise AssertionError({'timedOut': state})


def key(name):
    call('/key', {'key': name})


def inspect_movie(folder, previous, width, height, audio=False):
    created = sorted(set(folder.glob('*.mp4')) - previous)
    assert len(created) == 1, created
    movie = created[0]
    metadata = json.loads(subprocess.check_output([
        executable('ffprobe'), '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(movie)
    ], text=True, encoding='utf8'))
    video = next(stream for stream in metadata['streams'] if stream['codec_type'] == 'video')
    assert (video['width'], video['height']) == (width, height), metadata
    assert int(video['nb_frames']) >= 10 and float(video['duration']) > .2, metadata
    if audio:
        assert any(stream['codec_type'] == 'audio' for stream in metadata['streams']), metadata
        assert .9 <= float(video['duration']) <= 2.2, metadata
    pixels = subprocess.check_output([
        executable('ffmpeg'), '-v', 'error', '-i', str(movie), '-frames:v', '1',
        '-vf', 'scale=64:36', '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'
    ], timeout=30)
    assert len(pixels) == 64 * 36 * 3 and max(pixels) > 15, 'Empty world video'
    return {'movie': str(movie), 'metadata': metadata, 'nonblackPixels': sum(value > 15 for value in pixels)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--resize-window', action='store_true')
    args = parser.parse_args()
    target = checked_client()
    assets = Path(call('/bbs-export-fixture', {'paths': True})['assets'])
    name = 'sounds/__ai_world_export_' + uuid.uuid4().hex + '.wav'
    tone = assets / name
    tone.parent.mkdir(parents=True, exist_ok=True)
    rate = 48000
    samples = [int(6000 * math.sin(2 * math.pi * 440 * index / rate)) for index in range(rate * 2)]
    with wave.open(str(tone), 'wb') as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(rate)
        output.writeframes(struct.pack('<' + 'h' * len(samples), *samples))
    active = False
    report = {'ok': False, 'target': target}
    try:
        before = call('/bbs-world-export-probe', {'op': 'prepare', 'audio': name})
        active = True
        # Dashboard may have a preserved custom binding in this runtime profile.
        assert (before['worldKey'], before['filmKey']) == (62, 64), before
        dismiss_onboarding()
        call('/wait', {'ticks': 20})
        for _ in range(5):
            if call('/health')['screen'] is None:
                break
            key('escape')
            call('/wait', {'ticks': 2})
        assert call('/health')['screen'] is None
        before = status()
        folder = Path(before['output'])
        width, height = before['windowWidth'] // 2 * 2, before['windowHeight'] // 2 * 2
        report['before'] = before

        # Cancel in warm-up: no encoder/video starts, export dimensions are restored.
        call('/bbs-world-export-probe', {'op': 'configure', 'delay': 2})
        prior = set(folder.glob('*.mp4'))
        key('f4')
        report['warmup'] = wait_for(lambda value: value['warming'])
        key('f4')
        report['cancelled'] = wait_for(lambda value: not value['exporting'])
        assert set(folder.glob('*.mp4')) == prior and not report['cancelled']['customSize'], report

        call('/bbs-world-export-probe', {'op': 'configure', 'delay': .15})
        key('f4')
        report['worldRecording'] = wait_for(lambda value: value['recording'] and value['frames'] >= 15)
        key('f4')
        report['worldStopped'] = wait_for(lambda value: not value['exporting'])
        report['worldVideo'] = inspect_movie(folder, prior, width, height)
        assert not report['worldStopped']['customSize'], report

        time.sleep(1.1)  # The original filename uses one-second timestamp precision.
        prior = set(folder.glob('*.mp4'))
        key('f6')
        report['filmStarted'] = wait_for(lambda value: value['recording'] or value['warming'])
        report['filmStopped'] = wait_for(lambda value: not value['exporting'] and not value['filmRunning'])
        report['filmVideo'] = inspect_movie(folder, prior, width, height, audio=True)
        assert not report['filmStopped']['customSize'], report

        if args.resize_window:
            time.sleep(1.1)
            prior = set(folder.glob('*.mp4'))
            call('/bbs-world-export-probe', {'op': 'configure', 'resize': True})
            key('f4')
            resized = wait_for(lambda value: value['recording'] and value['frames'] >= 15)
            assert (resized['windowWidth'], resized['windowHeight']) == (320, 180), resized
            key('f4')
            restored = wait_for(lambda value: not value['exporting'])
            assert (restored['windowWidth'], restored['windowHeight']) == (before['windowWidth'], before['windowHeight']), restored
            assert (restored['displayWidth'], restored['displayHeight']) == (before['displayWidth'], before['displayHeight']), restored
            report['resizedVideo'] = inspect_movie(folder, prior, 320, 180)
            report['restoredWindow'] = restored
        report['ok'] = True
    finally:
        if active:
            report['cleanup'] = call('/bbs-world-export-probe', {'op': 'cleanup'})
        tone.unlink(missing_ok=True)
        output = report_path('forge1122-world-export-smoke.json')
        output.write_text(json.dumps(report, indent=2), encoding='utf8')
    print('PASS: native F4/F6 world/film video export', output)


if __name__ == '__main__':
    main()
