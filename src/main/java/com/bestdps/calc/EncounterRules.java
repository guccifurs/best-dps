package com.bestdps.calc;

import com.bestdps.data.GearItem;
import com.bestdps.data.GearSlot;
import com.bestdps.data.MonsterStats;
import com.bestdps.data.SpellStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntToDoubleFunction;
import net.runelite.api.Skill;

/**
 * Target rules for a sustained-attack comparison, not a fight simulator.
 * Mathematical game facts and research references are recorded in docs/encounter-coverage.md.
 */
public final class EncounterRules
{
	private static final IntToDoubleFunction UNCHANGED_HIT = damage -> damage;

	private EncounterRules() { }

	public static boolean isTombsOfAmascut(MonsterStats monster)
	{
		String name = monster.normalizedName();
		return name.equals("akkha") || name.equals("akkha's shadow") || name.equals("ba-ba")
			|| name.equals("kephri") || name.equals("zebak") || name.equals("obelisk (tombs of amascut)")
			|| name.equals("tumeken's warden") || name.equals("elidinis' warden")
			|| monster.getId() == 11724 || monster.getId() == 11725 || monster.getId() == 11726;
	}

	public static boolean isTheatreOfBlood(MonsterStats monster)
	{
		String name = monster.normalizedName();
		return name.equals("the maiden of sugadinti") || name.equals("pestilent bloat")
			|| name.equals("xarpus") || name.equals("sotetseg") || name.equals("verzik vitur")
			|| name.startsWith("nylocas ") || name.equals("blood spawn") || name.equals("crab (theatre of blood)");
	}

	/** Apply once, before the candidate search. ToA defence modifies rolls, not levels. */
	public static MonsterStats scale(MonsterStats monster, EncounterContext context)
	{
		EncounterContext c = context == null ? EncounterContext.DEFAULT : context;
		int hp = monster.getHitpoints();
		int def = monster.getDefence();
		int magic = monster.getMagic();
		String name = monster.normalizedName();
		if (monster.hasAttribute("xerician"))
		{
			int[] stats = chambersStats(monster, c);
			hp = stats[0];
			def = stats[1];
			magic = stats[2];
		}
		else if (isTombsOfAmascut(monster))
		{
			boolean core = monster.normalizedVersion().contains("core-ejected");
			hp = percent(hp, c.getRaidLevel() * (core ? 1 : 4) / 10);
			boolean path = !name.contains("warden") && !name.startsWith("obelisk");
			if (path && c.getPathLevel() > 0)
			{
				hp = percent(hp, 3 + 5 * c.getPathLevel());
			}
			// Party health: 100%, 190%, 280%, then +60% per member (up to eight).
			int party = Math.min(8, c.getPartySize());
			int extra = party < 4 ? 90 * (party - 1) : 180 + 60 * (party - 3);
			hp = percent(hp, extra);
			if (hp > 100)
			{
				int step = hp > 300 ? 10 : 5;
				hp = (hp + step / 2) / step * step;
			}
		}
		else if (isTheatreOfBlood(monster))
		{
			int party = Math.min(5, c.getPartySize());
			if (monster.normalizedVersion().contains("entry"))
			{
				int[] fortieths = {0, 10, 19, 27, 34, 40};
				hp = hp * fortieths[party] / 40;
			}
			else
			{
				hp = hp * (Math.max(3, party) + 3) / 8;
			}
		}
		if (name.equals("vardorvis"))
		{
			int low = 145;
			int high = 215;
			if (monster.normalizedVersion().equals("quest"))
			{
				low = 130;
				high = 180;
			}
			else if (monster.normalizedVersion().equals("awakened"))
			{
				low = 181;
				high = 268;
			}
			def = low + (high - low) * c.getTargetHpPercent() / 100;
		}
		if (c.getDefenceOverride() >= 0)
		{
			def = c.getDefenceOverride();
		}
		return monster.withCombatStats(Math.max(1, hp), def, magic);
	}

