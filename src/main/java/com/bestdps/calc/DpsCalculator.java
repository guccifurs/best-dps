package com.bestdps.calc;

import com.bestdps.data.GearItem;
import com.bestdps.data.GearSlot;
import com.bestdps.data.MonsterStats;
import com.bestdps.data.SpellStats;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

/** Normal-attack distributions using OSRS Wiki combat formulae and target rules.
 * Sources: https://oldschool.runescape.wiki/w/Damage_per_second and item mechanics pages.
 * Special attacks and movement downtime are deliberately separate from continuous normal-attack DPS.
 */
public final class DpsCalculator
{
	public DpsResult calculate(OptimizationRequest request, Loadout loadout)
	{
		switch (request.getStyle())
		{
			case RANGED: return calculateRanged(request, loadout);
			case MAGIC: return calculateMagic(request, loadout);
			default: return calculateMelee(request, loadout);
		}
	}

	private DpsResult calculateMelee(OptimizationRequest request, Loadout loadout)
	{
		DpsResult best = null;
		for (WeaponAttackStyles.Stance stance : WeaponAttackStyles.melee(loadout.getWeapon()))
		{
			best = best(best, meleeVariant(request, loadout, stance));
		}
		return best == null ? unavailable(loadout, "No supported melee attack") : best;
	}

	private DpsResult meleeVariant(OptimizationRequest request, Loadout loadout, WeaponAttackStyles.Stance stance)
	{
		String attackType = stance.type;
		String rejection = EncounterRules.rejectionReason(request.getMonster(), loadout, attackType, null);
		if (!rejection.isEmpty())
		{
			return unavailable(loadout, rejection);
		}
		PlayerLevels levels = request.getLevels();
		PrayerBonuses prayers = request.getPrayers();
		int effectiveAttack = RollMath.effectiveLevel(levels.getAttack(), prayers.getMeleeAccuracy(), stance.attack);
		int effectiveStrength = RollMath.effectiveDamageLevel(levels.getStrength(), prayers.getMeleeStrength(), stance.strength);
		if (wearing(loadout, "soulreaper axe"))
		{
			effectiveStrength += multiply(levels.getStrength(), 6 * request.getCombatContext().getSoulreaperStacks(), 100);
		}
		if (isWearingMeleeVoid(loadout))
		{
			effectiveAttack = multiply(effectiveAttack, 11, 10);
			effectiveStrength = multiply(effectiveStrength, 11, 10);
		}

		long attackRoll = RollMath.attackRoll(effectiveAttack, loadout.getOffensive().getAttackBonus(attackType));
		long baseAttackRoll = attackRoll;
		int strengthBonus = loadout.getBonuses().getStrength();
		if (wearing(loadout, "bulwark"))
		{
			int totalDefence = 0;
			for (GearItem item : loadout.getGear().values())
			{
				if (item != null) totalDefence += item.getDefensive().getStab() + item.getDefensive().getSlash()
					+ item.getDefensive().getCrush() + item.getDefensive().getRanged();
			}
			strengthBonus += Math.max(0, (totalDefence - 800) / 12 - 38);
		}
		if (wearing(loadout, "keris partisan of amascut") && !EncounterRules.isTombsOfAmascut(request.getMonster()))
		{
			strengthBonus -= 22;
			if ("stab".equals(attackType)) attackRoll -= (long) effectiveAttack * 50;
			baseAttackRoll = attackRoll;
		}
		int maxHit = RollMath.maxHitFromEffective(effectiveStrength, strengthBonus);
		int baseMaxHit = maxHit;
		attackRoll = applyMeleeAccuracyBonuses(request, loadout, attackRoll, baseAttackRoll, attackType);
		maxHit = applyMeleeDamageBonuses(request, loadout, maxHit, baseMaxHit, attackType);

		long defenceRoll = npcDefenceRoll(request, attackType, loadout.getWeapon());
		double accuracy = isFang(loadout) && "stab".equals(attackType)
			? (EncounterRules.isTombsOfAmascut(request.getMonster())
				? RollMath.fangAccuracyInTombs(attackRoll, defenceRoll) : RollMath.fangAccuracy(attackRoll, defenceRoll))
			: RollMath.normalAccuracy(attackRoll, defenceRoll);
		accuracy = EncounterRules.forcedAccuracy(request.getMonster(), loadout, attackType, accuracy);
		int minHit = 0;
		if (isFang(loadout))
		{
			minHit = (int) Math.floor(maxHit * 3.0 / 20.0);
			maxHit -= minHit;
		}
		minHit += EncounterRules.minimumHit(request.getMonster(), loadout, attackType, maxHit);
		double expected = expectedHit(request, loadout, attackType, accuracy, minHit, maxHit);
		if (isScythe(loadout))
		{
			for (int splat = 1; splat < Math.min(3, request.getMonster().getSize()); splat++)
			{
				expected += expectedHit(request, loadout, attackType, accuracy, 0, maxHit / (1 << splat));
			}
		}
		else if (wearing(loadout, "dual macuahuitl"))
		{
			int first = maxHit / 2;
			expected = expectedHit(request, loadout, attackType, accuracy, 0, first)
				+ expectedHit(request, loadout, attackType, accuracy * accuracy, 0, maxHit - first);
		}
		else if (wearing(loadout, "sulphur blades") || wearing(loadout, "glacial temotli") || wearing(loadout, "torag's hammers"))
		{
			int first = maxHit / 2;
			expected = expectedHit(request, loadout, attackType, accuracy, 0, first)
				+ expectedHit(request, loadout, attackType, accuracy, 0, maxHit - first);
		}
		if (wearing(loadout, "gadderhammer") && request.getMonster().hasAttribute("shade"))
		{
			expected = expectedScaledHit(request, loadout, attackType, accuracy, minHit, maxHit, 5, 4) * 0.95
				+ expectedScaledHit(request, loadout, attackType, accuracy, minHit, maxHit, 2, 1) * 0.05;
		}
		if (isKalphite(request) && wearing(loadout, "keris"))
		{
			// 1/51 triple damage roll, before target-specific reductions.
			expected = expected * 50.0 / 51.0 + expectedScaledHit(request, loadout, attackType, accuracy, minHit, maxHit, 3, 1) / 51.0;
		}
		if (fullSet(loadout, "verac's helm", "verac's brassard", "verac's plateskirt", "verac's flail"))
		{
			expected = expected * 0.75 + expectedHit(request, loadout, attackType, 1, 1, maxHit + 1) * 0.25;
		}

		int speed = attackSpeed(loadout, CombatStyle.MELEE);
		double expectedSpeed = EncounterRules.attackSpeed(request.getMonster(), loadout, attackType, null, speed);
		if (fullSet(loadout, "dual macuahuitl", "blood moon helm", "blood moon chestplate", "blood moon tassets"))
		{
			expectedSpeed -= accuracy / 3.0 + accuracy * accuracy * 2.0 / 9.0;
		}
		int maximum = attackMaximum(request, loadout, attackType, maxHit, speed);
		if (EncounterRules.guaranteedMaximumHit(request.getMonster(), loadout, attackType)) expected = accuracy * maximum;
		int instant = EncounterRules.instantKillHit(request.getMonster(), loadout, attackType, null);
		if (instant >= 0) { maximum = instant; expected = accuracy * instant; }
		return new DpsResult(loadout, expected / (expectedSpeed * RollMath.SECONDS_PER_TICK), accuracy, expected,
			maximum, speed, stance.label(), attackRoll, defenceRoll);
	}

	private DpsResult calculateRanged(OptimizationRequest request, Loadout loadout)
	{
		PlayerLevels levels = request.getLevels();
		PrayerBonuses prayers = request.getPrayers();

		String rejection = EncounterRules.rejectionReason(request.getMonster(), loadout, "ranged", null);
		if (loadout.getWeapon() == null || !RangedAmmo.compatible(loadout.get(GearSlot.AMMO), loadout.getWeapon()) || !rejection.isEmpty())
		{
			return unavailable(loadout, rejection.isEmpty() ? "Weapon needs compatible ammunition" : rejection);
		}
		DpsResult rapid = rangedVariant(request, loadout, levels, prayers, 0, true);
		DpsResult accurate = "salamander".equalsIgnoreCase(loadout.getWeapon().getCategory()) ? rapid
			: rangedVariant(request, loadout, levels, prayers, 3, false);
		return best(rapid, accurate);
	}

