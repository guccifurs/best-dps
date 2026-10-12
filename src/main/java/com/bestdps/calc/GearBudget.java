package com.bestdps.calc;

import com.bestdps.data.BestDpsData;
import com.bestdps.data.GearItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reuses one immutable ownership/price snapshot for every candidate in a search. */
final class GearBudget
{
	private final OptimizationRequest request;
	private final Map<Integer, TradePlan.Entry> sellable = new HashMap<>();
	private final long liquidity;

	GearBudget(BestDpsData data, OptimizationRequest request)
	{
		this.request = request;
		long proceeds = 0;
		if (request.isSellOwnedItems())
		{
			for (Map.Entry<Integer, Integer> owned : request.getOwnedItems().getQuantities().entrySet())
			{
				GearItem item = data.getGear(owned.getKey());
				if (item == null || !item.isTradeable() || !item.hasLivePrice() || item.getSellPriceOrZero() <= 0)
				{
					continue;
				}
				long price = item.getSellPriceOrZero();
				// GE tax is 2%, floored per item and capped at 5m. Reserving it even on
				// exempt items deliberately understates, never overstates, available cash.
				long unitNet = price - Math.min(5_000_000L, price / 50L);
				TradePlan.Entry sale = new TradePlan.Entry(item, owned.getValue(), unitNet);
				sellable.put(item.getId(), sale);
				proceeds = saturatedAdd(proceeds, sale.getTotal());
			}
		}
		liquidity = saturatedAdd(request.getBudget(), proceeds);
	}

	boolean reusesOwned(GearItem item)
	{
		return item != null && request.getOwnedItems().owns(item.getId())
			&& (request.getCandidateMode() != CandidateMode.BUDGET || !item.isTradeable());
	}

	long cost(GearItem item)
	{
		if (item == null)
		{
			return 0;
		}
		if (reusesOwned(item))
		{
			TradePlan.Entry sale = sellable.get(item.getId());
			// Keep all quantities of a selected item, including its ammo/charge supply.
			return sale == null ? 0 : sale.getTotal();
		}
		return item.getEstimatedPrice() == null ? Long.MAX_VALUE / 4 : item.getPriceOrZero();
	}

	long cost(Map<com.bestdps.data.GearSlot, GearItem> gear)
	{
		long total = 0;
		for (GearItem item : gear.values())
		{
			total = saturatedAdd(total, cost(item));
		}
		return total;
	}

	boolean fits(long cost)
	{
		return request.getCandidateMode() == CandidateMode.ALL_STANDARD || cost <= liquidity;
	}

	long getLiquidity()
	{
		return liquidity;
	}

	TradePlan plan(Loadout loadout)
	{
		List<TradePlan.Entry> buys = new ArrayList<>();
		List<TradePlan.Entry> sells = new ArrayList<>();
		Set<Integer> retained = new HashSet<>();
		long buyTotal = 0;
		for (GearItem item : loadout.getGear().values())
		{
			if (item == null || !retained.add(item.getId()))
			{
				continue;
			}
			if (!reusesOwned(item))
			{
				TradePlan.Entry buy = new TradePlan.Entry(item, 1, item.getPriceOrZero());
				buys.add(buy);
				buyTotal = saturatedAdd(buyTotal, buy.getTotal());
			}
		}
		long shortfall = Math.max(0, buyTotal - request.getBudget());
		long proceeds = 0;
		if (shortfall > 0 && request.isSellOwnedItems())
		{
			List<TradePlan.Entry> options = new ArrayList<>(sellable.values());
			options.removeIf(option -> retained.contains(option.getItem().getId()) || option.getUnitPrice() <= 0);
			options.sort(Comparator.comparingInt(option -> option.getItem().getId()));
			while (proceeds < shortfall && !options.isEmpty())
			{
				long remaining = shortfall - proceeds;
				TradePlan.Entry chosen = null;
				long chosenProceeds = 0;
				int chosenQuantity = 0;
				for (TradePlan.Entry option : options)
				{
					int quantity = (int) Math.min(option.getQuantity(), (remaining + option.getUnitPrice() - 1) / option.getUnitPrice());
					long net = quantity * option.getUnitPrice();
					// Prefer the smallest sale that covers the gap. If none can cover it,
					// use the largest smaller stack and repeat. Do not sell a 1b weapon
					// for a 100k gap when an available 110k item can cover it.
					boolean covers = net >= remaining;
					boolean chosenCovers = chosenProceeds >= remaining;
					if (chosen == null || covers && (!chosenCovers || net < chosenProceeds)
						|| !covers && !chosenCovers && net > chosenProceeds)
					{
						chosen = option;
						chosenQuantity = quantity;
						chosenProceeds = net;
					}
				}
				sells.add(new TradePlan.Entry(chosen.getItem(), chosenQuantity, chosen.getUnitPrice()));
				proceeds = saturatedAdd(proceeds, chosenProceeds);
				options.remove(chosen);
			}
		}
		return new TradePlan(buys, sells, buyTotal, proceeds,
			request.getCandidateMode() == CandidateMode.ALL_STANDARD || buyTotal - proceeds <= request.getBudget());
	}

	private static long saturatedAdd(long first, long second)
	{
		return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
	}
}
