package com.bestdps.verification;

import com.bestdps.calc.*;
import com.bestdps.data.*;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.runelite.api.Prayer;

/** Batch adapter for external numerical fixtures; no game client or bank profile is read. */
public final class ReferenceSnapshot
{
	private static final Gson JSON = new Gson();
	private static int number(JsonObject o, String key, int fallback) { return o.has(key) ? o.get(key).getAsInt() : fallback; }
	private static boolean flag(JsonObject o, String key) { return o.has(key) && o.get(key).getAsBoolean(); }
	private static String string(JsonObject o, String key, String fallback) { return o.has(key) ? o.get(key).getAsString() : fallback; }
	private static JsonObject object(JsonObject o, String key) { return o.has(key) ? o.getAsJsonObject(key) : new JsonObject(); }

	public static void main(String[] args) throws Exception
	{
		BestDpsData data = new BestDpsDataService().load();
		DpsCalculator calculator = new DpsCalculator();
		try (BufferedReader input = Files.newBufferedReader(Path.of(args[0]));
			BufferedWriter output = Files.newBufferedWriter(Path.of(args[1])))
		{
			String line;
			while ((line = input.readLine()) != null)
			{
				JsonObject f = new JsonParser().parse(line).getAsJsonObject();
				JsonObject result = new JsonObject();
				result.add("key", f.get("key"));
				result.addProperty("inputHash", Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256")
					.digest(line.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
				try
				{
					int npc = number(f, "npc", -1);
					String version = string(f, "pluginVersion", string(f, "version", ""));
					MonsterStats monster = data.getMonsters().stream().filter(m -> m.getId() == npc && m.getVersion().equals(version)
						&& (!f.has("npcName") || m.getName().equals(f.get("npcName").getAsString()))
						&& (!f.has("baseHp") || m.getHitpoints() == f.get("baseHp").getAsInt())).findFirst().orElse(null);
					if (monster == null) throw new IllegalArgumentException("Unknown target " + npc + "/" + version);
					CombatStyle style = CombatStyle.valueOf(f.get("style").getAsString());
					String blocked = EncounterRules.blockingReason(monster, style);
					if (blocked != null && !blocked.isEmpty()) result.addProperty("blocked", blocked);
					EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
					for (JsonElement id : f.getAsJsonArray("gear"))
					{
						GearItem item = data.getGear(id.getAsInt());
						if (item == null) throw new IllegalArgumentException("Unknown gear " + id);
						gear.put(item.getSlot(), item);
					}
					if (f.has("dart"))
					{
						GearItem dart = data.getGear(f.get("dart").getAsInt());
						gear.put(GearSlot.AMMO, new GearItem(dart.getId(), dart.getName(), dart.getVersion(), GearSlot.AMMO,
							dart.getCategory(), dart.getSpeed(), false, dart.isStandardGear(), dart.isTradeable(), dart.getEstimatedPrice(),
							StatBlock.ZERO, StatBlock.ZERO, new StatBlock(0,0,0,0,0,0,dart.getBonuses().getRangedStrength(),0,0), dart.getRequirements()));
					}
					JsonObject lv = object(f, "levels"), boost = object(f, "boosts"), ec = object(f, "encounter"), buffs = object(f, "buffs");
					PlayerLevels levels = new PlayerLevels(number(lv,"atk",99)+number(boost,"atk",0),number(lv,"str",99)+number(boost,"str",0),
						number(lv,"def",99)+number(boost,"def",0),number(lv,"ranged",99)+number(boost,"ranged",0),number(lv,"magic",99)+number(boost,"magic",0),number(lv,"prayer",99),number(lv,"hp",99));
					Set<Prayer> active = EnumSet.noneOf(Prayer.class);
					if (f.has("prayers")) for (JsonElement p : f.getAsJsonArray("prayers")) active.add(Prayer.valueOf(p.getAsString()));
					SpellStats spell = data.getSpells().stream().filter(s -> s.getName().equals(string(f,"spell",""))).findFirst().orElse(null);
					EncounterContext encounter = new EncounterContext(number(ec,"toaInvocationLevel",0),number(ec,"partySize",1),number(ec,"toaPathLevel",0),
						flag(ec,"isFromCoxCm"),number(ec,"partyMaxCombatLevel",126),number(ec,"partyMaxHpLevel",99),number(f,"hpPercent",100),number(f,"defenceOverride",-1));
					OptimizationRequest request = new OptimizationRequest(monster,style,levels,PrayerBonuses.fromActive(active),spell,0,CandidateMode.BUDGET,
						true,flag(buffs,"onSlayerTask"),OwnedItems.EMPTY,10).withEncounterContext(encounter).withMonster(EncounterRules.scale(monster,encounter))
						.withCombatContext(new CombatContext(number(lv,"hp",99)+number(boost,"hp",0),number(lv,"hp",99),number(buffs,"soulreaperStacks",0),
							flag(buffs,"markOfDarknessSpell"),flag(buffs,"kandarinDiary"),flag(buffs,"inWilderness")));
					DpsResult value = calculator.calculate(request,new Loadout(gear));
					if (value == null) result.addProperty("rejected",true);
					else
					{
						result.addProperty("dps",value.getDps()); result.addProperty("accuracy",value.getAccuracy()); result.addProperty("expected",value.getExpectedHit());
						result.addProperty("max",value.getMaxHit()); result.addProperty("speed",value.getAttackSpeed()); result.addProperty("attackRoll",value.getAttackRoll());
						result.addProperty("defenceRoll",value.getDefenceRoll()); result.addProperty("type",value.getAttackType());
					}
				}
				catch (Exception error) { result.addProperty("error",error.toString()); }
				output.write(JSON.toJson(result)); output.newLine();
			}
		}
	}
}
