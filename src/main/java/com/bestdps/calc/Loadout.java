package com.bestdps.calc;

import com.bestdps.data.GearItem;
import com.bestdps.data.GearSlot;
import com.bestdps.data.StatBlock;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class Loadout
{
	private final EnumMap<GearSlot, GearItem> gear;
	private final StatBlock offensive;
	private final StatBlock bonuses;
	private final int cost;

	public Loadout(Map<GearSlot, GearItem> gear)
	{
		this.gear = new EnumMap<>(GearSlot.class);
		this.gear.putAll(gear);

		int[] offensiveTotal = new int[9];
		int[] bonusTotal = new int[9];
		long totalCost = 0;
		for (GearItem item : this.gear.values())
		{
			if (item == null)
			{
				continue;
			}
			add(offensiveTotal, item.getOffensive());
			add(bonusTotal, item.getBonuses());
			totalCost += item.getPriceOrZero();
		}
		this.offensive = stats(offensiveTotal);
		this.bonuses = stats(bonusTotal);
		this.cost = (int) Math.min(Integer.MAX_VALUE, totalCost);
	}

	private static void add(int[] total, StatBlock item)
	{
		total[0] += item.getStab();
		total[1] += item.getSlash();
		total[2] += item.getCrush();
		total[3] += item.getMagic();
		total[4] += item.getRanged();
		total[5] += item.getStrength();
		total[6] += item.getRangedStrength();
		total[7] += item.getMagicDamage();
		total[8] += item.getPrayer();
	}

	private static StatBlock stats(int[] total)
	{
		return new StatBlock(total[0], total[1], total[2], total[3], total[4], total[5], total[6], total[7], total[8]);
	}

	public GearItem get(GearSlot slot)
	{
		return gear.get(slot);
	}

	public GearItem getWeapon()
	{
		return gear.get(GearSlot.WEAPON);
	}

	public Map<GearSlot, GearItem> getGear()
	{
		return Collections.unmodifiableMap(gear);
	}

	public StatBlock getOffensive()
	{
		return offensive;
	}

	public StatBlock getBonuses()
	{
		return bonuses;
	}

	public int getCost()
	{
		return cost;
	}
}
