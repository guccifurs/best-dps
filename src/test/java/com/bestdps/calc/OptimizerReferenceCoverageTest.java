package com.bestdps.calc;

import com.bestdps.data.BestDpsData;
import com.bestdps.data.BestDpsDataService;
import com.bestdps.data.GearItem;
import com.bestdps.data.GearSlot;
import com.bestdps.data.MonsterStats;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class OptimizerReferenceCoverageTest
{
	@Test
	public void everyNamedWeaponFamilySurvivesStatShortlisting() throws Exception
	{
		BestDpsData data = new BestDpsDataService().load();
		OptimizationRequest request = request(data, "goblin", CombatStyle.MELEE, CandidateMode.ALL_STANDARD, OwnedItems.EMPTY);
		List<GearItem> weapons = new ArrayList<>();
		Set<String> families = new HashSet<>();
		for (GearItem item : data.getGearItems())
		{
			if (item.getSlot() == GearSlot.WEAPON && item.isStandardGear())
			{
				weapons.add(item);
				families.add(item.getNormalizedName());
			}
		}
		Method shortlist = BestDpsOptimizer.class.getDeclaredMethod("shortlist", List.class, OptimizationRequest.class, GearBudget.class, int.class);
		shortlist.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<GearItem> selected = (List<GearItem>) shortlist.invoke(null, weapons, request, new GearBudget(data, request), 48);
		Set<String> surviving = new HashSet<>();
		for (GearItem item : selected) { surviving.add(item.getNormalizedName()); }
		Assert.assertEquals(families, surviving);
		System.out.println("WEAPON_FAMILY_REFERENCE catalogVariants=" + weapons.size() + " namedFamilies=" + families.size()
			+ " survivingVariants=" + selected.size());
	}

	@Test
	public void reportRealCatalogSearchTimingsAndLegalFunding()
	{
		BestDpsData data = new BestDpsDataService().load();
		Map<Integer, Integer> bank = new HashMap<>();
		for (int id : new int[]{4151, 11832, 11834, 6585, 19553, 21012, 12926, 12899, 12002, 11826, 11828, 11830, 6570, 7462}) { bank.put(id, 1); }
		bank.put(11230, 5000);
		bank.put(21944, 5000);
		for (OptimizationRequest request : new OptimizationRequest[]{
			request(data, "vorkath", CombatStyle.RANGED, CandidateMode.BUDGET, OwnedItems.EMPTY),
			request(data, "zulrah", CombatStyle.ANY, CandidateMode.BUDGET, OwnedItems.EMPTY),
			request(data, "vardorvis", CombatStyle.MELEE, CandidateMode.BUDGET, OwnedItems.EMPTY),
			request(data, "vorkath", CombatStyle.ANY, CandidateMode.OWNED_ONLY, new OwnedItems(bank, true))})
		{
			long start = System.nanoTime();
			List<DpsResult> results = new BestDpsOptimizer().optimize(data, request);
			long elapsed = (System.nanoTime() - start) / 1_000_000;
			Assert.assertFalse(request.getMonster().label(), results.isEmpty());
			for (DpsResult result : results)
			{
				Assert.assertTrue(result.getTradePlan().isAffordable());
				Assert.assertTrue(result.getTradePlan().getCashNeeded() <= request.getBudget());
			}
			System.out.println("CATALOG_SEARCH_REFERENCE npc=" + request.getMonster().label() + " id=" + request.getMonster().getId()
				+ " mode=" + request.getCandidateMode() + " style=" + request.getStyle() + " ms=" + elapsed
				+ " dps=" + results.get(0).getDps() + " weapon=" + results.get(0).getLoadout().getWeapon().getId());
		}
	}

	private static OptimizationRequest request(BestDpsData data, String target, CombatStyle style, CandidateMode mode, OwnedItems owned)
	{
		MonsterStats monster = data.getMonsters().stream().filter(npc -> npc.getName().equalsIgnoreCase(target)
			&& EncounterRules.blockingReason(npc, style).isEmpty()).findFirst().get();
		return new OptimizationRequest(monster, style, PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null,
			mode == CandidateMode.OWNED_ONLY ? 0 : 100_000_000, mode, true, false, owned, RequirementProfile.MAXED, 5);
	}
}
