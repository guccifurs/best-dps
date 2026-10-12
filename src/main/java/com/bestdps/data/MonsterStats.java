package com.bestdps.data;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MonsterStats
{
	private final int id;
	private final String name;
	private final String version;
	private final int combatLevel;
	private final int hitpoints;
	private final int size;
	private final int defence;
	private final int magic;
	private final int offensiveMagic;
	private final MonsterDefences defensive;
	private final List<String> attributes;
	private final boolean slayerMonster;
	private final String weaknessElement;
	private final int weaknessSeverity;
	private final int attack;
	private final int strength;
	private final int ranged;
	private final String normalizedName;
	private final String normalizedVersion;
	private final String searchText;
	private final Set<String> attributeSet;

	public MonsterStats(
		int id,
		String name,
		String version,
		int combatLevel,
		int hitpoints,
		int defence,
		int magic,
		MonsterDefences defensive,
		List<String> attributes)
	{
		this(id, name, version, combatLevel, hitpoints, 1, defence, magic, 0, defensive, attributes, false, "", 0);
	}

	public MonsterStats(
		int id,
		String name,
		String version,
		int combatLevel,
		int hitpoints,
		int size,
		int defence,
		int magic,
		int offensiveMagic,
		MonsterDefences defensive,
		List<String> attributes,
		boolean slayerMonster,
		String weaknessElement,
		int weaknessSeverity)
	{
		this(id, name, version, combatLevel, hitpoints, size, defence, magic, offensiveMagic,
			defensive, attributes, slayerMonster, weaknessElement, weaknessSeverity, 1, 1, 1);
	}

	public MonsterStats(int id, String name, String version, int combatLevel, int hitpoints,
		int size, int defence, int magic, int offensiveMagic, MonsterDefences defensive,
		List<String> attributes, boolean slayerMonster, String weaknessElement, int weaknessSeverity,
		int attack, int strength, int ranged)
	{
		this.id = id;
		this.name = name == null ? "" : name;
		this.version = version == null ? "" : version;
		this.combatLevel = combatLevel;
		this.hitpoints = hitpoints;
		this.size = Math.max(1, size);
		this.defence = defence;
		this.magic = magic;
		this.offensiveMagic = offensiveMagic;
		this.defensive = defensive == null ? MonsterDefences.ZERO : defensive;
		this.attributes = attributes == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(attributes));
		this.slayerMonster = slayerMonster;
		this.weaknessElement = weaknessElement == null ? "" : weaknessElement.toLowerCase(Locale.ROOT);
		this.weaknessSeverity = Math.max(0, weaknessSeverity);
		this.attack = attack;
		this.strength = strength;
		this.ranged = ranged;
		this.normalizedName = this.name.toLowerCase(Locale.ROOT);
		this.normalizedVersion = this.version.toLowerCase(Locale.ROOT);
		this.searchText = normalizedName + " " + normalizedVersion + " " + id;
		Set<String> normalizedAttributes = new HashSet<>();
		for (String attribute : this.attributes)
		{
			if (attribute != null)
			{
				normalizedAttributes.add(attribute.toLowerCase(Locale.ROOT));
			}
		}
		this.attributeSet = Collections.unmodifiableSet(normalizedAttributes);
	}

	public boolean hasAttribute(String attribute)
	{
		return attribute != null && attributeSet.contains(attribute.toLowerCase(Locale.ROOT));
	}

	public String label()
	{
		String suffix = version.isEmpty() ? "" : " (" + version + ")";
		return name + suffix;
	}

	public String searchText()
	{
		return searchText;
	}

	public String normalizedName() { return normalizedName; }
	public String normalizedVersion() { return normalizedVersion; }
	public int getAttack() { return attack; }
	public int getStrength() { return strength; }
	public int getRanged() { return ranged; }

	/** Preserve the selected NPC variant while applying encounter inputs once. */
	public MonsterStats withCombatStats(int hp, int def, int mage)
	{
		return new MonsterStats(id, name, version, combatLevel, hp, size, def, mage, offensiveMagic,
			defensive, attributes, slayerMonster, weaknessElement, weaknessSeverity, attack, strength, ranged);
	}

	public MonsterStats withVersion(String phase)
	{
		return new MonsterStats(id, name, phase, combatLevel, hitpoints, size, defence, magic, offensiveMagic,
			defensive, attributes, slayerMonster, weaknessElement, weaknessSeverity, attack, strength, ranged);
	}

	public MonsterStats withMagicDefenceBonus(int bonus)
	{
		return new MonsterStats(id, name, version, combatLevel, hitpoints, size, defence, magic, offensiveMagic,
			defensive.withMagic(bonus), attributes, slayerMonster, weaknessElement, weaknessSeverity, attack, strength, ranged);
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public String getVersion()
	{
		return version;
	}

	public int getCombatLevel()
	{
		return combatLevel;
	}

	public int getHitpoints()
	{
		return hitpoints;
	}

	public int getSize()
	{
		return size;
	}

	public int getDefence()
	{
		return defence;
	}

	public int getMagic()
	{
		return magic;
	}

	public int getOffensiveMagic()
	{
		return offensiveMagic;
	}

	public MonsterDefences getDefensive()
	{
		return defensive;
	}

	public List<String> getAttributes()
	{
		return attributes;
	}

	public boolean isSlayerMonster()
	{
		return slayerMonster;
	}

	public String getWeaknessElement()
	{
		return weaknessElement;
	}

	public int getWeaknessSeverity()
	{
		return weaknessSeverity;
	}
}