	private static int[] chambersStats(MonsterStats monster, EncounterContext c)
	{
		String name = monster.normalizedName();
		boolean claw = name.equals("great olm") && monster.normalizedVersion().contains("claw");
		boolean defensiveMagic = claw || name.equals("tekton") || name.equals("deathly ranger")
			|| name.equals("abyssal portal") || name.equals("vespula") || name.equals("vespine soldier");
		int defenceBase = defensiveMagic ? Math.max(monster.getDefence(), monster.getMagic()) : monster.getDefence();
		int offenceBase = Math.max(monster.getAttack(), Math.max(monster.getStrength(), monster.getRanged()));
		if (!defensiveMagic)
		{
			offenceBase = Math.max(offenceBase, monster.getMagic());
		}
		int hp = monster.getHitpoints() * Math.max(60, c.getPartyMaxCombat()) / 126;
		int defence;
		int offence;
		boolean solo = name.equals("scavenger beast") || name.equals("vespine soldier");
		if (solo)
		{
			int hpStat = Math.max(55, c.getPartyMaxHitpoints());
			int combatStat = Math.max(60, c.getPartyMaxCombat());
			if (c.isChallengeMode())
			{
				hpStat = percent(hpStat, 50);
				combatStat = percent(combatStat, 50);
			}
			defence = Math.max(1, defenceBase * hpStat / 99);
			offence = Math.max(1, offenceBase * hpStat / 99);
			hp = Math.max(5, monster.getHitpoints() * combatStat / 126);
		}
		else
		{
			int team = c.getPartySize() - 1;
			int root = (int) Math.sqrt(team);
			int hpStat = Math.min(99, 55 + 44 * c.getPartyMaxHitpoints() / 99);
			defence = percent(defenceBase * hpStat / 99, root + team * 7 / 10);
			offence = percent(offenceBase * hpStat / 99, 7 * root + team);
			hp *= 1 + c.getPartySize() / 2;
			if (c.isChallengeMode())
			{
				offence = percent(offence, 50);
				if (!name.equals("glowing crystal"))
				{
					hp = percent(hp, 50);
					defence = percent(defence, name.equals("tekton") ? (c.getPartySize() < 4 ? 20 : 35) : 50);
				}
			}
			hp = Math.min(30000, Math.max(50, hp));
			defence = Math.min(20000, Math.max(50, defence));
			offence = Math.min(5000, Math.max(50, offence));
		}
		int magic = monster.getMagic() == 1 ? 1 : (defensiveMagic ? defence : offence);
		if (name.equals("great olm"))
		{
			int players = Math.min(c.getPartySize(), 50);
			int phaseScale = Math.min(c.getPartySize() - 1, 50) - 3 * (players / 8);
			hp = claw ? 600 + 300 * phaseScale : 800 + 400 * phaseScale;
			if (monster.normalizedVersion().contains("right claw"))
			{
				magic /= 2;
			}
		}
		return new int[] {hp, monster.getDefence() == 1 ? 1 : defence, magic};
	}

	private static int percent(int value, int extraPercent)
	{
		return (int) ((long) value * (100 + extraPercent) / 100);
	}

	public static long defenceRoll(MonsterStats monster, EncounterContext context, long baseRoll)
	{
		return isTombsOfAmascut(monster)
			? baseRoll * (100 + context.getRaidLevel() * 4 / 10) / 100 : baseRoll;
	}

	public static int magicDefenceLevel(MonsterStats monster)
	{
		// Most NPCs use Magic alone. These encounters explicitly use Defence instead.
		String name = monster.normalizedName();
		return name.equals("ice demon") || name.equals("verzik vitur") || name.equals("fragment of seren")
			|| monster.getId() == 11709 || monster.getId() == 11712 || monster.getId() == 9118
			? monster.getDefence() : monster.getMagic();
	}

	public static boolean isWardenPhaseTwo(MonsterStats monster)
	{
		int id = monster.getId();
		return id == 11753 || id == 11754 || id == 11756 || id == 11757;
	}

	public static boolean isWardenCore(MonsterStats monster)
	{
		return monster.getId() == 11755 || monster.getId() == 11758;
	}

