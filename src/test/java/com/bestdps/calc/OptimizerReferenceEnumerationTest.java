package com.bestdps.calc;

import com.bestdps.data.BestDpsData;
import com.bestdps.data.BestDpsDataService;
import com.bestdps.data.GearItem;
import com.bestdps.data.GearSlot;
import com.bestdps.data.MonsterStats;
import com.bestdps.data.SpellStats;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/** Exact enumeration is an independent search reference, not an independent combat formula. */
public class OptimizerReferenceEnumerationTest
{
	private final BestDpsData all = new BestDpsDataService().load();
	private long combinations;
	private int cases;
	private long optimizerNanos;

	@Test
	public void effectOnlyAndDefenceDerivedDamageCandidatesMatchEnumeration() throws Exception
	{
		List<String> failures = new ArrayList<>();
		MonsterStats target = all.searchMonsters("goblin", 1).get(0);
		compare(new int[]{29000, 28991, 1725, 6585, 11832, 27229}, target, CombatStyle.RANGED, 100_000_000, failures);
		compare(new int[]{21015, 4749, 11832, 4751, 11834}, target, CombatStyle.MELEE, 100_000_000, failures);
		SpellStats bolt = all.getSpells().stream().filter(spell -> spell.getName().equals("Fire Bolt")).findFirst().get();
		compare(new int[]{1381, 34428, 12002}, target, CombatStyle.MAGIC, 100_000_000, failures, bolt);
		System.out.println("EFFECT_POOL_REFERENCE cases=" + cases + " combinations=" + combinations + " failures=" + failures.size());
		Assert.assertTrue(String.join("\n", failures), failures.isEmpty());
	}

	@Test
	public void largerRealPoolsCheckHeuristicBudgetAndSetTradeoffs() throws Exception
	{
		int[] melee = {4151, 4587, 11804, 6528, 4755, 21015, 1704, 1725, 6585, 19553, 11128,
			11832, 21301, 26384, 4757, 4749, 11834, 21304, 4087, 4759, 4751,
			6737, 6735, 28307, 28316, 1163, 4753, 22326};
		List<String> failures = new ArrayList<>();
		for (String target : new String[]{"goblin", "general graardor"})
		{
			for (int budget : new int[]{10_000_000, 30_000_000, 80_000_000, 250_000_000})
			{
				compare(melee, all.searchMonsters(target, 1).get(0), CombatStyle.MELEE, budget, failures);
			}
		}
		System.out.println("HEURISTIC_EXACT_REFERENCE cases=" + cases + " combinations=" + combinations + " optimizerMs="
			+ optimizerNanos / 1_000_000 + " failures=" + failures.size());
		Assert.assertTrue(String.join("\n", failures), failures.isEmpty());
	}

	@Test
	public void boundedRealGearPoolsMatchFullEnumeration() throws Exception
	{
		int[] melee = {4151, 4587, 11804, 6528, 1704, 1725, 6585, 19553, 11128,
			11832, 21301, 26384, 11834, 21304, 4087, 6737, 6735, 28307, 28316};
		int[] ranged = {861, 9185, 21902, 8880, 892, 21326, 9144, 9242, 9243, 8882,
			1704, 6585, 19547, 27229, 11828, 2503, 27232, 11830, 2497};
		List<String> failures = new ArrayList<>();
		for (String target : new String[]{"goblin", "vorkath", "general graardor"})
		{
			MonsterStats monster = all.searchMonsters(target, 1).get(0);
			for (int budget : new int[]{100_000, 1_000_000, 5_000_000, 20_000_000, 50_000_000, 150_000_000})
			{
				compare(melee, monster, CombatStyle.MELEE, budget, failures);
				compare(ranged, monster, CombatStyle.RANGED, budget, failures);
			}
		}
		System.out.println("EXACT_OPTIMIZER_REFERENCE cases=" + cases + " combinations=" + combinations
			+ " optimizerMs=" + optimizerNanos / 1_000_000 + " failures=" + failures.size());
		Assert.assertTrue(String.join("\n", failures), failures.isEmpty());
	}

	private void compare(int[] ids, MonsterStats monster, CombatStyle style, int budget, List<String> failures) throws Exception
	{
		compare(ids, monster, style, budget, failures, null);
	}

