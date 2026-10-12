package com.bestdps.calc;

import com.bestdps.data.BestDpsData;
import com.bestdps.data.BestDpsDataService;
import com.bestdps.data.GearItem;
import com.bestdps.data.GearSlot;
import com.bestdps.data.MonsterStats;
import com.bestdps.data.SpellStats;
import java.util.EnumMap;
import org.junit.Assert;
import org.junit.Test;

/** Live Wiki game facts; source links and deliberate oracle differences are in encounter-coverage.md. */
public class EncounterReferenceRulesTest
{
	private static final BestDpsData DATA = new BestDpsDataService().load();

	private static MonsterStats npc(int id, String version)
	{
		return DATA.getMonsters().stream().filter(m -> m.getId() == id && (version == null || m.getVersion().equals(version)))
			.findFirst().orElseThrow(() -> new AssertionError("Missing target " + id + " " + version));
	}

	private static Loadout gear(int... ids)
	{
		EnumMap<GearSlot, GearItem> items = new EnumMap<>(GearSlot.class);
		for (int id : ids)
		{
			GearItem item = DATA.getGear(id);
			Assert.assertNotNull("Missing equipment " + id, item);
			items.put(item.getSlot(), item);
		}
		return new Loadout(items);
	}

	private static SpellStats spell(String name)
	{
		return DATA.getSpells().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow(AssertionError::new);
	}

	@Test
	public void toaUsesFractionalInvocationRollAndExcludesOverlords()
	{
		EncounterContext one = new EncounterContext(1, 1, 0, false, 126, 99, 100, -1);
		Assert.assertEquals(25100, EncounterRules.defenceRoll(npc(11719, null), one, 25000));
		Assert.assertEquals(25000, EncounterRules.defenceRoll(npc(11724, null), one, 25000));
		Assert.assertEquals(4950, EncounterRules.scale(npc(11755, null),
			new EncounterContext(100, 1, 0, false, 126, 99, 100, -1)).getHitpoints());
	}

	@Test
	public void royalElementalsKeepChanceToSplashDespiteSuccessfulMaximumHit()
	{
		MonsterStats fire = npc(14150, null);
		Assert.assertTrue(EncounterRules.guaranteedMaximumHit(fire, gear(1387), "magic"));
		Assert.assertEquals(0.4, EncounterRules.forcedAccuracy(fire, gear(1387), "magic", spell("Fire Wave"), 0.99), 1e-12);
		Assert.assertEquals(1.0, EncounterRules.forcedAccuracy(fire, gear(1387), "magic", spell("Wind Strike"), 0.01), 1e-12);
	}

	@Test
	public void royalTitanExplicitPositionChangesRangedRollAndMeleeReach()
	{
		Assert.assertEquals(1, EncounterRules.attackRollMultiplier(npc(12596, "Melee range"), "ranged"));
		Assert.assertEquals(6, EncounterRules.attackRollMultiplier(npc(12596, "Out of melee range"), "ranged"));
		Assert.assertEquals(6, EncounterRules.attackRollMultiplier(npc(14147, "Elemental blast"), "ranged"));
		Assert.assertFalse(EncounterRules.rejectionReason(npc(12596, "Out of melee range"), gear(4151), "slash").isEmpty());
	}

	@Test
	public void voidFlareStyleAndContractAreNotUniversalMaxHits()
	{
		Assert.assertFalse(EncounterRules.guaranteedMaximumHit(npc(14179, "Fire"), gear(4151), "slash"));
		Assert.assertTrue(EncounterRules.guaranteedMaximumHit(npc(14179, "Fire"), gear(861, 892), "ranged"));
		Assert.assertFalse(EncounterRules.guaranteedMaximumHit(npc(14179, "Fire"), gear(9185), "ranged"));
		Assert.assertFalse(EncounterRules.guaranteedMaximumHit(npc(14179, "Fire, Bloodied Blows"), gear(861, 892), "ranged"));
		Assert.assertEquals(71, npc(14179, "Shadow, phase 3").getHitpoints());
	}

	@Test
	public void regularFlowerAndQuestFlowerHaveDifferentKillRules()
	{
		Assert.assertEquals(10, EncounterRules.instantKillHit(npc(8584, null), gear(4151), "slash", null));
		Assert.assertEquals(-1, EncounterRules.instantKillHit(npc(11193, null), gear(4151), "slash", null));
		Assert.assertEquals(10.0, EncounterRules.modifyHit(npc(9470, null), gear(4151), "slash", 1), 0.0);
	}

