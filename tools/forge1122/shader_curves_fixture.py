"""Install the tiny shader-curve test pack; does not launch or control the game."""
import argparse
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-dir', default='run-forge1122-obf')
    args = parser.parse_args()
    run = (ROOT / args.run_dir).resolve()
    if run.parent != ROOT.resolve() or run.name not in ('run-forge1122', 'run-forge1122-obf'):
        raise SystemExit('Use the isolated Forge run directory inside this workspace.')
    source = ROOT / 'src/forgeTest/resources/shader-curves-pack'
    destination = run / 'shaderpacks/BBS_Curve_QA.zip'
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(source.rglob('*')):
            if file.is_file():
                archive.write(file, file.relative_to(source).as_posix())
    print(destination)


if __name__ == '__main__':
    main()
