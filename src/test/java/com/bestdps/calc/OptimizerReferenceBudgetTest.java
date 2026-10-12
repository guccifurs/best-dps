package com.bestdps.calc;

import com.bestdps.data.BestDpsData;
import com.bestdps.data.BestDpsDataService;
import com.bestdps.data.GearItem;
import com.bestdps.data.GearSlot;
import com.bestdps.data.MonsterStats;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import org.junit.Assert;
import org.junit.Test;

public class OptimizerReferenceBudgetTest
{
	private final BestDpsData all = new BestDpsDataService().load();

	@Test
	public void saleFeasibilityMatchesIndependentAccountingForEveryRetainedSubset() throws Exception
	{
		int[] ids = {4151, 4587, 1163, 11826, 11832, 11828};
		List<GearItem> quotes = new ArrayList<>();
		Map<Integer, Integer> owned = new HashMap<>();
		long now = System.currentTimeMillis() / 1000;
		for (int i = 0; i < ids.length; i++)
		{
			// Deliberately fixed hypothetical prices isolate accounting from market changes.
			quotes.add(all.getGear(ids[i]).withPrices(1000 + i * 600, 600 + i * 200, now));
			if (i % 2 == 0) { owned.put(ids[i], i + 2); }
		}
		BestDpsData data = subset(quotes);
		int checked = 0;
		for (int cash : new int[]{0, 500, 2000, 20_000})
		{
			OptimizationRequest request = request(cash, CandidateMode.OWNED_OR_BUDGET, new OwnedItems(owned, true), true)
				.withSellOwnedItems(true);
			GearBudget budget = new GearBudget(data, request);
			for (int combination = 0; combination < 27; combination++)
			{
				EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
				int digits = combination;
				long buyCost = 0;
				for (int pair = 0; pair < 3; pair++)
				{
					int choice = digits % 3;
					digits /= 3;
					if (choice == 0) { continue; }
					GearItem item = quotes.get(pair * 2 + choice - 1);
					gear.put(item.getSlot(), item);
					if (!owned.containsKey(item.getId())) { buyCost += item.getPriceOrZero(); }
				}
				long availableNet = 0;
				for (GearItem item : quotes)
				{
					if (owned.containsKey(item.getId()) && !gear.containsValue(item))
					{
						long price = item.getSellPriceOrZero();
						availableNet += owned.get(item.getId()) * (price - Math.min(5_000_000L, price / 50));
					}
				}
				boolean expected = buyCost <= cash + availableNet;
				TradePlan plan = budget.plan(new Loadout(gear));
				Assert.assertEquals("Budget search / independent cash accounting", expected, budget.fits(budget.cost(gear)));
				Assert.assertEquals("Trade plan / independent cash accounting", expected, plan.isAffordable());
				Assert.assertEquals(buyCost, plan.getBuyCost());
				long actualNet = 0;
				for (TradePlan.Entry sale : plan.getSales())
				{
					Assert.assertFalse("Selected item cannot be sold", gear.containsValue(sale.getItem()));
					Assert.assertTrue(sale.getQuantity() > 0 && sale.getQuantity() <= owned.get(sale.getItem().getId()));
					actualNet += sale.getTotal();
				}
				Assert.assertEquals(actualNet, plan.getNetProceeds());
				if (cash >= buyCost) { Assert.assertTrue(plan.getSales().isEmpty()); }
				checked++;
			}
		}
		System.out.println("BUDGET_REFERENCE cashAndRetainedCases=" + checked);
	}