	public static double forcedAccuracy(MonsterStats monster, Loadout loadout, String attackType, double accuracy)
	{
		if (monster.normalizedName().equals("verzik vitur") && monster.normalizedVersion().contains("phase 1")
			&& attackType.equals("magic") && weaponName(loadout).equals("dawnbringer"))
		{
			return 1.0;
		}
		if (monster.normalizedName().equals("tormented demon") &&
			(monster.normalizedVersion().contains("defenceless") || monster.normalizedVersion().contains("unshielded")))
		{
			return 1.0;
		}
		return accuracy;
	}

	public static double missDamage(MonsterStats monster, Loadout loadout, String attackType)
	{
		return monster.getId() == 14014 && dominantCrush(loadout, attackType) ? 1.0 : 0.0;
	}

	private static boolean dominantCrush(Loadout loadout, String attackType)
	{
		return attackType.equals("crush") && loadout.getOffensive().getCrush() > loadout.getOffensive().getSlash()
			&& loadout.getOffensive().getCrush() > loadout.getOffensive().getStab();
	}

	public static boolean tormentedBonusApplies(MonsterStats monster, Loadout loadout, String attackType, SpellStats spell)
	{
		if (!monster.normalizedName().equals("tormented demon") || !monster.normalizedVersion().contains("unshielded"))
		{
			return false;
		}
		String weapon = weaponName(loadout);
		return attackType.equals("crush") || attackType.equals("magic") && spell != null
			|| attackType.startsWith("ranged") && (weapon.contains("crossbow") || weapon.contains("ballista"));
	}

	public static int unshieldedHitBonus(MonsterStats monster, Loadout loadout, String attackType, SpellStats spell, int speed)
	{
		return tormentedBonusApplies(monster, loadout, attackType, spell) ? Math.max(0, speed * speed - 16) : 0;
	}

	public static int attackSpeed(MonsterStats monster, Loadout loadout, String attackType, SpellStats spell, int speed)
	{
		return tormentedBonusApplies(monster, loadout, attackType, spell) && speed > 4 ? 4 : speed;
	}

	public static String blockingReason(MonsterStats monster, CombatStyle style)
	{
		String blocked = blockingReason(monster);
		if (!blocked.isEmpty())
		{
			return blocked;
		}
		String name = monster.normalizedName();
		if (style == CombatStyle.ANY && (name.equals("akkha") || name.equals("nylocas vasilias")
			|| name.equals("nylocas prinkipas") || name.equals("demonic gorilla") || name.equals("tormented demon")
			|| isWardenPhaseTwo(monster)))
		{
			return "This NPC changes protection. Choose the style for its vulnerable phase and search each phase separately.";
		}
		return "";
	}

	public static String blockingReason(MonsterStats monster)
	{
		String name = monster.normalizedName();
		String version = monster.normalizedVersion();
		if (monster.getHitpoints() <= 0 || monster.getId() < 0)
		{
			return "This target has incomplete combat data; no reliable gear ranking is available.";
		}
		if (version.contains("invulnerable") || version.contains("immune") || version.equals("whirlpool")
			|| version.equals("teleporting") || name.endsWith("portal shield"))
		{
			return "This target cannot take ordinary damage in the selected phase. Choose its attackable variant.";
		}
		if (name.equals("phantom muspah") && version.equals("shielded"))
		{
			return "Muspah's prayer shield needs prayer-drain, Smite and sapphire-bolt modelling; ranking is unavailable.";
		}
		if (name.contains("tanglefoot") || name.equals("zalcano") || name.equals("hespori (echo)"))
		{
			return "This encounter changes the damage formula using skills or special mechanics not yet modelled.";
		}
		if (name.contains("dagannoth mother") || name.contains("gelatinnoth mother") || name.contains("chronozon"))
		{
			return "This encounter's changing resistances or special damage rules are not fully modelled; no false best-in-slot claim is shown.";
		}
		if (name.equals("the hueycoatl") && (version.equals("shielded") || version.contains("broken")))
		{
			return "This Hueycoatl part is not attackable. Select its body, normal head or intact tail.";
		}
		if (name.equals("eclipse moon") && version.equals("clone"))
		{
			return "The Eclipse Moon clone counterattack ignores normal attack timing; ordinary DPS cannot rank this phase.";
		}
		if (name.equals("abyssal sire") && version.equals("phase 1"))
		{
			return "Attack the respiratory systems in phase 1; select an attackable Sire phase for a damage comparison.";
		}
		if (monster.hasAttribute("penance") || name.contains("hunllef") || name.contains("(pvm arena)")
			|| version.contains("deadman") || name.contains("(deadman)") || name.contains("(echo)"))
		{
			return "This activity has restricted equipment, calls or mode modifiers outside the normal bank-gear model.";
		}
		return "";
	}

