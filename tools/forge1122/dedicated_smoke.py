"""Load the actual release mod on a local, disposable, headless Forge server."""
import json
import argparse
import gzip
import os
from pathlib import Path
import secrets
import socket
import struct
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / 'run-forge1122-server'
LOG = ROOT / '.aihelper/dedicated-server.log'
MARKER = '# BBS isolated ai_server_test server'


def read_exact(stream, count):
    result = b''
    while len(result) < count:
        chunk = stream.recv(count - len(result))
        if not chunk:
            raise EOFError('Server closed RCON')
        result += chunk
    return result


def packet(stream, kind, body, request=1):
    payload = struct.pack('<ii', request, kind) + body.encode('utf-8') + b'\0\0'
    stream.sendall(struct.pack('<i', len(payload)) + payload)
    size = struct.unpack('<i', read_exact(stream, 4))[0]
    response = read_exact(stream, size)
    identifier, response_kind = struct.unpack('<ii', response[:8])
    assert identifier == request, (identifier, response_kind)
    return response[8:-2].decode('utf-8', errors='replace')


def main():
    argparse.ArgumentParser(description=__doc__).parse_args()
    SERVER.mkdir(exist_ok=True)
    config = SERVER / 'server.properties'
    marker = SERVER / '.bbs-qa-server'
    if config.exists() and not marker.exists() and not config.read_text(encoding='utf-8').startswith(MARKER):
        raise RuntimeError('Refusing to replace a server.properties outside this QA fixture')
    marker.write_text(MARKER, encoding='utf-8')
    password = secrets.token_hex(24)
    config.write_text(MARKER + '\n' + '\n'.join([
        'server-ip=127.0.0.1', 'server-port=25613', 'online-mode=false',
        'enable-rcon=true', 'rcon.port=25614', 'rcon.password=' + password,
        'level-name=ai_server_test', 'level-type=FLAT', 'max-players=1',
        'view-distance=3', 'spawn-protection=0', 'max-tick-time=120000',
        'spawn-monsters=false', 'spawn-animals=false', 'gamemode=1',
    ]) + '\n', encoding='utf-8')
    (SERVER / 'eula.txt').write_text('eula=true\n', encoding='utf-8')
    LOG.parent.mkdir(exist_ok=True)
    flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
    environment = os.environ.copy()
    local_jdk = ROOT / '.aihelper/jdk17/zulu17.66.19-ca-jdk17.0.19-win_x64'
    if not environment.get('JAVA_HOME') and (local_jdk / 'bin/java.exe').is_file():
        environment['JAVA_HOME'] = str(local_jdk)
    args = [str(ROOT / 'gradlew.bat'), 'runObfServer', '--no-daemon', '--console=plain']
    report = {'ok': False, 'directory': str(SERVER), 'commands': {}}
    connection = None
    with LOG.open('w', encoding='utf-8') as output:
        process = subprocess.Popen(args, cwd=ROOT, env=environment, stdin=subprocess.PIPE, stdout=output,
                                   stderr=subprocess.STDOUT, creationflags=flags)
        try:
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError('Server exited during startup; inspect ' + str(LOG))
                try:
                    connection = socket.create_connection(('127.0.0.1', 25614), timeout=1)
                    connection.settimeout(15)
                    packet(connection, 3, password)
                    break
                except (OSError, EOFError):
                    if connection:
                        connection.close()
                    connection = None
                    time.sleep(1)
            if connection is None:
                raise TimeoutError('Dedicated server did not start RCON; inspect ' + str(LOG))
            packet(connection, 2, 'save-all')
            # Vanilla may choose a flat-world spawn far from zero. Test inside its loaded chunks.
            level = gzip.decompress((SERVER / 'ai_server_test/level.dat').read_bytes())
            spawn = []
            for name in (b'SpawnX', b'SpawnZ'):
                tag = b'\x03\x00\x06' + name
                assert level.count(tag) == 1
                offset = level.index(tag) + len(tag)
                spawn.append(struct.unpack('>i', level[offset:offset + 4])[0])
            x, z = spawn
            # Clear the two disposable fixture positions first: a repeated run must verify
            # fresh registration/placement, not merely find blocks saved by an older JAR.
            commands = ['list', 'help bbs', 'gamerule bbsEditing',
                        f'setblock {x} 10 {z} air', f'testforblock {x} 10 {z} air',
                        f'setblock {x} 10 {z} bbs:model', f'testforblock {x} 10 {z} bbs:model',
                        f'setblock {x + 1} 10 {z} air', f'testforblock {x + 1} 10 {z} air',
                        f'setblock {x + 1} 10 {z} bbs:chroma_green', f'testforblock {x + 1} 10 {z} bbs:chroma_green',
                        'save-all']
            for command in commands:
                response = packet(connection, 2, command)
                report['commands'][command] = response
                assert 'Unknown command' not in response and 'not a valid block' not in response, (command, response)
                if command.startswith('testforblock'):
                    assert 'Successfully found' in response, (command, response)
            packet(connection, 2, 'stop')
            connection.close()
            connection = None
            assert process.wait(timeout=45) == 0
            log = LOG.read_text(encoding='utf-8', errors='replace')
            assert 'Done (' in log and 'Stopping server' in log, 'Missing full server lifecycle'
            assert 'NoClassDefFoundError' not in log and 'Exception in server tick loop' not in log, 'Server class-loading failure'
            report['ok'] = True
            report['log'] = str(LOG)
        finally:
            if connection:
                try:
                    packet(connection, 2, 'stop')
                except (OSError, EOFError):
                    pass
                connection.close()
            if process.poll() is None:
                try:
                    process.stdin.write(b'stop\n')
                    process.stdin.flush()
                    process.wait(timeout=30)
                except (OSError, subprocess.TimeoutExpired):
                    if os.name == 'nt':
                        subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                                       creationflags=flags, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                    else:
                        process.kill()
            (ROOT / 'build/reports/forge1122-dedicated-smoke.json').write_text(
                json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')
    print('PASS: release Forge dedicated server, native blocks and saved world')


if __name__ == '__main__':
    main()
