package com.bestdps.calc;

import com.bestdps.data.BestDpsData;
import com.bestdps.data.BestDpsDataService;
import com.bestdps.data.GearItem;
import com.bestdps.data.GearSlot;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class OptimizerReferenceAmmoTest
{
	@Test
	public void projectileCompatibilityMatchesPinnedWikiCalculator()
	{
		BestDpsData data = new BestDpsDataService().load();
		JsonObject reference = new JsonParser().parse(new InputStreamReader(
			getClass().getResourceAsStream("/com/bestdps/optimizer-ammo-reference.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		List<String> mismatches = new ArrayList<>();
		int checked = 0;
		int weapons = 0;
		for (Map.Entry<String, JsonElement> entry : reference.getAsJsonObject("weapons").entrySet())
		{
			GearItem weapon = data.getGear(Integer.parseInt(entry.getKey()));
			if (weapon == null || !weapon.isStandardGear()) { continue; }
			weapons++;
			Set<Integer> expected = new HashSet<>();
			for (JsonElement id : entry.getValue().getAsJsonArray()) { expected.add(id.getAsInt()); }
			if (RangedAmmo.compatible(null, weapon)) { mismatches.add(weapon.label() + ": accepted missing ammunition"); }
			checked++;
			for (GearItem ammo : data.getGearItems())
			{
				if (ammo.getSlot() != GearSlot.AMMO) { continue; }
				checked++;
				boolean actual = RangedAmmo.compatible(ammo, weapon);
				if (actual != expected.contains(ammo.getId()))
				{
					mismatches.add(weapon.label() + " #" + weapon.getId() + " / " + ammo.label() + " #" + ammo.getId()
						+ ": expected " + expected.contains(ammo.getId()) + ", got " + actual);
				}
			}
		}
		System.out.println("WIKI_AMMO_REFERENCE weapons=" + weapons + " pairs=" + checked + " mismatches=" + mismatches.size());
		Assert.assertTrue(String.join("\n", mismatches), mismatches.isEmpty());
	}
}
