#!/usr/bin/env python3
"""Count functional lines: no comments, no blanks. Run from the repo root."""
import subprocess, collections

def strip_c_like(t):
    out, i, n = [], 0, len(t)
    while i < n:
        c, nxt = t[i], t[i+1] if i+1 < n else ''
        if c == '/' and nxt == '/':
            while i < n and t[i] != '\n': i += 1
            continue
        if c == '/' and nxt == '*':
            i += 2
            while i < n and not (t[i] == '*' and i+1 < n and t[i+1] == '/'):
                if t[i] == '\n': out.append('\n')
                i += 1
            i += 2
            continue
        if c in '"\'':
            q = c; out.append(c); i += 1
            while i < n:
                if t[i] == '\\': out.append(t[i:i+2]); i += 2; continue
                out.append(t[i])
                if t[i] == q: i += 1; break
                i += 1
            continue
        out.append(c); i += 1
    return ''.join(out)

def strip_xml(t):
    out, i, n = [], 0, len(t)
    while i < n:
        if t.startswith('<!--', i):
            i += 4
            while i < n and not t.startswith('-->', i):
                if t[i] == '\n': out.append('\n')
                i += 1
            i += 3
            continue
        out.append(t[i]); i += 1
    return ''.join(out)

def strip_sql(t):
    res = []
    for ln in strip_c_like(t).split('\n'):
        idx = ln.find('--')
        if idx != -1 and ln[:idx].count("'") % 2 == 0: ln = ln[:idx]
        res.append(ln)
    return '\n'.join(res)

def strip_hash(t):
    return '\n'.join('' if ln.strip()[:1] in ('#', '!') else ln for ln in t.split('\n'))

STRIP = {'java': strip_c_like, 'css': strip_c_like,
         'xml': strip_xml, 'xhtml': strip_xml, 'html': strip_xml,
         'sql': strip_sql, 'properties': strip_hash}

totals = collections.Counter()
for path in subprocess.check_output(['git', 'ls-files']).decode().split():
    ext = path.rsplit('.', 1)[-1].lower()
    fn = STRIP.get(ext)
    if not fn: continue
    text = open(path, encoding='utf-8', errors='replace').read()
    totals[ext] += len([l for l in fn(text).split('\n') if l.strip()])

for ext, n in totals.most_common():
    print(f'{ext:<12}{n:>7}')
print('-' * 19)
print(f'{"TOTAL":<12}{sum(totals.values()):>7}')
