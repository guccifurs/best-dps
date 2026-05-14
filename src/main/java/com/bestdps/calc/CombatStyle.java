package com.bestdps.calc;

public enum CombatStyle
{
	ANY("Any"),
	MELEE("Melee"),
	RANGED("Ranged"),
	MAGIC("Magic");

	private final String label;

	CombatStyle(String label)
	{
		this.label = label;
	}

	public String getLabel()
	{
		return label;
	}

	public boolean isConcrete()
	{
		return this != ANY;
	}

	public static CombatStyle[] concreteValues()
	{
		return new CombatStyle[]{MELEE, RANGED, MAGIC};
	}

	@Override
	public String toString()
	{
		return label;
	}
}
