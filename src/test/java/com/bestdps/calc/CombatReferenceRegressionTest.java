package com.bestdps.calc;

import com.bestdps.data.GearItem;
import com.bestdps.data.GearRequirements;
import com.bestdps.data.GearSlot;
import com.bestdps.data.MonsterDefences;
import com.bestdps.data.MonsterStats;
import com.bestdps.data.SpellStats;
import com.bestdps.data.StatBlock;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import net.runelite.api.Prayer;
import org.junit.Assert;
import org.junit.Test;

/** Independently calculated cases checked against Wiki calculator revision 89c3e25 and live item rules. */
public class CombatReferenceRegressionTest
{
	private static final MonsterStats TARGET = new MonsterStats(200000, "Reference target", "", 1, 1000,
		1, 1, MonsterDefences.ZERO, Collections.emptyList());

	@Test
	public void percentageBoostsKeepExactIntegerBoundaries()
	{
		Assert.assertEquals(123, RollMath.effectiveLevel(100, 1.15, 0));
		Assert.assertEquals(115, RollMath.prayerLevel(100, 1.15));
		Assert.assertEquals(10, RollMath.effectiveDamageLevel(1, 1.05, 0));
		Assert.assertEquals(28, RollMath.effectiveDamageLevel(19, 1.05, 0));
	}

	@Test
	public void atlatlUsesRangedPrayerWhileReadingMeleeStrength()
	{
		Loadout loadout = loadout(
			gear(29000, "Eclipse atlatl", GearSlot.WEAPON, "Bow", 4, stats(0, 0, 87, 0, 0), stats(0, 0, 0, 40, 0)),
			gear(28991, "Atlatl dart", GearSlot.AMMO, "", 0, StatBlock.ZERO, StatBlock.ZERO));
		DpsResult piety = calculate(TARGET, CombatStyle.RANGED, loadout, PrayerBonuses.fromActive(EnumSet.of(Prayer.PIETY)), null);
		DpsResult rigour = calculate(TARGET, CombatStyle.RANGED, loadout, PrayerBonuses.fromActive(EnumSet.of(Prayer.RIGOUR)), null);
		Assert.assertEquals(17, piety.getMaxHit());
		Assert.assertEquals(21, rigour.getMaxHit());
		Assert.assertEquals(16157, piety.getAttackRoll());
		Assert.assertEquals(19026, rigour.getAttackRoll());
	}

	@Test
	public void magicDamageBonusIsAddedBeforeRounding()
	{
		Loadout loadout = loadout(gear(1, "Reference magic gear", GearSlot.BODY, "", 0,
			StatBlock.ZERO, stats(0, 0, 0, 0, 160)));
		DpsResult result = calculate(TARGET, CombatStyle.MAGIC, loadout, PrayerBonuses.NONE,
			new SpellStats("Ice Blitz", 25, 82, "ancient", ""));
		Assert.assertEquals(29, result.getMaxHit()); // 25 + floor(25 * 160 / 1000), not a binary-float 28.
	}

	@Test
	public void twinflameIncludesSmokeBonusAndAllowsZeroSecondarySplat()
	{
		Loadout loadout = loadout(gear(30634, "Twinflame staff", GearSlot.WEAPON, "Staff", 6,
			stats(0, 12, 0, 0, 0), StatBlock.ZERO));
		DpsResult result = calculate(TARGET, CombatStyle.MAGIC, loadout, PrayerBonuses.NONE,
			new SpellStats("Fire Bolt", 12, 35, "standard", "fire"));
		Assert.assertEquals(9028, result.getAttackRoll());
		Assert.assertEquals(18, result.getMaxHit()); // 13 main + floor(13 * 0.4).
		Assert.assertEquals(6, result.getAttackSpeed());
		Assert.assertEquals(8.473363606157935, result.getExpectedHit(), 1e-12); // sum of both splats = 123/14 before accuracy.
	}

	@Test
	public void explicitSpellOnShadowUsesManualCastingRules()
	{
		Loadout loadout = loadout(gear(27275, "Tumeken's shadow", GearSlot.WEAPON, "Powered staff", 5,
			stats(0, 35, 0, 0, 0), stats(0, 0, 0, 0, 100)));
		DpsResult result = calculate(TARGET, CombatStyle.MAGIC, loadout, PrayerBonuses.NONE,
			new SpellStats("Fire Bolt", 12, 35, "standard", "fire"));
		Assert.assertEquals(10692, result.getAttackRoll()); // (99 + 9) * (35 + 64), without tripling.
		Assert.assertEquals(13, result.getMaxHit());
		Assert.assertEquals("Fire Bolt", result.getSpellName());
	}

	@Test
	public void avariceAppliesItsNormalRevenantBonus()
	{
		MonsterStats revenant = new MonsterStats(200001, "Revenant goblin", "", 1, 1000, 1, 1,
			MonsterDefences.ZERO, Arrays.asList("undead"));
		GearItem neck = gear(22557, "Amulet of avarice", GearSlot.NECK, "", 0, StatBlock.ZERO, StatBlock.ZERO);
		DpsResult result = calculate(revenant, CombatStyle.MAGIC, loadout(neck), PrayerBonuses.NONE,
			new SpellStats("Fire Bolt", 12, 35, "standard", "fire"));
		Assert.assertEquals(8294, result.getAttackRoll());
		Assert.assertEquals(14, result.getMaxHit());
	}

