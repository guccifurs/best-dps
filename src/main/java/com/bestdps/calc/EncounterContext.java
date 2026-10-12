package com.bestdps.calc;

/** Explicit inputs which change NPC stats; never inferred from the player's equipment. */
public final class EncounterContext
{
	public static final EncounterContext DEFAULT = new EncounterContext(0, 1, 0, false, 126, 99, 100, -1);

	private final int raidLevel;
	private final int partySize;
	private final int pathLevel;
	private final boolean challengeMode;
	private final int partyMaxCombat;
	private final int partyMaxHitpoints;
	private final int targetHpPercent;
	private final int defenceOverride;

	public EncounterContext(int raidLevel, int partySize, int pathLevel, boolean challengeMode,
		int partyMaxCombat, int partyMaxHitpoints, int targetHpPercent, int defenceOverride)
	{
		this.raidLevel = clamp(raidLevel, 0, 600);
		this.partySize = clamp(partySize, 1, 100);
		this.pathLevel = clamp(pathLevel, 0, 6);
		this.challengeMode = challengeMode;
		this.partyMaxCombat = clamp(partyMaxCombat, 3, 126);
		this.partyMaxHitpoints = clamp(partyMaxHitpoints, 10, 99);
		this.targetHpPercent = clamp(targetHpPercent, 0, 100);
		this.defenceOverride = clamp(defenceOverride, -1, 20000);
	}

	private static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
	public int getRaidLevel() { return raidLevel; }
	public int getPartySize() { return partySize; }
	public int getPathLevel() { return pathLevel; }
	public boolean isChallengeMode() { return challengeMode; }
	public int getPartyMaxCombat() { return partyMaxCombat; }
	public int getPartyMaxHitpoints() { return partyMaxHitpoints; }
	public int getTargetHpPercent() { return targetHpPercent; }
	public int getDefenceOverride() { return defenceOverride; }
}
