package com.bestdps;

import com.bestdps.calc.OwnedItems;
import com.bestdps.calc.BestDpsOptimizer;
import com.bestdps.data.MarketPrices;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.bestdps.calc.PlayerLevels;
import com.bestdps.calc.PrayerBonuses;
import net.runelite.api.gameval.VarbitID;
import com.bestdps.calc.ProfileSnapshot;
import com.bestdps.calc.RequirementProfile;
import com.bestdps.data.BestDpsData;
import com.bestdps.data.BestDpsDataService;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import java.util.Objects;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Prayer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Best DPS"
)
public class BestDpsPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(BestDpsPlugin.class);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private BestDpsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	private volatile BestDpsPanel panel;
	private BestDpsLauncherPanel launcherPanel;
	private BestDpsWindow window;
	private NavigationButton navButton;
	private volatile ExecutorService executor;
	private volatile BestDpsData data;
	private volatile BestDpsData baseData;
	private volatile Future<?> searchTask;
	private final MarketPrices marketPrices = new MarketPrices();
	private final BestDpsOptimizer optimizer = new BestDpsOptimizer();
	private volatile boolean running;
	private volatile long lifecycle;
	private boolean bankFresh;
	private boolean bankDirty;
	private long bankUpdatedAt;
	private String bankProfile;

	private final Map<Integer, Integer> equipmentItems = new HashMap<>();
	private final Map<Integer, Integer> inventoryItems = new HashMap<>();
	private final Map<Integer, Integer> bankItems = new HashMap<>();
	private final Object itemLock = new Object();
	private volatile ProfileSnapshot profileSnapshot = ProfileSnapshot.MAXED;
	private boolean bankScanned;

	@Provides
	BestDpsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BestDpsConfig.class);
	}

	@Override
	protected void startUp()
	{
		running = true;
		long runId = ++lifecycle;
		executor = Executors.newFixedThreadPool(2, runnable -> {
			Thread thread = new Thread(runnable, "best-dps-worker");
			thread.setDaemon(true);
			return thread;
		});

		panel = new BestDpsPanel(this, itemManager, config);
		launcherPanel = new BestDpsLauncherPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Best DPS")
			.icon(createIcon())
			.panel(launcherPanel)
			.priority(6)
			.build();
		clientToolbar.addNavigation(navButton);
		clientThread.invokeLater(this::restoreBankCache);
		refreshContainers();
		if (config.readProfileOnStartup())
		{
			refreshProfileFromClient("Profile loaded");
		}

		executor.submit(() -> {
			try
			{
				BestDpsData loaded = new BestDpsDataService().load();
				if (!running || lifecycle != runId) { return; }
				baseData = loaded;
				data = loaded;
				SwingUtilities.invokeLater(() -> {
					if (panel != null && lifecycle == runId)
					{
						panel.setData(loaded);
					}
				});
				refreshPrices();
			}
			catch (RuntimeException ex)
			{
				log.warn("Could not load Best DPS data", ex);
				SwingUtilities.invokeLater(() -> {
					if (panel != null && lifecycle == runId)
					{
						panel.setStatus("Data failed to load.");
					}
				});
			}
		});
	}

	@Override
	protected void shutDown()
	{
		running = false;
		lifecycle++;
		cancelSearch();
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (window != null)
		{
			window.dispose();
			window = null;
		}
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
		launcherPanel = null;
		panel = null;
		data = null;
		baseData = null;
		synchronized (itemLock)
		{
			equipmentItems.clear();
			inventoryItems.clear();
			bankItems.clear();
			bankScanned = false;
			bankFresh = false;
			bankProfile = null;
		}
		profileSnapshot = ProfileSnapshot.MAXED;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!running || client.getGameState() != GameState.LOGGED_IN) { return; }
		if (!Objects.equals(bankProfile, configManager.getRSProfileKey())) { restoreBankCache(); }
		int containerId = event.getContainerId();
		if (containerId != InventoryID.EQUIPMENT.getId() && containerId != InventoryID.INVENTORY.getId()
			&& containerId != InventoryID.BANK.getId()) { return; }
		synchronized (itemLock)
		{
			if (containerId == InventoryID.EQUIPMENT.getId())
			{
				copyItems(event.getItemContainer(), equipmentItems);
			}
			else if (containerId == InventoryID.INVENTORY.getId())
			{
				copyItems(event.getItemContainer(), inventoryItems);
			}
			else if (containerId == InventoryID.BANK.getId() && event.getItemContainer() != null)
			{
				copyItems(event.getItemContainer(), bankItems);
				bankScanned = true;
				bankFresh = true;
				bankDirty = true;
				bankUpdatedAt = System.currentTimeMillis();
			}
		}
		updateBankStatus();
	}

	void openWindowFromUi()
	{
		SwingUtilities.invokeLater(() -> {
			if (panel == null)
			{
				return;
			}
			Window owner = launcherPanel == null ? null : SwingUtilities.getWindowAncestor(launcherPanel);
			if (window == null || !window.isDisplayable())
			{
				window = new BestDpsWindow(this, panel, owner);
			}
			window.setVisible(true);
			window.toFront();
			window.requestFocus();
		});
	}

	void onWindowClosed()
	{
		window = null;
	}

	void optimize(com.bestdps.calc.OptimizationRequest request, int generation)
	{
		BestDpsData snapshot = data;
		BestDpsPanel target = panel;
		if (snapshot == null || executor == null || target == null) { return; }
		cancelSearch();
		searchTask = executor.submit(() -> {
			long started = System.nanoTime();
			try
			{
				java.util.List<com.bestdps.calc.DpsResult> results = optimizer.optimize(snapshot, request);
				long elapsed = (System.nanoTime() - started) / 1_000_000;
				if (Thread.currentThread().isInterrupted()) { return; }
				log.debug("Best DPS search target={} style={} source={} results={} duration={}ms",
					request.getMonster().getId(), request.getStyle(), request.getCandidateMode(), results.size(), elapsed);
				SwingUtilities.invokeLater(() -> {
					if (panel == target) { target.showResults(generation, results, elapsed); }
				});
			}
			catch (CancellationException ex) { /* Replaced by a newer request. */ }
			catch (RuntimeException ex)
			{
				log.warn("Best DPS search failed after {} ms", (System.nanoTime() - started) / 1_000_000, ex);
				SwingUtilities.invokeLater(() -> {
					if (panel == target) { target.searchFailed(generation, "Search failed; see client log."); }
				});
			}
		});
	}

	void cancelSearch()
	{
		if (searchTask != null) { searchTask.cancel(true); searchTask = null; }
	}

	void refreshPrices()
	{
		BestDpsData original = baseData;
		if (original == null || !running) { return; }
		try
		{
			BestDpsData quoted = marketPrices.refresh(original);
			if (!running || baseData != original) { return; }
			data = quoted;
			SwingUtilities.invokeLater(() -> {
				if (panel != null) { panel.pricesUpdated(quoted, "Wiki quotes: 2% buffer; old/missing quotes estimated"); }
			});
		}
		catch (java.io.IOException | RuntimeException ex)
		{
			log.debug("Best DPS price refresh unavailable", ex);
			SwingUtilities.invokeLater(() -> {
				if (panel != null) { panel.pricesUpdated(data, "Bundled price estimates; resale needs fresh quotes"); }
			});
		}
	}

	void saveSellPreference(boolean enabled)
	{
		configManager.setConfiguration("bestdps", "sellOwnedItems", enabled);
	}

	void requestPriceRefresh()
	{
		if (executor != null) { executor.submit(this::refreshPrices); }
	}

	boolean isBankFresh() { synchronized (itemLock) { return bankFresh; } }

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!bankDirty || !running || configManager.getRSProfileKey() == null) { return; }
		synchronized (itemLock)
		{
			if (!Objects.equals(bankProfile, configManager.getRSProfileKey())) { return; }
			JsonObject saved = new JsonObject();
			saved.addProperty("updatedAt", bankUpdatedAt);
			saved.add("items", gson.toJsonTree(bankItems));
			configManager.setRSProfileConfiguration("bestdps", "bankCacheV1", saved.toString());
			bankDirty = false;
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		if (!running) { return; }
		cancelSearch();
		restoreBankCache();
		refreshContainers();
		SwingUtilities.invokeLater(() -> { if (panel != null) { panel.invalidateResults("Account changed; search again."); } });
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (!running) { return; }
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			cancelSearch();
			synchronized (itemLock)
			{
				equipmentItems.clear(); inventoryItems.clear(); bankItems.clear();
				bankScanned = false; bankFresh = false; bankDirty = false; bankProfile = null;
			}
			profileSnapshot = ProfileSnapshot.MAXED;
			updateBankStatus();
			SwingUtilities.invokeLater(() -> { if (panel != null) { panel.invalidateResults("Log in to use your bank cache."); } });
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			if (!Objects.equals(bankProfile, configManager.getRSProfileKey())) { restoreBankCache(); }
			refreshContainers();
		}
	}

	private void restoreBankCache()
	{
		if (!running) { return; }
		synchronized (itemLock)
		{
			equipmentItems.clear(); inventoryItems.clear(); bankItems.clear();
			bankScanned = false; bankFresh = false; bankDirty = false; bankUpdatedAt = 0;
			bankProfile = configManager.getRSProfileKey();
			if (bankProfile == null || client.getGameState() != GameState.LOGGED_IN) { return; }
			String saved = configManager.getRSProfileConfiguration("bestdps", "bankCacheV1");
			if (saved != null)
			{
				try
				{
					JsonObject object = new JsonParser().parse(saved).getAsJsonObject();
					for (Map.Entry<String, com.google.gson.JsonElement> entry : object.getAsJsonObject("items").entrySet())
					{
						int id = Integer.parseInt(entry.getKey());
						int quantity = entry.getValue().getAsInt();
						if (id > 0 && quantity > 0) { bankItems.put(id, quantity); }
					}
					bankUpdatedAt = object.get("updatedAt").getAsLong();
					bankScanned = true;
				}
				catch (RuntimeException ex)
				{
					bankItems.clear();
					log.debug("Best DPS saved bank cache could not be read");
				}
			}
		}
		updateBankStatus();
	}

	OwnedItems ownedItems()
	{
		synchronized (itemLock)
		{
			Map<Integer, Integer> combined = new HashMap<>();
			merge(combined, equipmentItems);
			merge(combined, inventoryItems);
			merge(combined, bankItems);
			return new OwnedItems(combined, bankScanned);
		}
	}

	boolean ownsItem(int itemId)
	{
		synchronized (itemLock)
		{
			return equipmentItems.getOrDefault(itemId, 0) > 0
				|| inventoryItems.getOrDefault(itemId, 0) > 0
				|| bankItems.getOrDefault(itemId, 0) > 0;
		}
	}

	void profileSnapshot(boolean useMyLevels, java.util.function.Consumer<ProfileSnapshot> consumer)
	{
		if (!useMyLevels)
		{
			SwingUtilities.invokeLater(() -> consumer.accept(ProfileSnapshot.MAXED));
			return;
		}
		clientThread.invokeLater(() -> {
			if (!running) { return; }
			ProfileSnapshot snapshot = buildProfileSnapshot("Profile refreshed");
			profileSnapshot = snapshot;
			SwingUtilities.invokeLater(() -> {
				updateProfileStatus();
				consumer.accept(snapshot);
			});
		});
	}

	void refreshProfileFromClient(String source)
	{
		clientThread.invokeLater(() -> {
			if (!running) { return; }
			ProfileSnapshot snapshot = buildProfileSnapshot(source);
			profileSnapshot = snapshot;
			SwingUtilities.invokeLater(this::updateProfileStatus);
		});
	}

	private ProfileSnapshot buildProfileSnapshot(String source)
	{
		if (client.getGameState() != GameState.LOGGED_IN) { return ProfileSnapshot.MAXED; }
		EnumMap<Skill, Integer> levels = new EnumMap<>(Skill.class);
		EnumMap<Skill, Integer> boosted = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				levels.put(skill, client.getRealSkillLevel(skill));
				boosted.put(skill, client.getBoostedSkillLevel(skill));
			}
		}
		Set<String> quests = new HashSet<>();
		for (Quest quest : Quest.values())
		{
			if (quest.getState(client) == QuestState.FINISHED)
			{
				quests.add(quest.name());
			}
		}
		EnumSet<Prayer> prayers = EnumSet.noneOf(Prayer.class);
		for (Prayer prayer : Prayer.values())
		{
			if (client.isPrayerActive(prayer))
			{
				prayers.add(prayer);
			}
		}
		return new ProfileSnapshot(
			new RequirementProfile(levels, quests),
			PlayerLevels.from(levels),
			PlayerLevels.from(boosted),
			prayers,
			source,
			PrayerBonuses.bestAvailable(PlayerLevels.from(levels),
				client.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE) == 8,
				client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED) == 1,
				client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED) == 1,
				client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) == 1,
				client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) == 1));
	}

	BestDpsData getData()
	{
		return data;
	}

	boolean isBankScanned()
	{
		synchronized (itemLock)
		{
			return bankScanned;
		}
	}

	ProfileSnapshot getProfileSnapshot()
	{
		return profileSnapshot;
	}

	private void refreshContainers()
	{
		clientThread.invokeLater(() -> {
			if (!running || client.getGameState() != GameState.LOGGED_IN) { return; }
			synchronized (itemLock)
			{
				copyItems(client.getItemContainer(InventoryID.EQUIPMENT), equipmentItems);
				copyItems(client.getItemContainer(InventoryID.INVENTORY), inventoryItems);
				// Bank is replaced only by an observed bank container event for this account.
			}
			updateBankStatus();
		});
	}

	private void updateBankStatus()
	{
		boolean scanned;
		boolean fresh;
		long updatedAt;
		int observedItems;
		synchronized (itemLock)
		{
			scanned = bankScanned;
			fresh = bankFresh;
			updatedAt = bankUpdatedAt;
			observedItems = equipmentItems.size() + inventoryItems.size() + bankItems.size();
		}
		SwingUtilities.invokeLater(() -> {
			if (panel != null)
			{
				panel.updateBankStatus(scanned, observedItems, fresh, updatedAt);
			}
			if (launcherPanel != null)
			{
				launcherPanel.updateBankStatus(scanned, observedItems, fresh, updatedAt);
			}
		});
	}

	private void updateProfileStatus()
	{
		if (panel != null)
		{
			panel.updateProfileStatus(profileSnapshot);
		}
	}

	private void copyItems(ItemContainer container, Map<Integer, Integer> target)
	{
		target.clear();
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			ItemComposition definition = client.getItemDefinition(item.getId());
			if (definition.getPlaceholderTemplateId() != -1) { continue; }
			int id = definition.getNote() != -1 ? definition.getLinkedNoteId() : item.getId();
			target.merge(id, item.getQuantity(), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
		}
	}

	private static void merge(Map<Integer, Integer> target, Map<Integer, Integer> source)
	{
		for (Map.Entry<Integer, Integer> entry : source.entrySet())
		{
			target.merge(entry.getKey(), entry.getValue(), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
		}
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(18, 24, 38));
		g.fillRoundRect(2, 2, 28, 28, 8, 8);
		g.setColor(new Color(45, 212, 191));
		g.fillOval(8, 7, 16, 16);
		g.setColor(new Color(15, 23, 42));
		g.fillOval(12, 11, 8, 8);
		g.setColor(new Color(250, 204, 21));
		g.fillRect(15, 20, 2, 7);
		g.dispose();
		return image;
	}
}