	@Test
	public void karilDamnedPassiveSurvivesItemNameCapitalization()
	{
		Loadout loadout = loadout(
			gear(4734, "Karil's crossbow", GearSlot.WEAPON, "Crossbow", 4, stats(0, 0, 84, 0, 0), StatBlock.ZERO),
			gear(4732, "Karil's coif", GearSlot.HEAD, "", 0, stats(0, 0, 7, 0, 0), StatBlock.ZERO),
			gear(4736, "Karil's leathertop", GearSlot.BODY, "", 0, stats(0, 0, 30, 0, 0), StatBlock.ZERO),
			gear(4738, "Karil's leatherskirt", GearSlot.LEGS, "", 0, stats(0, 0, 17, 0, 0), StatBlock.ZERO),
			gear(12851, "Amulet of the Damned", GearSlot.NECK, "", 0, stats(0, 0, 10, 0, 0), StatBlock.ZERO),
			gear(4740, "Bolt rack", GearSlot.AMMO, "", 0, StatBlock.ZERO, new StatBlock(0, 0, 0, 0, 0, 0, 55, 0, 0)));
		DpsResult result = calculate(TARGET, CombatStyle.RANGED, loadout, PrayerBonuses.NONE, null);
		double accuracy = 1.0 - 642.0 / (2.0 * 22685.0);
		Assert.assertEquals(22684, result.getAttackRoll());
		Assert.assertEquals(30, result.getMaxHit());
		Assert.assertEquals(accuracy * (211.0 / 21.0 + 0.25 * 102.0 / 21.0), result.getExpectedHit(), 1e-12);
	}

	@Test
	public void markOfDarknessPrecedesTormentedFlatDamage()
	{
		MonsterStats demon = new MonsterStats(200002, "Tormented demon", "Unshielded", 1, 1000, 1, 1,
			MonsterDefences.ZERO, Arrays.asList("demon"));
		Loadout loadout = loadout(gear(29594, "Purging staff", GearSlot.WEAPON, "Staff", 5, StatBlock.ZERO, StatBlock.ZERO));
		OptimizationRequest request = new OptimizationRequest(demon, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.NONE, new SpellStats("Dark Demonbane", 30, 82, "arceuus", ""), 0, CandidateMode.OWNED_ONLY,
			true, false, OwnedItems.EMPTY, 1).withCombatContext(new CombatContext(99, 99, 0, true, false, false));
		DpsResult result = new DpsCalculator().calculate(request, loadout);
		Assert.assertEquals(54, result.getMaxHit()); // 30 + floor(30 / 2) + (5 squared - 16).
		Assert.assertEquals(4, result.getAttackSpeed());
		Assert.assertEquals(969.0 / 31.0, result.getExpectedHit(), 1e-12);
	}

	@Test
	public void ahrimProcScalesDamageAfterMarkOfDarkness()
	{
		MonsterStats demon = new MonsterStats(200003, "Reference demon", "", 1, 1000, 1, 1,
			MonsterDefences.ZERO, Arrays.asList("demon"));
		Loadout loadout = loadout(
			gear(4710, "Ahrim's staff", GearSlot.WEAPON, "Staff", 5, StatBlock.ZERO, StatBlock.ZERO),
			gear(4708, "Ahrim's hood", GearSlot.HEAD, "", 0, StatBlock.ZERO, StatBlock.ZERO),
			gear(4712, "Ahrim's robetop", GearSlot.BODY, "", 0, StatBlock.ZERO, StatBlock.ZERO),
			gear(4714, "Ahrim's robeskirt", GearSlot.LEGS, "", 0, StatBlock.ZERO, StatBlock.ZERO),
			gear(12851, "Amulet of the Damned", GearSlot.NECK, "", 0, StatBlock.ZERO, StatBlock.ZERO));
		OptimizationRequest request = new OptimizationRequest(demon, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.NONE, new SpellStats("Dark Demonbane", 30, 82, "arceuus", ""), 0, CandidateMode.OWNED_ONLY,
			true, false, OwnedItems.EMPTY, 1).withCombatContext(new CombatContext(99, 99, 0, true, false, false));
		DpsResult result = new DpsCalculator().calculate(request, loadout);
		Assert.assertEquals(48, result.getMaxHit()); // floor((30 + floor(30 / 4)) * 1.3).
		Assert.assertEquals(result.getAccuracy() * (571.0 * 0.75 + 728.0 * 0.25) / 31.0, result.getExpectedHit(), 1e-12);
	}

	private static DpsResult calculate(MonsterStats monster, CombatStyle style, Loadout gear, PrayerBonuses prayers, SpellStats spell)
	{
		OptimizationRequest request = new OptimizationRequest(monster, style, PlayerLevels.MAXED, prayers,
			spell, 0, CandidateMode.OWNED_ONLY, true, false, OwnedItems.EMPTY, 1);
		return new DpsCalculator().calculate(request, gear);
	}

	private static StatBlock stats(int crush, int magic, int ranged, int strength, int magicDamage)
	{
		return new StatBlock(0, 0, crush, magic, ranged, strength, 0, magicDamage, 0);
	}

	private static GearItem gear(int id, String name, GearSlot slot, String category, int speed, StatBlock offensive, StatBlock bonuses)
	{
		return new GearItem(id, name, "", slot, category, speed, false, true, false, 0,
			offensive, StatBlock.ZERO, bonuses, GearRequirements.NONE);
	}

	private static Loadout loadout(GearItem... pieces)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		for (GearItem item : pieces) gear.put(item.getSlot(), item);
		return new Loadout(gear);
	}
}
