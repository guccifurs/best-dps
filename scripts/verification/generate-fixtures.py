"""Original, deterministic inputs for the separately installed Wiki reference calculator."""
import json
import pathlib
import sys

source, output = map(pathlib.Path, sys.argv[1:3])
monsters = json.loads((source / 'cdn/json/monsters.json').read_text())
gear = json.loads((source / 'cdn/json/equipment.json').read_text())
spells = json.loads((source / 'cdn/json/spells.json').read_text())
output.mkdir(parents=True, exist_ok=True)

def item(name):
    matches = [e for e in gear if e['name'].lower() == name.lower()]
    matches = [e for e in matches if e.get('version', '').lower() not in ['uncharged', 'empty', 'broken', 'inactive', '0']]
    if not matches:
        raise ValueError(name)
    matches.sort(key=lambda e: (e.get('version','').lower() not in ('','undamaged','unpoisoned','charged'), e['id']))
    return matches[0]['id']

def fixture(monster, case, key):
    f = dict(key=key, npc=monster['id'], npcName=monster['name'], baseHp=monster['skills']['hp'], version=monster.get('version', ''), **case)
    if monster['name'].lower() == 'tormented demon':
        f['pluginVersion'] = f['version'] + ', Shielded'
        f.setdefault('encounter', {})['phase'] = 'Shielded'
    if monster['name'].lower() == 'doom of mokhaiotl':
        f.setdefault('encounter', {})['phase'] = 'Normal'
    if monster['name'].lower() == 'yama' and f['version'] == 'Normal':
        f['pluginVersion'] = 'Normal, tank uses melee/ranged'
        f.setdefault('encounter', {})['phase'] = 'Tank using melee/ranged'
    if monster['id'] in (12596, 14147):
        f['pluginVersion'] = 'Melee range'
        f.setdefault('encounter', {})['phase'] = 'Melee'
    if monster['id'] == 5914:
        f['pluginVersion'] = 'Stunned'
        f.setdefault('encounter', {})['phase'] = 'Stunned'
    if monster['name'] == 'Jhallan':
        f['pluginVersion'] = 'Clone' if monster['skills']['hp'] == 1 else 'Main opponent'
    return f

baseline = [dict(label='whip',style='MELEE',gear=[4151],prayers=['PIETY']),
            dict(label='bow',style='RANGED',gear=[861,892],prayers=['RIGOUR']),
            dict(label='fire',style='MAGIC',gear=[1387],spell='Fire Wave',prayers=['AUGURY'])]
catalog = [fixture(m, c, f'{i}:{c["label"]}') for i,m in enumerate(monsters) for c in baseline]

targets = [m for m in monsters if m['id'] in (415, 8059, 2042, 3129, 319, 6503, 7540, 7550, 7552, 11719, 11721, 11724,
           11730, 11750, 11753, 11754, 11755, 11278, 11280, 10818, 10819, 12191, 12214, 14176, 13599, 14009, 14179, 14707)]
targets += [m for m in monsters if m['name'] in ('Verzik Vitur','The Nightmare',"Phosani's Nightmare",'Ice demon',
            'The Hueycoatl','Scurrius','Akkha','Ba-Ba','Commander Zilyana','General Graardor','Cerberus',
            'The Leviathan','Vardorvis','The Whisperer','Araxxor','Duke Sucellus')]
seen = set()
targets = [m for m in targets if not ((m['id'],m['name']) in seen or seen.add((m['id'],m['name'])))]
cases = []
def add(style, name, **extra):
    cases.append(dict(label=name, style=style, gear=[item(name)], **extra))

for name in ['Abyssal whip',"Osmumten's fang",'Dragon hunter lance','Dragon hunter wand','Arclight','Emberlight',
             'Silverlight','Darklight','Keris partisan','Keris partisan of amascut','Keris partisan of breaching',
             'Keris partisan of the sun',"Inquisitor's mace",'Scythe of Vitur','Soulreaper axe','Ghrazi rapier',
             'Dragon scimitar',"Dharok's greataxe",'Dual macuahuitl',"Torag's hammers",'Sulphur blades',
             'Fang of the hound',"Dinh's bulwark",'Leaf-bladed battleaxe','Barronite mace','Brine sabre',
             'Zamorakian spear',"Sarachnis cudgel"]:
    add('MELEE',name)
for name, ammo in [('Twisted bow','Dragon arrow'),('Magic shortbow','Rune arrow'),('Dark bow','Dragon arrow'),
                   ('Scorching bow','Dragon arrow'),('Venator bow','Amethyst arrow'),('Eclipse atlatl','Atlatl dart'),
                   ('Dragon hunter crossbow','Diamond dragon bolts (e)'),('Armadyl crossbow','Ruby dragon bolts (e)'),
                   ('Zaryte crossbow','Onyx dragon bolts (e)'),('Rune crossbow','Diamond bolts (e)'),
                   ('Hunter\'s spear',None),('Dragon knife',None),('Red chinchompa',None),('Toxic blowpipe',None)]:
    add('RANGED',name, **({'dart':11230} if name=='Toxic blowpipe' else {}))
    if ammo: cases[-1]['gear'].append(item(ammo))
