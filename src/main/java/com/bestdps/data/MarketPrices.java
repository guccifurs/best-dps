package com.bestdps.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Public, bulk Wiki quotes. No account or owned-item data leaves the client. */
public final class MarketPrices
{
	private static final String ENDPOINT = "https://prices.runescape.wiki/api/v1/osrs/latest";
	private static final long MAX_QUOTE_AGE = 86_400;
	private long lastAttempt;
	private JsonObject quotes;

	public synchronized BestDpsData refresh(BestDpsData base) throws IOException
	{
		long now = System.currentTimeMillis() / 1000;
		if (now - lastAttempt >= 300)
		{
			lastAttempt = now;
			HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
			connection.setRequestProperty("User-Agent", "BestDps/0.2 (https://github.com/guccifurs/best-dps)");
			connection.setConnectTimeout(4000);
			connection.setReadTimeout(6000);
			try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))
			{
				quotes = new JsonParser().parse(reader).getAsJsonObject().getAsJsonObject("data");
			}
			finally
			{
				connection.disconnect();
			}
		}
		List<GearItem> gear = new ArrayList<>();
		Map<Integer, GearItem> byId = new HashMap<>();
		for (GearItem item : base.getGearItems())
		{
			JsonObject row = quotes == null ? null : quotes.getAsJsonObject(String.valueOf(item.getId()));
			int buy = freshPrice(row, "high", now);
			int sell = freshPrice(row, "low", now);
			// A small spread buffer avoids treating the last observed trade as a guaranteed fill.
			int bufferedBuy = (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(buy * 1.02));
			int bufferedSell = (int) Math.floor(sell * 0.98);
			long timestamp = sell > 0 ? number(row, "lowTime") : 0;
			GearItem quoted = item.withPrices(bufferedBuy, bufferedSell, timestamp);
			gear.add(quoted);
			byId.put(quoted.getId(), quoted);
		}
		return new BestDpsData(gear, base.getMonsters(), base.getSpells(), byId);
	}

	private static int freshPrice(JsonObject row, String side, long now)
	{
		long price = number(row, side);
		long time = number(row, side + "Time");
		return price > 0 && price <= Integer.MAX_VALUE && time > 0
			&& time <= now + 60 && now - time <= MAX_QUOTE_AGE ? (int) price : 0;
	}

	private static long number(JsonObject row, String key)
	{
		JsonElement value = row == null ? null : row.get(key);
		return value == null || value.isJsonNull() ? 0 : value.getAsLong();
	}
}