	private void compare(int[] ids, MonsterStats monster, CombatStyle style, int budget, List<String> failures, SpellStats spell) throws Exception
	{
		List<GearItem> items = new ArrayList<>();
		Map<Integer, GearItem> byId = new HashMap<>();
		for (int id : ids)
		{
			GearItem item = all.getGear(id);
			Assert.assertNotNull("Catalog item " + id, item);
			items.add(item);
			byId.put(id, item);
		}
		Constructor<BestDpsData> constructor = BestDpsData.class.getDeclaredConstructor(List.class, List.class, List.class, Map.class);
		constructor.setAccessible(true);
		BestDpsData data = constructor.newInstance(items, Collections.singletonList(monster), all.getSpells(), byId);
		OptimizationRequest request = new OptimizationRequest(monster, style, PlayerLevels.MAXED, PrayerBonuses.NONE,
			spell, budget, CandidateMode.BUDGET, false, false, OwnedItems.EMPTY, RequirementProfile.MAXED, 5);
		EnumMap<GearSlot, List<GearItem>> pools = new EnumMap<>(GearSlot.class);
		for (GearSlot slot : GearSlot.values())
		{
			List<GearItem> options = new ArrayList<>();
			options.add(null);
			pools.put(slot, options);
		}
		for (GearItem item : items)
		{
			if (item.isTradeable() && item.isStandardGear() && item.getPriceOrZero() > 0
				&& item.getPriceOrZero() <= budget && request.getRequirementProfile().canEquip(item.getRequirements()))
			{
				pools.get(item.getSlot()).add(item);
			}
		}
		double[] exact = {0};
		String[] exactGear = {""};
		enumerate(request, pools, new EnumMap<>(GearSlot.class), 0, 0, exact, exactGear);
		long start = System.nanoTime();
		List<DpsResult> results = new BestDpsOptimizer().optimize(data, request);
		optimizerNanos += System.nanoTime() - start;
		cases++;
		double actual = results.isEmpty() ? 0 : results.get(0).getDps();
		if (Math.abs(exact[0] - actual) > 1e-10)
		{
			failures.add(monster.label() + " " + style + " budget=" + budget + " exact=" + exact[0] + " actual=" + actual
				+ " bestGear=" + exactGear[0] + " actualGear=" + (results.isEmpty() ? "none" : signature(results.get(0).getLoadout())));
		}
		for (DpsResult result : results)
		{
			Assert.assertTrue(result.getTradePlan().getCashNeeded() <= budget);
			Assert.assertTrue(result.getLoadout().getWeapon() == null || !result.getLoadout().getWeapon().isTwoHanded()
				|| result.getLoadout().get(GearSlot.SHIELD) == null);
		}
	}

	private void enumerate(OptimizationRequest request, Map<GearSlot, List<GearItem>> pools,
		EnumMap<GearSlot, GearItem> gear, int index, long cost, double[] best, String[] bestGear)
	{
		if (index == GearSlot.values().length)
		{
			GearItem weapon = gear.get(GearSlot.WEAPON);
			if (request.getStyle() == CombatStyle.RANGED && (weapon == null || !RangedAmmo.compatible(gear.get(GearSlot.AMMO), weapon))) { return; }
			if (weapon != null && weapon.isTwoHanded() && gear.containsKey(GearSlot.SHIELD)) { return; }
			combinations++;
			DpsResult result = new DpsCalculator().calculate(request, new Loadout(gear));
			if (result.getDps() > best[0]) { best[0] = result.getDps(); bestGear[0] = signature(result.getLoadout()); }
			return;
		}
		GearSlot slot = GearSlot.values()[index];
		for (GearItem item : pools.get(slot))
		{
			long price = item == null ? 0 : item.getPriceOrZero();
			if (cost + price > request.getBudget()) { continue; }
			if (item == null) { gear.remove(slot); } else { gear.put(slot, item); }
			enumerate(request, pools, gear, index + 1, cost + price, best, bestGear);
		}
		gear.remove(slot);
	}

	private static String signature(Loadout loadout)
	{
		StringBuilder result = new StringBuilder();
		for (Map.Entry<GearSlot, GearItem> entry : loadout.getGear().entrySet())
		{
			result.append(entry.getKey()).append('=').append(entry.getValue().getId()).append(' ');
		}
		return result.toString();
	}
}