for name in ['Trident of the Seas','Trident of the Swamp','Sanguinesti staff',"Tumeken's shadow",'Warped sceptre',
             'Accursed sceptre','Thammaron\'s sceptre','Bone staff','Eye of ayak']:
    add('MAGIC',name)
for name,spell in [('Staff of fire','Fire Surge'),('Twinflame staff','Fire Wave'),('Harmonised nightmare staff','Fire Surge'),
                   ('Purging staff','Dark Demonbane'),('Dragon hunter wand','Fire Surge'),('Kodai wand','Ice Barrage'),
                   ('Smoke battlestaff','Fire Bolt'),("Iban's staff (u)",'Iban Blast'),('Slayer\'s staff (e)','Magic Dart')]:
    add('MAGIC',name,spell=spell)
for style,names,extra in [
    ('MELEE',["Dharok's greataxe","Dharok's helm","Dharok's platebody","Dharok's platelegs"],dict(boosts=dict(hp=-98))),
    ('MELEE',["Verac's flail","Verac's helm","Verac's brassard","Verac's plateskirt"],{}),
    ('MELEE',['Dual macuahuitl','Blood moon helm','Blood moon chestplate','Blood moon tassets'],{}),
    ('MELEE',["Inquisitor's mace","Inquisitor's great helm","Inquisitor's hauberk","Inquisitor's plateskirt"],{}),
    ('MELEE',['Toktz-xil-ak','Obsidian helmet','Obsidian platebody','Obsidian platelegs','Berserker necklace'],{}),
    ('MELEE',['Abyssal whip','Void melee helm','Elite void top','Elite void robe','Void knight gloves'],{}),
    ('RANGED',['Twisted bow','Dragon arrow','Void ranger helm','Elite void top','Elite void robe','Void knight gloves'],{}),
    ('RANGED',['Bow of faerdhinen','Crystal helm','Crystal body','Crystal legs'],{}),
    ('RANGED',["Karil's crossbow","Karil's coif","Karil's leathertop","Karil's leatherskirt",'Bolt rack','Amulet of the damned'],{}),
    ('MAGIC',["Ahrim's staff","Ahrim's hood","Ahrim's robetop","Ahrim's robeskirt",'Amulet of the damned'],dict(spell='Fire Surge')),
    ('MAGIC',['Kodai wand','Virtus mask','Virtus robe top','Virtus robe bottom'],dict(spell='Ice Barrage')),
    ('MAGIC',['Trident of the Seas','Void mage helm','Elite void top','Elite void robe','Void knight gloves'],{}),
    ('MELEE',['Soulreaper axe'],dict(buffs=dict(soulreaperStacks=5))),
    ('RANGED',['Dragon hunter crossbow','Ruby dragon bolts (e)','Salve amulet(ei)'],dict(hpPercent=25,buffs=dict(onSlayerTask=True,kandarinDiary=True))),
    ('MELEE',['Emberlight','Slayer helmet (i)'],dict(buffs=dict(onSlayerTask=True))),
    ('MAGIC',['Purging staff','Slayer helmet (i)'],dict(spell='Dark Demonbane',buffs=dict(onSlayerTask=True,markOfDarknessSpell=True))),
]:
    cases.append(dict(label=' + '.join(names),style=style,gear=[item(n) for n in names],**extra))
melee = [item(n) for n in ['Torva full helm','Torva platebody','Torva platelegs','Infernal cape','Amulet of rancour','Ferocious gloves','Primordial boots','Ultor ring']]
ranged = [item(n) for n in ['Masori mask (f)','Masori body (f)','Masori chaps (f)',"Blessed Dizana's quiver",'Necklace of anguish','Zaryte vambraces','Pegasian boots','Venator ring']]
magic = [item(n) for n in ['Ancestral hat','Ancestral robe top','Ancestral robe bottom','Imbued saradomin cape','Occult necklace','Tormented bracelet','Eternal boots','Magus ring']]
matrix=[]
for mi,m in enumerate(targets):
    for ci,c in enumerate(cases):
        for boosted in [False, True]:
            variant = dict(c, gear=c['gear'][:])
            if boosted:
                slots = {next(e['slot'] for e in gear if e['id']==i) for i in variant['gear']}
                variant['gear'] += [i for i in {'MELEE':melee,'RANGED':ranged,'MAGIC':magic}[c['style']]
                                   if next(e['slot'] for e in gear if e['id']==i) not in slots]
                variant['prayers'] = [{'MELEE':'PIETY','RANGED':'RIGOUR','MAGIC':'AUGURY'}[c['style']]]
                variant['boosts'] = dict(c.get('boosts',{}),atk=19,str=19,ranged=13,magic=10)
            matrix.append(fixture(m,variant,f'gear:{mi}:{ci}:{int(boosted)}'))
