"""Actual structure save UI/network roundtrip, without world writes or OS input."""
import json
from smoke import call, ROOT


def main():
    opened = call('/bbs-extended-forms-probe', {'wand': 'open'})
    try:
        assert opened['reshaping'], opened
        call('/wait', {'ticks': 10})
        call('/screenshot', {'name': 'structure-wand-save'})
        confirmed = call('/bbs-extended-forms-probe', {'wand': 'confirm'})
        assert confirmed['confirmed'], confirmed
        call('/wait', {'ticks': 20})
        saved = call('/bbs-extended-forms-probe', {'wand': 'poll'})
        for field in ('fileExists', 'replyInvalidated', 'recentAdded', 'loaded', 'sameSize', 'sameBlocks', 'listed'):
            assert saved.get(field), (field, saved)
        report = {'ok': True, 'opened': opened, 'saved': saved,
                  'checks': ['selection push/move', 'actual save dialog and preview',
                             'client/server save packet and reply', 'compressed template roundtrip',
                             'structure picker list', 'recent form only after reply']}
        path = ROOT / 'build/reports/forge1122-structure-wand-smoke.json'
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print('PASS:', path)
    finally:
        call('/bbs-extended-forms-probe', {'wand': 'cleanup'})


if __name__ == '__main__':
    main()
