package com.bestdps.calc;

import com.bestdps.data.BestDpsData;
import com.bestdps.data.GearItem;
import com.bestdps.data.GearSlot;
import com.bestdps.data.SpellStats;
import com.bestdps.data.StatBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

public final class BestDpsOptimizer
{
	// Multi-start coordinate search: bounded work, not an exhaustive optimum claim.
	private static final int SLOT_LIMIT = 12;
	private static final int WEAPON_LIMIT = 48;
	private static final int MAX_EVALUATIONS_PER_STYLE = 24_000;
	private static final int EXACT_COMBINATIONS_LIMIT = 20_000;
	private static final GearSlot[] NON_WEAPON_SLOTS = {
		GearSlot.AMMO, GearSlot.NECK, GearSlot.BODY, GearSlot.LEGS, GearSlot.HEAD,
		GearSlot.HANDS, GearSlot.SHIELD, GearSlot.CAPE, GearSlot.FEET, GearSlot.RING
	};
	private static final Comparator<DpsResult> RESULT_ORDER = Comparator.comparingDouble(DpsResult::getDps)
		.reversed().thenComparingLong(result -> result.getTradePlan() == null
			? result.getPurchaseCost() : result.getTradePlan().getCashNeeded());
	private final DpsCalculator calculator = new DpsCalculator();

	public List<DpsResult> optimize(BestDpsData data, OptimizationRequest request)
	{
		if (data == null || request == null || request.getMonster() == null || request.getStyle() == null || request.getLevels() == null)
		{
			return java.util.Collections.emptyList();
		}
		checkCancelled();
		if (!EncounterRules.blockingReason(request.getMonster(), request.getStyle()).isEmpty())
		{
			return java.util.Collections.emptyList();
		}
		CombatContext combat = request.getCombatContext();
		if (combat.isMarkOfDarkness() && (request.getLevels().getMagic() < 59
			|| !request.getRequirementProfile().hasCompletedQuest(Quest.A_KINGDOM_DIVIDED)))
		{
			request = request.withCombatContext(new CombatContext(combat.getCurrentHp(), combat.getMaxHp(),
				combat.getSoulreaperStacks(), false, combat.isKandarinDiary(), combat.isWilderness()));
		}
		List<DpsResult> baseline = request.isSellOwnedItems()
			? optimize(data, request.withSellOwnedItems(false)) : java.util.Collections.emptyList();
		double baselineDps = baseline.isEmpty() ? 0 : baseline.get(0).getDps();
		OptimizationRequest scaled = request.withMonster(EncounterRules.scale(request.getMonster(), request.getEncounterContext()));
		List<DpsResult> results = new ArrayList<>();
		if (request.getStyle() == CombatStyle.ANY)
		{
			for (CombatStyle style : CombatStyle.concreteValues())
			{
				results.addAll(optimizeStyle(data, scaled.withStyle(style)));
			}
		}
		else
		{
			results.addAll(optimizeStyle(data, scaled));
		}
		if (request.isSellOwnedItems())
		{
			results.removeIf(result -> result.getTradePlan() != null && !result.getTradePlan().getSales().isEmpty()
				&& result.getDps() <= baselineDps);
			results.addAll(baseline);
		}
		results.sort(RESULT_ORDER);
		List<DpsResult> unique = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (DpsResult result : results)
		{
			if (seen.add(result.getAttackType() + ':' + signature(result.getLoadout())))
			{
				unique.add(request.isSellOwnedItems() ? result.withBaselineDps(baselineDps) : result);
				if (unique.size() >= request.getResultLimit())
				{
					break;
				}
			}
		}
		return unique;
	}

