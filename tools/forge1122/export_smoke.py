"""Export the open disposable film through the actual preview Record button.

Requires OriginalDashboardProbe's read-only film snapshot. Does not change settings,
create a substitute screen, or start VideoRecorder directly. The movie is retained.
"""
import argparse
import json
import shutil
import subprocess
import time
from fractions import Fraction
from pathlib import Path

from dashboard_smoke import click, state
from smoke import ROOT, call


def executable(name):
    found = shutil.which(name)
    fallback = Path('C:/ffmpeg/bin') / (name + '.exe')
    assert found or fallback.is_file(), 'Missing ' + name
    return found or str(fallback)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--film', default='dashboard_qa_1791140179212834100')
    parser.add_argument('--width', type=int, default=1280)
    parser.add_argument('--height', type=int, default=720)
    args = parser.parse_args()
    ffprobe, ffmpeg = executable('ffprobe'), executable('ffmpeg')
    health, before = call('/health'), state()
    assert Path(health['gameDir']).resolve() == (ROOT / 'run-forge1122').resolve(), health
    assert health['inWorld'] and before['menu'] == 'UIDashboard', before
    assert before['selectedPanel'] == 'UIFilmPanel' and before['dataId'] == args.film, before
    assert args.film.startswith('dashboard_qa_'), 'Only the disposable Dashboard QA film is supported'
    film = before['film']
    assert film['duration'] > 0 and not film['running'] and not film['exporting'], film
    assert not before['overlays'], before
    folder = Path(film['videoFolder'])
    previous = set(folder.iterdir()) if folder.exists() else set()
    snapshots = []
    active = False
    report = {'ok': False, 'film': args.film, 'before': before, 'snapshots': snapshots}
    output = ROOT / 'build/reports/forge1122-export-smoke.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        click(film['exportButton'])
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            current = state()
            assert current['glError'] == 0, current
            current_film = current['film']
            snapshots.append(current_film)
            assert not current_film.get('failure'), current_film
            active |= current_film['exporting'] or current_film['recording']
            if active and not current_film['exporting'] and not current_film['recording']:
                break
            assert not current['overlays'], current
            time.sleep(0.2)
        else:
            raise AssertionError({'exportTimedOut': state()})
        recorded = [s for s in snapshots if s['recording'] and s['frames'] > 0]
        assert recorded, {'noRecordedFrames': snapshots}
        assert all((s['videoWidth'], s['videoHeight']) == (args.width, args.height) for s in recorded), recorded
        final = state()
        final_film = final['film']
        assert not final_film['running'] and not final_film['exporting'], final
        assert final_film['frames'] >= max(s['frames'] for s in recorded), final
        assert (final_film['displayWidth'], final_film['displayHeight']) == (film['displayWidth'], film['displayHeight']), final
        assert (final_film['videoWidth'], final_film['videoHeight']) == (film['videoWidth'], film['videoHeight']), final
        assert final_film['exportButton']['enabled'] and final['glError'] == 0, final
        movies = [p for p in folder.iterdir() if p not in previous and p.suffix.lower() in ('.mp4', '.mkv', '.mov', '.webm', '.avi')]
        assert len(movies) == 1 and movies[0].stat().st_size > 0, movies
        movie = movies[0]
        probe = json.loads(subprocess.check_output([
            ffprobe, '-v', 'error', '-count_frames', '-select_streams', 'v:0',
            '-show_entries', 'stream=codec_name,width,height,avg_frame_rate,nb_read_frames,duration:format=duration',
            '-of', 'json', str(movie)], text=True, encoding='utf-8', timeout=60))
        stream = probe['streams'][0]
        assert (stream['width'], stream['height']) == (args.width, args.height), probe
        frame_rate = float(Fraction(stream['avg_frame_rate']))
        duration = float(probe['format']['duration'])
        assert frame_rate > 0 and int(stream['nb_read_frames']) > 1, probe
        # The default fixture is one unlooped Idle clip; fixed-step export must span it.
        expected_seconds = film['duration'] / 20
        assert abs(duration - expected_seconds) <= max(0.15, 2 / frame_rate), probe
        frame = output.parent / ('original-export-frame-' + str(time.time_ns()) + '.png')
        subprocess.run([ffmpeg, '-v', 'error', '-ss', str(duration / 2), '-i', str(movie),
                        '-frames:v', '1', '-y', str(frame)], check=True, timeout=60)
        assert frame.is_file() and frame.stat().st_size > 0, frame
        call('/screenshot', {'name': 'original-export-editor-restored-verified'})
        report.update(ok=True, movie=str(movie), decodedFrame=str(frame), ffprobe=probe, after=final)
    except Exception as error:
        report['failure'] = repr(error)
        try:
            current = state()
            report['failureSnapshot'] = current
            if current.get('film', {}).get('exporting'):
                call('/key', {'key': 'escape'})
        except Exception as cleanup_error:
            report['cleanupFailure'] = repr(cleanup_error)
        raise
    finally:
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