	@Test
	public void staleQuotesAndDisabledSellingCannotFundPurchases() throws Exception
	{
		GearItem stale = all.getGear(4151).withPrices(10_000, 9_000, System.currentTimeMillis() / 1000 - 86_401);
		GearItem purchase = all.getGear(11832).withPrices(1_000, 900, System.currentTimeMillis() / 1000);
		BestDpsData data = subset(java.util.Arrays.asList(stale, purchase));
		OwnedItems owned = new OwnedItems(Collections.singletonMap(4151, 10), true);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.BODY, purchase);
		for (boolean sell : new boolean[]{false, true})
		{
			GearBudget budget = new GearBudget(data, request(0, CandidateMode.OWNED_OR_BUDGET, owned, true).withSellOwnedItems(sell));
			TradePlan plan = budget.plan(new Loadout(gear));
			Assert.assertFalse(plan.isAffordable());
			Assert.assertTrue(plan.getSales().isEmpty());
		}
	}

	@Test
	public void longTotalsDoNotWrapIntoAnAffordableInteger() throws Exception
	{
		List<GearItem> items = java.util.Arrays.asList(all.getGear(4151).withPrices(2_000_000_000, 1, 1),
			all.getGear(1163).withPrices(2_000_000_000, 1, 1), all.getGear(11832).withPrices(2_000_000_000, 1, 1));
		GearBudget budget = new GearBudget(subset(items), request(Integer.MAX_VALUE, CandidateMode.BUDGET, OwnedItems.EMPTY, false));
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		for (GearItem item : items) { gear.put(item.getSlot(), item); }
		Assert.assertEquals(6_000_000_000L, budget.cost(gear));
		Assert.assertFalse(budget.fits(budget.cost(gear)));
		TradePlan plan = budget.plan(new Loadout(gear));
		Assert.assertEquals(6_000_000_000L, plan.getBuyCost());
		Assert.assertFalse(plan.isAffordable());
	}

	@Test
	public void cachedUntradeablesAndBaseRequirementsAreEnforced() throws Exception
	{
		BestDpsData data = subset(java.util.Arrays.asList(all.getGear(4151), all.getGear(6570), all.getGear(4587)));
		OwnedItems owned = new OwnedItems(Map.of(4151, 1, 6570, 1, 4587, 1), true);
		OptimizationRequest disabled = request(0, CandidateMode.OWNED_ONLY, owned, false);
		List<DpsResult> results = new BestDpsOptimizer().optimize(data, disabled);
		Assert.assertFalse(results.isEmpty());
		for (DpsResult result : results) { Assert.assertNull(result.getLoadout().get(GearSlot.CAPE)); }
		EnumMap<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values()) { levels.put(skill, 1); }
		OptimizationRequest locked = new OptimizationRequest(disabled.getMonster(), CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.NONE, null, 0, CandidateMode.OWNED_ONLY, true, false,
			owned, new RequirementProfile(levels, Collections.emptySet()), 5);
		for (DpsResult result : new BestDpsOptimizer().optimize(data, locked))
		{
			Assert.assertNull("Boosted attack cannot bypass base requirements", result.getLoadout().getWeapon());
		}
	}

	@Test
	public void brokenBarrowsCannotBeRecommendedAsUsableGear() throws Exception
	{
		BestDpsData data = subset(java.util.Arrays.asList(all.getGear(4938), all.getGear(4740)));
		OptimizationRequest request = request(0, CandidateMode.OWNED_ONLY, new OwnedItems(Map.of(4938, 1, 4740, 100), true), true)
			.withStyle(CombatStyle.RANGED);
		Assert.assertTrue("Karil's crossbow0 cannot attack until repaired", new BestDpsOptimizer().optimize(data, request).isEmpty());
	}

	@Test
	public void sellingRecommendsOnlyStrictGainsOverTheSameNoSaleSearch() throws Exception
	{
		long now = System.currentTimeMillis() / 1000;
		BestDpsData data = subset(java.util.Arrays.asList(all.getGear(1333).withPrices(100, 80, now),
			all.getGear(4151).withPrices(1000, 900, now), all.getGear(1127).withPrices(1500, 1200, now)));
		OptimizationRequest request = request(0, CandidateMode.OWNED_OR_BUDGET, new OwnedItems(Map.of(1333, 1, 1127, 1), true), false);
		List<DpsResult> baseline = new BestDpsOptimizer().optimize(data, request);
		List<DpsResult> selling = new BestDpsOptimizer().optimize(data, request.withSellOwnedItems(true));
		Assert.assertFalse(baseline.isEmpty());
		Assert.assertTrue(selling.get(0).getDps() > baseline.get(0).getDps());
		for (DpsResult result : selling)
		{
			Assert.assertEquals(baseline.get(0).getDps(), result.getBaselineDps(), 0);
			if (!result.getTradePlan().getSales().isEmpty()) { Assert.assertTrue(result.getDps() > result.getBaselineDps()); }
			Assert.assertTrue(result.getTradePlan().isAffordable());
		}
	}

	private OptimizationRequest request(int cash, CandidateMode mode, OwnedItems owned, boolean untradeables)
	{
		MonsterStats monster = all.searchMonsters("goblin", 1).get(0);
		return new OptimizationRequest(monster, CombatStyle.MELEE, PlayerLevels.MAXED, PrayerBonuses.NONE, null,
			cash, mode, untradeables, false, owned, RequirementProfile.MAXED, 10);
	}

	private BestDpsData subset(List<GearItem> items) throws Exception
	{
		Constructor<BestDpsData> constructor = BestDpsData.class.getDeclaredConstructor(List.class, List.class, List.class, Map.class);
		constructor.setAccessible(true);
		Map<Integer, GearItem> byId = new HashMap<>();
		for (GearItem item : items) { byId.put(item.getId(), item); }
		return constructor.newInstance(items, all.getMonsters(), all.getSpells(), byId);
	}
}