	public static String rejectionReason(MonsterStats monster, Loadout loadout, String attackType)
	{
		return rejectionReason(monster, loadout, attackType, null);
	}

	public static String rejectionReason(MonsterStats monster, Loadout loadout, String attackType, SpellStats spell)
	{
		String blocked = blockingReason(monster);
		if (!blocked.isEmpty())
		{
			return blocked;
		}
		String name = monster.normalizedName();
		String weapon = weaponName(loadout);
		boolean melee = isMelee(attackType);
		boolean magic = attackType.equals("magic");
		boolean ranged = attackType.startsWith("ranged");
		if ((name.equals("tekton") || name.equals("glowing crystal")) && ranged)
		{
			return "This target is immune to ranged damage.";
		}
		if (name.equals("salarin the twisted") && !(magic && spell != null && spell.getName().endsWith(" Strike")))
		{
			return "Salarin can only be damaged with strike spells.";
		}
		int id = monster.getId();
		if (!melee && (id == 2137 || id == 2463 || id == 2464 || id >= 2450 && id <= 2456))
		{
			return "Warriors' Guild opponents must be fought with melee.";
		}
		if (name.equals("fire warrior of lesarkus") && !(ranged && itemName(loadout.get(GearSlot.AMMO)).equals("ice arrows")))
		{
			return "The Fire Warrior requires ranged attacks with ice arrows equipped.";
		}
		if (name.equals("fareed"))
		{
			if (magic && (spell == null || !spell.getElement().equals("water")))
			{
				return "Fareed can only be damaged with water spells when using magic.";
			}
			if (melee && !itemName(loadout.get(GearSlot.HANDS)).equals("ice gloves"))
			{
				return "Wear ice gloves to keep a melee weapon equipped against Fareed.";
			}
		}
		if (name.equals("dusk") && !melee)
		{
			return "Dusk is immune to ranged and magic damage.";
		}
		if (name.equals("guardian (chambers of xeric)") && !(melee && weapon.contains("pickaxe")))
		{
			return "Guardians require melee attacks with a pickaxe.";
		}
		if (name.equals("zulrah") && melee && !weapon.contains("halberd"))
		{
			return "This target is out of reach of ordinary melee weapons.";
		}
		if (melee && (name.equals("kraken") || name.equals("cave kraken") || name.equals("the leviathan")
			|| name.equals("vespula") || name.equals("tzkal-zuk") || name.equals("jal-mejjak") || name.equals("abyssal portal")
			|| (name.equals("great olm") && monster.normalizedVersion().contains("head"))))
		{
			return "This target cannot be reached with melee.";
		}
		if (melee && monster.hasAttribute("flying") && !weapon.contains("halberd") && !weapon.contains("salamander"))
		{
			return "Flying targets require a halberd or salamander for melee.";
		}
		if (melee && name.contains("aviansie") && !weapon.contains("salamander"))
		{
			return "Aviansies only permit salamander melee attacks.";
		}
		if (isWardenPhaseTwo(monster) && melee)
		{
			return "The Warden's active phase must be attacked with the unprotected ranged or magic style.";
		}
		if (isWardenCore(monster) && !melee)
		{
			return "Select melee for the Warden core's guaranteed maximum-hit comparison.";
		}
		if (monster.hasAttribute("leafy"))
		{
			GearItem ammo = loadout.get(GearSlot.AMMO);
			boolean permitted = melee && weapon.startsWith("leaf-bladed")
				|| magic && spell != null && spell.getName().equals("Magic Dart")
				|| ranged && ammo != null && (ammo.getName().startsWith("Broad") || ammo.getName().startsWith("Amethyst broad") || ammo.getName().startsWith("Seeking broad"));
			if (!permitted)
			{
				return "Turoth and kurask need leaf-bladed melee, broad ammunition or Magic Dart.";
			}
		}
		if (monster.hasAttribute("vampyre3") && !isVampyrebane(loadout, attackType))
		{
			return "This vampyre requires an Ivandis or blisterwood weapon.";
		}
		if (monster.hasAttribute("vampyre2") && !isSilverWeapon(loadout, attackType)
			&& !itemName(loadout.get(GearSlot.RING)).startsWith("efaritay's aid"))
		{
			return "This vampyre requires silver weaponry or Efaritay's aid.";
		}
		if (name.startsWith("nylocas ischyros") && !melee || name.startsWith("nylocas toxobolos") && !ranged
			|| name.startsWith("nylocas hagios") && !magic)
		{
			return "This Nylocas is immune to the selected style.";
		}
		return "";
	}

