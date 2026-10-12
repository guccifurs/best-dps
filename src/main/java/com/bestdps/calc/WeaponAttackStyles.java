package com.bestdps.calc;

import com.bestdps.data.GearItem;
import java.util.Locale;

/** Legal damaging stances, from https://oldschool.runescape.wiki/w/Combat_Options. */
final class WeaponAttackStyles
{
	static final class Stance
	{
		final String type;
		final int attack;
		final int strength;

		Stance(String type, int attack, int strength)
		{
			this.type = type;
			this.attack = attack;
			this.strength = strength;
		}

		String label()
		{
			return type + (attack == 3 ? " accurate" : strength == 3 ? " aggressive" : attack == 1 ? " controlled" : " defensive");
		}
	}

	private static final Stance STAB_ACCURATE = new Stance("stab", 3, 0);
	private static final Stance STAB_AGGRESSIVE = new Stance("stab", 0, 3);
	private static final Stance STAB_CONTROLLED = new Stance("stab", 1, 1);
	private static final Stance SLASH_ACCURATE = new Stance("slash", 3, 0);
	private static final Stance SLASH_AGGRESSIVE = new Stance("slash", 0, 3);
	private static final Stance SLASH_CONTROLLED = new Stance("slash", 1, 1);
	private static final Stance CRUSH_ACCURATE = new Stance("crush", 3, 0);
	private static final Stance CRUSH_AGGRESSIVE = new Stance("crush", 0, 3);
	private static final Stance CRUSH_CONTROLLED = new Stance("crush", 1, 1);
	private static final Stance[] CRUSH = {CRUSH_ACCURATE, CRUSH_AGGRESSIVE};
	private static final Stance[] SLASH = {SLASH_ACCURATE, SLASH_AGGRESSIVE, STAB_CONTROLLED};
	private static final Stance[] STAB = {STAB_ACCURATE, STAB_AGGRESSIVE, SLASH_AGGRESSIVE};
	private static final Stance[] AXE = {SLASH_ACCURATE, SLASH_AGGRESSIVE, CRUSH_AGGRESSIVE};
	private static final Stance[] SPIKED = {CRUSH_ACCURATE, CRUSH_AGGRESSIVE, STAB_CONTROLLED};
	private static final Stance[] PARTISAN = {STAB_ACCURATE, STAB_AGGRESSIVE, CRUSH_AGGRESSIVE};
	private static final Stance[] SPEAR = {STAB_CONTROLLED, SLASH_CONTROLLED, CRUSH_CONTROLLED};
	private static final Stance[] WHIP = {SLASH_ACCURATE, SLASH_CONTROLLED};
	private static final Stance[] POLEARM = {STAB_CONTROLLED, SLASH_AGGRESSIVE};
	private static final Stance[] BANNER = {STAB_ACCURATE, SLASH_AGGRESSIVE, CRUSH_CONTROLLED};
	private static final Stance[] BLADED_STAFF = {STAB_ACCURATE, SLASH_AGGRESSIVE, new Stance("crush", 0, 0)};
	private static final Stance[] MULTI = {STAB_ACCURATE, SLASH_AGGRESSIVE, CRUSH_AGGRESSIVE};
	private static final Stance[] AGGRESSIVE_CRUSH = {CRUSH_AGGRESSIVE};
	private static final Stance[] AGGRESSIVE_SLASH = {SLASH_AGGRESSIVE};
	private static final Stance[] BULWARK = {CRUSH_ACCURATE};
	private static final Stance[] NONE = {};

	private WeaponAttackStyles()
	{
	}

	static Stance[] melee(GearItem weapon)
	{
		String category = weapon == null ? "unarmed" : weapon.getCategory().toLowerCase(Locale.ROOT);
		switch (category)
		{
			case "stab sword": return STAB;
			case "flail":
				return new Stance[]{SLASH_ACCURATE, SLASH_AGGRESSIVE};
			case "slash sword":
			case "claw": return SLASH;
			case "axe":
			case "2h sword":
			case "scythe": return AXE;
			case "spiked": return SPIKED;
			case "pickaxe":
			case "partisan": return PARTISAN;
			case "spear": return SPEAR;
			case "whip": return WHIP;
			case "polearm": return POLEARM;
			case "banner": return BANNER;
			case "bladed staff": return BLADED_STAFF;
			case "multi-melee": return MULTI;
			case "bludgeon":
			case "gun": return AGGRESSIVE_CRUSH;
			case "salamander": return AGGRESSIVE_SLASH;
			case "bulwark": return BULWARK;
			case "unarmed":
			case "blunt":
			case "polestaff":
			case "staff": return CRUSH;
			default: return NONE;
		}
	}
}
