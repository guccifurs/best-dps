package com.bestdps.calc;

/** Player state that changes passive damage; defaults never assume optional buffs. */
public final class CombatContext
{
	public static final CombatContext DEFAULT = new CombatContext(99, 99, 0, false, false, false);
	private final int currentHp;
	private final int maxHp;
	private final int soulreaperStacks;
	private final boolean markOfDarkness;
	private final boolean kandarinDiary;
	private final boolean wilderness;

	public CombatContext(int currentHp, int maxHp, int soulreaperStacks, boolean markOfDarkness,
		boolean kandarinDiary, boolean wilderness)
	{
		this.maxHp = Math.max(1, Math.min(99, maxHp));
		this.currentHp = Math.max(1, Math.min(126, currentHp));
		this.soulreaperStacks = Math.max(0, Math.min(5, soulreaperStacks));
		this.markOfDarkness = markOfDarkness;
		this.kandarinDiary = kandarinDiary;
		this.wilderness = wilderness;
	}

	public int getCurrentHp() { return currentHp; }
	public int getMaxHp() { return maxHp; }
	public int getSoulreaperStacks() { return soulreaperStacks; }
	public boolean isMarkOfDarkness() { return markOfDarkness; }
	public boolean isKandarinDiary() { return kandarinDiary; }
	public boolean isWilderness() { return wilderness; }
}
