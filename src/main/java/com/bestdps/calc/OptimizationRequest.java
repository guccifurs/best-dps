package com.bestdps.calc;

import com.bestdps.data.MonsterStats;
import com.bestdps.data.SpellStats;

public final class OptimizationRequest
{
	private final MonsterStats monster;
	private final CombatStyle style;
	private final PlayerLevels levels;
	private final PrayerBonuses prayers;
	private final SpellStats spell;
	private final int budget;
	private final CandidateMode candidateMode;
	private final boolean includeUntradeables;
	private final boolean onSlayerTask;
	private final OwnedItems ownedItems;
	private final RequirementProfile requirementProfile;
	private final int resultLimit;
	private final boolean sellOwnedItems;
	private final EncounterContext encounterContext;
	private final CombatContext combatContext;

	public OptimizationRequest(
		MonsterStats monster,
		CombatStyle style,
		PlayerLevels levels,
		PrayerBonuses prayers,
		SpellStats spell,
		int budget,
		CandidateMode candidateMode,
		boolean includeUntradeables,
		boolean onSlayerTask,
		OwnedItems ownedItems,
		int resultLimit)
	{
		this(monster, style, levels, prayers, spell, budget, candidateMode, includeUntradeables, onSlayerTask, ownedItems, RequirementProfile.MAXED, resultLimit);
	}

	public OptimizationRequest(
		MonsterStats monster,
		CombatStyle style,
		PlayerLevels levels,
		PrayerBonuses prayers,
		SpellStats spell,
		int budget,
		CandidateMode candidateMode,
		boolean includeUntradeables,
		boolean onSlayerTask,
		OwnedItems ownedItems,
		RequirementProfile requirementProfile,
		int resultLimit)
	{
		this(monster, style, levels, prayers, spell, budget, candidateMode, includeUntradeables, onSlayerTask,
			ownedItems, requirementProfile, resultLimit, false, EncounterContext.DEFAULT, CombatContext.DEFAULT);
	}

	private OptimizationRequest(
		MonsterStats monster, CombatStyle style, PlayerLevels levels, PrayerBonuses prayers, SpellStats spell,
		int budget, CandidateMode candidateMode, boolean includeUntradeables, boolean onSlayerTask,
		OwnedItems ownedItems, RequirementProfile requirementProfile, int resultLimit,
		boolean sellOwnedItems, EncounterContext encounterContext, CombatContext combatContext)
	{
		this.monster = monster;
		this.style = style;
		this.levels = levels;
		this.prayers = prayers == null ? PrayerBonuses.NONE : prayers;
		this.spell = spell;
		this.budget = Math.max(0, budget);
		this.candidateMode = candidateMode == null ? CandidateMode.BUDGET : candidateMode;
		this.includeUntradeables = includeUntradeables;
		this.onSlayerTask = onSlayerTask;
		this.ownedItems = ownedItems == null ? OwnedItems.EMPTY : ownedItems;
		this.requirementProfile = requirementProfile == null ? RequirementProfile.MAXED : requirementProfile;
		this.resultLimit = Math.max(1, Math.min(50, resultLimit));
		this.sellOwnedItems = sellOwnedItems;
		this.encounterContext = encounterContext == null ? EncounterContext.DEFAULT : encounterContext;
		this.combatContext = combatContext == null ? CombatContext.DEFAULT : combatContext;
	}

	public MonsterStats getMonster()
	{
		return monster;
	}

	public CombatStyle getStyle()
	{
		return style;
	}

	public PlayerLevels getLevels()
	{
		return levels;
	}

	public PrayerBonuses getPrayers()
	{
		return prayers;
	}

	public SpellStats getSpell()
	{
		return spell;
	}

	public boolean isAutoSpell()
	{
		return spell == null;
	}

	public int getBudget()
	{
		return budget;
	}

	public CandidateMode getCandidateMode()
	{
		return candidateMode;
	}

	public boolean isIncludeUntradeables()
	{
		return includeUntradeables;
	}

	public boolean isOnSlayerTask()
	{
		return onSlayerTask;
	}

	public OwnedItems getOwnedItems()
	{
		return ownedItems;
	}

	public RequirementProfile getRequirementProfile()
	{
		return requirementProfile;
	}

	public int getResultLimit()
	{
		return resultLimit;
	}

	public boolean isSellOwnedItems()
	{
		return sellOwnedItems && candidateMode == CandidateMode.OWNED_OR_BUDGET;
	}

	public EncounterContext getEncounterContext()
	{
		return encounterContext;
	}

	public CombatContext getCombatContext()
	{
		return combatContext;
	}

	public OptimizationRequest withCombatContext(CombatContext context)
	{
		return new OptimizationRequest(monster, style, levels, prayers, spell, budget, candidateMode,
			includeUntradeables, onSlayerTask, ownedItems, requirementProfile, resultLimit, sellOwnedItems, encounterContext, context);
	}

	public OptimizationRequest withSellOwnedItems(boolean enabled)
	{
		return new OptimizationRequest(monster, style, levels, prayers, spell, budget, candidateMode,
			includeUntradeables, onSlayerTask, ownedItems, requirementProfile, resultLimit, enabled, encounterContext, combatContext);
	}

	public OptimizationRequest withEncounterContext(EncounterContext context)
	{
		return new OptimizationRequest(monster, style, levels, prayers, spell, budget, candidateMode,
			includeUntradeables, onSlayerTask, ownedItems, requirementProfile, resultLimit, sellOwnedItems, context, combatContext);
	}

	public OptimizationRequest withMonster(MonsterStats target)
	{
		return new OptimizationRequest(target, style, levels, prayers, spell, budget, candidateMode,
			includeUntradeables, onSlayerTask, ownedItems, requirementProfile, resultLimit, sellOwnedItems, encounterContext, combatContext);
	}

	public OptimizationRequest withStyle(CombatStyle style)
	{
		return new OptimizationRequest(
			monster,
			style,
			levels,
			prayers,
			spell,
			budget,
			candidateMode,
			includeUntradeables,
			onSlayerTask,
			ownedItems,
			requirementProfile,
			resultLimit, sellOwnedItems, encounterContext, combatContext);
	}

	public OptimizationRequest withSpell(SpellStats spell)
	{
		return new OptimizationRequest(
			monster,
			style,
			levels,
			prayers,
			spell,
			budget,
			candidateMode,
			includeUntradeables,
			onSlayerTask,
			ownedItems,
			requirementProfile,
			resultLimit, sellOwnedItems, encounterContext, combatContext);
	}
}