	private DpsResult rangedVariant(OptimizationRequest request, Loadout loadout, PlayerLevels levels, PrayerBonuses prayers, int stanceBonus, boolean rapid)
	{
		int effectiveAccuracy = RollMath.effectiveLevel(levels.getRanged(), prayers.getRangedAccuracy(), stanceBonus);
		boolean strengthWeapon = wearing(loadout, "eclipse atlatl") || wearing(loadout, "hunter's spear");
		// Atlatl/spear use the Strength level and equipment, but still use ranged prayers.
		int effectiveDamage = RollMath.effectiveDamageLevel(strengthWeapon ? levels.getStrength() : levels.getRanged(),
			prayers.getRangedStrength(), stanceBonus);

		if (isWearingRangedVoid(loadout))
		{
			effectiveAccuracy = multiply(effectiveAccuracy, 11, 10);
			effectiveDamage = isWearingEliteVoid(loadout) ? multiply(effectiveDamage, 9, 8) : multiply(effectiveDamage, 11, 10);
		}

		long attackRoll = RollMath.attackRoll(effectiveAccuracy, loadout.getOffensive().getRanged() + (quiverApplies(loadout) ? 10 : 0));
		int maxHit = RollMath.maxHitFromEffective(effectiveDamage, strengthWeapon ? loadout.getBonuses().getStrength() : effectiveRangedStrength(loadout));
		attackRoll = applyRangedAccuracyBonuses(request, loadout, attackRoll);
		attackRoll *= EncounterRules.attackRollMultiplier(request.getMonster(), "ranged");
		String weaponName = name(loadout.getWeapon());
		if (weaponName.equals("ogre bow") || weaponName.equals("comp ogre bow"))
		{
			// Ogre bows ignore prayers, stance, armour strength and ordinary equipment multipliers.
			GearItem ammo = loadout.get(GearSlot.AMMO);
			maxHit = RollMath.maxHitFromEffective(levels.getRanged() + 10, ammo == null ? 0 : ammo.getBonuses().getRangedStrength());
		}
		else if (weaponName.equals("holy water"))
		{
			if (!isDemon(request)) return unavailable(loadout, "Holy water only damages demons");
			maxHit = RollMath.maxHitFromEffective(levels.getRanged() + 10, loadout.getWeapon().getBonuses().getRangedStrength());
			maxHit = multiply(maxHit, 100 + demonbaneBonus(request, 60), 100);
			if (request.getMonster().normalizedName().equals("nezikchened")) maxHit += 5;
		}
		else maxHit = applyRangedDamageBonuses(request, loadout, maxHit);
		long defenceRoll = npcDefenceRoll(request, "ranged", loadout.getWeapon());
		double accuracy = RollMath.normalAccuracy(attackRoll, defenceRoll);
		accuracy = EncounterRules.forcedAccuracy(request.getMonster(), loadout, "ranged", accuracy);
		int minHit = EncounterRules.minimumHit(request.getMonster(), loadout, "ranged", maxHit);
		if (EncounterRules.isWardenPhaseTwo(request.getMonster()))
		{
			int modifier = wardensModifier(attackRoll, defenceRoll);
			minHit = multiply(maxHit, modifier, 100);
			maxHit = multiply(maxHit, modifier + 20, 100);
			accuracy = 1;
		}
		int speed = attackSpeed(loadout, CombatStyle.RANGED);
		if (rapid && !"salamander".equalsIgnoreCase(loadout.getWeapon().getCategory())) speed = Math.max(1, speed - 1);
		double expected = rangedExpected(request, loadout, accuracy, minHit, maxHit, speed);
		if (wearing(loadout, "dark bow") || (wearing(loadout, "tonalztics of ralos") && "charged".equalsIgnoreCase(loadout.getWeapon().getVersion())))
		{
			expected *= 2;
		}
		if (fullSet(loadout, "karil's crossbow", "karil's coif", "karil's leathertop", "karil's leatherskirt", "amulet of the damned"))
		{
			expected += expectedScaledHit(request, loadout, "ranged", accuracy, minHit, maxHit, 1, 2, speed) * 0.25;
		}

		int damageTicks = speed;
		speed = EncounterRules.attackSpeed(request.getMonster(), loadout, "ranged", null, speed);
		int maximum = attackMaximum(request, loadout, "ranged", maxHit, damageTicks);
		if (EncounterRules.guaranteedMaximumHit(request.getMonster(), loadout, "ranged")) expected = accuracy * maximum;
		int instant = EncounterRules.instantKillHit(request.getMonster(), loadout, "ranged", null);
		if (instant >= 0) { maximum = instant; expected = accuracy * instant; }
		return new DpsResult(loadout, expected / (speed * RollMath.SECONDS_PER_TICK), accuracy, expected, maximum, speed, rapid ? "ranged rapid" : "ranged accurate", attackRoll, defenceRoll);
	}

	private DpsResult calculateMagic(OptimizationRequest request, Loadout loadout)
	{
		OptimizationRequest effectiveRequest = request;
		boolean powered = request.getSpell() == null && isPoweredStaff(loadout);
		String rejection = EncounterRules.rejectionReason(request.getMonster(), loadout, "magic", effectiveRequest.getSpell());
		if (!rejection.isEmpty())
		{
			return unavailable(loadout, rejection);
		}
		if (request.getSpell() != null && !BestDpsOptimizer.spellAllowed(request, loadout, request.getSpell()))
		{
			return unavailable(loadout, "The selected spell requires its level, unlocks and compatible staff.");
		}
		PlayerLevels levels = effectiveRequest.getLevels();
		PrayerBonuses prayers = effectiveRequest.getPrayers();
		int effectiveAccuracy = RollMath.prayerLevel(levels.getMagic(), prayers.getMagicAccuracy()) + (powered ? 11 : 9);
		if (isWearingMagicVoid(loadout))
		{
			effectiveAccuracy = multiply(effectiveAccuracy, 29, 20);
		}

		int magicAttackBonus = loadout.getOffensive().getMagic();
		if (powered && wearing(loadout, "tumeken"))
		{
			magicAttackBonus *= EncounterRules.isTombsOfAmascut(request.getMonster()) ? 4 : 3;
		}
		long attackRoll = RollMath.attackRoll(effectiveAccuracy, magicAttackBonus);
		int maxHit = magicMaxHit(effectiveRequest, loadout);
		attackRoll = applyMagicAccuracyBonuses(effectiveRequest, loadout, attackRoll);
		maxHit = applyMagicDamageBonuses(effectiveRequest, loadout, maxHit);
		long defenceRoll = npcDefenceRoll(effectiveRequest, "magic", loadout.getWeapon());
		double accuracy = RollMath.normalAccuracy(attackRoll, defenceRoll);
		boolean confliction = wearing(loadout, "confliction gauntlets") && (loadout.getWeapon() == null || !loadout.getWeapon().isTwoHanded());
		if (confliction) accuracy = conflictionAccuracy(attackRoll, defenceRoll);
		if (wearing(loadout, "brimstone ring"))
		{
			long reducedDefence = multiply(defenceRoll, 9, 10);
			accuracy = accuracy * 0.75 + (confliction ? conflictionAccuracy(attackRoll, reducedDefence)
				: RollMath.normalAccuracy(attackRoll, reducedDefence)) * 0.25;
		}
		accuracy = EncounterRules.forcedAccuracy(request.getMonster(), loadout, "magic", effectiveRequest.getSpell(), accuracy);
		int minHit = EncounterRules.minimumHit(request.getMonster(), loadout, "magic", maxHit);
		if (EncounterRules.isWardenPhaseTwo(request.getMonster()))
		{
			int modifier = wardensModifier(attackRoll, defenceRoll);
			minHit = multiply(maxHit, modifier, 100);
			maxHit = multiply(maxHit, modifier + 20, 100);
			accuracy = 1;
		}
		double expected = expectedHit(effectiveRequest, loadout, "magic", accuracy, minHit, maxHit);
		if (fullSet(loadout, "ahrim's staff", "ahrim's hood", "ahrim's robetop", "ahrim's robeskirt", "amulet of the damned"))
		{
			expected = expected * 0.75 + expectedScaledHit(effectiveRequest, loadout, "magic", accuracy, minHit, maxHit, 13, 10) * 0.25;
		}
		if (wearing(loadout, "sanguinesti"))
		{
			expected = expected * 0.8 + expectedHit(effectiveRequest, loadout, "magic", accuracy, minHit + 8, maxHit + 8) * 0.2;
		}
		if (isStandardSpell(effectiveRequest) && wearing(loadout, "twinflame staff")
			&& (effectiveRequest.getSpell().getName().endsWith("Bolt") || effectiveRequest.getSpell().getName().endsWith("Blast") || effectiveRequest.getSpell().getName().endsWith("Wave")))
		{
			expected += expectedTwinflameHit(effectiveRequest, loadout, accuracy, minHit, maxHit);
		}

		if (request.getMonster().normalizedName().equals("salarin the twisted"))
		{
			maxHit = EncounterRules.salarinFixedHit(effectiveRequest, loadout);
			expected = accuracy * maxHit;
		}
		if (request.getMonster().normalizedName().equals("zombified spawn") && effectiveRequest.getSpell() != null
			&& effectiveRequest.getSpell().getName().equals("Crumble Undead"))
		{
			// This spell kills the spawn outright; normal damage bonuses and rolls do not apply.
			maxHit = request.getMonster().getHitpoints();
			accuracy = magicAttackBonus > -64 ? 1 : 0;
			expected = accuracy * maxHit;
		}
		int speed = nominalSpeed(effectiveRequest, loadout, "magic");
		speed = EncounterRules.attackSpeed(request.getMonster(), loadout, "magic", effectiveRequest.getSpell(), speed);
		String spellName = effectiveRequest.getSpell() == null ? "" : effectiveRequest.getSpell().getName();
		String poweredName = spellName.isEmpty() && isPoweredStaff(loadout) && loadout.getWeapon() != null ? loadout.getWeapon().getName() : "";
		String attackType = spellName.isEmpty() ? poweredName.isEmpty() ? "magic" : "magic: " + poweredName : "magic: " + spellName;
		int maximum = attackMaximum(effectiveRequest, loadout, "magic", maxHit, nominalSpeed(effectiveRequest, loadout, "magic"));
		if (EncounterRules.guaranteedMaximumHit(request.getMonster(), loadout, "magic")) expected = accuracy * maximum;
		int instant = EncounterRules.instantKillHit(request.getMonster(), loadout, "magic", effectiveRequest.getSpell());
		if (instant >= 0) { maximum = instant; expected = accuracy * instant; }
		if (request.getMonster().normalizedName().equals("salarin the twisted") || request.getMonster().normalizedName().equals("zombified spawn") && "Crumble Undead".equals(spellName)) maximum = maxHit;
		return new DpsResult(loadout, expected / (speed * RollMath.SECONDS_PER_TICK), accuracy, expected,
			maximum, speed, attackType, attackRoll, defenceRoll, loadout.getCost(), spellName);
	}

