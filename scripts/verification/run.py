"""Opt-in repeatable verification using separate, pinned reference checkouts."""
import argparse
import json
import pathlib
import subprocess
import sys

parser=argparse.ArgumentParser()
parser.add_argument('--wiki-source',type=pathlib.Path,required=True)
parser.add_argument('--runner-deps',type=pathlib.Path,required=True)
parser.add_argument('--insight-source',type=pathlib.Path)
args=parser.parse_args()
root=pathlib.Path(__file__).resolve().parents[2]
output=root/'build/verification'
output.mkdir(parents=True,exist_ok=True)
def run(command,**kwargs):
    subprocess.run([str(x) for x in command],cwd=root,check=True,**kwargs)
def pin(source,revision):
    actual=subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'],text=True).strip()
    if actual!=revision: raise ValueError(f'Expected reference {revision}, got {actual}')
pin(args.wiki_source,'89c3e25b344aea90d0189746e4b5f73dde0f0383')
run([sys.executable,'scripts/verification/generate-fixtures.py',args.wiki_source,output])
run(['node','scripts/verification/build-oracle.cjs',args.wiki_source,args.runner_deps,output/'wiki-oracle.mjs'])
for suite in ['catalog','mechanics','external']:
    with (output/f'wiki-{suite}.jsonl').open('w') as stream:
        run(['node',output/'wiki-oracle.mjs',output/f'{suite}.jsonl'],stdout=stream)
    run(['./gradlew','referenceSnapshot',f'-Pfixtures={output}/{suite}.jsonl',f'-Psnapshot={output}/plugin-{suite}.jsonl','--console=plain'])
    run([sys.executable,'scripts/verification/compare.py',output,suite])
external={r['key']:r for r in map(json.loads,(output/'plugin-external.jsonl').read_text().splitlines())}
for f in map(json.loads,(output/'external.jsonl').read_text().splitlines()):
    actual=external[f['key']]
    if abs(actual['dps']-f['observedDps'])>0.000005:
        raise AssertionError((f['key'],f['observedDps'],actual['dps']))
    if 'observedMax' in f and actual['max']!=f['observedMax']:
        raise AssertionError((f['key'],'max',f['observedMax'],actual['max']))
print('All five independent GearScape/Bitterkoekje screen values match their displayed precision.')
if args.insight_source:
    pin(args.insight_source,'98e49fd2c18534f376b1004a8adf1b3aec1c4813')
    classes=output/'insight-classes'
    classes.mkdir(exist_ok=True)
    package=args.insight_source/'src/main/java/com/combatinsight/calculation'
    run(['javac','--release','11','-d',classes,*[package/(n+'.java') for n in ['CombatPrediction','DamageRoll','TargetProfile','AttackType']]])
    run(['./gradlew','externalFormulaReference',f'-PinsightClasses={classes}','--console=plain'])
run([sys.executable,'scripts/verification/classify-differences.py',output])
print('Raw differences remain in build/verification/*-errors.json; classified exceptions are not numerical matches.')
