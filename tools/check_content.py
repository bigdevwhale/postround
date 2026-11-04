"""Checks app/src/main/assets/content/*.tsv against docs/CONTENT.md.

    PYTHONIOENCODING=utf-8 python tools/check_content.py
"""
import collections
import pathlib
import sys

D = pathlib.Path(__file__).resolve().parent.parent / 'app/src/main/assets/content'
errors = []


def rows(name):
    lines = [l for l in (D / name).read_text(encoding='utf-8').split('\n') if l and not l.startswith('#')]
    head = lines[0].split('\t')
    out = []
    for i, l in enumerate(lines[1:], 2):
        c = l.split('\t')
        if len(c) != len(head):
            errors.append(f'{name}:{i}: {len(c)} columns, want {len(head)}')
            continue
        out.append(dict(zip(head, c)))
    return out


LOOK_KEYS = {'hair', 'cloth', 'kerchief', 'glasses', 'beard', 'moustache', 'long', 'bun', 'curls', 'pigtails', 'earrings', 'apron', 'cap'}
HAIR = {'grey', 'wood', 'dark', 'ginger', 'honey'}
CLOTH = {'cream', 'green', 'jam', 'roofB', 'navy', 'apple', 'lilac', 'oak', 'honey', 'enamel', 'autumn', 'red'}
GENDERED = ['пришёл', 'пришла', 'молодой человек', 'девушка,', 'почтальонша']

cast = {r['id']: r for r in rows('cast.tsv')}
for cid, r in cast.items():
    p = r['pick']
    if not (p in ('any', 'water', 'park', 'shop', 'inst') or (p.split(':')[0] in ('far', 'near') and p.split(':')[1] in cast)):
        errors.append(f'cast {cid}: bad pick {p}')
    for kv in filter(None, r['look'].split(',')):
        k, _, v = kv.partition('=')
        if k not in LOOK_KEYS or (k == 'hair' and v not in HAIR) or (k in ('cloth', 'kerchief') and v not in CLOTH):
            errors.append(f'cast {cid}: bad look {kv}')

meta = {r['arc']: r for r in rows('arcs_meta.tsv')}
arcs = collections.defaultdict(list)
for r in rows('arcs.tsv'):
    arcs[r['arc']].append(r)
for a, ss in arcs.items():
    if a not in meta:
        errors.append(f'arc {a}: not in arcs_meta')
    ns = [int(s['n']) for s in ss if s['n'] != 'end']
    if sorted(ns) != list(range(1, len(ns) + 1)):
        errors.append(f'arc {a}: steps {sorted(ns)}')
    if sum(1 for s in ss if s['n'] == 'end') != 1:
        errors.append(f'arc {a}: needs exactly one end row')
    for s in ss:
        for who in (s['from'], s['to']):
            if who and not who.startswith('ext:') and who != 'post' and who not in cast:
                errors.append(f'arc {a}/{s["n"]}: unknown cast {who}')
        if s['n'] == 'end':
            continue
        if s['kind'] not in ('letter', 'card', 'parcel', 'telegram', 'chess', 'pickup'):
            errors.append(f'arc {a}/{s["n"]}: kind {s["kind"]}')
        if s['kind'] == 'pickup' and (s['to'] != 'post' or '|' not in s['extra']):
            errors.append(f'arc {a}/{s["n"]}: pickup goes to post with extra "ru|en"')
        if s['next'] != 'now' and not s['next'].isdigit():
            errors.append(f'arc {a}/{s["n"]}: next {s["next"]}')
        if s['to'] == 'post' and s['kind'] != 'pickup':
            errors.append(f'arc {a}/{s["n"]}: only a pickup goes to post')
for a, m in meta.items():
    if m.get('months') and not all(x.isdigit() and 1 <= int(x) <= 12 for x in m['months'].split(',')):
        errors.append(f'arcs_meta {a}: months {m["months"]}')
    if a not in arcs:
        errors.append(f'arcs_meta {a}: no steps')

for r in rows('fillers.tsv'):
    if r['kind'] not in ('card', 'bill', 'mag', 'parcel') or r['where'] not in ('home', 'shop', 'any'):
        errors.append(f'filler {r["from_ru"]}: kind/where')
    if r['months'] and not all(x.isdigit() and 1 <= int(x) <= 12 for x in r['months'].split(',')):
        errors.append(f'filler {r["from_ru"]}: months {r["months"]}')
    if (r['kind'] == 'card') != bool(r['text_ru']):
        errors.append(f'filler {r["from_ru"]}: only cards have text')
for r in rows('telegrams.tsv'):
    if ',' in r['text_ru'] or r['text_ru'] != r['text_ru'].upper():
        errors.append(f'telegram {r["text_ru"]}: caps, no commas')

texts = []
for name in ('arcs.tsv', 'fillers.tsv', 'telegrams.tsv', 'riddles.tsv', 'paper.tsv'):
    for r in rows(name):
        for k, v in r.items():
            if k.endswith('_ru') or k == 'ru':
                texts.append((name, v))
for name, v in texts:
    for g in GENDERED:
        if g in v.lower():
            print(f'warning {name}: «{g}» — make sure it is not about the postman: {v[:60]}')

counts = {n: len(rows(n)) for n in ('names.tsv', 'cast.tsv', 'arcs_meta.tsv', 'arcs.tsv', 'fillers.tsv', 'telegrams.tsv', 'riddles.tsv', 'paper.tsv')}
print(counts)
for e in errors:
    print('ERROR', e)
sys.exit(1 if errors else 0)