	@Test
	public void respiratoryReachAndDemonbaneAreExplicit()
	{
		MonsterStats target = npc(5914, "Stunned");
		Assert.assertEquals(15, EncounterRules.minimumHit(target, gear(861, 892), "ranged", 31));
		Assert.assertFalse(EncounterRules.rejectionReason(target, gear(4151), "slash").isEmpty());
		Assert.assertEquals(50, EncounterRules.instantKillHit(target, gear(1387), "magic", spell("Dark Demonbane")));
		Assert.assertFalse(EncounterRules.blockingReason(npc(5914, "Protected")).isEmpty());
		Assert.assertEquals(0.6, EncounterRules.forcedAccuracy(npc(5916, null), gear(4151), "slash", 0.6), 0.0);
	}

	@Test
	public void zogreAndOlmModifiersUseSpecificAttacks()
	{
		Assert.assertEquals(5.0, EncounterRules.modifyHit(npc(866, null), gear(4151), "slash", 23), 0.0);
		Assert.assertEquals(7.0, EncounterRules.modifyHit(npc(866, null), gear(1387), "magic", spell("Crumble Undead"), 15), 0.0);
		Assert.assertEquals(23.0, EncounterRules.modifyHit(npc(866, null), gear(4827, 4803), "ranged", 23), 0.0);
		Assert.assertEquals(30.0, EncounterRules.modifyHit(npc(7550, null), gear(4151), "slash", 30), 0.0);
		Assert.assertEquals(10.0, EncounterRules.modifyHit(npc(7550, null), gear(861, 892), "ranged", 30), 0.0);
	}

	@Test
	public void targetDamageFloorsPreserveZeroSecondaryHits()
	{
		Assert.assertEquals(0.0, EncounterRules.modifyHit(npc(7540, null), gear(1387), "magic", 0), 0.0);
		Assert.assertEquals(1.0, EncounterRules.modifyHit(npc(7540, null), gear(1387), "magic", 1), 0.0);
	}

	@Test
	public void directSpellCallsEnforceTargetAttributes()
	{
		Assert.assertFalse(EncounterRules.rejectionReason(npc(415, null), gear(1387), "magic", spell("Crumble Undead")).isEmpty());
		Assert.assertFalse(EncounterRules.rejectionReason(npc(866, null), gear(1387), "magic", spell("Dark Demonbane")).isEmpty());
		Assert.assertFalse(EncounterRules.rejectionReason(npc(6319, null), gear(1387), "magic", spell("Crumble Undead")).isEmpty());
		Assert.assertTrue(EncounterRules.rejectionReason(npc(1163, null), gear(1387), "magic", spell("Crumble Undead")).isEmpty());
	}

	@Test
	public void sharedJhallanIdKeepsMainAndCloneStatsDistinct()
	{
		Assert.assertEquals(1500, npc(12353, "Main opponent").getHitpoints());
		Assert.assertEquals(250, npc(12353, "Main opponent").getDefence());
		Assert.assertEquals(1, npc(12353, "Clone").getHitpoints());
		Assert.assertEquals(83, npc(12353, "Clone").getDefence());
	}

	@Test
	public void coreOtherStylesRemainAttackableAndScurriusMagicRatHitsAreGuaranteed()
	{
		Assert.assertTrue(EncounterRules.rejectionReason(npc(11755, null), gear(861, 892), "ranged").isEmpty());
		Assert.assertTrue(EncounterRules.rejectionReason(npc(11758, null), gear(1387), "magic", spell("Fire Wave")).isEmpty());
		Assert.assertEquals(1.0, EncounterRules.forcedAccuracy(npc(7223, null), gear(1387), "magic", spell("Fire Wave"), 0.5), 0.0);
	}

	@Test
	public void knownUnsupportedTargetsNeverReturnOrdinaryRankings()
	{
		for (int id : new int[] {1743, 1754, 5912, 5646, 9026, 9048, 16305, 15742})
		{
			Assert.assertFalse("Missing guard for " + id, EncounterRules.blockingReason(npc(id, null)).isEmpty());
		}
	}
}