	public static boolean hasHitModifier(MonsterStats monster)
	{
		String name = monster.normalizedName();
		return name.equals("zulrah") || name.equals("corporeal beast") || name.equals("great olm")
			|| name.equals("tekton") || name.equals("ice demon") || name.equals("kraken")
			|| name.equals("cave kraken") || name.startsWith("totem (") || name.contains("slagilith")
			|| name.equals("tormented demon") || name.equals("the hueycoatl")
			|| name.equals("guardian (chambers of xeric)") || name.equals("fragment of seren") || name.equals("glowing crystal")
			|| name.equals("verzik vitur") && monster.normalizedVersion().contains("phase 1")
			|| monster.hasAttribute("vampyre2");
	}

	/** Salarin's successful strike uses the highest unlocked bolt tier, ignoring normal damage gear. */
	public static int salarinFixedHit(OptimizationRequest request, Loadout loadout)
	{
		int magic = request.getLevels().getMagic();
		int hit = magic >= 35 ? 12 : magic >= 29 ? 11 : magic >= 23 ? 10 : 9;
		String neck = itemName(loadout.get(GearSlot.NECK));
		String element = request.getSpell() == null ? "" : request.getSpell().getElement();
		boolean boost = neck.equals("elemental amulet") || !element.isEmpty() && neck.equals("amulet of " + element)
			|| element.equals("wind") && neck.equals("amulet of air");
		return hit + (boost ? 2 : 0);
	}

	/** Resolve target and weapon conditions once, outside the damage-distribution loop. */
	public static IntToDoubleFunction hitModifier(OptimizationRequest request, Loadout loadout, String attackType)
	{
		if (request.getMonster().normalizedName().equals("guardian (chambers of xeric)"))
		{
			int mining = request.getRequirementProfile().level(Skill.MINING);
			String weapon = weaponName(loadout);
			int pickLevel;
			if (weapon.contains("dragon") || weapon.contains("crystal") || weapon.contains("3rd age") || weapon.contains("infernal"))
			{
				pickLevel = 61;
			}
			else if (weapon.contains("rune")) { pickLevel = 41; }
			else if (weapon.contains("adamant")) { pickLevel = 31; }
			else if (weapon.contains("mithril")) { pickLevel = 21; }
			else if (weapon.contains("black")) { pickLevel = 11; }
			else if (weapon.contains("steel")) { pickLevel = 6; }
			else { pickLevel = 1; }
			int multiplier = 50 + mining + pickLevel;
			return damage -> damage * multiplier / 150;
		}
		return hitModifier(request.getMonster(), loadout, attackType, request.getSpell());
	}

	public static double modifyHit(OptimizationRequest request, Loadout loadout, String attackType, int damage)
	{
		return hitModifier(request, loadout, attackType).applyAsDouble(damage);
	}

