// Original test adapter. The separately cloned GPL Wiki calculator is never shipped with the plugin.
import fs from 'node:fs';
import readline from 'node:readline';
import { createHash } from 'node:crypto';
import PlayerVsNPCCalc from '@/lib/PlayerVsNPCCalc';
import { getMonsters, INITIAL_MONSTER_INPUTS } from '@/lib/Monsters';
import { scaleMonster } from '@/lib/MonsterScaling';
import { calculateEquipmentBonusesFromGear } from '@/lib/Equipment';
import { getCombatStylesForCategory } from '@/utils';
import { Prayer } from '@/enums/Prayer';
import equipment from '@data/equipment.json';
import spells from '@data/spells.json';

const monsters = getMonsters();
const byId = new Map(equipment.map(e => [e.id, e]));
const slots = ['head', 'cape', 'neck', 'ammo', 'weapon', 'body', 'shield', 'legs', 'hands', 'feet', 'ring'];
for await (const line of readline.createInterface({ input: fs.createReadStream(process.argv[2]) })) {
  const f = JSON.parse(line);
  const inputHash = createHash('sha256').update(line).digest('base64');
  try {
    const base = monsters.find(m => m.id === f.npc && (m.version || '') === (f.version || '')
      && (!f.npcName || m.name === f.npcName) && (f.baseHp === undefined || m.skills.hp === f.baseHp));
    if (!base) throw new Error(`Unknown target ${f.npc}/${f.version}`);
    let monster: any = { ...base, inputs: { ...INITIAL_MONSTER_INPUTS, ...f.encounter } };
    // Current HP is a percentage of fully scaled raid HP, not the raw bestiary HP.
    monster.inputs.monsterCurrentHp = Math.trunc(scaleMonster(monster).skills.hp * (f.hpPercent ?? 100) / 100);
    if (f.defenceOverride !== undefined) {
      monster = scaleMonster(monster);
      monster = { ...monster, skills: { ...monster.skills, def:f.defenceOverride } };
    }
    const gear: any = Object.fromEntries(slots.map(s => [s, null]));
    for (const id of f.gear) {
      const e: any = byId.get(id);
      if (!e) throw new Error(`Unknown gear ${id}`);
      gear[e.slot] = structuredClone(e);
    }
    if (f.dart) gear.weapon.itemVars = { blowpipeDartId: f.dart };
    let styles = getCombatStylesForCategory(gear.weapon?.category || 'unarmed');
    styles = styles.filter(s => f.style === 'MELEE' ? ['stab', 'slash', 'crush'].includes(s.type || '') : s.type === f.style.toLowerCase());
    if (f.style === 'MAGIC' && f.spell) {
      styles = [{ name: 'Spell', type: 'magic', stance: 'Autocast' }, { name: 'Spell', type: 'magic', stance: 'Manual Cast' }];
    }
    const outcomes: any[] = [];
    for (const style of styles) {
      const skills = { atk:99, str:99, def:99, ranged:99, magic:99, hp:99, prayer:99, mining:99, herblore:99, ...f.levels };
      const player: any = {
        name: f.key, equipment: structuredClone(gear), style, skills,
        boosts: { atk:0, str:0, def:0, ranged:0, magic:0, hp:0, prayer:0, mining:0, herblore:0, ...f.boosts },
        prayers: (f.prayers || []).map((p: string) => (Prayer as any)[p]),
        buffs: { potions:[], onSlayerTask: false, inWilderness:false, forinthrySurge:false, soulreaperStacks:0,
          baAttackerLevel:0, chinchompaDistance:4, kandarinDiary:false, chargeSpell:false, markOfDarknessSpell:false, usingSunfireRunes:false, ...f.buffs },
        spell: f.spell ? spells.find(s => s.name === f.spell) : null,
      };
      Object.assign(player, calculateEquipmentBonusesFromGear(player, monster));
      const calc = new PlayerVsNPCCalc(player, monster, { detailedOutput: !!f.details, disableMonsterScaling: f.defenceOverride !== undefined });
      outcomes.push({ key:f.key, dps:calc.getDps(), accuracy:calc.getHitChance(), expected:calc.getExpectedDamage(),
        max:calc.getMax(), speed:calc.getExpectedAttackSpeed(), attackRoll:calc.getMaxAttackRoll(), defenceRoll:calc.getNPCDefenceRoll(),
        type: style.type, stance:style.stance, issues:calc.userIssues, ...(f.details ? { details:calc.details } : {}) });
    }
    outcomes.sort((a,b) => b.dps-a.dps);
    if (!outcomes.length) throw new Error('No matching attack style');
    console.log(JSON.stringify({ ...outcomes[0], inputHash }));
  } catch(e) { console.log(JSON.stringify({ key:f.key, inputHash, error:String(e) })); }
}