	private List<DpsResult> optimizeStyle(BestDpsData data, OptimizationRequest request)
	{
		GearBudget budget = new GearBudget(data, request);
		EnumMap<GearSlot, List<GearItem>> pools = new EnumMap<>(GearSlot.class);
		for (GearSlot slot : GearSlot.values())
		{
			pools.put(slot, new ArrayList<>());
		}
		// One dataset pass, not eleven full scans per weapon/style.
		for (GearItem item : data.getGearItems())
		{
			checkCancelled();
			if (item.getSlot() == null || !item.isStandardGear() || unusable(item)
				|| !request.getRequirementProfile().canEquip(item.getRequirements()) || !allowed(request, budget, item))
			{
				continue;
			}
			if (item.getSlot() == GearSlot.WEAPON && !item.isWeaponFor(request.getStyle())
				&& !(request.getStyle() == CombatStyle.MAGIC && request.getMonster().hasAttribute("dragon")
					&& item.getNormalizedName().contains("dragon hunter")))
			{
				continue;
			}
			pools.get(item.getSlot()).add(item);
		}
		List<SpellStats> spells = spellsFor(data, request);
		List<DpsResult> exact = exactIfSmall(pools, request, budget, spells);
		if (exact != null) { return exact; }
		List<GearItem> weapons = shortlist(pools.get(GearSlot.WEAPON), request, budget, WEAPON_LIMIT);
		if (request.getStyle() != CombatStyle.RANGED)
		{
			weapons.add(null);
		}
		EnumMap<GearSlot, List<GearItem>> sharedChoices = new EnumMap<>(GearSlot.class);
		for (GearSlot slot : NON_WEAPON_SLOTS)
		{
			if (slot != GearSlot.AMMO)
			{
				List<GearItem> selected = shortlist(pools.get(slot), request, budget, SLOT_LIMIT);
				selected.add(null);
				sharedChoices.put(slot, selected);
			}
		}
		List<DpsResult> results = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		List<SearchTask> tasks = new ArrayList<>();
		int[] evaluations = {0};
		for (GearItem weapon : weapons)
		{
			checkCancelled();
			if (evaluations[0] >= MAX_EVALUATIONS_PER_STYLE)
			{
				break;
			}
			EnumMap<GearSlot, List<GearItem>> choices = new EnumMap<>(GearSlot.class);
			for (GearSlot slot : NON_WEAPON_SLOTS)
			{
				if (slot != GearSlot.AMMO)
				{
					choices.put(slot, slot == GearSlot.SHIELD && weapon != null && weapon.isTwoHanded()
						? java.util.Collections.singletonList(null) : weaponSpecificChoices(pools.get(slot), sharedChoices.get(slot), request, budget, weapon));
					continue;
				}
				List<GearItem> available = ammoChoices(pools, weapon, request);
				List<GearItem> selected = shortlist(available, request, budget, SLOT_LIMIT);
				if (request.getStyle() != CombatStyle.RANGED || RangedAmmo.compatible(null, weapon))
				{
					selected.add(null);
				}
				choices.put(slot, selected);
			}
			if (choices.get(GearSlot.AMMO).isEmpty())
			{
				continue;
			}
			Evaluator evaluator = new Evaluator(request, weapon, spells, evaluations);
			List<EnumMap<GearSlot, GearItem>> seeds = seeds(request, weapon, choices, budget);
			for (int seedIndex = 0; seedIndex < seeds.size(); seedIndex++)
			{
				EnumMap<GearSlot, GearItem> seed = seeds.get(seedIndex);
				if (!budget.fits(budget.cost(seed)))
				{
					continue;
				}
				fillSeed(seed, choices, request, budget, seedIndex == 1);
				DpsResult initial = evaluator.score(seed);
				if (initial != null)
				{
					tasks.add(new SearchTask(seed, choices, budget, evaluator, seedIndex == 1, initial));
					addResult(initial, budget, seen, results);
				}
			}
		}
		// Score a complete seed for every weapon before spending time refining any one family.
		tasks.sort(Comparator.comparingDouble((SearchTask task) -> task.initial.getDps()).reversed());
		for (SearchTask task : tasks)
		{
			if (task.evaluator.exhausted())
			{
				break;
			}
			DpsResult result = improve(task.gear, task.choices, task.budget, task.evaluator, task.reverse, task.initial);
			addResult(result, task.budget, seen, results);
		}
		results.sort(RESULT_ORDER);
		return new ArrayList<>(results.subList(0, Math.min(request.getResultLimit(), results.size())));
	}

	/** Small banks/pools can be searched completely, including zero-stat effect items. */
	private List<DpsResult> exactIfSmall(Map<GearSlot, List<GearItem>> pools, OptimizationRequest request,
		GearBudget budget, List<SpellStats> spells)
	{
		long combinations = 1;
		long loadedDarts = request.getStyle() == CombatStyle.RANGED
			&& pools.get(GearSlot.WEAPON).stream().anyMatch(RangedAmmo::isBlowpipe)
			? pools.get(GearSlot.WEAPON).stream().filter(RangedAmmo::isDart).count() : 0;
		for (GearSlot slot : GearSlot.values())
		{
			combinations *= pools.get(slot).size() + 1L + (slot == GearSlot.AMMO ? loadedDarts : 0);
			if (combinations > EXACT_COMBINATIONS_LIMIT) { return null; }
		}
		List<GearItem> weapons = new ArrayList<>(pools.get(GearSlot.WEAPON));
		if (request.getStyle() != CombatStyle.RANGED) { weapons.add(null); }
		List<DpsResult> results = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		int[] evaluations = {0};
		for (GearItem weapon : weapons)
		{
			checkCancelled();
			EnumMap<GearSlot, List<GearItem>> choices = new EnumMap<>(GearSlot.class);
			for (GearSlot slot : NON_WEAPON_SLOTS)
			{
				List<GearItem> options = new ArrayList<>(slot == GearSlot.AMMO ? ammoChoices(pools, weapon, request) : pools.get(slot));
				if (slot == GearSlot.SHIELD && weapon != null && weapon.isTwoHanded()) { options.clear(); }
				if (slot != GearSlot.AMMO || request.getStyle() != CombatStyle.RANGED || RangedAmmo.compatible(null, weapon)) { options.add(null); }
				choices.put(slot, options);
			}
			EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
			put(gear, GearSlot.WEAPON, weapon);
			if (budget.fits(budget.cost(weapon)))
			{
				exactVisit(0, choices, gear, budget.cost(weapon), budget, new Evaluator(request, weapon, spells, evaluations), seen, results);
			}
		}
		results.sort(RESULT_ORDER);
		return new ArrayList<>(results.subList(0, Math.min(request.getResultLimit(), results.size())));
	}

