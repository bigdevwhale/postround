"""Merges story drafts from tools/content_src/<x>_cast.tsv, <x>_meta.tsv and <x>_arcs.tsv into the content files.

    PYTHONIOENCODING=utf-8 python tools/merge_content.py w s a

Rows whose id is already present are skipped, so running it twice is harmless.
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / 'tools/content_src'
DST = ROOT / 'app/src/main/assets/content'


def read(p):
    lines = [l for l in p.read_text(encoding='utf-8').split('\n') if l and not l.startswith('#')]
    return lines[0].split('\t'), [l.split('\t') for l in lines[1:]]


for x in sys.argv[1:]:
    for part, main, key in (('cast', 'cast.tsv', 0), ('meta', 'arcs_meta.tsv', 0), ('arcs', 'arcs.tsv', None)):
        src = SRC / f'{x}_{part}.tsv'
        if not src.exists():
            print('missing', src); continue
        h1, rows = read(src)
        h0, have = read(DST / main)
        if h1 != h0:
            raise SystemExit(f'{src.name}: header {h1} differs from {main} {h0}')
        if key is None:
            arcs_have = {r[0] for r in have}
            new = [r for r in rows if r[0] not in arcs_have]
        else:
            ids = {r[key] for r in have}
            new = [r for r in rows if r[key] not in ids]
        tail_ok = (DST / main).read_text(encoding='utf-8').endswith('\n')
        with open(DST / main, 'a', encoding='utf-8', newline='\n') as f:
            if not tail_ok:
                f.write('\n')
            for r in new:
                f.write('\t'.join(r) + '\n')
        print(f'{x}_{part}: +{len(new)} into {main}')
