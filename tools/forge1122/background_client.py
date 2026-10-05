"""Launch the existing ai_helper client on a separate, never activated Win32 desktop.

The regular ai_helper HTTP and framebuffer screenshot routes remain available.
No SwitchDesktop, SetForegroundWindow, mouse input, or visible launcher is used.
OpenGL support on a non-input desktop depends on the installed Windows GPU driver;
only a successful real launch and framebuffer capture can validate it.
"""
import argparse
import ctypes
from ctypes import wintypes
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT.parent / 'ai_helper/tools/mc.py'


class STARTUPINFOW(ctypes.Structure):
    _fields_ = [('cb', wintypes.DWORD), ('lpReserved', wintypes.LPWSTR),
                ('lpDesktop', wintypes.LPWSTR), ('lpTitle', wintypes.LPWSTR),
                ('dwX', wintypes.DWORD), ('dwY', wintypes.DWORD),
                ('dwXSize', wintypes.DWORD), ('dwYSize', wintypes.DWORD),
                ('dwXCountChars', wintypes.DWORD), ('dwYCountChars', wintypes.DWORD),
                ('dwFillAttribute', wintypes.DWORD), ('dwFlags', wintypes.DWORD),
                ('wShowWindow', wintypes.WORD), ('cbReserved2', wintypes.WORD),
                ('lpReserved2', ctypes.POINTER(wintypes.BYTE)),
                ('hStdInput', wintypes.HANDLE), ('hStdOutput', wintypes.HANDLE),
                ('hStdError', wintypes.HANDLE)]


class PROCESS_INFORMATION(ctypes.Structure):
    _fields_ = [('hProcess', wintypes.HANDLE), ('hThread', wintypes.HANDLE),
                ('dwProcessId', wintypes.DWORD), ('dwThreadId', wintypes.DWORD)]


def api():
    user = ctypes.WinDLL('user32', use_last_error=True)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    user.CreateDesktopW.argtypes = [wintypes.LPCWSTR, wintypes.LPCWSTR,
                                   ctypes.c_void_p, wintypes.DWORD,
                                   wintypes.DWORD, ctypes.c_void_p]
    user.CreateDesktopW.restype = wintypes.HANDLE
    user.CloseDesktop.argtypes = [wintypes.HANDLE]
    user.CloseDesktop.restype = wintypes.BOOL
    kernel.CreateProcessW.argtypes = [wintypes.LPCWSTR, wintypes.LPWSTR,
                                     ctypes.c_void_p, ctypes.c_void_p,
                                     wintypes.BOOL, wintypes.DWORD,
                                     ctypes.c_void_p, wintypes.LPCWSTR,
                                     ctypes.POINTER(STARTUPINFOW),
                                     ctypes.POINTER(PROCESS_INFORMATION)]
    kernel.CreateProcessW.restype = wintypes.BOOL
    kernel.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    kernel.WaitForSingleObject.restype = wintypes.DWORD
    kernel.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
    kernel.GetExitCodeProcess.restype = wintypes.BOOL
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel.CloseHandle.restype = wintypes.BOOL
    return user, kernel