	private int magicMaxHit(OptimizationRequest request, Loadout loadout)
	{
		GearItem weapon = loadout.getWeapon();
		String weaponName = name(weapon);
		int magicLevel = request.getLevels().getMagic();
		SpellStats spell = request.getSpell();
		boolean poweredStaff = spell == null && isPoweredStaff(loadout);
		if (spell != null)
		{
			if (magicLevel < spell.getMagicLevel()) return 0;
			if ("Magic Dart".equals(spell.getName()))
			{
				int base = magicLevel / 10 + 10;
				return wearing(loadout, "slayer's staff (e)") && isSlayerTaskEligible(request) ? magicLevel / 6 + 13 : base;
			}
			return elementalMaxHit(spell, magicLevel);
		}
		if (!poweredStaff)
		{
			return 0;
		}
		if (weaponName.contains("trident of the seas"))
		{
			return Math.max(1, magicLevel / 3 - 5);
		}
		if (weaponName.contains("thammaron"))
		{
			return Math.max(1, magicLevel / 3 - 8);
		}
		if (weaponName.contains("accursed sceptre"))
		{
			return Math.max(1, magicLevel / 3 - 6);
		}
		if (weaponName.contains("trident of the swamp"))
		{
			return Math.max(1, magicLevel / 3 - 2);
		}
		if (weaponName.contains("sanguinesti"))
		{
			return Math.max(1, magicLevel / 3);
		}
		if (weaponName.contains("tumeken"))
		{
			return Math.max(1, magicLevel / 3 + 1);
		}
		if (weaponName.contains("warped sceptre"))
		{
			return Math.max(1, (8 * magicLevel + 96) / 37);
		}
		if (weaponName.contains("dawnbringer")) return Math.max(1, magicLevel / 3 - 2);
		if (weaponName.contains("eye of ayak")) return Math.max(1, magicLevel / 3 - 6);
		if (weaponName.contains("starter staff")) return 8;
		if (weaponName.contains("staff") && (weaponName.contains("crystal") || weaponName.contains("corrupted")))
		{
			String label = weapon.getNormalizedLabel();
			return label.contains("perfected") ? 39 : label.contains("attuned") ? 31 : 23;
		}
		if (weaponName.contains("bone staff"))
		{
			return Math.max(1, magicLevel / 3 - 5) + 10;
		}
		return 0;
	}

	private long npcDefenceRoll(OptimizationRequest request, String attackType, GearItem weapon)
	{
		MonsterStats monster = request.getMonster();
		int level = "magic".equals(attackType) ? EncounterRules.magicDefenceLevel(monster) : monster.getDefence();
		int bonus;
		if ("ranged".equals(attackType))
		{
			bonus = weapon != null && "salamander".equalsIgnoreCase(weapon.getCategory())
				? (monster.getDefensive().get("light") + monster.getDefensive().get("standard") + monster.getDefensive().get("heavy")) / 3
				: monster.getDefensive().get(rangedDefenceType(weapon));
		}
		else
		{
			bonus = monster.getDefensive().get(attackType);
		}
		return EncounterRules.defenceRoll(monster, request.getEncounterContext(), RollMath.defenceRoll(level + 9, bonus));
	}

	private static String rangedDefenceType(GearItem weapon)
	{
		String category = weapon == null ? "" : weapon.getCategory().toLowerCase(Locale.ROOT);
		if (category.contains("thrown"))
		{
			return "light";
		}
		if (category.contains("crossbow") || category.contains("chinchompa"))
		{
			return "heavy";
		}
		return "standard";
	}

	private static int effectiveRangedStrength(Loadout loadout)
	{
		int rangedStrength = loadout.getBonuses().getRangedStrength() + (quiverApplies(loadout) ? 1 : 0);
		GearItem ammo = loadout.get(GearSlot.AMMO);
		if (ammo != null && ammo.getBonuses().getRangedStrength() > 0 && !RangedAmmo.strengthApplies(ammo, loadout.getWeapon()))
		{
			rangedStrength -= ammo.getBonuses().getRangedStrength();
		}
		if (RangedAmmo.isBlowpipe(loadout.getWeapon()) && (wearing(loadout, "toxic blowpipe") || wearing(loadout, "blazing blowpipe")))
		{
			// The tradeable empty entry has zero strength; the charged weapon contributes 20 plus loaded darts.
			rangedStrength += Math.max(0, 20 - loadout.getWeapon().getBonuses().getRangedStrength());
		}
		return rangedStrength;
	}

	private static int attackSpeed(Loadout loadout, CombatStyle style)
	{
		GearItem weapon = loadout.getWeapon();
		if (weapon == null || weapon.getSpeed() <= 0)
		{
			return style == CombatStyle.MAGIC ? 5 : 4;
		}
		return Math.max(1, weapon.getSpeed());
	}

