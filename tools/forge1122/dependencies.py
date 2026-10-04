"""Read-only source dependency inventory for incremental Forge porting.

Accepts fully qualified BBS roots; resolves imports and package-local symbols.
It is a planning aid, not a proof of compilation or complete dependency coverage.
"""
from pathlib import Path
import re
import sys
from collections import Counter

ROOT = Path(__file__).resolve().parents[2]
sources = {}
for source_root in ('src/main/java', 'src/client/java', 'src/forge/java'):
    for path in (ROOT / source_root).rglob('*.java'):
        sources['.'.join(path.relative_to(ROOT / source_root).with_suffix('').parts)] = path


def closure(roots):
    pending = list(roots)
    visited = set()
    imports = set()
    while pending:
        name = pending.pop()
        if name in visited or name not in sources:
            continue
        visited.add(name)
        text = sources[name].read_text(encoding='utf-8')
        package = name.rsplit('.', 1)[0]
        # Javadoc links and example strings are not compilation dependencies.
        code = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', '', text, flags=re.S)
        for symbol in set(re.findall(r'\b[A-Z][A-Za-z0-9_]*\b', code)):
            candidate = package + '.' + symbol
            if candidate in sources:
                pending.append(candidate)
        for imported in re.findall(r'import\s+(?:static\s+)?([\w.*]+);', text):
            if imported.endswith('.*'):
                prefix = imported[:-1]
                pending.extend(c for c in sources if c.startswith(prefix) and '.' not in c[len(prefix):])
            else:
                candidate = imported
                while candidate and candidate not in sources:
                    candidate = candidate.rpartition('.')[0]
                if candidate:
                    pending.append(candidate)
                else:
                    imports.add(imported)
    return visited, imports


if __name__ == '__main__':
    names, external = closure(sys.argv[1:])
    print('Classes:', len(names))
    print('Packages:', Counter('.'.join(n.split('.')[:4]) for n in names))
    print('Platform imports:')
    for name in sorted(external):
        if not name.startswith(('java.', 'javax.', 'org.joml.', 'com.google.')):
            print(name)
