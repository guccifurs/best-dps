package com.bestdps;

import com.bestdps.calc.OwnedItems;
import com.bestdps.calc.PlayerLevels;
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
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
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

	private BestDpsPanel panel;
	private BestDpsLauncherPanel launcherPanel;
	private BestDpsWindow window;
	private NavigationButton navButton;
	private ExecutorService executor;
	private volatile BestDpsData data;

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
		executor = Executors.newSingleThreadExecutor(runnable -> {
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
		refreshContainers();
		if (config.readProfileOnStartup())
		{
			refreshProfileFromClient("Profile loaded");
		}

		executor.submit(() -> {
			try
			{
				BestDpsData loaded = new BestDpsDataService().load();
				data = loaded;
				SwingUtilities.invokeLater(() -> {
					if (panel != null)
					{
						panel.setData(loaded);
					}
				});
			}
			catch (RuntimeException ex)
			{
				log.warn("Could not load Best DPS data", ex);
				SwingUtilities.invokeLater(() -> {
					if (panel != null)
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
		synchronized (itemLock)
		{
			equipmentItems.clear();
			inventoryItems.clear();
			bankItems.clear();
			bankScanned = false;
		}
		profileSnapshot = ProfileSnapshot.MAXED;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
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
			else if (containerId == InventoryID.BANK.getId())
			{
				copyItems(event.getItemContainer(), bankItems);
				bankScanned = true;
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
		if (snapshot == null || executor == null)
		{
			panel.setStatus("Data is still loading.");
			return;
		}
		executor.submit(() -> {
			java.util.List<com.bestdps.calc.DpsResult> results = new com.bestdps.calc.BestDpsOptimizer().optimize(snapshot, request);
			SwingUtilities.invokeLater(() -> {
				if (panel != null)
				{
					panel.showResults(generation, results);
				}
			});
		});
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
			ProfileSnapshot snapshot = buildProfileSnapshot(source);
			profileSnapshot = snapshot;
			SwingUtilities.invokeLater(this::updateProfileStatus);
		});
	}

	private ProfileSnapshot buildProfileSnapshot(String source)
	{
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
			source);
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
			synchronized (itemLock)
			{
				copyItems(client.getItemContainer(InventoryID.EQUIPMENT), equipmentItems);
				copyItems(client.getItemContainer(InventoryID.INVENTORY), inventoryItems);
				ItemContainer bank = client.getItemContainer(InventoryID.BANK);
				if (bank != null)
				{
					copyItems(bank, bankItems);
					bankScanned = true;
				}
			}
			updateBankStatus();
		});
	}

	private void updateBankStatus()
	{
		boolean scanned;
		int observedItems;
		synchronized (itemLock)
		{
			scanned = bankScanned;
			observedItems = equipmentItems.size() + inventoryItems.size() + bankItems.size();
		}
		SwingUtilities.invokeLater(() -> {
			if (panel != null)
			{
				panel.updateBankStatus(scanned, observedItems);
			}
			if (launcherPanel != null)
			{
				launcherPanel.updateBankStatus(scanned);
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

	private static void copyItems(ItemContainer container, Map<Integer, Integer> target)
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
			target.merge(item.getId(), item.getQuantity(), Integer::sum);
		}
	}

	private static void merge(Map<Integer, Integer> target, Map<Integer, Integer> source)
	{
		for (Map.Entry<Integer, Integer> entry : source.entrySet())
		{
			target.merge(entry.getKey(), entry.getValue(), Integer::sum);
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
