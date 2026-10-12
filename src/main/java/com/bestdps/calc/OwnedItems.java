package com.bestdps.calc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class OwnedItems
{
	public static final OwnedItems EMPTY = new OwnedItems(Collections.emptyMap(), false);

	private final Map<Integer, Integer> quantities;
	private final boolean bankScanned;

	public OwnedItems(Map<Integer, Integer> quantities, boolean bankScanned)
	{
		Map<Integer, Integer> copy = new HashMap<>();
		if (quantities != null)
		{
			for (Map.Entry<Integer, Integer> entry : quantities.entrySet())
			{
				if (entry.getKey() != null && entry.getKey() > 0 && entry.getValue() != null && entry.getValue() > 0)
				{
					copy.put(entry.getKey(), entry.getValue());
				}
			}
		}
		this.quantities = Collections.unmodifiableMap(copy);
		this.bankScanned = bankScanned;
	}

	public boolean owns(int itemId)
	{
		return quantities.getOrDefault(itemId, 0) > 0;
	}

	public int getQuantity(int itemId)
	{
		return quantities.getOrDefault(itemId, 0);
	}

	public Map<Integer, Integer> getQuantities()
	{
		return quantities;
	}

	public boolean isBankScanned()
	{
		return bankScanned;
	}

	public int size()
	{
		return quantities.size();
	}
}
