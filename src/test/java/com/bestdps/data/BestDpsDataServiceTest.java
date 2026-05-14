package com.bestdps.data;

import org.junit.Assert;
import org.junit.Test;

public class BestDpsDataServiceTest
{
	@Test
	public void loadsMergedGearAndMonsterDataset()
	{
		BestDpsData data = new BestDpsDataService().load();
		Assert.assertTrue(data.getGearItems().size() > 5000);
		Assert.assertTrue(data.getMonsters().size() > 2500);
		Assert.assertTrue(data.getSpells().size() > 20);
		Assert.assertFalse(data.searchMonsters("zulrah", 10).isEmpty());
	}

	@Test
	public void impossiblePvpVariantsAreNotStandardGear()
	{
		BestDpsData data = new BestDpsDataService().load();
		GearItem vesta = data.getGear(22616);
		Assert.assertNotNull(vesta);
		Assert.assertFalse(vesta.isStandardGear());
	}

	@Test
	public void loadsEquipmentRequirements()
	{
		BestDpsData data = new BestDpsDataService().load();
		GearItem whip = data.getGear(4151);
		GearItem bandosChestplate = data.getGear(11832);
		Assert.assertNotNull(whip);
		Assert.assertNotNull(bandosChestplate);
		Assert.assertEquals(70, whip.getRequirements().getSkills().get("attack").intValue());
		Assert.assertEquals(65, bandosChestplate.getRequirements().getSkills().get("defence").intValue());
	}
}