	private void exactVisit(int index, Map<GearSlot, List<GearItem>> choices, EnumMap<GearSlot, GearItem> gear,
		long cost, GearBudget budget, Evaluator evaluator, Set<String> seen, List<DpsResult> results)
	{
		checkCancelled();
		if (index == NON_WEAPON_SLOTS.length)
		{
			addResult(evaluator.score(gear), budget, seen, results);
			return;
		}
		GearSlot slot = NON_WEAPON_SLOTS[index];
		for (GearItem item : choices.get(slot))
		{
			long nextCost = cost + budget.cost(item);
			if (nextCost < cost || !budget.fits(nextCost)) { continue; }
			put(gear, slot, item);
			exactVisit(index + 1, choices, gear, nextCost, budget, evaluator, seen, results);
		}
		gear.remove(slot);
	}

	private static List<GearItem> ammoChoices(Map<GearSlot, List<GearItem>> pools, GearItem weapon, OptimizationRequest request)
	{
		if (request.getStyle() != CombatStyle.RANGED) { return pools.get(GearSlot.AMMO); }
		List<GearItem> available = candidatesForSlotWithWeapon(pools.get(GearSlot.AMMO), weapon, GearSlot.AMMO);
		if (RangedAmmo.isBlowpipe(weapon))
		{
			for (GearItem dart : pools.get(GearSlot.WEAPON))
			{
				if (!RangedAmmo.isDart(dart)) { continue; }
				GearItem loaded = new GearItem(dart.getId(), dart.getName(), dart.getVersion(), GearSlot.AMMO,
					dart.getCategory(), dart.getSpeed(), false, dart.isStandardGear(), dart.isTradeable(),
					dart.getEstimatedPrice(), StatBlock.ZERO, StatBlock.ZERO,
					new StatBlock(0, 0, 0, 0, 0, 0, dart.getBonuses().getRangedStrength(), 0, 0), dart.getRequirements());
				if (dart.hasLivePrice()) { loaded = loaded.withPrices(dart.getPriceOrZero(), dart.getSellPriceOrZero(), System.currentTimeMillis() / 1000); }
				available.add(loaded);
			}
		}
		return available;
	}

	private static List<GearItem> weaponSpecificChoices(List<GearItem> pool, List<GearItem> shared,
		OptimizationRequest request, GearBudget budget, GearItem weapon)
	{
		if (!strengthRangedWeapon(weapon) && !label(weapon).contains("bulwark")) { return shared; }
		List<GearItem> result = shortlist(pool, request, budget, SLOT_LIMIT, weapon);
		result.add(null);
		return result;
	}

	private static void addResult(DpsResult result, GearBudget budget, Set<String> seen, List<DpsResult> results)
	{
		if (result != null && result.getDps() > 0 && seen.add(signature(result.getLoadout())))
		{
			TradePlan plan = budget.plan(result.getLoadout());
			if (plan.isAffordable())
			{
				results.add(result.withTradePlan(plan));
			}
		}
	}

	private static void fillSeed(EnumMap<GearSlot, GearItem> seed, Map<GearSlot, List<GearItem>> choices,
		OptimizationRequest request, GearBudget budget, boolean reverse)
	{
		long total = budget.cost(seed);
		for (int i = 0; i < NON_WEAPON_SLOTS.length; i++)
		{
			GearSlot slot = NON_WEAPON_SLOTS[reverse ? NON_WEAPON_SLOTS.length - i - 1 : i];
			if (seed.containsKey(slot) && slot != GearSlot.AMMO)
			{
				continue;
			}
			GearItem selected = seed.get(slot);
			long baseCost = total - budget.cost(selected);
			double bestScore = selected == null ? 0 : candidateScore(request, selected, seed.get(GearSlot.WEAPON));
			for (GearItem candidate : choices.get(slot))
			{
				if (candidate != null && budget.fits(baseCost + budget.cost(candidate)))
				{
					double score = candidateScore(request, candidate, seed.get(GearSlot.WEAPON));
					if (score > bestScore || score == bestScore && budget.cost(candidate) < budget.cost(selected))
					{
						selected = candidate;
						bestScore = score;
					}
				}
			}
			put(seed, slot, selected);
			total = baseCost + budget.cost(selected);
		}
	}

	private static final class SearchTask
	{
		private final EnumMap<GearSlot, GearItem> gear;
		private final Map<GearSlot, List<GearItem>> choices;
		private final GearBudget budget;
		private final Evaluator evaluator;
		private final boolean reverse;
		private final DpsResult initial;

		private SearchTask(EnumMap<GearSlot, GearItem> gear, Map<GearSlot, List<GearItem>> choices,
			GearBudget budget, Evaluator evaluator, boolean reverse, DpsResult initial)
		{
			this.gear = gear;
			this.choices = choices;
			this.budget = budget;
			this.evaluator = evaluator;
			this.reverse = reverse;
			this.initial = initial;
		}
	}

