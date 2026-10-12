package com.bestdps.calc;

import com.bestdps.data.GearItem;
import com.bestdps.data.GearSlot;
import java.util.ArrayList;
import java.util.List;

/** Disclosure for mechanics outside a single-target, normal-attack calculation. */
public final class CombatMechanics
{
	private CombatMechanics()
	{
	}

	public static List<String> warnings(Loadout loadout)
	{
		List<String> warnings = new ArrayList<>();
		GearItem weapon = loadout.getWeapon();
		String name = weapon == null ? "" : weapon.getNormalizedName();
		if (weapon != null && (weapon.getNormalizedLabel().contains("uncharged") || weapon.getNormalizedLabel().contains("empty") || weapon.getNormalizedLabel().contains("inactive")))
		{
			warnings.add("Charge this weapon before use. Displayed DPS assumes a usable charged weapon; charging supplies and recurring costs are excluded.");
		}
		if (name.contains("blowpipe")) warnings.add("Ammo row shows loaded darts; scales and recurring ammunition costs are excluded.");
		if (wears(loadout, "amulet of avarice")) warnings.add("Amulet of avarice assumes its normal 20% revenant bonus; Forinthry Surge is not active in this estimate.");
		if (name.contains("venator")) warnings.add("Single-target DPS: Venator ricochets require extra nearby targets and are excluded.");
		if (name.contains("chinchompa")) warnings.add("Chinchompa DPS assumes optimal distance for the selected fuse; nearby targets are excluded.");
		if (name.contains("eclipse atlatl") && wears(loadout, "eclipse moon chestplate") && wears(loadout, "eclipse moon tassets") && wears(loadout, "eclipse moon helm"))
		{
			warnings.add("Eclipse Moon burn damage is not included in the normal-hit estimate.");
		}
		if (name.contains("ancient godsword") || name.contains("voidwaker") || name.contains("claws") || name.contains("dawnbringer"))
		{
			warnings.add("Normal attacks only; special-attack damage, energy and rotations are excluded.");
		}
		if (name.contains("salamander") || name.contains("swamp lizard")) warnings.add("Salamander magic mode is not modelled; other styles use their own defence categories.");
		if (name.contains("obsidian") || name.contains("toxic") || name.contains("serpent") || name.contains("noxious"))
		{
			warnings.add("Damage over time from poison, venom and burn is excluded.");
		}
		GearItem ammo = loadout.get(GearSlot.AMMO);
		if (ammo != null && ammo.getNormalizedName().startsWith("ruby") && ammo.getNormalizedName().endsWith("(e)"))
		{
			warnings.add("Ruby bolt DPS uses the selected target HP, not an average over a full kill; Blood Forfeit requires at least 10 player HP.");
		}
		return warnings;
	}

	private static boolean wears(Loadout loadout, String marker)
	{
		for (GearItem item : loadout.getGear().values())
		{
			if (item != null && item.getNormalizedName().contains(marker)) return true;
		}
		return false;
	}
}