	/** Top of the resulting hit distribution, distinct from its expected value. */
	public static int maximumHit(OptimizationRequest request, Loadout loadout, String attackType, int damage)
	{
		MonsterStats monster = request.getMonster();
		String name = monster.normalizedName();
		if (name.equals("zulrah"))
		{
			return Math.min(damage, 50);
		}
		if (name.equals("fragment of seren"))
		{
			return Math.min(damage, 24);
		}
		if (name.equals("verzik vitur") && monster.normalizedVersion().contains("phase 1") && !weaponName(loadout).equals("dawnbringer"))
		{
			return Math.min(damage, isMelee(attackType) ? 10 : 3);
		}
		if (monster.getId() == 14014)
		{
			boolean crush = dominantCrush(loadout, attackType);
			boolean earth = attackType.equals("magic") && request.getSpell() != null && request.getSpell().getElement().equals("earth");
			int capped = Math.max(crush ? 1 : 0, Math.min(damage, crush || earth ? 9 : 4));
			return monster.normalizedVersion().contains("with pillar") ? capped * 13 / 10 : capped;
		}
		return (int) modifyHit(request, loadout, attackType, damage);
	}

	public static double modifyHit(MonsterStats monster, Loadout loadout, String attackType, int damage)
	{
		return modifyHit(monster, loadout, attackType, null, damage);
	}

	/** A transformed individual hit, including expectations for random damage caps. */
	public static double modifyHit(MonsterStats monster, Loadout loadout, String attackType, SpellStats spell, int damage)
	{
		return hitModifier(monster, loadout, attackType, spell).applyAsDouble(damage);
	}

	private static IntToDoubleFunction hitModifier(MonsterStats monster, Loadout loadout, String attackType, SpellStats spell)
	{
		String name = monster.normalizedName();
		if (monster.hasAttribute("vampyre2") && !isSilverWeapon(loadout, attackType)
			&& itemName(loadout.get(GearSlot.RING)).startsWith("efaritay's aid"))
		{
			return damage -> damage / 2;
		}
		if (name.equals("fragment of seren"))
		{
			return damage -> (Math.min(damage, 22) + Math.min(damage, 23) + Math.min(damage, 24)) / 3.0;
		}
		if (name.equals("glowing crystal") && attackType.equals("magic"))
		{
			return damage -> damage / 3;
		}
		if (name.equals("tormented demon") && !monster.normalizedVersion().contains("unshielded"))
		{
			String weapon = weaponName(loadout);
			boolean bypass = isMelee(attackType) && (weapon.startsWith("abyssal ") || weapon.contains("silverlight")
				|| weapon.contains("darklight") || weapon.contains("arclight") || weapon.contains("emberlight")
				|| weapon.contains("burning claws") || weapon.contains("bone claws"))
				|| attackType.startsWith("ranged") && weapon.contains("scorching bow")
				|| attackType.equals("magic") && spell != null && spell.getName().contains("Demonbane");
			return bypass ? UNCHANGED_HIT : damage -> Math.max(1, damage * 4 / 5);
		}
		if (name.equals("the hueycoatl"))
		{
			boolean pillar = monster.normalizedVersion().contains("with pillar");
			if (monster.getId() == 14014)
			{
				boolean crush = dominantCrush(loadout, attackType);
				boolean earth = attackType.equals("magic") && spell != null && spell.getElement().equals("earth");
				int cap = crush || earth ? 9 : 4;
				// A tail hit cannot exceed its cap. Resolve the small cap distribution just once.
				double[] cappedHits = new double[cap + 1];
				for (int damage = 0; damage <= cap; damage++)
				{
					for (int roll = 0; roll <= cap; roll++)
					{
						int hit = Math.max(crush ? 1 : 0, Math.min(damage, roll));
						cappedHits[damage] += pillar ? hit * 13 / 10 : hit;
					}
					cappedHits[damage] /= cap + 1;
				}
				return damage -> cappedHits[Math.min(damage, cap)];
			}
			return pillar ? damage -> damage * 13 / 10 : UNCHANGED_HIT;
		}
		if (name.equals("verzik vitur") && monster.normalizedVersion().contains("phase 1") && !weaponName(loadout).equals("dawnbringer"))
		{
			int cap = isMelee(attackType) ? 10 : 3;
			return damage -> expectedCappedHit(damage, cap);
		}
		if (name.equals("zulrah"))
		{
			return damage -> damage > 50 ? 47.5 : damage;
		}
		if (name.equals("corporeal beast") && !attackType.equals("magic"))
		{
			GearItem weapon = loadout.getWeapon();
			String weaponName = weaponName(loadout);
			boolean corpbane = attackType.equals("stab") && weapon != null
				&& (weaponName.equals("sunspear") || weapon.isTwoHanded()
					&& (weapon.getCategory().equalsIgnoreCase("spear") || weaponName.contains("halberd")));
			return corpbane ? UNCHANGED_HIT : damage -> damage / 2;
		}
		if (name.equals("great olm"))
		{
			String phase = monster.normalizedVersion();
			boolean correct = phase.contains("head") && attackType.startsWith("ranged")
				|| phase.contains("left claw") && isMelee(attackType)
				|| phase.contains("right claw") && attackType.equals("magic");
			return correct ? UNCHANGED_HIT : damage -> damage / 3;
		}
		if (name.equals("tekton") && attackType.equals("magic"))
		{
			return damage -> damage / 5;
		}
		if (name.equals("ice demon") && !(attackType.equals("magic") && spell != null && spell.getElement().equals("fire")))
		{
			return damage -> damage / 3;
		}
		if ((name.equals("kraken") || name.equals("cave kraken")) && attackType.startsWith("ranged"))
		{
			return damage -> Math.max(1, damage / 7);
		}
		if (name.startsWith("totem (") && attackType.equals("magic"))
		{
			return damage -> damage * 2;
		}
		if (name.contains("slagilith") && !weaponName(loadout).contains("pickaxe"))
		{
			return damage -> damage / 3;
		}
		return UNCHANGED_HIT;
	}

