"""Report reviewed reference defects separately; never relabel them numerical matches."""
import collections
import json
import pathlib
import sys

root=pathlib.Path(__file__).resolve().parents[2]
directory=pathlib.Path(sys.argv[1])
exceptions=json.loads((root/'docs/reference-source-exceptions.json').read_text())['exceptions']
report={}
unclassified=[]
for suite in ['catalog','mechanics']:
    groups=collections.Counter()
    for f,w,p in json.loads((directory/f'{suite}-errors.json').read_text()):
        code=None
        if 'error' not in w and 'error' not in p:
            if suite=='catalog' and f['label'] in ('whip','bow','fire'):
                matches=[x for x in exceptions if f['npc'] in x['npcIds'] and f['style'] in x['styles']]
                if len(matches)==1: code=matches[0]['code']
            elif suite=='mechanics':
                if f['npc'] in (11753,11754,11756,11757) and f['style']=='MELEE' and p.get('dps')==0:
                    code='warden_active_melee_protection'
                elif f['npc']==14179 and f['style'] in ('MELEE','RANGED'):
                    code='void_flare_guarantee_style_restriction'
                elif f.get('spell')=='Magic Dart' and p.get('dps',0)>w.get('dps',0):
                    code='wiki_dynamic_magic_dart_zero_uplift'
                elif f['label'].startswith("Karil's crossbow +") and p.get('dps',0)>w.get('dps',0):
                    code='wiki_karil_amulet_name_case'
                elif f['label']=='Eclipse atlatl' and p.get('dps',0)<w.get('dps',0):
                    code='wiki_atlatl_quiver_passive'
        if code: groups[code]+=1
        else: unclassified.append(dict(suite=suite,fixture=f,wiki=w,plugin=p))
    report[suite]=dict(groups)
report['unclassified']=len(unclassified)
(directory/'difference-classification.json').write_text(json.dumps(report,indent=2)+'\n')
(directory/'unclassified.json').write_text(json.dumps(unclassified,indent=2)+'\n')
print(json.dumps(report,indent=2))
if unclassified: raise SystemExit(1)
