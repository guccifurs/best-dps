"""Compare aligned adapter outputs without silently accepting missing cases or errors."""
import collections
import json
import hashlib
import base64
import pathlib
import sys

directory = pathlib.Path(sys.argv[1])
suite = sys.argv[2]
def read(name):
    rows = [json.loads(line) for line in (directory / name).read_text().splitlines()]
    result = {row['key']: row for row in rows}
    if len(result) != len(rows):
        raise ValueError('Duplicate fixture key: ' + name)
    return result
fixtures = read(suite + '.jsonl')
fixture_hashes = {json.loads(line)['key']: base64.b64encode(hashlib.sha256(line.encode()).digest()).decode()
                  for line in (directory/(suite+'.jsonl')).read_text().splitlines()}
wiki = read('wiki-' + suite + '.jsonl')
plugin = read('plugin-' + suite + '.jsonl')
if fixtures.keys() != wiki.keys() or fixtures.keys() != plugin.keys():
    raise ValueError('Incomplete or unexpected adapter output')
counts = collections.Counter()
errors = []
diagnostics = collections.Counter()
for key, fixture in fixtures.items():
    expected, actual = wiki[key], plugin[key]
    if expected.get('inputHash') != fixture_hashes[key] or expected.get('inputHash') != actual.get('inputHash'):
        raise ValueError('Adapters used different fixture inputs: ' + key)
    if actual.get('error') or expected.get('error'):
        counts['adapter_error'] += 1
        errors.append((fixture, expected, actual))
    elif actual.get('blocked'):
        counts['unsupported_explicitly_blocked'] += 1
    elif actual.get('rejected'):
        counts['rejected_zero' if expected['dps'] == 0 else 'rejected_nonzero'] += 1
        if expected['dps']: errors.append((fixture, expected, actual))
    elif abs(expected['dps'] - actual['dps']) > 1e-7:
        counts['dps_difference'] += 1
        errors.append((fixture, expected, actual))
    else:
        counts['dps_match'] += 1
        for field in ('accuracy','expected','max','speed','attackRoll','defenceRoll'):
            if abs(expected[field] - actual[field]) > 1e-7:
                diagnostics[field] += 1
(directory / (suite + '-errors.json')).write_text(json.dumps(errors,indent=2)+'\n')
summary = dict(fixtures=len(fixtures),npc_variants=len({(f['npc'],f['version'],f.get('npcName'),f.get('baseHp')) for f in fixtures.values()}),
               results=dict(counts),matching_dps_diagnostic_differences=dict(diagnostics))
(directory / (suite + '-summary.json')).write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary,indent=2))
groups = collections.Counter((f['label'],f['style']) for f,w,p in errors)
print('Difference groups:', groups)
