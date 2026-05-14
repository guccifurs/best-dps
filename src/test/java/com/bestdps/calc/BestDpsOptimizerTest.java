package com.bestdps.calc;

import com.bestdps.data.BestDpsData;
import com.bestdps.data.BestDpsDataService;
import com.bestdps.data.GearSlot;
import com.bestdps.data.MonsterStats;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Assert;
import org.junit.Test;

public class BestDpsOptimizerTest
{
	@Test
	public void optimizerReturnsLegalStandardGear()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("zulrah", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.RANGED,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			10_000_000,
			CandidateMode.BUDGET,
			false,
			false,
			OwnedItems.EMPTY,
			10);

		List<DpsResult> results = new BestDpsOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertTrue(results.get(0).getDps() > 0.0);
		Assert.assertTrue(results.get(0).getPurchaseCost() <= 10_000_000);
		results.get(0).getLoadout().getGear().values().forEach(item -> Assert.assertTrue(item.isStandardGear()));
	}

	@Test
	public void anyStyleReturnsBestConcreteSetups()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("zulrah", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.ANY,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			10_000_000,
			CandidateMode.OWNED_OR_BUDGET,
			false,
			false,
			OwnedItems.EMPTY,
			10);

		List<DpsResult> results = new BestDpsOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertTrue(results.stream().allMatch(result -> result.getDps() > 0.0));
	}

	@Test
	public void ownedGearDoesNotConsumePurchaseBudget()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			10);

		List<DpsResult> results = new BestDpsOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertEquals(0, results.get(0).getPurchaseCost());
		Assert.assertTrue(results.get(0).getLoadout().getCost() > 0);
	}

	@Test
	public void untradeablesMustBeCachedAndEnabled()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		owned.put(6570, 1);

		OptimizationRequest disabled = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			10);
		OptimizationRequest enabled = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			true,
			false,
			new OwnedItems(owned, true),
			10);

		DpsResult withoutUntradeable = new BestDpsOptimizer().optimize(data, disabled).get(0);
		DpsResult withUntradeable = new BestDpsOptimizer().optimize(data, enabled).get(0);
		Assert.assertNull(withoutUntradeable.getLoadout().get(GearSlot.CAPE));
		Assert.assertEquals(6570, withUntradeable.getLoadout().get(GearSlot.CAPE).getId());
	}

	@Test
	public void useMyLevelsBlocksGearAboveCurrentLevel()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			new PlayerLevels(1, 99, 99, 99, 99, 99, 99),
			PrayerBonuses.NONE,
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			requirements(1, Collections.emptySet()),
			10);

		List<DpsResult> results = new BestDpsOptimizer().optimize(data, request);
		Assert.assertTrue(results.isEmpty());
	}

	@Test
	public void questRequirementsBlockLockedQuestGear()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4587, 1);
		OptimizationRequest locked = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			requirements(99, Collections.emptySet()),
			10);
		Set<String> quests = new HashSet<>();
		quests.add(Quest.MONKEY_MADNESS_I.name());
		OptimizationRequest unlocked = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			requirements(99, quests),
			10);

		Assert.assertTrue(new BestDpsOptimizer().optimize(data, locked).isEmpty());
		Assert.assertFalse(new BestDpsOptimizer().optimize(data, unlocked).isEmpty());
	}

	@Test
	public void magicAutomaticallyChoosesBestAvailableSpell()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MAGIC,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			10_000_000,
			CandidateMode.OWNED_OR_BUDGET,
			false,
			false,
			OwnedItems.EMPTY,
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new BestDpsOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertFalse(results.get(0).getSpellName().isEmpty());
		Assert.assertTrue(results.get(0).getAttackType().startsWith("magic: "));
	}

	@Test
	public void autoMagicDoesNotUseDemonbaneOnNonDemon()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MAGIC,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			10_000_000,
			CandidateMode.OWNED_OR_BUDGET,
			false,
			false,
			OwnedItems.EMPTY,
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new BestDpsOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertFalse(results.get(0).getSpellName().contains("Demonbane"));
	}

	@Test
	public void superCombatBoostRaisesMeleeDps()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		PlayerLevels base = new PlayerLevels(70, 70, 70, 70, 70, 70, 70);
		OptimizationRequest normal = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			base,
			PrayerBonuses.NONE,
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);
		OptimizationRequest boosted = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			base.boosted(BoostProfile.SUPER_COMBAT, base),
			PrayerBonuses.NONE,
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);

		Assert.assertTrue(new BestDpsOptimizer().optimize(data, boosted).get(0).getDps()
			> new BestDpsOptimizer().optimize(data, normal).get(0).getDps());
	}

	@Test
	public void bestAvailablePrayersUseExactRangedAndMagicFactors()
	{
		PrayerBonuses prayers = PrayerBonuses.bestAvailable(PlayerLevels.MAXED);
		Assert.assertEquals(1.20, prayers.getRangedAccuracy(), 0.00001);
		Assert.assertEquals(1.23, prayers.getRangedStrength(), 0.00001);
		Assert.assertEquals(1.25, prayers.getMagicAccuracy(), 0.00001);
		Assert.assertEquals(4.0, prayers.getMagicDamagePercent(), 0.00001);
	}

	@Test
	public void slayerHeadIsIgnoredForNonSlayerMonster()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		owned.put(8901, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			true,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new BestDpsOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertNull(results.get(0).getLoadout().get(GearSlot.HEAD));
	}

	@Test
	public void slayerTaskCanChooseMeleeSlayerHead()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("aberrant spectre", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		owned.put(8901, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			true,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new BestDpsOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertNotNull(results.get(0).getLoadout().get(GearSlot.HEAD));
		Assert.assertTrue(results.get(0).getLoadout().get(GearSlot.HEAD).isSlayerHead());
	}

	@Test
	public void slayerTaskCanChooseImbuedRangedSlayerHead()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("aberrant spectre", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(861, 1);
		owned.put(11864, 1);
		owned.put(11865, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.RANGED,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			true,
			true,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new BestDpsOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertNotNull(results.get(0).getLoadout().get(GearSlot.HEAD));
		Assert.assertTrue(results.get(0).getLoadout().get(GearSlot.HEAD).isImbuedSlayerHead());
	}

	@Test
	public void slayerTaskCanChooseImbuedMagicSlayerHead()
	{
		BestDpsData data = new BestDpsDataService().load();
		MonsterStats monster = data.searchMonsters("aberrant spectre", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(1381, 1);
		owned.put(11865, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MAGIC,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			true,
			true,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new BestDpsOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertNotNull(results.get(0).getLoadout().get(GearSlot.HEAD));
		Assert.assertTrue(results.get(0).getLoadout().get(GearSlot.HEAD).isImbuedSlayerHead());
	}

	private static RequirementProfile requirements(int level, Set<String> quests)
	{
		EnumMap<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				levels.put(skill, level);
			}
		}
		return new RequirementProfile(levels, quests);
	}
}
