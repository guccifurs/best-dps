package com.bestdps.calc;

public final class DpsResult
{
	private final Loadout loadout;
	private final double dps;
	private final double accuracy;
	private final double expectedHit;
	private final int maxHit;
	private final int attackSpeed;
	private final String attackType;
	private final long attackRoll;
	private final long defenceRoll;
	private final int purchaseCost;
	private final String spellName;
	private final TradePlan tradePlan;
	private final double baselineDps;

	public DpsResult(
		Loadout loadout,
		double dps,
		double accuracy,
		double expectedHit,
		int maxHit,
		int attackSpeed,
		String attackType,
		long attackRoll,
		long defenceRoll)
	{
		this(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed, attackType, attackRoll, defenceRoll, loadout == null ? 0 : loadout.getCost(), "");
	}

	public DpsResult(
		Loadout loadout,
		double dps,
		double accuracy,
		double expectedHit,
		int maxHit,
		int attackSpeed,
		String attackType,
		long attackRoll,
		long defenceRoll,
		int purchaseCost,
		String spellName)
	{
		this(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed, attackType, attackRoll, defenceRoll,
			purchaseCost, spellName, null, 0);
	}

	private DpsResult(Loadout loadout, double dps, double accuracy, double expectedHit, int maxHit,
		int attackSpeed, String attackType, long attackRoll, long defenceRoll, int purchaseCost,
		String spellName, TradePlan tradePlan, double baselineDps)
	{
		this.loadout = loadout;
		this.dps = dps;
		this.accuracy = accuracy;
		this.expectedHit = expectedHit;
		this.maxHit = maxHit;
		this.attackSpeed = attackSpeed;
		this.attackType = attackType;
		this.attackRoll = attackRoll;
		this.defenceRoll = defenceRoll;
		this.purchaseCost = Math.max(0, purchaseCost);
		this.spellName = spellName == null ? "" : spellName;
		this.tradePlan = tradePlan;
		this.baselineDps = Math.max(0, baselineDps);
	}

	public DpsResult withPurchaseCost(int purchaseCost)
	{
		return new DpsResult(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed, attackType, attackRoll, defenceRoll, purchaseCost, spellName, tradePlan, baselineDps);
	}

	public DpsResult withTradePlan(TradePlan plan)
	{
		int cost = plan == null ? purchaseCost : (int) Math.min(Integer.MAX_VALUE, plan.getBuyCost());
		return new DpsResult(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed, attackType,
			attackRoll, defenceRoll, cost, spellName, plan, baselineDps);
	}

	public DpsResult withBaselineDps(double baseline)
	{
		return new DpsResult(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed, attackType,
			attackRoll, defenceRoll, purchaseCost, spellName, tradePlan, baseline);
	}

	public double getBaselineDps()
	{
		return baselineDps;
	}

	public TradePlan getTradePlan()
	{
		return tradePlan;
	}

	public Loadout getLoadout()
	{
		return loadout;
	}

	public double getDps()
	{
		return dps;
	}

	public double getAccuracy()
	{
		return accuracy;
	}

	public double getExpectedHit()
	{
		return expectedHit;
	}

	public int getMaxHit()
	{
		return maxHit;
	}

	public int getAttackSpeed()
	{
		return attackSpeed;
	}

	public String getAttackType()
	{
		return attackType;
	}

	public long getAttackRoll()
	{
		return attackRoll;
	}

	public long getDefenceRoll()
	{
		return defenceRoll;
	}

	public int getPurchaseCost()
	{
		return purchaseCost;
	}

	public String getSpellName()
	{
		return spellName;
	}
}
