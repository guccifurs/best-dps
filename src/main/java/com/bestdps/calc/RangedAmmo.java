package com.bestdps.calc;

import com.bestdps.data.GearItem;
import java.util.Locale;

final class RangedAmmo
{
	private RangedAmmo()
	{
	}

	static boolean compatible(GearItem ammo, GearItem weapon)
	{
		if (ammo == null)
		{
			return true;
		}
		if (projectileMatches(ammo, weapon))
		{
			return true;
		}
		return !weaponUsesProjectileAmmo(weapon) && passiveAmmoSlotItem(ammo);
	}

	static boolean strengthApplies(GearItem ammo, GearItem weapon)
	{
		return ammo != null && projectileMatches(ammo, weapon);
	}

	private static boolean weaponUsesProjectileAmmo(GearItem weapon)
	{
		String weaponName = name(weapon);
		String category = category(weapon);
		if (weaponName.contains("blowpipe")
			|| weaponName.contains("ballista")
			|| weaponName.contains("karil") && weaponName.contains("crossbow"))
		{
			return true;
		}
		if (weaponName.contains("crystal bow") || weaponName.contains("bow of faerdhinen"))
		{
			return false;
		}
		return category.contains("bow") || category.contains("crossbow");
	}

	private static boolean projectileMatches(GearItem ammo, GearItem weapon)
	{
		String ammoName = name(ammo);
		String weaponName = name(weapon);
		String category = category(weapon);
		if (weaponName.contains("blowpipe"))
		{
			return ammoName.contains("dart");
		}
		if (weaponName.contains("ballista"))
		{
			return ammoName.contains("javelin");
		}
		if (weaponName.contains("karil") && weaponName.contains("crossbow"))
		{
			return ammoName.contains("bolt rack");
		}
		if (weaponName.contains("crystal bow") || weaponName.contains("bow of faerdhinen"))
		{
			return false;
		}
		if (category.contains("bow") && !category.contains("crossbow"))
		{
			return ammoName.contains("arrow");
		}
		if (category.contains("crossbow"))
		{
			return ammoName.contains("bolt") && !ammoName.contains("bolt rack");
		}
		return false;
	}

	private static boolean passiveAmmoSlotItem(GearItem ammo)
	{
		return ammo.getBonuses().getRangedStrength() <= 0 && ammo.getOffensive().getRanged() <= 0;
	}

	private static String name(GearItem item)
	{
		return item == null ? "" : item.getName().toLowerCase(Locale.ROOT);
	}

	private static String category(GearItem item)
	{
		return item == null ? "" : item.getCategory().toLowerCase(Locale.ROOT);
	}
}
