"""Record a native note-block sound through Minecraft's sound engine into the exported AAC."""
import array
import json
import math
from pathlib import Path
import subprocess
from smoke import call
from qa_environment import checked_client, report_path
from dashboard_smoke import dismiss_onboarding
from world_export_smoke import key, wait_for, inspect_movie
from export_smoke import executable


def rms(samples):
    return math.sqrt(sum(value * value for value in samples) / max(1, len(samples)))


def main():
    report = {'ok': False, 'target': checked_client()}
    try:
        before = call('/bbs-world-export-probe', {'op': 'prepare'})
        dismiss_onboarding()
        call('/wait', {'ticks': 10})
        for _ in range(5):
            if call('/health')['screen'] is None:
                break
            key('escape')
            call('/wait', {'ticks': 2})
        assert call('/health')['screen'] is None
        call('/bbs-world-export-probe', {'op': 'configure', 'captureSounds': True, 'mute': True})
        folder = Path(before['output'])
        previous = set(folder.glob('*.mp4'))
        key('f4')
        report['started'] = wait_for(lambda state: state['recording'] and state['captureActive'] and state['frames'] >= 9)
        report['nativeSound'] = call('/bbs-world-export-probe', {'op': 'native_sound'})
        sounds = [sound for sound in report['nativeSound']['capturedSounds'] if 'note/pling.ogg' in sound['resource']]
        assert len(sounds) == 1, report['nativeSound']
        note = sounds[0]
        wait_for(lambda state: state['recording'] and state['frames'] >= note['frame'] + 60)
        key('f4')
        report['stopped'] = wait_for(lambda state: not state['exporting'])
        assert not report['stopped']['captureActive'] and not report['stopped']['customSize'], report['stopped']
        report['video'] = inspect_movie(folder, previous, before['windowWidth'] // 2 * 2, before['windowHeight'] // 2 * 2)
        streams = report['video']['metadata']['streams']
        audio = next((stream for stream in streams if stream['codec_type'] == 'audio'), None)
        assert audio is not None and audio['codec_name'] == 'aac', streams
        rate = 48000
        pcm = array.array('f')
        pcm.frombytes(subprocess.check_output([
            executable('ffmpeg'), '-v', 'error', '-i', report['video']['movie'],
            '-vn', '-ac', '1', '-ar', str(rate), '-f', 'f32le', '-'
        ], timeout=30))
        onset = note['frame'] / 30
        preceding = pcm[:max(0, int((onset - .05) * rate))]
        sound = pcm[int((onset + .02) * rate):int((onset + .35) * rate)]
        before_rms, sound_rms = rms(preceding), rms(sound)
        assert sound_rms > .003 and sound_rms > before_rms * 3, (before_rms, sound_rms, note)
        report['audio'] = {'stream': audio, 'nativeResource': note['resource'], 'recordedFrame': note['frame'],
                           'onsetSeconds': onset, 'precedingRms': before_rms, 'noteRms': sound_rms,
                           'decodedSeconds': len(pcm) / rate}
        report['ok'] = True
    finally:
        report['cleanup'] = call('/bbs-world-export-probe', {'op': 'cleanup'})
        output = report_path('forge1122-native-sound-export-smoke.json')
        output.write_text(json.dumps(report, indent=2), encoding='utf8')
    print('PASS: native SoundHandler -> capture -> mix -> AAC', output)


if __name__ == '__main__':
    main()