def launch(args):
    if os.name != 'nt':
        raise SystemExit('This launcher requires Windows.')
    if not HELPER.is_file():
        raise SystemExit(f'ai_helper was not found: {HELPER}')
    if args.isolated:
        if os.environ.get('AIH_PORT', '25615') != '25615':
            raise SystemExit('--isolated requires AIH_PORT=25615, never the user client port.')
        os.environ['AIH_PORT'] = '25615'
    else:
        os.environ.setdefault('AIH_PORT', '25612')
    state = ROOT / '.aihelper'
    runner = HELPER
    if args.isolated:
        state = state / 'clients/25615'
        runner = ROOT / 'tools/forge1122/isolated_client.py'
    gradle = args.gradle_args.split()
    if args.isolated and '-PisolatedQa' not in gradle:
        gradle.append('-PisolatedQa')
    if '--daemon' in gradle:
        raise SystemExit('--daemon would reuse a process on another desktop; use --no-daemon.')
    if '--no-daemon' not in gradle:
        gradle.append('--no-daemon')
    desktop_name = f'BBS_FS_QA_{os.getpid()}_{time.time_ns()}'
    command = [sys.executable, '-X', 'utf8', '-u', str(runner), 'launch', '--project', str(ROOT),
               '--timeout', str(args.timeout), '--gradle-args=' + ' '.join(gradle)]
    plan = {'desktop': 'winsta0\\' + desktop_name, 'command': command,
            'helperPort': os.environ.get('AIH_PORT', '25612'),
            'log': str(state / 'background-client-launch.log'),
            'gameLog': str(state / 'runClient.log'),
            'activated': False, 'openGLVerified': False}
    if args.dry_run:
        print(json.dumps(plan, ensure_ascii=False, indent=2))
        return 0
    os.environ.setdefault('AIH_PORT', '25612')
    spec = importlib.util.spec_from_file_location('bbs_ai_helper_launch', HELPER)
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    health = helper.call('/health', timeout=2)
    if health.get('ok'):
        raise SystemExit('A client already owns the helper port. Stop that client explicitly before launching a separate desktop.')
    user, kernel = api()
    # Read/write/create/enumerate only. DESKTOP_SWITCHDESKTOP and HOOKCONTROL are
    # deliberately not requested; this process never makes the desktop visible.
    access = 0x0001 | 0x0002 | 0x0040 | 0x0080
    desktop = user.CreateDesktopW(desktop_name, None, None, 0, access, None)
    if not desktop:
        raise ctypes.WinError(ctypes.get_last_error())
    process = PROCESS_INFORMATION()
    log_path = Path(plan['log'])
    log_path.parent.mkdir(parents=True, exist_ok=True)
    try:
        import msvcrt
        with log_path.open('wb') as log, open(os.devnull, 'rb') as null:
            out_handle = msvcrt.get_osfhandle(log.fileno())
            in_handle = msvcrt.get_osfhandle(null.fileno())
            os.set_handle_inheritable(out_handle, True)
            os.set_handle_inheritable(in_handle, True)
            startup = STARTUPINFOW()
            startup.cb = ctypes.sizeof(startup)
            startup.lpDesktop = plan['desktop']
            startup.dwFlags = 0x00000001 | 0x00000100  # STARTF_USESHOWWINDOW | STARTF_USESTDHANDLES
            startup.wShowWindow = 0  # SW_HIDE: only the launcher, never the game's desktop switch
            startup.hStdInput = in_handle
            startup.hStdOutput = startup.hStdError = out_handle
            line = ctypes.create_unicode_buffer(subprocess.list2cmdline(command))
            # DETACHED_PROCESS avoids a console in the user's active desktop.
            # Fresh --no-daemon Gradle descendants inherit this new desktop.
            if not kernel.CreateProcessW(sys.executable, line, None, None, True,
                                         0x00000008 | 0x00000200, None, str(ROOT),
                                         ctypes.byref(startup), ctypes.byref(process)):
                raise ctypes.WinError(ctypes.get_last_error())
            os.set_handle_inheritable(out_handle, False)
            os.set_handle_inheritable(in_handle, False)
            plan['launcherPid'] = process.dwProcessId
            (state / 'background-client.json').write_text(
                json.dumps(plan, ensure_ascii=False, indent=2), encoding='utf-8')
            print(json.dumps(plan, ensure_ascii=False), flush=True)
            # Retain the desktop while ai_helper waits for readiness. Once Java
            # owns its window, its own desktop reference retains the object.
            while True:
                status = kernel.WaitForSingleObject(process.hProcess, 500)
                if status == 0:
                    break
                if status != 0x00000102:
                    raise ctypes.WinError(ctypes.get_last_error())
            exit_code = wintypes.DWORD()
            if not kernel.GetExitCodeProcess(process.hProcess, ctypes.byref(exit_code)):
                raise ctypes.WinError(ctypes.get_last_error())
        print(log_path.read_text(encoding='utf-8', errors='replace'), end='')
        return exit_code.value
    finally:
        if process.hThread:
            kernel.CloseHandle(process.hThread)
        if process.hProcess:
            kernel.CloseHandle(process.hProcess)
        user.CloseDesktop(desktop)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--gradle-args', default='-PwithOptiFine --no-daemon')
    parser.add_argument('--timeout', type=int, default=300)
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--isolated', action='store_true', help='Use separate game directory, port and PID/log files alongside the user client')
    return launch(parser.parse_args())


if __name__ == '__main__':
    raise SystemExit(main())