	private DpsResult improve(EnumMap<GearSlot, GearItem> gear, Map<GearSlot, List<GearItem>> choices,
		GearBudget budget, Evaluator evaluator, boolean reverse, DpsResult initial)
	{
		DpsResult current = initial;
		long cost = budget.cost(gear);
		for (int pass = 0; pass < 3; pass++)
		{
			boolean changed = false;
			for (int index = 0; index < NON_WEAPON_SLOTS.length; index++)
			{
				GearSlot slot = NON_WEAPON_SLOTS[reverse ? NON_WEAPON_SLOTS.length - 1 - index : index];
				GearItem original = gear.get(slot);
				GearItem bestItem = original;
				DpsResult bestResult = current;
				long bestCost = cost;
				for (GearItem candidate : choices.get(slot))
				{
					if (candidate == original || evaluator.exhausted())
					{
						continue;
					}
					long trialCost = cost - budget.cost(original) + budget.cost(candidate);
					if (!budget.fits(trialCost))
					{
						continue;
					}
					put(gear, slot, candidate);
					DpsResult trial = evaluator.score(gear);
					if (trial != null && (bestResult == null || trial.getDps() > bestResult.getDps()
						|| trial.getDps() == bestResult.getDps() && trialCost < bestCost))
					{
						bestItem = candidate;
						bestResult = trial;
						bestCost = trialCost;
					}
				}
				put(gear, slot, bestItem);
				changed |= bestItem != original;
				current = bestResult;
				cost = bestCost;
			}
			if (!changed || evaluator.exhausted())
			{
				break;
			}
		}
		// A downgrade in one slot can fund a larger gain elsewhere. Single-slot hill climbing
		// cannot cross that budget boundary, so inspect bounded two-slot exchanges as well.
		if (current != null && !evaluator.exhausted())
		{
			current = improveExchange(gear, choices, budget, evaluator, current);
		}
		return current;
	}

	private DpsResult improveExchange(EnumMap<GearSlot, GearItem> gear, Map<GearSlot, List<GearItem>> choices,
		GearBudget budget, Evaluator evaluator, DpsResult current)
	{
		long cost = budget.cost(gear);
		DpsResult best = current;
		EnumMap<GearSlot, GearItem> bestGear = new EnumMap<>(gear);
		for (GearSlot donor : NON_WEAPON_SLOTS)
		{
			GearItem original = gear.get(donor);
			if (budget.cost(original) == 0)
			{
				continue;
			}
			List<GearItem> cheaper = new ArrayList<>(choices.get(donor));
			cheaper.sort(Comparator.comparingLong((GearItem item) -> budget.cost(item)));
			for (int i = 0; i < Math.min(2, cheaper.size()); i++)
			{
				GearItem replacement = cheaper.get(i);
				long freed = budget.cost(original) - budget.cost(replacement);
				if (freed <= 0)
				{
					continue;
				}
				put(gear, donor, replacement);
				for (GearSlot receiver : NON_WEAPON_SLOTS)
				{
					if (receiver == donor)
					{
						continue;
					}
					GearItem old = gear.get(receiver);
					List<GearItem> options = choices.get(receiver);
					for (int j = 0; j < Math.min(4, options.size()); j++)
					{
						GearItem candidate = options.get(j);
						long newCost = cost - freed - budget.cost(old) + budget.cost(candidate);
						if (candidate == old || !budget.fits(newCost) || evaluator.exhausted())
						{
							continue;
						}
						put(gear, receiver, candidate);
						DpsResult trial = evaluator.score(gear);
						if (trial != null && trial.getDps() > best.getDps())
						{
							best = trial;
							bestGear = new EnumMap<>(gear);
						}
					}
					put(gear, receiver, old);
				}
				put(gear, donor, original);
			}
		}
		gear.clear();
		gear.putAll(bestGear);
		return best;
	}