	private long applyMeleeAccuracyBonuses(OptimizationRequest request, Loadout loadout, long roll, long baseRoll, String attackType)
	{
		roll = applyMeleeTaskBonus(request, loadout, roll);
		if (isTzhaarWeapon(loadout) && isWearingObsidian(loadout))
		{
			roll += multiply(baseRoll, 1, 10);
		}
		if (revenantWeapon(request, loadout)) roll = multiply(roll, 3, 2);
		if (isDemon(request))
		{
			roll = multiply(roll, 100 + demonbanePercent(request, loadout), 100);
		}
		if (isKalphite(request) && wearing(loadout, "keris partisan of breaching"))
		{
			roll = multiply(roll, 133, 100);
		}
		if (wearing(loadout, "keris partisan of the sun") && EncounterRules.isTombsOfAmascut(request.getMonster())
			&& request.getMonster().getHitpoints() * request.getEncounterContext().getTargetHpPercent() / 100 < request.getMonster().getHitpoints() / 4)
		{
			roll = multiply(roll, 5, 4);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter lance"))
		{
			roll = multiply(roll, 6, 5);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter wand")) roll = multiply(roll, 7, 4);
		if (request.getMonster().hasAttribute("golem") && wearing(loadout, "granite hammer"))
		{
			roll = multiply(roll, 13, 10);
		}
		if (request.getMonster().hasAttribute("vampyre1") || request.getMonster().hasAttribute("vampyre2") || request.getMonster().hasAttribute("vampyre3"))
		{
			if (wearing(loadout, "blisterwood"))
			{
				roll = multiply(roll, wearing(loadout, "blisterwood stake") ? 125 : 105, 100);
			}
			if (wearing(loadout, "hallowed flail") || wearing(loadout, "sunspear")) roll = multiply(roll, 125, 100);
			if (wearing(loadout, "efaritay's aid") && EncounterRules.isSilverWeapon(loadout, attackType)) roll = multiply(roll, 23, 20);
		}
		return "crush".equals(attackType) ? applyInquisitorBonus(loadout, roll) : roll;
	}

	private int applyMeleeDamageBonuses(OptimizationRequest request, Loadout loadout, int maxHit, int baseMaxHit, String attackType)
	{
		maxHit = (int) applyMeleeTaskBonus(request, loadout, maxHit);
		if (isDemon(request) && (wearing(loadout, "arclight") || wearing(loadout, "emberlight") || wearing(loadout, "claws")))
		{
			maxHit = multiply(maxHit, 100 + demonbanePercent(request, loadout), 100);
		}
		if (isTzhaarWeapon(loadout) && isWearingObsidian(loadout))
		{
			maxHit += multiply(baseMaxHit, 1, 10);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter lance"))
		{
			maxHit = multiply(maxHit, 6, 5);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter wand")) maxHit = multiply(maxHit, 7, 5);
		if (isKalphite(request) && wearing(loadout, "keris"))
		{
			maxHit = multiply(maxHit, wearing(loadout, "keris partisan of amascut") ? 115 : 133, 100);
		}
		if (revenantWeapon(request, loadout)) maxHit = multiply(maxHit, 3, 2);
		if (isDemon(request) && (wearing(loadout, "silverlight") || wearing(loadout, "darklight")))
		{
			maxHit = multiply(maxHit, 100 + demonbaneBonus(request, 60), 100);
		}
		if (request.getMonster().hasAttribute("leafy") && wearing(loadout, "leaf-bladed battleaxe"))
		{
			maxHit = multiply(maxHit, 47, 40);
		}
		if (request.getMonster().hasAttribute("golem"))
		{
			if (wearing(loadout, "barronite mace")) maxHit = multiply(maxHit, 23, 20);
			if (wearing(loadout, "granite hammer")) maxHit = multiply(maxHit, 13, 10);
		}
		if (wearing(loadout, "colossal blade")) maxHit += Math.min(10, request.getMonster().getSize() * 2);
		if (request.getMonster().hasAttribute("rat") && wearing(loadout, "bone mace")) maxHit += 10;
		if ("crush".equals(attackType)) maxHit = (int) applyInquisitorBonus(loadout, maxHit);
		return maxHit;
	}

	private long applyRangedAccuracyBonuses(OptimizationRequest request, Loadout loadout, long roll)
	{
		if (isCrystalBow(loadout)) roll = multiply(roll, 20 + crystalArmourPieces(loadout), 20);
		if (wearing(loadout, "blisterwood stake") && (request.getMonster().hasAttribute("vampyre1") || request.getMonster().hasAttribute("vampyre2") || request.getMonster().hasAttribute("vampyre3"))) roll = multiply(roll, 125, 100);
		if ((request.getMonster().hasAttribute("vampyre1") || request.getMonster().hasAttribute("vampyre2") || request.getMonster().hasAttribute("vampyre3"))
			&& wearing(loadout, "efaritay's aid") && EncounterRules.isSilverWeapon(loadout, "ranged")) roll = multiply(roll, 23, 20);
		if (avariceApplies(request, loadout))
		{
			roll = multiply(roll, 6, 5);
		}
		else if (isUndead(request) && salveEnhanced(loadout) && salveImbued(loadout))
		{
			roll = multiply(roll, 6, 5);
		}
		else if (isUndead(request) && salveImbued(loadout))
		{
			roll = multiply(roll, 7, 6);
		}
		else if (isSlayerTaskEligible(request) && isImbuedSlayerHelm(loadout))
		{
			roll = multiply(roll, 23, 20);
		}
		if (revenantWeapon(request, loadout)) roll = multiply(roll, 3, 2);
		if (isDragon(request) && wearing(loadout, "dragon hunter crossbow")) roll = multiply(roll, 13, 10);
		if (isDemon(request) && wearing(loadout, "scorching bow")) roll = multiply(roll, 100 + demonbaneBonus(request, 30), 100);
		if (wearing(loadout, "twisted bow"))
		{
			int cap = request.getMonster().hasAttribute("xerician") ? 350 : 250;
			int magic = Math.min(cap, Math.max(request.getMonster().getMagic(), request.getMonster().getOffensiveMagic()));
			roll = tbowScaling(roll, magic, true);
			if (EncounterRules.isWardenPhaseTwo(request.getMonster())) roll = tbowScaling(roll, magic, true);
		}
		return roll;
	}

	private int applyRangedDamageBonuses(OptimizationRequest request, Loadout loadout, int maxHit)
	{
		if (isCrystalBow(loadout)) maxHit = multiply(maxHit, 40 + crystalArmourPieces(loadout), 40);
		boolean strengthWeapon = wearing(loadout, "eclipse atlatl") || wearing(loadout, "hunter's spear");
		boolean dragonbane = isDragon(request) && wearing(loadout, "dragon hunter crossbow");
		boolean demonbane = isDemon(request) && wearing(loadout, "scorching bow");
		boolean revenant = revenantWeapon(request, loadout);
		if (avariceApplies(request, loadout))
		{
			maxHit = multiply(maxHit, 6, 5);
		}
		else if (isUndead(request) && salveEnhanced(loadout) && (strengthWeapon || salveImbued(loadout)))
		{
			maxHit = multiply(maxHit, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet") && (strengthWeapon || salveImbued(loadout)))
		{
			maxHit = multiply(maxHit, 7, 6);
		}
		else if (isSlayerTaskEligible(request) && strengthWeapon && isSlayerHelm(loadout))
		{
			maxHit = multiply(maxHit, 7, 6);
		}
		else if (isSlayerTaskEligible(request) && isImbuedSlayerHelm(loadout))
		{
			// Slayer and ranged dragon/demonbane damage are additive; salve is multiplicative.
			maxHit = multiply(maxHit, 100 + 15 + (dragonbane ? 25 : 0) + (demonbane ? demonbaneBonus(request, 30) : 0) + (revenant ? 50 : 0), 100);
			dragonbane = false;
			demonbane = false;
			revenant = false;
		}
		if (revenant) maxHit = multiply(maxHit, 3, 2);
		if (dragonbane) maxHit = multiply(maxHit, 5, 4);
		if (demonbane) maxHit = multiply(maxHit, 100 + demonbaneBonus(request, 30), 100);
		if (wearing(loadout, "twisted bow"))
		{
			int cap = request.getMonster().hasAttribute("xerician") ? 350 : 250;
			maxHit = (int) tbowScaling(maxHit, Math.min(cap, Math.max(request.getMonster().getMagic(), request.getMonster().getOffensiveMagic())), false);
		}
		if (request.getMonster().hasAttribute("rat") && wearing(loadout, "bone shortbow")) maxHit += 10;
		if (wearing(loadout, "tonalztics of ralos")) maxHit = multiply(maxHit, 3, 4);
		return maxHit;
	}

	private long applyMagicAccuracyBonuses(OptimizationRequest request, Loadout loadout, long roll)
	{
		long baseRoll = roll;
		boolean slayer = isSlayerTaskEligible(request) && isImbuedSlayerHelm(loadout);
		int additive = isStandardSpell(request) && isSmokeStaff(loadout) ? 10 : 0;
		if (isUndead(request) && salveImbued(loadout))
		{
			additive += salveEnhanced(loadout) ? 20 : 15;
			slayer = false;
		}
		else if (avariceApplies(request, loadout))
		{
			additive += 20;
			slayer = false;
		}
		roll = multiply(roll, 100 + additive, 100);
		if (isDragon(request) && wearing(loadout, "dragon hunter wand")) roll = multiply(roll, 7, 4);
		if (isDragon(request) && wearing(loadout, "dragon hunter lance")) roll = multiply(roll, 6, 5);
		if (isDragon(request) && wearing(loadout, "dragon hunter crossbow")) roll = multiply(roll, 13, 10);
		if (slayer) roll = multiply(roll, 23, 20);
		if (isDemon(request) && request.getSpell() != null && request.getSpell().getName().contains("Demonbane"))
		{
			int bonus = request.getCombatContext().isMarkOfDarkness() ? 40 : 20;
			if (wearing(loadout, "purging staff")) bonus *= 2;
			roll = multiply(roll, 100 + demonbaneBonus(request, bonus), 100);
		}
		if (request.getSpell() != null && "water".equals(request.getSpell().getElement()) && wearing(loadout, "tome of water"))
		{
			roll = multiply(roll, 6, 5);
		}
		if (revenantWeapon(request, loadout)) roll = multiply(roll, 3, 2);
		if (matchesWeakness(request)) roll += multiply(baseRoll, request.getMonster().getWeaknessSeverity(), 100);
		return roll;
	}

	private int applyMagicDamageBonuses(OptimizationRequest request, Loadout loadout, int maxHit)
	{
		if (maxHit <= 0) return 0;
		if (request.getSpell() != null && request.getSpell().getName().endsWith("Bolt") && wearing(loadout, "chaos gauntlets")) maxHit += 3;
		if (request.getSpell() != null && !request.getSpell().getElement().isEmpty()
			&& (wearing(loadout, "elemental amulet") || name(loadout.get(GearSlot.NECK)).equals("amulet of " + request.getSpell().getElement()))) maxHit += 2;
		int baseMaxHit = maxHit;
		int magicDamage = loadout.getBonuses().getMagicDamage();
		if (isWearingMagicVoid(loadout) && isWearingEliteVoid(loadout)) magicDamage += 50;
		if (request.getSpell() != null && "ancient".equals(request.getSpell().getSpellbook()))
		{
			if (wearing(loadout, "virtus mask")) magicDamage += 30;
			if (wearing(loadout, "virtus robe top")) magicDamage += 30;
			if (wearing(loadout, "virtus robe bottom")) magicDamage += 30;
		}
		if (request.getSpell() == null && wearing(loadout, "tumeken"))
		{
			magicDamage = Math.min(1000, magicDamage * (EncounterRules.isTombsOfAmascut(request.getMonster()) ? 4 : 3));
		}
		if (isStandardSpell(request) && isSmokeStaff(loadout)) magicDamage += 100;
		boolean slayer = isSlayerTaskEligible(request) && isImbuedSlayerHelm(loadout);
		if (isUndead(request) && salveImbued(loadout))
		{
			magicDamage += salveEnhanced(loadout) ? 200 : 150;
			slayer = false;
		}
		else if (avariceApplies(request, loadout))
		{
			magicDamage += 200;
			slayer = false;
		}
		maxHit += multiply(maxHit, magicDamage + (int) Math.round(request.getPrayers().getMagicDamagePercent() * 10), 1000);
		if (slayer) maxHit = multiply(maxHit, 23, 20);
		if (isDragon(request) && wearing(loadout, "dragon hunter wand")) maxHit = multiply(maxHit, 7, 5);
		if (isDragon(request) && wearing(loadout, "dragon hunter lance")) maxHit = multiply(maxHit, 6, 5);
		if (isDragon(request) && wearing(loadout, "dragon hunter crossbow")) maxHit = multiply(maxHit, 5, 4);
		if (wearing(loadout, "dawnbringer")) maxHit = Math.max(2, maxHit / 2);
		if (revenantWeapon(request, loadout)) maxHit = multiply(maxHit, 3, 2);
		if (matchesWeakness(request)) maxHit += multiply(baseMaxHit, request.getMonster().getWeaknessSeverity(), 100);
		// Demonbane spells gain accuracy natively; extra damage requires Mark of Darkness.
		GearItem shield = loadout.get(GearSlot.SHIELD);
		if (request.getSpell() != null && shield != null && "charged".equalsIgnoreCase(shield.getVersion())
			&& name(shield).equals("tome of " + request.getSpell().getElement())) maxHit = multiply(maxHit, 11, 10);
		return maxHit;
	}

	private static DpsResult best(DpsResult left, DpsResult right)
	{
		if (left == null)
		{
			return right;
		}
		if (right == null)
		{
			return left;
		}
		return right.getDps() > left.getDps() ? right : left;
	}

	private static boolean wearing(Loadout loadout, String marker)
	{
		String needle = marker;
		for (Map.Entry<GearSlot, GearItem> entry : loadout.getGear().entrySet())
		{
			GearItem item = entry.getValue();
			if (item != null && item.getNormalizedName().contains(needle))
			{
				return true;
			}
		}
		return false;
	}

	private static String name(GearItem item)
	{
		return item == null ? "" : item.getNormalizedName();
	}

	private static boolean isWearingMeleeVoid(Loadout loadout)
	{
		return wearing(loadout, "void melee helm") && isWearingVoidRobes(loadout);
	}

	private static boolean isWearingRangedVoid(Loadout loadout)
	{
		return wearing(loadout, "void ranger helm") && isWearingVoidRobes(loadout);
	}

	private static boolean isWearingMagicVoid(Loadout loadout)
	{
		return wearing(loadout, "void mage helm") && isWearingVoidRobes(loadout);
	}

	private static boolean isWearingVoidRobes(Loadout loadout)
	{
		return (wearing(loadout, "void knight top") || wearing(loadout, "elite void top"))
			&& (wearing(loadout, "void knight robe") || wearing(loadout, "elite void robe"))
			&& wearing(loadout, "void knight gloves");
	}

	private static boolean isWearingEliteVoid(Loadout loadout)
	{
		return wearing(loadout, "elite void top") && wearing(loadout, "elite void robe") && wearing(loadout, "void knight gloves");
	}

	private static boolean isSlayerHelm(Loadout loadout)
	{
		for (GearItem item : loadout.getGear().values())
		{
			if (item != null && item.isSlayerHead())
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isImbuedSlayerHelm(Loadout loadout)
	{
		for (GearItem item : loadout.getGear().values())
		{
			if (item != null && item.isImbuedSlayerHead())
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isUndead(OptimizationRequest request)
	{
		return request.getMonster().hasAttribute("undead");
	}

	private static boolean isDragon(OptimizationRequest request)
	{
		return request.getMonster().hasAttribute("dragon");
	}

	private static boolean isDemon(OptimizationRequest request)
	{
		return request.getMonster().hasAttribute("demon");
	}

	private static boolean isKalphite(OptimizationRequest request)
	{
		return request.getMonster().hasAttribute("kalphite");
	}

	private static boolean isSlayerTaskEligible(OptimizationRequest request)
	{
		return request.isOnSlayerTask() && request.getMonster().isSlayerMonster();
	}

	private static boolean isPoweredStaff(Loadout loadout)
	{
		GearItem weapon = loadout.getWeapon();
		String weaponName = name(weapon);
		String category = weapon == null ? "" : weapon.getCategory().toLowerCase(Locale.ROOT);
		if (weaponName.contains("accursed sceptre (a)")) return false;
		return category.contains("powered staff")
			|| weaponName.contains("trident")
			|| weaponName.contains("thammaron")
			|| weaponName.contains("accursed sceptre")
			|| weaponName.contains("sanguinesti")
			|| weaponName.contains("tumeken")
			|| weaponName.contains("warped sceptre")
			|| weaponName.contains("bone staff")
			|| weaponName.contains("dawnbringer")
			|| weaponName.contains("eye of ayak")
			|| weaponName.contains("starter staff")
			|| weaponName.contains("crystal staff")
			|| weaponName.contains("corrupted staff");
	}

	private static boolean isFang(Loadout loadout)
	{
		return wearing(loadout, "osmumten's fang");
	}

	private static boolean isScythe(Loadout loadout)
	{
		return wearing(loadout, "scythe of vitur");
	}

	private static boolean isCrystalBow(Loadout loadout)
	{
		return wearing(loadout, "crystal bow") || wearing(loadout, "bow of faerdhinen");
	}

	private static int crystalArmourPieces(Loadout loadout)
	{
		int pieces = 0;
		if (wearing(loadout, "crystal helm"))
		{
			pieces += 1;
		}
		if (wearing(loadout, "crystal legs"))
		{
			pieces += 2;
		}
		if (wearing(loadout, "crystal body"))
		{
			pieces += 3;
		}
		return pieces;
	}

	private static boolean isTzhaarWeapon(Loadout loadout)
	{
		return wearing(loadout, "tzhaar-ket-em")
			|| wearing(loadout, "tzhaar-ket-om")
			|| wearing(loadout, "toktz-xil-ak")
			|| wearing(loadout, "toktz-xil-ek")
			|| wearing(loadout, "toktz-mej-tal");
	}

	private static boolean isWearingObsidian(Loadout loadout)
	{
		return wearing(loadout, "obsidian helmet")
			&& wearing(loadout, "obsidian platebody")
			&& wearing(loadout, "obsidian platelegs");
	}

	private static DpsResult unavailable(Loadout loadout, String reason)
	{
		return new DpsResult(loadout, 0, 0, 0, 0, 4, reason, 0, 0);
	}

	private static boolean matchesWeakness(OptimizationRequest request)
	{
		return request.getSpell() != null && !request.getSpell().getElement().isEmpty()
			&& request.getSpell().getElement().equals(request.getMonster().getWeaknessElement());
	}

	private static boolean isStandardSpell(OptimizationRequest request)
	{
		return request.getSpell() != null && "standard".equalsIgnoreCase(request.getSpell().getSpellbook());
	}

	private static boolean isSmokeStaff(Loadout loadout)
	{
		return wearing(loadout, "smoke battlestaff") || wearing(loadout, "mystic smoke staff") || wearing(loadout, "twinflame staff");
	}

	private static boolean quiverApplies(Loadout loadout)
	{
		GearItem cape = loadout.get(GearSlot.CAPE);
		GearItem ammo = loadout.get(GearSlot.AMMO);
		if (cape == null || ammo == null || !RangedAmmo.strengthApplies(ammo, loadout.getWeapon())) return false;
		String ammunition = name(ammo);
		if (!ammunition.contains("arrow") && !ammunition.contains("bolt")) return false;
		String name = name(cape);
		return name.contains("blessed dizana") || name.contains("dizana's max cape")
			|| name.contains("dizana's quiver") && cape.getVersion().equalsIgnoreCase("charged");
	}

	private static boolean salveEnhanced(Loadout loadout)
	{
		String neck = name(loadout.get(GearSlot.NECK));
		return neck.equals("salve amulet (e)") || neck.equals("salve amulet(ei)");
	}

	private static boolean salveImbued(Loadout loadout)
	{
		String neck = name(loadout.get(GearSlot.NECK));
		return neck.equals("salve amulet(i)") || neck.equals("salve amulet(ei)");
	}

	private static long applyMeleeTaskBonus(OptimizationRequest request, Loadout loadout, long value)
	{
		if (avariceApplies(request, loadout)) return multiply(value, 6, 5);
		if (isUndead(request) && salveEnhanced(loadout)) return multiply(value, 6, 5);
		if (isUndead(request) && wearing(loadout, "salve amulet")) return multiply(value, 7, 6);
		if (isSlayerTaskEligible(request) && isSlayerHelm(loadout)) return multiply(value, 7, 6);
		return value;
	}

	private static boolean avariceApplies(OptimizationRequest request, Loadout loadout)
	{
		return request.getMonster().normalizedName().startsWith("revenant") && wearing(loadout, "amulet of avarice");
	}

	private static int demonbanePercent(OptimizationRequest request, Loadout loadout)
	{
		if (wearing(loadout, "arclight") || wearing(loadout, "emberlight")) return demonbaneBonus(request, 70);
		if (wearing(loadout, "silverlight") || wearing(loadout, "darklight")) return demonbaneBonus(request, 60);
		if (wearing(loadout, "bone claws") || wearing(loadout, "burning claws")) return demonbaneBonus(request, 5);
		return 0;
	}

	private static double expectedHit(OptimizationRequest request, Loadout loadout, String attackType, double accuracy, int minHit, int maxHit)
	{
		return expectedHit(request, loadout, attackType, accuracy, minHit, maxHit, nominalSpeed(request, loadout, attackType));
	}

	private static double expectedHit(OptimizationRequest request, Loadout loadout, String attackType, double accuracy, int minHit, int maxHit, int ticks)
	{
		if (maxHit <= 0) return 0;
		double missed = (1 - accuracy) * EncounterRules.missDamage(request.getMonster(), loadout, attackType);
		if (accuracy <= 0) return missed;
		int extraDamage = EncounterRules.unshieldedHitBonus(request.getMonster(), loadout, attackType, request.getSpell(), ticks);
		int armour = "magic".equals(attackType) ? 0 : request.getMonster().getDefensive().getFlatArmour();
		HitEffects effects = hitEffects(request, loadout, attackType);
		if (extraDamage == 0 && !EncounterRules.hasHitModifier(request.getMonster()) && (effects == HitEffects.NONE || effects == HitEffects.SEEKING))
		{
			return RollMath.expectedHitAfterArmour(accuracy, minHit, maxHit, armour, effects.minimum) + missed;
		}
		IntToDoubleFunction targetHit = EncounterRules.hitModifier(request, loadout, attackType);
		int low = Math.max(0, Math.min(minHit, maxHit));
		double total = 0;
		for (int hit = low; hit <= maxHit; hit++)
		{
			total += Math.max(0, targetHit.applyAsDouble(Math.max(1, effects.apply(hit, extraDamage))) - armour);
		}
		return accuracy * total / (maxHit - low + 1.0) + missed;
	}

	private static double expectedScaledHit(OptimizationRequest request, Loadout loadout, String attackType, double accuracy,
		int minHit, int maxHit, int numerator, int denominator)
	{
		return expectedScaledHit(request, loadout, attackType, accuracy, minHit, maxHit, numerator, denominator, nominalSpeed(request, loadout, attackType));
	}

	private static double expectedScaledHit(OptimizationRequest request, Loadout loadout, String attackType, double accuracy,
		int minHit, int maxHit, int numerator, int denominator, int ticks)
	{
		if (maxHit <= 0 || accuracy <= 0) return 0;
		int extraDamage = EncounterRules.unshieldedHitBonus(request.getMonster(), loadout, attackType, request.getSpell(), ticks);
		int armour = "magic".equals(attackType) ? 0 : request.getMonster().getDefensive().getFlatArmour();
		HitEffects effects = hitEffects(request, loadout, attackType);
		IntToDoubleFunction targetHit = EncounterRules.hitModifier(request, loadout, attackType);
		double total = 0;
		for (int hit = minHit; hit <= maxHit; hit++)
		{
			int scaled = Math.max(1, effects.applyScaled(hit, extraDamage, numerator, denominator));
			total += Math.max(0, targetHit.applyAsDouble(scaled) - armour);
		}
		return accuracy * total / (maxHit - minHit + 1.0);
	}

	private static int displayedMax(OptimizationRequest request, Loadout loadout, String attackType, int maxHit)
	{
		return displayedMax(request, loadout, attackType, maxHit, nominalSpeed(request, loadout, attackType));
	}

	/** Twinflame copies the already-clamped main hit; its smaller second splat may be zero. */
	private static double expectedTwinflameHit(OptimizationRequest request, Loadout loadout, double accuracy, int minHit, int maxHit)
	{
		if (maxHit <= 0 || accuracy <= 0) return 0;
		int extra = EncounterRules.unshieldedHitBonus(request.getMonster(), loadout, "magic", request.getSpell(), nominalSpeed(request, loadout, "magic"));
		IntToDoubleFunction targetHit = EncounterRules.hitModifier(request, loadout, "magic");
		double total = 0;
		for (int hit = minHit; hit <= maxHit; hit++)
		{
			total += targetHit.applyAsDouble(multiply(Math.max(1, hit + extra), 2, 5));
		}
		return accuracy * total / (maxHit - minHit + 1.0);
	}

	private static int displayedMax(OptimizationRequest request, Loadout loadout, String attackType, int maxHit, int ticks)
	{
		int armour = "magic".equals(attackType) ? 0 : request.getMonster().getDefensive().getFlatArmour();
		int extra = EncounterRules.unshieldedHitBonus(request.getMonster(), loadout, attackType, request.getSpell(), ticks);
		int damage = hitEffects(request, loadout, attackType).apply(maxHit, extra);
		return (int) Math.max(0, EncounterRules.maximumHit(request, loadout, attackType, damage) - armour);
	}

	private static boolean fullSet(Loadout loadout, String... pieces)
	{
		for (String piece : pieces)
		{
			if (!wearing(loadout, piece)) return false;
		}
		return true;
	}

	/** Maximum damage of the entire attack, including secondary splats and possible normal procs. */
	private static int attackMaximum(OptimizationRequest request, Loadout loadout, String type, int maxHit, int ticks)
	{
		int maximum = displayedMax(request, loadout, type, maxHit, ticks);
		if ("magic".equals(type))
		{
			if (wearing(loadout, "sanguinesti")) maximum = displayedMax(request, loadout, type, maxHit + 8, ticks);
			if (fullSet(loadout, "ahrim's staff", "ahrim's hood", "ahrim's robetop", "ahrim's robeskirt", "amulet of the damned"))
			{
				int extra = EncounterRules.unshieldedHitBonus(request.getMonster(), loadout, type, request.getSpell(), ticks);
				int damage = hitEffects(request, loadout, type).applyScaled(maxHit, extra, 13, 10);
				maximum = EncounterRules.maximumHit(request, loadout, type, damage);
			}
			if (isStandardSpell(request) && wearing(loadout, "twinflame staff")
				&& (request.getSpell().getName().endsWith("Bolt") || request.getSpell().getName().endsWith("Blast") || request.getSpell().getName().endsWith("Wave")))
			{
				int extra = EncounterRules.unshieldedHitBonus(request.getMonster(), loadout, type, request.getSpell(), ticks);
				maximum += EncounterRules.maximumHit(request, loadout, type, multiply(Math.max(1, maxHit + extra), 2, 5));
			}
			return maximum;
		}
		if ("ranged".equals(type))
		{
			String ammo = name(loadout.get(GearSlot.AMMO));
			boolean zaryte = wearing(loadout, "zaryte crossbow");
			if (ammo.endsWith("(e)") && "crossbow".equalsIgnoreCase(loadout.getWeapon().getCategory()))
			{
				int procMax = maxHit;
				if (ammo.startsWith("diamond")) procMax = multiply(maxHit, zaryte ? 126 : 115, 100);
				else if (ammo.startsWith("onyx") && !isUndead(request)) procMax = multiply(maxHit, zaryte ? 132 : 120, 100);
				else if (ammo.startsWith("dragonstone") && !isDragon(request) && !request.getMonster().hasAttribute("fiery")) procMax += request.getLevels().getRanged() * 2 / (zaryte ? 9 : 10);
				else if (ammo.startsWith("opal")) procMax += request.getLevels().getRanged() / (zaryte ? 9 : 10);
				else if (ammo.startsWith("pearl")) procMax += request.getLevels().getRanged() / ((request.getMonster().hasAttribute("fiery") ? 15 : 20) - (zaryte ? 2 : 0));
				maximum = Math.max(maximum, displayedMax(request, loadout, type, procMax, ticks));
				if (ammo.startsWith("ruby") && request.getCombatContext().getCurrentHp() >= 10)
				{
					int hp = request.getMonster().getHitpoints() * request.getEncounterContext().getTargetHpPercent() / 100;
					int ruby = Math.min(zaryte ? 110 : 100, multiply(hp, zaryte ? 22 : 20, 100));
					int proc = request.getMonster().normalizedName().equals("corporeal beast") ? ruby : EncounterRules.maximumHit(request, loadout, type, ruby);
					maximum = Math.max(maximum, Math.max(0, proc - request.getMonster().getDefensive().getFlatArmour()));
				}
			}
			if (wearing(loadout, "dark bow") || wearing(loadout, "tonalztics of ralos") && "charged".equalsIgnoreCase(loadout.getWeapon().getVersion())) maximum *= 2;
			if (fullSet(loadout, "karil's crossbow", "karil's coif", "karil's leathertop", "karil's leatherskirt", "amulet of the damned")) maximum += displayedMax(request, loadout, type, maxHit / 2, ticks);
			return maximum;
		}
		if (isScythe(loadout))
		{
			for (int splat = 1; splat < Math.min(3, request.getMonster().getSize()); splat++) maximum += displayedMax(request, loadout, type, maxHit / (1 << splat), ticks);
		}
		else if (wearing(loadout, "dual macuahuitl") || wearing(loadout, "sulphur blades") || wearing(loadout, "glacial temotli") || wearing(loadout, "torag's hammers"))
		{
			maximum = displayedMax(request, loadout, type, maxHit / 2, ticks) + displayedMax(request, loadout, type, maxHit - maxHit / 2, ticks);
		}
		if (wearing(loadout, "gadderhammer") && request.getMonster().hasAttribute("shade")) maximum = displayedMax(request, loadout, type, maxHit * 2, ticks);
		if (isKalphite(request) && wearing(loadout, "keris")) maximum = displayedMax(request, loadout, type, maxHit * 3, ticks);
		if (fullSet(loadout, "verac's helm", "verac's brassard", "verac's plateskirt", "verac's flail")) maximum = displayedMax(request, loadout, type, maxHit + 1, ticks);
		return maximum;
	}

	/** Immutable factors computed once per hit distribution, never once per possible damage. */
	private static final class HitEffects
	{
		private static final HitEffects NONE = new HitEffects(10000, false, false, 100, 0, 100, 1);
		private static final HitEffects SEEKING = new HitEffects(10000, false, false, 100, 0, 100, 3);
		private final int dharok;
		private final boolean berserker;
		private final boolean silverRing;
		private final int vampyre;
		private final int mark;
		private final int vulnerability;
		private final int minimum;

		private HitEffects(int dharok, boolean berserker, boolean silverRing, int vampyre, int mark, int vulnerability, int minimum)
		{
			this.dharok = dharok;
			this.berserker = berserker;
			this.silverRing = silverRing;
			this.vampyre = vampyre;
			this.mark = mark;
			this.vulnerability = vulnerability;
			this.minimum = minimum;
		}

		private int apply(int damage, int extra)
		{
			return applyScaled(damage, extra, 1, 1);
		}

		private int applyScaled(int damage, int extra, int numerator, int denominator)
		{
			// Mark precedes Ahrim's proc and the Tormented Demon's flat unshielded bonus.
			if (mark != 0) damage += multiply(multiply(damage, mark, 100), vulnerability, 100);
			damage = multiply(damage, numerator, denominator) + extra;
			if (dharok != 10000) damage = multiply(damage, dharok, 10000);
			if (berserker) damage = multiply(damage, 6, 5);
			if (silverRing) damage = multiply(damage, 11, 10);
			if (vampyre != 100) damage = multiply(damage, vampyre, 100);
			return Math.max(minimum, damage);
		}
	}

	private static HitEffects hitEffects(OptimizationRequest request, Loadout loadout, String style)
	{
		if ("magic".equals(style))
		{
			if (request.getCombatContext().isMarkOfDarkness() && isDemon(request)
				&& request.getSpell() != null && request.getSpell().getName().contains("Demonbane"))
			{
				return new HitEffects(10000, false, false, 100, wearing(loadout, "purging staff") ? 50 : 25, demonbaneBonus(request, 100), 1);
			}
			return request.getMonster().hasAttribute("vampyre1") && wearing(loadout, "efaritay's aid")
				? new HitEffects(10000, false, true, 100, 0, 100, 1) : HitEffects.NONE;
		}
		boolean firstTier = request.getMonster().hasAttribute("vampyre1");
		boolean vampyre = firstTier || request.getMonster().hasAttribute("vampyre2") || request.getMonster().hasAttribute("vampyre3");
		if ("ranged".equals(style))
		{
			boolean seeking = name(loadout.get(GearSlot.AMMO)).startsWith("seeking ")
				&& RangedAmmo.strengthApplies(loadout.get(GearSlot.AMMO), loadout.getWeapon());
			if (!vampyre) return seeking ? HitEffects.SEEKING : HitEffects.NONE;
			boolean stake = wearing(loadout, "blisterwood stake");
			boolean silver = name(loadout.get(GearSlot.AMMO)).contains("silver bolt");
			boolean ring = (firstTier || stake || silver) && wearing(loadout, "efaritay's aid");
			return new HitEffects(10000, false, ring, stake ? 125 : firstTier && silver ? 110 : 100, 0, 100, seeking ? 3 : 1);
		}
		int dharok = 10000;
		if (fullSet(loadout, "dharok's greataxe", "dharok's helm", "dharok's platebody", "dharok's platelegs"))
		{
			CombatContext context = request.getCombatContext();
			dharok += Math.max(0, context.getMaxHp() - context.getCurrentHp()) * context.getMaxHp();
		}
		boolean berserker = wearing(loadout, "berserker necklace") && isTzhaarWeapon(loadout);
		int vampireBonus = 100;
		boolean silverRing = false;
		if (vampyre)
		{
			String weapon = name(loadout.getWeapon());
			if (weapon.contains("blisterwood flail") || weapon.contains("hallowed flail") || weapon.contains("blisterwood stake")) vampireBonus = 125;
			else if (weapon.contains("blisterwood sickle")) vampireBonus = 115;
			else if (weapon.contains("sunspear")) vampireBonus = 150;
			else if (weapon.contains("ivandis flail")) vampireBonus = 120;
			else if (weapon.contains("rod of ivandis")) vampireBonus = 110;
			else if (weapon.contains("silver") && firstTier) vampireBonus = 110;
			silverRing = (EncounterRules.isSilverWeapon(loadout, style) || firstTier) && wearing(loadout, "efaritay's aid");
		}
		return dharok == 10000 && !berserker && !silverRing && vampireBonus == 100 ? HitEffects.NONE
			: new HitEffects(dharok, berserker, silverRing, vampireBonus, 0, 100, 1);
	}

	private static int elementalMaxHit(SpellStats spell, int magic)
	{
		String name = spell.getName();
		if (spell.getElement().isEmpty()) return spell.getMaxHit();
		if (name.endsWith("Strike")) return magic >= 13 ? 8 : magic >= 9 ? 6 : magic >= 5 ? 4 : 2;
		if (name.endsWith("Bolt")) return magic >= 35 ? 12 : magic >= 29 ? 11 : magic >= 23 ? 10 : 9;
		if (name.endsWith("Blast")) return magic >= 59 ? 16 : magic >= 53 ? 15 : magic >= 47 ? 14 : 13;
		if (name.endsWith("Wave")) return magic >= 75 ? 20 : magic >= 70 ? 19 : magic >= 65 ? 18 : 17;
		if (name.endsWith("Surge")) return magic >= 95 ? 24 : magic >= 90 ? 23 : magic >= 85 ? 22 : 21;
		return spell.getMaxHit();
	}

	private static int wardensModifier(long attackRoll, long defenceRoll)
	{
		long surplus = Math.max(0, Math.min(42000, attackRoll - defenceRoll / 3));
		return 15 + (int) (surplus * 25 / 42000);
	}

	private static int nominalSpeed(OptimizationRequest request, Loadout loadout, String attackType)
	{
		if ("magic".equals(attackType))
		{
			if (wearing(loadout, "twinflame staff")) return 6;
			return request.getSpell() == null && isPoweredStaff(loadout) ? attackSpeed(loadout, CombatStyle.MAGIC)
				: wearing(loadout, "harmonised nightmare staff") && isStandardSpell(request) ? 4 : 5;
		}
		return attackSpeed(loadout, "ranged".equals(attackType) ? CombatStyle.RANGED : CombatStyle.MELEE);
	}

	private static double conflictionAccuracy(long attack, long defence)
	{
		double one = RollMath.normalAccuracy(attack, defence);
		double two = RollMath.fangAccuracy(attack, defence);
		return two / (1 + two - one);
	}

	private static int demonbaneBonus(OptimizationRequest request, int bonus)
	{
		String target = request.getMonster().normalizedName();
		if (request.getMonster().getId() == 14179) return bonus * 2;
		if (target.equals("ice demon")) return multiply(bonus, 115, 100);
		return target.equals("yama") ? multiply(bonus, 120, 100) : target.equals("duke sucellus") ? multiply(bonus, 70, 100) : bonus;
	}

	private static boolean revenantWeapon(OptimizationRequest request, Loadout loadout)
	{
		if (!request.getCombatContext().isWilderness()) return false;
		String weapon = name(loadout.getWeapon());
		return weapon.contains("viggora") || weapon.contains("ursine chainmace") || weapon.contains("craw's bow")
			|| weapon.contains("webweaver") || weapon.contains("thammaron") || weapon.contains("accursed sceptre");
	}

	/** Enchanted bolt normal-attack procs; the optional Kandarin diary boosts proc chance by 10%. */
	private static double rangedExpected(OptimizationRequest request, Loadout loadout, double accuracy, int minHit, int maxHit, int ticks)
	{
		double ordinary = expectedHit(request, loadout, "ranged", accuracy, minHit, maxHit, ticks);
		String ammo = name(loadout.get(GearSlot.AMMO));
		if (!ammo.endsWith("(e)")) return ordinary;
		boolean zaryte = wearing(loadout, "zaryte crossbow");
		double sixPercent = request.getCombatContext().isKandarinDiary() ? 0.066 : 0.06;
		double tenPercent = request.getCombatContext().isKandarinDiary() ? 0.11 : 0.10;
		double elevenPercent = request.getCombatContext().isKandarinDiary() ? 0.121 : 0.11;
		if (ammo.startsWith("ruby") && request.getCombatContext().getCurrentHp() >= 10)
		{
			int hp = request.getMonster().getHitpoints() * request.getEncounterContext().getTargetHpPercent() / 100;
			int procHit = Math.min(zaryte ? 110 : 100, multiply(hp, zaryte ? 22 : 20, 100));
			// Corp's resistance precedes Blood Forfeit; its ruby proc is not halved.
			double procDamage = request.getMonster().getName().equalsIgnoreCase("Corporeal Beast") ? procHit
				: EncounterRules.modifyHit(request, loadout, "ranged", procHit);
			return ordinary * (1 - sixPercent) + Math.max(0, procDamage - request.getMonster().getDefensive().getFlatArmour()) * sixPercent;
		}
		if (ammo.startsWith("opal") || ammo.startsWith("pearl"))
		{
			boolean opal = ammo.startsWith("opal");
			int divisor = opal ? 10 : request.getMonster().hasAttribute("fiery") ? 15 : 20;
			if (zaryte) divisor -= opal ? 1 : 2;
			int bonus = request.getLevels().getRanged() / divisor;
			double chance = (opal ? 0.05 : 0.06) * (request.getCombatContext().isKandarinDiary() ? 1.1 : 1.0);
			double proc = expectedHit(request, loadout, "ranged", accuracy, minHit + bonus, maxHit + bonus, ticks)
				+ (1 - accuracy) * EncounterRules.modifyHit(request, loadout, "ranged", bonus);
			return ordinary * (1 - chance) + proc * chance;
		}
		if (ammo.startsWith("diamond"))
		{
			return ordinary * (1 - tenPercent) + expectedHit(request, loadout, "ranged", 1, 0, multiply(maxHit, zaryte ? 126 : 115, 100), ticks) * tenPercent;
		}
		if (ammo.startsWith("onyx") && !isUndead(request))
		{
			return ordinary * (1 - elevenPercent) + expectedHit(request, loadout, "ranged", accuracy, 0, multiply(maxHit, zaryte ? 132 : 120, 100), ticks) * elevenPercent;
		}
		if (ammo.startsWith("dragonstone") && !isDragon(request) && !request.getMonster().hasAttribute("fiery"))
		{
			int bonus = request.getLevels().getRanged() * 2 / (zaryte ? 9 : 10);
			return ordinary * (1 - sixPercent) + expectedHit(request, loadout, "ranged", accuracy, bonus, maxHit + bonus, ticks) * sixPercent;
		}
		return ordinary;
	}

	private static long applyInquisitorBonus(Loadout loadout, long value)
	{
		int pieces = 0;
		if (wearing(loadout, "inquisitor's great helm"))
		{
			pieces++;
		}
		if (wearing(loadout, "inquisitor's hauberk"))
		{
			pieces += 2;
		}
		if (wearing(loadout, "inquisitor's plateskirt"))
		{
			pieces += 2;
		}
		if (pieces <= 0)
		{
			return value;
		}
		int numerator = 200 + pieces;
		return multiply(value, numerator, 200);
	}

	private static long tbowScaling(long current, int magic, boolean accuracyMode)
	{
		int factor = accuracyMode ? 10 : 14;
		int base = accuracyMode ? 140 : 250;
		int t2 = (3 * magic - factor) / 100;
		int inner = Math.floorDiv(3 * magic, 10) - (10 * factor);
		int t3 = Math.floorDiv(inner * inner, 100);
		int bonus = Math.max(0, Math.min(base, base + t2 - t3));
		return Math.floorDiv(current * bonus, 100);
	}

	private static int multiply(int value, int numerator, int denominator)
	{
		return (int) Math.floor((double) value * numerator / denominator);
	}

	private static long multiply(long value, int numerator, int denominator)
	{
		return (long) Math.floor((double) value * numerator / denominator);
	}
}
