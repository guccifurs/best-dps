package com.bestdps.calc;

public enum PrayerProfile
{
	NONE("No prayers"),
	BEST_AVAILABLE("Best available"),
	CURRENT_ACTIVE("Current active");

	private final String label;

	PrayerProfile(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