	private static List<EnumMap<GearSlot, GearItem>> seeds(OptimizationRequest request, GearItem weapon,
		Map<GearSlot, List<GearItem>> choices, GearBudget budget)
	{
		EnumMap<GearSlot, GearItem> base = new EnumMap<>(GearSlot.class);
		put(base, GearSlot.WEAPON, weapon);
		// Start with the cheapest legal ammo. Missing mandatory ammo must never produce a scored bow.
		GearItem ammo = null;
		long ammoCost = Long.MAX_VALUE;
		for (GearItem candidate : choices.get(GearSlot.AMMO))
		{
			if (budget.cost(candidate) < ammoCost)
			{
				ammo = candidate;
				ammoCost = budget.cost(candidate);
			}
		}
		put(base, GearSlot.AMMO, ammo);
		List<EnumMap<GearSlot, GearItem>> result = new ArrayList<>();
		result.add(new EnumMap<>(base));
		result.add(new EnumMap<>(base)); // alternate slot order explores different budget allocations
		String voidHelm = request.getStyle() == CombatStyle.MELEE ? "void melee helm"
			: request.getStyle() == CombatStyle.RANGED ? "void ranger helm" : "void mage helm";
		addSetSeed(result, base, choices, new GearSlot[]{GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS, GearSlot.HANDS},
			new String[]{voidHelm, "void knight top", "void knight robe", "void knight gloves"});
		addSetSeed(result, base, choices, new GearSlot[]{GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS, GearSlot.HANDS},
			new String[]{voidHelm, "elite void top", "elite void robe", "void knight gloves"});
		if (request.getStyle() == CombatStyle.MELEE)
		{
			addSetSeed(result, base, choices, new GearSlot[]{GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS, GearSlot.NECK},
				new String[]{"obsidian helmet", "obsidian platebody", "obsidian platelegs", "berserker necklace"});
			addSetSeed(result, base, choices, new GearSlot[]{GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS},
				new String[]{"inquisitor's great helm", "inquisitor's hauberk", "inquisitor's plateskirt"});
			if (label(weapon).contains("dual macuahuitl"))
			{
				addSetSeed(result, base, choices, new GearSlot[]{GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS},
					new String[]{"blood moon helm", "blood moon chestplate", "blood moon tassets"});
			}
		}
		if (label(weapon).contains("crystal bow") || label(weapon).contains("bow of faerdhinen"))
		{
			addSetSeed(result, base, choices, new GearSlot[]{GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS},
				new String[]{"crystal helm", "crystal body", "crystal legs"});
		}
		for (String barrows : new String[]{"dharok", "ahrim", "karil", "verac"})
		{
			if (label(weapon).contains(barrows))
			{
				addSetSeed(result, base, choices, new GearSlot[]{GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS},
					new String[]{barrows, barrows, barrows});
				if (barrows.equals("ahrim") || barrows.equals("karil"))
				{
					addSetSeed(result, base, choices, new GearSlot[]{GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS, GearSlot.NECK},
						new String[]{barrows, barrows, barrows, "amulet of the damned"});
				}
			}
		}
		return result;
	}

	private static void addSetSeed(List<EnumMap<GearSlot, GearItem>> seeds, Map<GearSlot, GearItem> base,
		Map<GearSlot, List<GearItem>> choices, GearSlot[] slots, String[] names)
	{
		EnumMap<GearSlot, GearItem> seed = new EnumMap<>(base);
		for (int index = 0; index < slots.length; index++)
		{
			GearItem match = null;
			for (GearItem item : choices.get(slots[index]))
			{
				if (label(item).contains(names[index]))
				{
					match = item;
					break;
				}
			}
			if (match == null)
			{
				return;
			}
			seed.put(slots[index], match);
		}
		seeds.add(seed);
	}

	private static void put(Map<GearSlot, GearItem> gear, GearSlot slot, GearItem item)
	{
		if (item == null)
		{
			gear.remove(slot);
		}
		else
		{
			gear.put(slot, item);
		}
	}

	private static List<GearItem> shortlist(List<GearItem> source, OptimizationRequest request, GearBudget budget, int limit)
	{
		return shortlist(source, request, budget, limit, null);
	}

	private static List<GearItem> shortlist(List<GearItem> source, OptimizationRequest request, GearBudget budget, int limit, GearItem weapon)
	{
		List<GearItem> sorted = new ArrayList<>(source);
		sorted.removeIf(java.util.Objects::isNull);
		sorted.sort(Comparator.comparingDouble((GearItem item) -> candidateScore(request, item, weapon)).reversed()
			.thenComparingLong(budget::cost).thenComparingInt(GearItem::getId));
		// Only collapse identical named variants. Equal stats do not make different item effects equivalent.
		Map<String, GearItem> unique = new java.util.LinkedHashMap<>();
		for (GearItem item : sorted)
		{
			String key = item.getName().toLowerCase(Locale.ROOT) + ':' + item.getVersion() + ':' + item.getSpeed()
				+ ':' + item.getCategory() + ':' + item.isTwoHanded()
				+ ':' + item.getOffensive().getStab() + ':' + item.getOffensive().getSlash() + ':' + item.getOffensive().getCrush()
				+ ':' + item.getOffensive().getMagic() + ':' + item.getOffensive().getRanged()
				+ ':' + item.getBonuses().getStrength() + ':' + item.getBonuses().getRangedStrength() + ':' + item.getBonuses().getMagicDamage()
				+ ':' + item.getDefensive().getStab() + ':' + item.getDefensive().getSlash() + ':' + item.getDefensive().getCrush() + ':' + item.getDefensive().getRanged();
			GearItem previous = unique.get(key);
			if (previous == null || budget.cost(item) < budget.cost(previous))
			{
				unique.put(key, item);
			}
		}
		sorted = new ArrayList<>(unique.values());
		// Every eligible named weapon family gets an initial scored setup; raw stats alone
		// cannot rank weapon-specific passives, speed, stances or encounter restrictions.
		if (!sorted.isEmpty() && sorted.get(0).getSlot() == GearSlot.WEAPON) { return sorted; }
		List<GearItem> result = new ArrayList<>(sorted.subList(0, Math.min(limit, sorted.size())));
		// Preserve cheap routes, owned gear and whole-set pieces alongside top raw stats.
		List<GearItem> cheap = new ArrayList<>(sorted);
		cheap.sort(Comparator.comparingLong((GearItem item) -> budget.cost(item))
			.thenComparing(Comparator.comparingDouble((GearItem item) -> candidateScore(request, item, weapon)).reversed()));
		for (int i = 0; i < Math.min(4, cheap.size()); i++)
		{
			if (!result.contains(cheap.get(i)))
			{
				result.add(cheap.get(i));
			}
		}
		Set<String> sets = new HashSet<>();
		int ownedAdded = 0;
		for (GearItem item : sorted)
		{
			if (result.contains(item))
			{
				continue;
			}
			if (setPiece(item) && sets.add(item.getName().toLowerCase(Locale.ROOT))
				|| item.getSlot() == GearSlot.AMMO && item.getNormalizedName().contains("bolts (e)")
				|| budget.reusesOwned(item) && ownedAdded++ < 4)
			{
				result.add(item);
			}
		}
		return result;
	}

