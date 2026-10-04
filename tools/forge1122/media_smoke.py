"""Real OpenAL and FFmpeg roundtrip, driven through the isolated ai_helper client."""
import json
import subprocess
from pathlib import Path
from smoke import ROOT, call


def main():
    health = call('/health')
    assert Path(health['gameDir']).resolve() == (ROOT / 'run-forge1122').resolve(), health
    assert health['inWorld'], health
    audio = call('/bbs-media-probe', {'audio': True})
    assert audio['independentSources'] and audio['secondSurvives'] and audio['waveform'], audio
    assert abs(audio['firstOffset'] - 0.2) < 0.03 and abs(audio['secondOffset'] - 0.7) < 0.03, audio
    assert audio['alError'] == audio['glError'] == 0, audio
    recording = call('/bbs-media-probe', {'record': True})
    assert recording['active'] and recording['stopped'] and recording['success'], recording
    assert recording['frames'] == 18 and recording['pixelStoreRestored'] and recording['glError'] == 0, recording
    path = Path(recording['path'])
    assert path.is_file(), recording
    metadata = json.loads(subprocess.check_output([
        'ffprobe', '-v', 'error', '-count_frames', '-select_streams', 'v:0',
        '-show_entries', 'stream=width,height,nb_read_frames,r_frame_rate', '-of', 'json', str(path)
    ]))['streams'][0]
    assert int(metadata['nb_read_frames']) == 18, metadata
    assert (metadata['width'], metadata['height']) == (32, 24), metadata
    raw = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(path), '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'])
    frame_bytes = 32 * 24 * 3
    colors = []
    for index in (0, 6, 17):
        data = raw[index * frame_bytes:(index + 1) * frame_bytes]
        colors.append(tuple(sum(data[c::3]) / (32 * 24) for c in range(3)))
    for color, channel in zip(colors, range(3)):
        assert color[channel] > 220 and all(v < 30 for i, v in enumerate(color) if i != channel), colors
    report = {'ok': True, 'audio': audio, 'recording': recording, 'metadata': metadata, 'frameColors': colors}
    output = ROOT / 'build/reports/forge1122-media-smoke.json'
    output.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print('PASS:', output)


if __name__ == '__main__':
    main()
