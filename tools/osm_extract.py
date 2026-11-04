"""Turn an Overpass JSON dump into the compact map the prototype draws.

    python tools/osm_extract.py data/petrogradka.osm.json design/spb.js

Buildings carry 'a': [street, housenumber] from their own tags or an address node inside.

Coordinates are projected to local pixels (MPP metres per pixel, origin at the
north-west corner of the bbox), clipped to the bbox and simplified.
"""
import json, math, sys
from shapely.geometry import Polygon, LineString, box, Point
from shapely.ops import polygonize, unary_union

S, N, W, E = 59.9455, 59.9585, 30.3040, 30.3260
MPP = 0.8
lat0 = (S + N) / 2
KX = math.cos(math.radians(lat0)) * 111320 / MPP
KY = 110574 / MPP
WPX, HPX = round((E - W) * KX), round((N - S) * KY)
BB = box(0, 0, WPX, HPX)


def P(lon, lat):
    return ((lon - W) * KX, (N - lat) * KY)


def main(src, dst):
    els = json.load(open(src, encoding='utf-8'))['elements']
    nodes = {e['id']: (e['lon'], e['lat']) for e in els if e['type'] == 'node'}
    ways = {e['id']: e for e in els if e['type'] == 'way'}

    def wc(w):
        return [P(*nodes[n]) for n in w['nodes'] if n in nodes]

    def closed(w):
        return len(w['nodes']) >= 4 and w['nodes'][0] == w['nodes'][-1]

    def wpoly(w):
        c = wc(w)
        return Polygon(c).buffer(0) if len(c) >= 4 else None

    def rpoly(r):
        def rings(roles):
            ls = [LineString(wc(ways[m['ref']])) for m in r['members']
                  if m['type'] == 'way' and m['ref'] in ways and m.get('role', '') in roles and len(wc(ways[m['ref']])) >= 2]
            return unary_union(list(polygonize(unary_union(ls)))) if ls else None
        o = rings(('outer', ''))
        if o is None or o.is_empty:
            return None
        i = rings(('inner',))
        return o.difference(i) if i is not None and not i.is_empty else o

    def polys(g, tol=0.4):
        if g is None:
            return []
        g = g.buffer(0).intersection(BB).simplify(tol)
        if g.is_empty:
            return []
        out = []
        for p in getattr(g, 'geoms', [g]):
            if p.geom_type != 'Polygon' or p.area < 4:
                continue
            rings = [p.exterior] + list(p.interiors)
            out.append([[round(v, 1) for xy in r.coords[:-1] for v in xy] for r in rings])
        return out

    def lines(coords, tol=0.3):
        if len(coords) < 2:
            return []
        g = LineString(coords).intersection(BB.buffer(20))
        if g.is_empty:
            return []
        out = []
        for l in getattr(g, 'geoms', [g]):
            if l.geom_type != 'LineString':
                continue
            l = l.simplify(tol)
            out.append([round(v, 1) for xy in l.coords for v in xy])
        return out

    addr_nodes = []
    for e in els:
        t = e.get('tags') or {}
        if e['type'] == 'node' and t.get('addr:housenumber') and t.get('addr:street'):
            addr_nodes.append((Point(*P(e['lon'], e['lat'])), t['addr:street'], t['addr:housenumber']))

    def addr_of(t, geom):
        if t.get('addr:housenumber') and t.get('addr:street'):
            return [t['addr:street'], t['addr:housenumber']]
        if geom is not None and not geom.is_empty:
            for pt, st, hn in addr_nodes:
                if geom.contains(pt):
                    return [st, hn]
        return None

    M = dict(w=WPX, h=HPX, mpp=MPP, bbox=[S, W, N, E], b=[], r=[], wa=[], g=[], t=[], l=[], poi=[], stop=[], metro=[])
    for e in els:
        t = e.get('tags') or {}
        if not t:
            continue
        typ = e['type']
        if typ == 'node':
            x, y = P(e['lon'], e['lat'])
            if not (0 <= x <= WPX and 0 <= y <= HPX):
                continue
            pt = [round(x, 1), round(y, 1)]
            if t.get('natural') == 'tree':
                M['t'].append(pt)
            elif t.get('highway') == 'street_lamp':
                M['l'].append(pt)
            elif t.get('railway') == 'tram_stop':
                M['stop'].append(pt + [t.get('name', '')])
            elif t.get('railway') == 'subway_entrance':
                M['metro'].append(pt + [t.get('name', '')])
            elif t.get('amenity') in ('pharmacy', 'cafe', 'school', 'library') or t.get('shop') in ('bakery', 'greengrocer'):
                M['poi'].append(pt + [t.get('amenity') or t.get('shop'), t.get('name', '')])
            continue
        geom = None
        if 'building' in t:
            geom = rpoly(e) if typ == 'relation' else (wpoly(e) if closed(e) else None)
            lv = t.get('building:levels')
            try:
                lv = float(lv) if lv else (float(t['height']) / 3.2 if 'height' in t else None)
            except ValueError:
                lv = None
            kind = t['building']
            for p in polys(geom, 0.3):
                area = Polygon(p[0][i:i + 2] for i in range(0, len(p[0]), 2)).area if len(p[0]) >= 6 else 0
                if lv is None:
                    lv = 5 if area > 600 else 2
                d = dict(p=p, lv=round(min(lv, 12), 1), k=kind, n=t.get('name', ''))
                a = addr_of(t, geom)
                if a:
                    d['a'] = a
                M['b'].append(d)
            continue
        if typ == 'way' and 'highway' in t:
            hw = t['highway']
            if hw in ('corridor', 'construction', 'proposed', 'elevator', 'platform'):
                continue
            for l in lines(wc(e)):
                r = dict(t=hw, p=l)
                if t.get('bridge') and t['bridge'] != 'no':
                    r['br'] = 1
                if t.get('tunnel') in ('building_passage', 'yes'):
                    r['tu'] = 1
                if t.get('surface'):
                    r['sf'] = t['surface']
                if t.get('name'):
                    r['n'] = t['name']
                M['r'].append(r)
            continue
        if typ == 'way' and t.get('railway') == 'tram':
            for l in lines(wc(e)):
                M['r'].append(dict(t='tram', p=l, **({'br': 1} if t.get('bridge') else {})))
            continue
        if t.get('natural') == 'water' or t.get('waterway') == 'riverbank':
            geom = rpoly(e) if typ == 'relation' else (wpoly(e) if closed(e) else None)
            for p in polys(geom):
                M['wa'].append(p)
            continue
        if typ == 'way' and t.get('waterway') in ('stream', 'ditch', 'drain'):
            for l in lines(wc(e)):
                M['r'].append(dict(t='stream', p=l))
            continue
        kind = None
        if t.get('leisure') in ('park', 'garden'):
            kind = 'park'
        elif t.get('landuse') in ('grass', 'recreation_ground'):
            kind = 'grass'
        elif t.get('natural') == 'wood':
            kind = 'wood'
        elif t.get('natural') == 'scrub':
            kind = 'scrub'
        elif t.get('natural') in ('beach', 'sand'):
            kind = 'sand'
        elif t.get('leisure') == 'playground':
            kind = 'play'
        elif t.get('leisure') == 'pitch':
            kind = 'pitch'
        if kind:
            geom = rpoly(e) if typ == 'relation' else (wpoly(e) if closed(e) else None)
            for p in polys(geom):
                M['g'].append(dict(k=kind, p=p))
    order = {'park': 0, 'grass': 1, 'wood': 2, 'scrub': 2, 'sand': 3, 'play': 4, 'pitch': 4}
    M['g'].sort(key=lambda g: order[g['k']])
    js = 'window.SPB=' + json.dumps(M, ensure_ascii=False, separators=(',', ':')) + ';\n'
    open(dst, 'w', encoding='utf-8').write(js)
    print(f'{WPX}x{HPX}px  buildings {len(M["b"])}  roads {len(M["r"])}  water {len(M["wa"])}  green {len(M["g"])}  '
          f'trees {len(M["t"])}  lamps {len(M["l"])}  poi {len(M["poi"])}  -> {len(js)//1024} KB')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