	private static List<GearItem> candidatesForSlotWithWeapon(List<GearItem> candidates, GearItem weapon, GearSlot slot)
	{
		if (slot == GearSlot.SHIELD && weapon != null && weapon.isTwoHanded())
		{
			return java.util.Collections.emptyList();
		}
		if (slot != GearSlot.AMMO)
		{
			return candidates;
		}
		List<GearItem> result = new ArrayList<>();
		for (GearItem item : candidates)
		{
			if (RangedAmmo.compatible(item, weapon))
			{
				result.add(item);
			}
		}
		return result;
	}

	private static boolean allowed(OptimizationRequest request, GearBudget budget, GearItem item)
	{
		if (!item.isTradeable())
		{
			return request.isIncludeUntradeables() && request.getOwnedItems().owns(item.getId());
		}
		if (request.getCandidateMode() == CandidateMode.OWNED_ONLY)
		{
			return request.getOwnedItems().owns(item.getId());
		}
		return budget.reusesOwned(item) || item.getPriceOrZero() > 0 && budget.fits(budget.cost(item));
	}

	private static boolean unusable(GearItem item)
	{
		String version = item.getVersion().toLowerCase(Locale.ROOT);
		if (version.contains("broken")) { return true; }
		String name = item.getNormalizedName();
		return version.equals("0") && (name.contains("ahrim") || name.contains("dharok") || name.contains("guthan")
			|| name.contains("karil") || name.contains("torag") || name.contains("verac"));
	}

	private static boolean setPiece(GearItem item)
	{
		String name = label(item);
		return name.contains("void") || name.contains("obsidian") || name.contains("berserker necklace") || name.contains("amulet of the damned")
			|| name.contains("inquisitor") || name.contains("crystal helm") || name.contains("crystal body")
			|| name.contains("crystal legs") || name.contains("blood moon") || name.contains("dharok")
			|| name.contains("ahrim") || name.contains("karil") || name.contains("verac") || name.contains("elemental amulet")
			|| name.startsWith("amulet of air") || name.startsWith("amulet of water") || name.startsWith("amulet of earth")
			|| name.startsWith("amulet of fire") || name.contains("efaritay") || name.contains("amulet of avarice");
	}

	private static boolean strengthRangedWeapon(GearItem weapon)
	{
		String name = label(weapon);
		return name.contains("eclipse atlatl") || name.contains("hunter's spear");
	}

	private static double candidateScore(OptimizationRequest request, GearItem item, GearItem weapon)
	{
		double score = candidateScore(request, item);
		if (request.getStyle() == CombatStyle.RANGED && strengthRangedWeapon(weapon))
		{
			score += 2.5 * (item.getBonuses().getStrength() - item.getBonuses().getRangedStrength());
		}
		if (request.getStyle() == CombatStyle.MELEE && label(weapon).contains("bulwark"))
		{
			score += 0.4 * (item.getDefensive().getStab() + item.getDefensive().getSlash()
				+ item.getDefensive().getCrush() + item.getDefensive().getRanged());
		}
		return score;
	}

