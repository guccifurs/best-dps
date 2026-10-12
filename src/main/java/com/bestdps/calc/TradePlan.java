package com.bestdps.calc;

import com.bestdps.data.GearItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A price estimate for manual review. This model cannot place GE offers. */
public final class TradePlan
{
	private final List<Entry> purchases;
	private final List<Entry> sales;
	private final long buyCost;
	private final long netProceeds;
	private final boolean affordable;

	TradePlan(List<Entry> purchases, List<Entry> sales, long buyCost, long netProceeds, boolean affordable)
	{
		this.purchases = Collections.unmodifiableList(new ArrayList<>(purchases));
		this.sales = Collections.unmodifiableList(new ArrayList<>(sales));
		this.buyCost = buyCost;
		this.netProceeds = netProceeds;
		this.affordable = affordable;
	}

	public List<Entry> getPurchases()
	{
		return purchases;
	}

	public List<Entry> getSales()
	{
		return sales;
	}

	public long getBuyCost()
	{
		return buyCost;
	}

	public long getNetProceeds()
	{
		return netProceeds;
	}

	public long getCashNeeded()
	{
		return Math.max(0L, buyCost - netProceeds);
	}

	public boolean isAffordable()
	{
		return affordable;
	}

	public static final class Entry
	{
		private final GearItem item;
		private final int quantity;
		private final long unitPrice;

		Entry(GearItem item, int quantity, long unitPrice)
		{
			this.item = item;
			this.quantity = quantity;
			this.unitPrice = unitPrice;
		}

		public GearItem getItem()
		{
			return item;
		}

		public int getQuantity()
		{
			return quantity;
		}

		/** Net of the tax reserve for sales; estimated offer price for purchases. */
		public long getUnitPrice()
		{
			return unitPrice;
		}

		public long getTotal()
		{
			return unitPrice * quantity;
		}
	}
}