	private static double expectedCappedHit(int hit, int cap)
	{
		int bound = Math.min(hit, cap);
		return (bound * (bound - 1) / 2.0 + bound * (cap - bound + 1)) / (cap + 1);
	}

	public static List<String> warnings(MonsterStats monster, EncounterContext context)
	{
		List<String> warnings = new ArrayList<>();
		warnings.add("Sustained attacks in the selected phase; movement, mechanics, incoming damage, special attacks and whole-fight gear switches are not simulated.");
		String blocked = blockingReason(monster);
		if (!blocked.isEmpty())
		{
			warnings.add(blocked);
			return warnings;
		}
		String name = monster.normalizedName();
		if (monster.hasAttribute("xerician"))
		{
			warnings.add("CoX stats use party size, highest combat/HP and Challenge Mode. Set current Defence after any drains.");
		}
		if (isTombsOfAmascut(monster))
		{
			warnings.add("ToA: invocation scales defence rolls; path level and party size scale HP. Input the current path, not the raid's starting level.");
		}
		if (isTheatreOfBlood(monster))
		{
			warnings.add("ToB party size changes HP only; select the correct Entry, Normal or Hard variant.");
		}
		if (name.equals("akkha") || name.equals("nylocas vasilias") || name.equals("nylocas prinkipas") || name.equals("demonic gorilla"))
		{
			warnings.add("Assumes this style is currently unprotected; search each vulnerable style separately. Final/transition phases are excluded.");
		}
		if (name.equals("vardorvis"))
		{
			warnings.add("Vardorvis Defence follows target HP %. This is a snapshot, not an average across the kill.");
		}
		if (name.equals("tormented demon"))
		{
			warnings.add("Choose Shielded, Defenceless or the single Unshielded bonus hit. Assumes an unprotected style and one demon; unshielded timing is not sustained fight DPS.");
		}
		if (name.equals("the hueycoatl"))
		{
			warnings.add("Tail random damage limits, dominant-crush misses and the selected pillar bonus are modelled; attack a separate part for each phase.");
		}
		if (isWardenPhaseTwo(monster))
		{
			warnings.add("Phase 2 uses its accuracy-to-damage formula; selected style must be unprotected. Repeat for the other style.");
		}
		if (isWardenCore(monster))
		{
			warnings.add("Core melee hits use maximum damage. The Warden loses five times the core hit; special-attack energy and exposure timing are excluded.");
		}
		if (name.equals("verzik vitur") && monster.normalizedVersion().contains("phase 1"))
		{
			warnings.add("Phase 1 random damage caps are included. This compares normal attacks between Dawnbringer specials, not the special-attack rotation.");
		}
		if (name.equals("guardian (chambers of xeric)"))
		{
			warnings.add("Guardian damage uses your base Mining and equipped pickaxe; temporary Mining boosts are excluded. HP scaling assumes party average Mining 99.");
		}
		if (name.equals("salarin the twisted"))
		{
			warnings.add("Strike spells use fixed successful-hit damage from your highest unlocked bolt tier; only matching elemental amulets boost damage. Splashing remains possible.");
		}
		if (name.equals("doom of mokhaiotl"))
		{
			warnings.add("Uses the selected normal form and delve stats; demonbane shield phases are not represented by the base variant.");
		}
		if (name.equals("yama"))
		{
			warnings.add("Choose Yama's phase and the tank's attack style. Demonbane vulnerability is included; pre-teleport resistance, contracts beyond the selected variant and duo uptime are excluded.");
		}
		if (name.equals("zulrah"))
		{
			warnings.add("Zulrah's selected form, halberd reach and 45-50 reroll above 50 damage are modelled; rotation downtime is excluded.");
		}
		if (name.equals("great olm"))
		{
			warnings.add("Olm claw/head damage resistance is modelled. Head damage only counts in the final phase; movement cycles are excluded.");
		}
		if (name.equals("corporeal beast"))
		{
			warnings.add("Corp damage reduction is modelled. Set Defence after special attacks; Magic drains and regeneration are not simulated.");
		}
		if (name.contains("moon"))
		{
			warnings.add("Moon flat armour is modelled; incoming-hit effects, healing, freeze and burn cycles can change practical gear choices.");
		}
		if (name.equals("alchemical hydra"))
		{
			warnings.add("Assumes Hydra has been weakened with the correct chemical vent; unweakened resistance is excluded.");
		}
		if (name.equals("duke sucellus") || name.equals("pestilent bloat") || name.equals("hespori")
			|| name.equals("araxxor") || name.equals("the leviathan") || name.equals("phantom muspah")
			|| name.equals("the nightmare") || name.equals("phosani's nightmare") || name.equals("the whisperer")
			|| name.equals("sol heredit") || name.equals("doom of mokhaiotl"))
		{
			warnings.add("Only the selected attackable state is compared; preparation, vulnerability windows, enrages and encounter-specific uptime are excluded.");
		}
		if (monster.hasAttribute("vampyre1") || monster.hasAttribute("vampyre2") || monster.hasAttribute("vampyre3"))
		{
			warnings.add("Vampyre tier restrictions and weapon bonuses apply. Vampyre Juvenile/Juvinate finishing actions and flail cremation mastery are not simulated.");
		}
		if (context != null && context.getDefenceOverride() >= 0)
		{
			warnings.add("Manual Defence override is used as entered; encounter drain floors are your responsibility.");
		}
		return warnings;
	}