	private static double candidateScore(OptimizationRequest request, GearItem item)
	{
		double score = item.roughScore(request.getStyle());
		if (item.getSlot() == GearSlot.WEAPON)
		{
			score = (score + 64) / Math.max(2, item.getSpeed() - (request.getStyle() == CombatStyle.RANGED ? 1 : 0));
		}
		String name = label(item);
		if (request.isOnSlayerTask() && item.isSlayerHead()
			&& (request.getStyle() == CombatStyle.MELEE || item.isImbuedSlayerHead()))
		{
			score += 10_000;
		}
		if (request.getMonster().hasAttribute("undead") && name.contains("salve amulet"))
		{
			score += 5_000;
		}
		if (request.getMonster().hasAttribute("dragon") && name.contains("dragon hunter")
			|| request.getMonster().hasAttribute("demon") && (name.contains("arclight") || name.contains("emberlight") || name.contains("darklight") || name.contains("silverlight") || name.contains("scorching")))
		{
			score += 4_000;
		}
		if (request.getMonster().hasAttribute("kalphite") && name.contains("keris"))
		{
			score += 3_000;
		}
		if (request.getStyle() == CombatStyle.MAGIC && item.getNormalizedName().equals("chaos gauntlets")
			&& (request.getLevels().getMagic() < 59 || request.getSpell() != null && request.getSpell().getName().endsWith(" Bolt")))
		{
			// Their +3 bolt damage is absent from ordinary offensive stats.
			score += 1_000;
		}
		String target = request.getMonster().normalizedName();
		if (target.contains("lesarkus") && name.startsWith("ice arrow")
			|| target.equals("fareed") && name.startsWith("ice gloves")
			|| target.equals("salarin the twisted") && (name.contains("elemental amulet")
				|| name.startsWith("amulet of air") || name.startsWith("amulet of water") || name.startsWith("amulet of earth") || name.startsWith("amulet of fire")))
		{
			score += 7_000;
		}
		if (request.getMonster().hasAttribute("leafy") && (name.startsWith("leaf-bladed") || name.contains("broad")
			|| name.contains("slayer's staff") || name.contains("staff of the dead") || name.contains("staff of light") || name.contains("staff of balance"))
			|| target.contains("slagilith") && name.contains("pickaxe")
			|| request.getMonster().hasAttribute("vampyre3") && (name.contains("ivandis") || name.contains("blisterwood"))
			|| request.getMonster().hasAttribute("vampyre2") && (name.contains("silver") || name.contains("ivandis")
				|| name.contains("blisterwood") || name.contains("efaritay"))
			|| target.equals("corporeal beast") && (item.getCategory().equalsIgnoreCase("spear") || name.contains("halberd")))
		{
			score += 6_000;
		}
		if ((target.equals("zulrah") || request.getMonster().hasAttribute("flying")) && name.contains("halberd")
			|| target.contains("tormented demon") && (item.getCategory().toLowerCase(Locale.ROOT).contains("crossbow")
				|| name.contains("ballista") || item.getOffensive().getCrush() > 0 && item.getSpeed() >= 5)
			|| target.contains("hueycoatl") && (name.contains("macuahuitl") || name.contains("temotli") || name.contains("torag's hammers")))
		{
			score += 2_000;
		}
		if (name.contains("twisted bow") || name.contains("tumeken") || name.contains("scythe") || name.contains("osmumten")
			|| name.contains("bow of faerdhinen") || name.contains("crystal bow") || name.contains("soulreaper")
			|| name.contains("blowpipe") || name.contains("dual macuahuitl") || isTzhaarWeapon(name))
		{
			score += 1_000;
		}
		return score;
	}

	private static List<SpellStats> spellsFor(BestDpsData data, OptimizationRequest request)
	{
		Map<String, SpellStats> best = new java.util.LinkedHashMap<>();
		List<SpellStats> chaosBolts = new ArrayList<>();
		for (SpellStats spell : data.getSpells())
		{
			if (request.getMonster().normalizedName().equals("salarin the twisted") && !spell.getName().endsWith(" Strike"))
			{
				continue;
			}
			if (!spellUnlocked(request, spell))
			{
				continue;
			}
			// At 41-58 Magic, +3 from chaos gauntlets can beat the newly unlocked blast tier.
			if (request.getLevels().getMagic() < 59 && spell.getName().endsWith(" Bolt"))
			{
				chaosBolts.add(spell);
			}
			// Most tiers share effects; retain the explicit gauntlet alternatives below.
			String family = spell.getName().split(" ")[0];
			String key = spell.getSpellbook() + ':' + spell.getElement() + ':' + family;
			SpellStats current = best.get(key);
			if (current == null || spell.getMaxHit() > current.getMaxHit())
			{
				best.put(key, spell);
			}
		}
		List<SpellStats> selected = new ArrayList<>(best.values());
		for (SpellStats bolt : chaosBolts)
		{
			if (!selected.contains(bolt)) { selected.add(bolt); }
		}
		return selected;
	}

	private final class Evaluator
	{
		private final List<OptimizationRequest> variants = new ArrayList<>();
		// Loadout owns an immutable gear copy. Map equality checks every slot/item;
		// hash collisions cannot reuse a different setup, and lookup needs no key allocation.
		private final Map<Map<GearSlot, GearItem>, DpsResult> scores = new java.util.HashMap<>();
		private final int[] evaluations;

		private Evaluator(OptimizationRequest request, GearItem weapon, List<SpellStats> spells, int[] evaluations)
		{
			this.evaluations = evaluations;
			if (request.getStyle() != CombatStyle.MAGIC || request.isAutoSpell() && isPoweredStaff(weapon))
			{
				variants.add(request);
			}
			else
			{
				EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
				put(gear, GearSlot.WEAPON, weapon);
				Loadout loadout = new Loadout(gear);
				List<SpellStats> options = request.isAutoSpell() ? spells : java.util.Collections.singletonList(request.getSpell());
				for (SpellStats spell : options)
				{
					if (spell.getMagicLevel() <= request.getLevels().getMagic() && spellAllowed(request, loadout, spell))
					{
						variants.add(request.withSpell(spell));
					}
				}
			}
		}

		private boolean exhausted()
		{
			return evaluations[0] >= MAX_EVALUATIONS_PER_STYLE;
		}