for m in targets[:8]:
    for spell in spells:
        if spell.get('max_hit',0) <= 0: continue
        weapon = {'Iban Blast':"Iban's staff (u)",'Saradomin Strike':'Saradomin staff',
                  'Claws of Guthix':'Guthix staff','Flames of Zamorak':'Zamorak staff', 'Magic Dart':"Slayer's staff (e)"}.get(spell['name'],'Staff of fire')
        case = dict(label=spell['name'],style='MAGIC',gear=[item(weapon)],spell=spell['name'],prayers=['AUGURY'])
        matrix.append(fixture(m,case,f'spell:{m["id"]}:{spell["name"]}'))
for m in [m for m in targets if 11700 <= m['id'] <= 11799 or 'cox' in m.get('attributes', [])]:
    for raid,party,path in [(1,1,0),(150,3,1),(300,8,3),(600,5,6)]:
        for c in baseline:
            variant=dict(c,encounter=dict(toaInvocationLevel=raid,partySize=party,toaPathLevel=path))
            matrix.append(fixture(m,variant,f'raid:{m["id"]}:{raid}:{party}:{path}:{c["label"]}'))
prayers = {'MELEE':['BURST_OF_STRENGTH','CLARITY_OF_THOUGHT','SUPERHUMAN_STRENGTH','IMPROVED_REFLEXES',
                    'ULTIMATE_STRENGTH','INCREDIBLE_REFLEXES','CHIVALRY','PIETY'],
           'RANGED':['SHARP_EYE','HAWK_EYE','EAGLE_EYE','DEADEYE','RIGOUR'],
           'MAGIC':['MYSTIC_WILL','MYSTIC_LORE','MYSTIC_MIGHT','MYSTIC_VIGOUR','AUGURY']}
for level in [1,5,19,20,40,41,50,58,59,60,75,80,90,99,100,110,112,118,125]:
    for base in baseline:
        for prayer in prayers[base['style']]:
            case=dict(base,prayers=[prayer],levels=dict(atk=level,str=level,ranged=level,magic=level))
            if base['style']=='MAGIC': case['spell']='Wind Strike'
            matrix.append(fixture(targets[0],case,f'prayer:{level}:{prayer}'))
for m in targets:
    for hp in [9,10,50,99]:
        for target_hp in [24,25,26,100]:
            for label, style, ids, extra in [
                ('ruby','RANGED',[item('Dragon hunter crossbow'),item('Ruby dragon bolts (e)')],dict(buffs=dict(kandarinDiary=True))),
                ('sun','MELEE',[item('Keris partisan of the sun')],{}),
                ('dharok','MELEE',[item(n) for n in ["Dharok's greataxe","Dharok's helm","Dharok's platebody","Dharok's platelegs"]],{}),
            ]:
                c=dict(label=label,style=style,gear=ids,boosts=dict(hp=hp-99),hpPercent=target_hp,**extra)
                matrix.append(fixture(m,c,f'hp:{m["id"]}:{label}:{hp}:{target_hp}'))
vorkath=next(m for m in monsters if m['id']==8059)
external=[]
for label,style,ids,prayers,dps,maxhit in [
    ('gearscape-whip-none','MELEE',[4151],[],1.07383,25),
    ('gearscape-whip-piety','MELEE',[4151],['PIETY'],1.51389,30),
    ('gearscape-bow-rigour','RANGED',[861,892],['RIGOUR'],2.67682,23),
    ('gearscape-trident-augury','MAGIC',[11905,892],['AUGURY'],0.6631,29),
]:
    external.append(fixture(vorkath,dict(label=label,style=style,gear=ids,prayers=prayers,
                             observedDps=dps,observedMax=maxhit,source='https://gearscape.net/calculators/dps'),label))
olm=next(m for m in monsters if m['id']==7552)
external.append(fixture(olm,dict(label='Bitterkoekje published scythe example',style='MELEE',
    gear=[item(n) for n in ['Scythe of Vitur','Torva full helm','Torva platebody','Torva platelegs','Infernal cape',
                          'Amulet of rancour','Ferocious gloves','Avernic treads (max)','Ultor ring']],
    prayers=['PIETY'],boosts=dict(atk=19,str=19),defenceOverride=150,
    observedDps=10.96714375,observedPrimaryMax=51,
    source='https://docs.google.com/spreadsheets/d/1wBXIlvAmqoQpu5u9XBfD4B0PW7D8owyO_CnRDiTHBKQ/edit'), 'bitterkoekje-scythe'))
for name,rows in [('catalog',catalog),('mechanics',matrix),('external',external)]:
    (output/f'{name}.jsonl').write_text(''.join(json.dumps(f)+'\n' for f in rows))
    print(name,len(rows))
