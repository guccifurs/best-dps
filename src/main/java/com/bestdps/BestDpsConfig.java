package com.bestdps;

import com.bestdps.calc.CombatStyle;
import com.bestdps.calc.BoostProfile;
import com.bestdps.calc.PrayerProfile;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bestdps")
public interface BestDpsConfig extends Config
{
	@ConfigItem(
		keyName = "defaultStyle",
		name = "Default style",
		description = "Default combat style for new searches",
		position = 1
	)
	default CombatStyle defaultStyle()
	{
		return CombatStyle.ANY;
	}

	@ConfigItem(
		keyName = "budget",
		name = "Budget",
		description = "Default GP budget",
		position = 2
	)
	default int budget()
	{
		return 10_000_000;
	}

	@ConfigItem(
		keyName = "includeUntradeables",
		name = "Include Untradeables",
		description = "Include untradeable gear when gear source allows it",
		position = 3
	)
	default boolean includeUntradeables()
	{
		return false;
	}

	@ConfigItem(
		keyName = "useMyLevels",
		name = "Use my levels",
		description = "Only show gear your current skills and quest unlocks can equip",
		position = 4
	)
	default boolean useMyLevels()
	{
		return false;
	}

	@ConfigItem(
		keyName = "defaultBoostProfile",
		name = "Default boosts",
		description = "Default stat boost assumption for DPS calculations",
		position = 5
	)
	default BoostProfile defaultBoostProfile()
	{
		return BoostProfile.LIVE_CURRENT;
	}

	@ConfigItem(
		keyName = "defaultPrayerProfile",
		name = "Default prayers",
		description = "Default prayer assumption for DPS calculations",
		position = 6
	)
	default PrayerProfile defaultPrayerProfile()
	{
		return PrayerProfile.BEST_AVAILABLE;
	}

	@ConfigItem(
		keyName = "readProfileOnStartup",
		name = "Read stats on startup",
		description = "Read levels, active prayers, equipment, inventory, and bank cache when the plugin starts",
		position = 7
	)
	default boolean readProfileOnStartup()
	{
		return true;
	}
}