		private DpsResult score(Map<GearSlot, GearItem> gear)
		{
			checkCancelled();
			DpsResult cached = scores.get(gear);
			if (cached != null)
			{
				return cached;
			}
			if (exhausted() || variants.isEmpty())
			{
				return null;
			}
			evaluations[0]++;
			Loadout loadout = new Loadout(gear);
			DpsResult best = null;
			for (OptimizationRequest variant : variants)
			{
				DpsResult next = calculator.calculate(variant, loadout);
				if (best == null || next.getDps() > best.getDps())
				{
					best = next;
				}
			}
			if (best != null)
			{
				scores.put(best.getLoadout().getGear(), best);
			}
			return best;
		}
	}

	private static void checkCancelled()
	{
		if (Thread.currentThread().isInterrupted())
		{
			throw new java.util.concurrent.CancellationException("DPS search superseded");
		}
	}

	private static boolean spellUnlocked(OptimizationRequest request, SpellStats spell)
	{
		if (spell == null || request.getLevels().getMagic() < spell.getMagicLevel())
		{
			return false;
		}
		RequirementProfile profile = request.getRequirementProfile();
		String name = spell.getName();
		if (spell.getSpellbook().equalsIgnoreCase("ancient") && !profile.hasCompletedQuest(Quest.DESERT_TREASURE_I))
		{
			return false;
		}
		if (spell.getSpellbook().equalsIgnoreCase("arceuus") && !name.equals("Ghostly Grasp") && !name.equals("Inferior Demonbane")
			&& !profile.hasCompletedQuest(Quest.A_KINGDOM_DIVIDED))
		{
			return false;
		}
		if (name.equals("Iban Blast") && !profile.hasCompletedQuest(Quest.UNDERGROUND_PASS))
		{
			return false;
		}
		if (name.equals("Magic Dart") && profile.level(Skill.SLAYER) < 55)
		{
			return false;
		}
		if (name.equals("Saradomin Strike") || name.equals("Claws of Guthix") || name.equals("Flames of Zamorak"))
		{
			// The snapshot does not capture each spell's 100 arena casts. Mage Arena II
			// completion proves all three unlocks; MAXED includes every completed quest.
			return profile.hasCompletedQuest(Quest.MAGE_ARENA_II);
		}
		return true;
	}

	static boolean spellAllowed(OptimizationRequest request, Loadout loadout, SpellStats spell)
	{
		if (!spellUnlocked(request, spell))
		{
			return false;
		}
		String spellName = spell.getName();
		String weapon = label(loadout.getWeapon());
		if (request.isAutoSpell() && isPoweredStaff(loadout.getWeapon()))
		{
			return false;
		}
		if (spellName.contains("Demonbane") && (request.getMonster() == null || !request.getMonster().hasAttribute("demon")))
		{
			return false;
		}
		if ("Crumble Undead".equals(spellName) && (request.getMonster() == null || !request.getMonster().hasAttribute("undead")))
		{
			return false;
		}
		if ("Iban Blast".equals(spellName))
		{
			return weapon.contains("iban's staff");
		}
		if ("Saradomin Strike".equals(spellName))
		{
			return weapon.contains("saradomin staff") || weapon.contains("staff of light");
		}
		if ("Claws of Guthix".equals(spellName))
		{
			return weapon.contains("guthix staff") || weapon.contains("void knight mace") || weapon.contains("staff of balance");
		}
		if ("Flames of Zamorak".equals(spellName))
		{
			return weapon.contains("zamorak staff")
				|| weapon.contains("staff of the dead")
				|| weapon.contains("toxic staff of the dead")
				|| weapon.contains("thammaron")
				|| weapon.contains("accursed sceptre");
		}
		if ("Magic Dart".equals(spellName))
		{
			return weapon.contains("slayer's staff")
				|| weapon.contains("staff of the dead")
				|| weapon.contains("toxic staff of the dead")
				|| weapon.contains("staff of light")
				|| weapon.contains("staff of balance");
		}
		return true;
	}

	private static String label(GearItem item)
	{
		return item == null ? "" : item.getNormalizedLabel();
	}

	private static boolean isPoweredStaff(GearItem weapon)
	{
		String category = weapon == null ? "" : weapon.getCategory().toLowerCase(Locale.ROOT);
		String name = label(weapon);
		return category.contains("powered staff")
			|| name.contains("trident")
			|| name.contains("thammaron")
			|| name.contains("accursed sceptre")
			|| name.contains("sanguinesti")
			|| name.contains("tumeken")
			|| name.contains("warped sceptre")
			|| name.contains("bone staff");
	}

	private static boolean isTzhaarWeapon(String name)
	{
		return name.contains("tzhaar-ket-em")
			|| name.contains("tzhaar-ket-om")
			|| name.contains("toktz-xil-ak")
			|| name.contains("toktz-xil-ek")
			|| name.contains("toktz-mej-tal");
	}

	private static String signature(Loadout loadout)
	{
		StringBuilder builder = new StringBuilder();
		for (GearSlot slot : GearSlot.values())
		{
			GearItem item = loadout.get(slot);
			builder.append(slot.name()).append('=');
			if (item != null)
			{
				builder.append(item.getId());
			}
			builder.append(';');
		}
		return builder.toString();
	}

}