	private static boolean isMelee(String type)
	{
		return type.equals("stab") || type.equals("slash") || type.equals("crush");
	}

	private static boolean isSilverWeapon(Loadout loadout, String type)
	{
		String weapon = weaponName(loadout);
		if (isMelee(type))
		{
			return weapon.contains("silver") || weapon.contains("wolfbane") || weapon.contains("blessed axe")
				|| weapon.contains("darklight") || weapon.contains("arclight") || weapon.contains("emberlight")
				|| weapon.contains("ivandis") || weapon.contains("blisterwood") || weapon.equals("hallowed flail") || weapon.equals("sunspear");
		}
		return type.startsWith("ranged") && (weapon.equals("blisterwood stake") || itemName(loadout.get(GearSlot.AMMO)).startsWith("silver bolts"));
	}

	private static boolean isVampyrebane(Loadout loadout, String type)
	{
		String weapon = weaponName(loadout);
		return isMelee(type) && (weapon.contains("ivandis flail") || weapon.contains("blisterwood sickle")
			|| weapon.contains("blisterwood flail") || weapon.equals("hallowed flail") || weapon.equals("sunspear"))
			|| type.startsWith("ranged") && weapon.equals("blisterwood stake");
	}

	private static String weaponName(Loadout loadout) { return itemName(loadout.getWeapon()); }
	private static String itemName(GearItem item) { return item == null ? "" : item.getName().toLowerCase(Locale.ROOT); }
}
