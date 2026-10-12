package com.bestdps;

import com.bestdps.calc.CandidateMode;
import com.bestdps.calc.CombatStyle;
import com.bestdps.calc.BoostProfile;
import com.bestdps.calc.DpsResult;
import com.bestdps.calc.EncounterContext;
import com.bestdps.calc.CombatContext;
import com.bestdps.calc.EncounterRules;
import com.bestdps.calc.TradePlan;
import com.bestdps.calc.CombatMechanics;
import com.bestdps.calc.OptimizationRequest;
import com.bestdps.calc.PlayerLevels;
import com.bestdps.calc.PrayerBonuses;
import com.bestdps.calc.PrayerProfile;
import com.bestdps.calc.ProfileSnapshot;
import com.bestdps.calc.RequirementProfile;
import com.bestdps.data.BestDpsData;
import com.bestdps.data.GearItem;
import com.bestdps.data.GearSlot;
import com.bestdps.data.MonsterStats;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;

final class BestDpsPanel extends JPanel
{
	private static final int CONTROL_WIDTH = 328;
	private static final int RESULT_WIDTH = 680;

	private final BestDpsPlugin plugin;
	private final ItemManager itemManager;
	private final BestDpsConfig config;
	private final IconTextField monsterSearch = new IconTextField();
	private final JComboBox<MonsterEntry> monsterBox = new JComboBox<>();
	private final JComboBox<CombatStyle> styleBox = new JComboBox<>(CombatStyle.values());
	private final JComboBox<BoostProfile> boostBox = new JComboBox<>(BoostProfile.values());
	private final JComboBox<PrayerProfile> prayerBox = new JComboBox<>(PrayerProfile.values());
	private final JTextField budgetField = new JTextField("10m");
	private final JCheckBox untradeablesBox = new JCheckBox("Include Untradeables");
	private final JCheckBox useMyLevelsBox = new JCheckBox("Use my levels");
	private final JComboBox<CandidateMode> sourceBox = new JComboBox<>(new CandidateMode[]{CandidateMode.OWNED_OR_BUDGET, CandidateMode.OWNED_ONLY, CandidateMode.BUDGET});
	private final JCheckBox sellBox = new JCheckBox("Suggest selling unused gear");
	private final JSpinner raidLevel = new JSpinner(new SpinnerNumberModel(0, 0, 600, 5));
	private final JSpinner partySize = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
	private final JSpinner pathLevel = new JSpinner(new SpinnerNumberModel(0, 0, 6, 1));
	private final JCheckBox challengeBox = new JCheckBox("CoX challenge mode");
	private final JSpinner partyCombat = new JSpinner(new SpinnerNumberModel(126, 3, 126, 1));
	private final JSpinner partyHp = new JSpinner(new SpinnerNumberModel(99, 10, 99, 1));
	private final JSpinner targetHp = new JSpinner(new SpinnerNumberModel(100, 1, 100, 5));
	private final JSpinner defenceOverride = new JSpinner(new SpinnerNumberModel(-1, -1, 2000, 1));
	private final JCheckBox liveHpBox = new JCheckBox("Use live HP", true);
	private final JSpinner playerHp = new JSpinner(new SpinnerNumberModel(99, 1, 126, 1));
	private final JSpinner maxPlayerHp = new JSpinner(new SpinnerNumberModel(99, 10, 99, 1));
	private final JSpinner soulStacks = new JSpinner(new SpinnerNumberModel(0, 0, 5, 1));
	private final JCheckBox markBox = new JCheckBox("Mark of Darkness active");
	private final JCheckBox diaryBox = new JCheckBox("Kandarin hard diary complete");
	private final JCheckBox wildernessBox = new JCheckBox("Fighting in the Wilderness");
	private final JLabel resultContextLabel = new JLabel("Choose a target and find gear");
	private OptimizationRequest activeRequest;
	private OptimizationRequest displayedRequest;
	private final JLabel priceLabel = new JLabel("Bundled price estimates; refreshing Wiki quotes...");
	private final JLabel encounterLabel = new JLabel("Select an NPC variant / phase");
	private final JCheckBox slayerBox = new JCheckBox("On slayer task");
	private final JLabel statusLabel = new JLabel("Loading data...");
	private final JLabel bankLabel = new JLabel("Bank not scanned");
	private final JLabel profileLabel = new JLabel("Profile not synced");
	private final DefaultTableModel resultModel = new DefaultTableModel(new String[]{"DPS", "Needed GP", "Type", "Acc", "Max", "Weapon"}, 0)
	{
		@Override
		public boolean isCellEditable(int row, int column)
		{
			return false;
		}
	};
	private final JTable resultsTable = new JTable(resultModel);
	private final EquipmentLayoutPanel equipmentLayout = new EquipmentLayoutPanel();
	private final JPanel loadoutDetails = new JPanel(new GridLayout(0, 1, 0, 3));
	private final List<DpsResult> currentResults = new ArrayList<>();

	private BestDpsData data;
	private int generation;
	private Timer searchTimer;

	BestDpsPanel(BestDpsPlugin plugin, ItemManager itemManager, BestDpsConfig config)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.config = config;
		build();
	}

	void setData(BestDpsData data)
	{
		this.data = data;
		styleBox.setSelectedItem(config.defaultStyle());
		boostBox.setSelectedItem(config.defaultBoostProfile());
		prayerBox.setSelectedItem(config.defaultPrayerProfile());
		budgetField.setText(formatGp(config.budget()));
		untradeablesBox.setSelected(config.includeUntradeables());
		sellBox.setSelected(config.sellOwnedItems());
		useMyLevelsBox.setSelected(config.useMyLevels());
		maxPlayerHp.setEnabled(!config.useMyLevels());
		playerHp.setEnabled(!config.useMyLevels() || !liveHpBox.isSelected());
		setStatus("Loaded " + data.getGearItems().size() + " items and " + data.getMonsters().size() + " monsters.");
		updateSearchResults();
	}

	void setStatus(String message)
	{
		statusLabel.setText(message);
	}

	void updateBankStatus(boolean bankScanned, int observedItems, boolean fresh, long updatedAt)
	{
		if (bankScanned)
		{
			bankLabel.setText((fresh ? "Bank read: " : "Saved bank: ") + observedItems + " item types");
			bankLabel.setToolTipText("Last bank read: " + new java.util.Date(updatedAt) + "; reopen bank to refresh quantities.");
			bankLabel.setForeground(new Color(94, 234, 212));
		}
		else
		{
			bankLabel.setText("Open your bank once to include banked gear");
			bankLabel.setForeground(new Color(250, 204, 21));
		}
	}

	void updateProfileStatus(ProfileSnapshot snapshot)
	{
		if (snapshot == null)
		{
			profileLabel.setText("Profile not synced");
			profileLabel.setForeground(new Color(250, 204, 21));
			return;
		}
		if (useMyLevelsBox.isSelected()) { maxPlayerHp.setValue(snapshot.getBaseLevels().getHitpoints()); }
		profileLabel.setText(snapshot.getSource() + ": "
			+ snapshot.getBaseLevels().getAttack() + "/"
			+ snapshot.getBaseLevels().getStrength() + "/"
			+ snapshot.getBaseLevels().getDefence() + " melee, "
			+ snapshot.getBaseLevels().getRanged() + " ranged, "
			+ snapshot.getBaseLevels().getMagic() + " magic");
		profileLabel.setForeground(new Color(94, 234, 212));
	}

	void showResults(int resultGeneration, List<DpsResult> results, long elapsedMillis)
	{
		if (resultGeneration != generation)
		{
			return;
		}
		currentResults.clear();
		currentResults.addAll(results);
		displayedRequest = activeRequest;
		if (displayedRequest != null) { resultContextLabel.setText(displayedRequest.getMonster().label() + " · " + displayedRequest.getCandidateMode()); }
		resultModel.setRowCount(0);
		for (DpsResult result : results)
		{
			GearItem weapon = result.getLoadout().getWeapon();
			resultModel.addRow(new Object[]{
				String.format("%.3f", result.getDps()),
				formatGp(result.getTradePlan() == null ? result.getPurchaseCost() : result.getTradePlan().getCashNeeded()),
				result.getAttackType(),
				String.format("%.1f%%", result.getAccuracy() * 100.0),
				result.getMaxHit(),
				weapon == null ? "" : weapon.getName()
			});
		}
		setStatus(results.isEmpty() ? "No legal setup found." : results.size() + " setups · " + elapsedMillis + " ms · best found");
		if (!results.isEmpty())
		{
			resultsTable.setRowSelectionInterval(0, 0);
			showLoadout(results.get(0));
		}
	}

	private void build()
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(14, 14, 14, 14));
		setPreferredSize(new Dimension(1080, 720));

		JPanel root = new JPanel(new BorderLayout(12, 12));
		root.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel left = new JPanel(new GridBagLayout());
		left.setBackground(ColorScheme.DARK_GRAY_COLOR);
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1.0;
		c.fill = GridBagConstraints.HORIZONTAL;

		left.add(searchPanel(), c);
		c.gridy++;
		JTabbedPane options = new JTabbedPane();
		options.addTab("Gear", settingsPanel());
		options.addTab("Encounter", encounterPanel());
		options.addTab("Effects", combatPanel());
		options.setPreferredSize(new Dimension(CONTROL_WIDTH, 360));
		left.add(options, c);
		c.gridy++;
		left.add(actionsPanel(), c);
		c.gridy++;
		left.add(statusPanel(), c);
		c.gridy++;
		c.weighty = 1.0;
		c.fill = GridBagConstraints.BOTH;
		left.add(Box.createVerticalGlue(), c);

		JPanel right = new JPanel(new BorderLayout(0, 12));
		right.setBackground(ColorScheme.DARK_GRAY_COLOR);
		right.add(resultsPanel(), BorderLayout.CENTER);
		right.add(loadoutPanel(), BorderLayout.SOUTH);

		JPanel columns = new JPanel(new BorderLayout(12, 0));
		columns.setBackground(ColorScheme.DARK_GRAY_COLOR);
		columns.add(left, BorderLayout.WEST);
		columns.add(right, BorderLayout.CENTER);

		root.add(title(), BorderLayout.NORTH);
		root.add(columns, BorderLayout.CENTER);

		JScrollPane scroll = new JScrollPane(root);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);
	}

	private JPanel title()
	{
		JPanel panel = section();
		panel.setLayout(new BorderLayout(10, 0));
		JLabel title = new JLabel("Best DPS");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		JLabel subtitle = new JLabel("Search a monster, choose assumptions, and compare gear setups.");
		subtitle.setFont(FontManager.getRunescapeSmallFont());
		subtitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(title, BorderLayout.WEST);
		panel.add(subtitle, BorderLayout.CENTER);
		return panel;
	}

	private JPanel searchPanel()
	{
		JPanel panel = section();
		panel.setPreferredSize(new Dimension(CONTROL_WIDTH, 116));
		panel.setLayout(new GridBagLayout());
		GridBagConstraints c = constraints();

		monsterSearch.setIcon(IconTextField.Icon.SEARCH);
		monsterSearch.setPreferredSize(new Dimension(CONTROL_WIDTH - 18, 30));
		monsterSearch.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		monsterSearch.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		monsterSearch.addActionListener(e -> updateSearchResults());
		monsterSearch.addClearListener(this::updateSearchResults);
		monsterSearch.addKeyListener(new java.awt.event.KeyAdapter()
		{
			@Override
			public void keyReleased(java.awt.event.KeyEvent e)
			{
				scheduleSearch();
			}
		});

		panel.add(label("Target"), c);
		c.gridy++;
		panel.add(monsterSearch, c);
		c.gridy++;
		panel.add(monsterBox, c);
		return panel;
	}

	void pricesUpdated(BestDpsData updated, String status)
	{
		if (updated != null) { data = updated; }
		priceLabel.setText("<html>" + status + "</html>");
	}

	void searchFailed(int resultGeneration, String message)
	{
		if (resultGeneration == generation) { setStatus(message); }
	}

	void invalidateResults(String message)
	{
		generation++;
		currentResults.clear();
		resultModel.setRowCount(0);
		equipmentLayout.clear();
		loadoutDetails.removeAll();
		loadoutDetails.revalidate();
		loadoutDetails.repaint();
		setStatus(message);
	}

	private JPanel settingsPanel()
	{
		JPanel panel = section();
		panel.setPreferredSize(new Dimension(CONTROL_WIDTH, 322));
		panel.setLayout(new GridBagLayout());
		GridBagConstraints c = constraints();
		addRow(panel, c, "Style", styleBox);
		addRow(panel, c, "Boosts", boostBox);
		addRow(panel, c, "Prayers", prayerBox);
		addRow(panel, c, "Cash budget", budgetField);
		budgetField.setToolTipText("Cash available for purchases, excluding gear sale proceeds. Accepts k, m, b.");
		addRow(panel, c, "Gear source", sourceBox);
		sourceBox.addActionListener(e -> sellBox.setEnabled(sourceBox.getSelectedItem() == CandidateMode.OWNED_OR_BUDGET));
		sellBox.addActionListener(e -> plugin.saveSellPreference(sellBox.isSelected()));
		sellBox.setOpaque(false);
		sellBox.setForeground(Color.WHITE);
		sellBox.setToolTipText("Recommendations only. Retains selected gear; excludes untradeables and missing/stale sell quotes.");
		untradeablesBox.setOpaque(false);
		untradeablesBox.setForeground(Color.WHITE);
		useMyLevelsBox.addActionListener(e -> {
			maxPlayerHp.setEnabled(!useMyLevelsBox.isSelected());
			liveHpBox.setEnabled(useMyLevelsBox.isSelected());
			playerHp.setEnabled(!useMyLevelsBox.isSelected() || !liveHpBox.isSelected());
		});
		useMyLevelsBox.setOpaque(false);
		useMyLevelsBox.setForeground(Color.WHITE);
		slayerBox.setOpaque(false);
		slayerBox.setForeground(Color.WHITE);
		c.gridx = 0;
		c.gridwidth = 2;
		panel.add(untradeablesBox, c);
		c.gridy++;
		panel.add(useMyLevelsBox, c);
		c.gridy++;
		panel.add(slayerBox, c);
		c.gridy++;
		panel.add(sellBox, c);
		return panel;
	}

	private JPanel encounterPanel()
	{
		JPanel panel = section();
		panel.setLayout(new GridBagLayout());
		panel.setPreferredSize(new Dimension(CONTROL_WIDTH, 312));
		GridBagConstraints c = constraints();
		addRow(panel, c, "ToA invocation", raidLevel);
		addRow(panel, c, "Party size", partySize);
		addRow(panel, c, "ToA path level", pathLevel);
		addRow(panel, c, "CoX max combat", partyCombat);
		addRow(panel, c, "CoX max HP", partyHp);
		addRow(panel, c, "Target HP %", targetHp);
		addRow(panel, c, "Defence override", defenceOverride);
		defenceOverride.setToolTipText("-1: normal defence; 0+: remaining defence after drains (not a reduction amount)");
		challengeBox.setOpaque(false);
		challengeBox.setForeground(Color.WHITE);
		c.gridwidth = 2;
		panel.add(challengeBox, c);
		c.gridy++;
		encounterLabel.setForeground(new Color(250, 204, 21));
		panel.add(encounterLabel, c);
		monsterBox.addActionListener(e -> updateEncounter());
		return panel;
	}

	private void updateEncounter()
	{
		MonsterEntry entry = (MonsterEntry) monsterBox.getSelectedItem();
		if (entry == null) { return; }
		String blocked = EncounterRules.blockingReason(entry.monster, (CombatStyle) styleBox.getSelectedItem());
		List<String> warnings = EncounterRules.warnings(entry.monster, encounterContext());
		String text = blocked.isEmpty() ? (warnings.isEmpty() ? "Continuous attacks; selected phase" : warnings.get(0)) : blocked;
		encounterLabel.setText("<html>" + text + "</html>");
		encounterLabel.setToolTipText(String.join("; ", warnings));
	}

	private EncounterContext encounterContext()
	{
		return new EncounterContext((int) raidLevel.getValue(), (int) partySize.getValue(),
			(int) pathLevel.getValue(), challengeBox.isSelected(), (int) partyCombat.getValue(),
			(int) partyHp.getValue(), (int) targetHp.getValue(), (int) defenceOverride.getValue());
	}

	private JPanel combatPanel()
	{
		JPanel panel = section();
		panel.setLayout(new GridBagLayout());
		GridBagConstraints c = constraints();
		addRow(panel, c, "Current HP", playerHp);
		addRow(panel, c, "Base HP", maxPlayerHp);
		addRow(panel, c, "Soulreaper stacks", soulStacks);
		c.gridwidth = 2;
		liveHpBox.addActionListener(e -> playerHp.setEnabled(!useMyLevelsBox.isSelected() || !liveHpBox.isSelected()));
		for (JCheckBox option : new JCheckBox[]{liveHpBox, markBox, diaryBox, wildernessBox})
		{
			option.setOpaque(false);
			option.setForeground(Color.WHITE);
			panel.add(option, c);
			c.gridy++;
		}
		panel.add(label("<html>Set the combat state for the phase you want to compare. Charged gear assumes usable charges.</html>"), c);
		return panel;
	}

	private JPanel actionsPanel()
	{
		JPanel panel = section();
		panel.setPreferredSize(new Dimension(CONTROL_WIDTH, 58));
		panel.setLayout(new BorderLayout());
		JButton find = button("Find");
		find.addActionListener(e -> startFind());
		panel.add(find, BorderLayout.CENTER);
		JButton cancel = button("Cancel");
		cancel.addActionListener(e -> { plugin.cancelSearch(); invalidateResults("Search cancelled."); });
		panel.add(cancel, BorderLayout.EAST);
		return panel;
	}

	private JPanel statusPanel()
	{
		JPanel panel = section();
		panel.setPreferredSize(new Dimension(CONTROL_WIDTH, 160));
		panel.setLayout(new GridLayout(0, 1, 0, 4));
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		bankLabel.setForeground(new Color(250, 204, 21));
		profileLabel.setForeground(new Color(250, 204, 21));
		panel.add(statusLabel);
		panel.add(profileLabel);
		panel.add(bankLabel);
		priceLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(priceLabel);
		JButton prices = button("Refresh prices");
		prices.addActionListener(e -> plugin.requestPriceRefresh());
		panel.add(prices);
		return panel;
	}

	private JPanel resultsPanel()
	{
		JPanel panel = section();
		panel.setLayout(new BorderLayout());
		resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resultsTable.setRowHeight(24);
		resultsTable.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		resultsTable.setForeground(Color.WHITE);
		resultsTable.setGridColor(ColorScheme.DARK_GRAY_COLOR);
		resultsTable.getSelectionModel().addListSelectionListener(e -> {
			int row = resultsTable.getSelectedRow();
			if (!e.getValueIsAdjusting() && row >= 0 && row < currentResults.size())
			{
				showLoadout(currentResults.get(row));
			}
		});
		JScrollPane scroll = new JScrollPane(resultsTable);
		scroll.setPreferredSize(new Dimension(RESULT_WIDTH, 310));
		panel.add(scroll, BorderLayout.CENTER);
		resultContextLabel.setForeground(Color.WHITE);
		panel.add(resultContextLabel, BorderLayout.NORTH);
		return panel;
	}

	private JPanel loadoutPanel()
	{
		JPanel panel = section();
		panel.setLayout(new BorderLayout());
		panel.setPreferredSize(new Dimension(RESULT_WIDTH, 292));
		JLabel heading = label("Loadout");
		heading.setBorder(new EmptyBorder(0, 0, 6, 0));
		JPanel content = new JPanel(new BorderLayout(12, 0));
		content.setOpaque(false);
		loadoutDetails.setOpaque(false);
		loadoutDetails.setBorder(new EmptyBorder(2, 0, 2, 0));
		JScrollPane details = new JScrollPane(loadoutDetails);
		details.setBorder(null);
		details.setPreferredSize(new Dimension(360, 220));
		details.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		details.getVerticalScrollBar().setUnitIncrement(12);
		content.add(equipmentLayout, BorderLayout.WEST);
		content.add(details, BorderLayout.CENTER);
		panel.add(heading, BorderLayout.NORTH);
		panel.add(content, BorderLayout.CENTER);
		return panel;
	}

	private void startFind()
	{
		MonsterEntry entry = (MonsterEntry) monsterBox.getSelectedItem();
		if (entry == null || entry.monster == null)
		{
			setStatus("Choose a target first.");
			return;
		}
		String blocked = EncounterRules.blockingReason(entry.monster, (CombatStyle) styleBox.getSelectedItem());
		if (!blocked.isEmpty()) { setStatus(blocked); return; }
		if (sellBox.isSelected() && sourceBox.getSelectedItem() == CandidateMode.OWNED_OR_BUDGET && !plugin.isBankFresh())
		{
			setStatus("Open your bank this session before planning sales."); return;
		}
		CombatStyle style = (CombatStyle) styleBox.getSelectedItem();
		int budget;
		try { budget = parseBudget(budgetField.getText()); }
		catch (IllegalArgumentException ex) { setStatus("Enter cash from 0 to 2147m GP (k/m/b accepted)."); return; }
		plugin.cancelSearch();
		int runGeneration = ++generation;
		currentResults.clear();
		resultModel.setRowCount(0);
		equipmentLayout.clear();
		loadoutDetails.removeAll();
		loadoutDetails.revalidate();
		loadoutDetails.repaint();
		setStatus("Finding...");
		boolean needsProfile = useMyLevelsBox.isSelected()
			|| boostBox.getSelectedItem() == BoostProfile.LIVE_CURRENT
			|| prayerBox.getSelectedItem() == PrayerProfile.CURRENT_ACTIVE;
		plugin.profileSnapshot(needsProfile, profile -> submitFind(entry, style, budget, profile, runGeneration));
	}

	private void submitFind(MonsterEntry entry, CombatStyle style, int budget, ProfileSnapshot snapshot, int runGeneration)
	{
		if (runGeneration != generation) { return; }
		RequirementProfile profile = useMyLevelsBox.isSelected() ? snapshot.getRequirements() : RequirementProfile.MAXED;
		PlayerLevels base = useMyLevelsBox.isSelected() ? snapshot.getBaseLevels() : PlayerLevels.MAXED;
		PlayerLevels current = useMyLevelsBox.isSelected() ? snapshot.getCurrentLevels() : PlayerLevels.MAXED;
		BoostProfile boost = (BoostProfile) boostBox.getSelectedItem();
		PlayerLevels levels = base.boosted(boost, current);
		PrayerBonuses prayers = prayerBonuses((PrayerProfile) prayerBox.getSelectedItem(), levels, snapshot);
		if (useMyLevelsBox.isSelected() && snapshot == ProfileSnapshot.MAXED) { setStatus("Log in to read your levels, or turn off Use my levels."); return; }
		activeRequest = new OptimizationRequest(
			entry.monster,
			style,
			levels,
			prayers,
			null,
			budget,
			(CandidateMode) sourceBox.getSelectedItem(),
			untradeablesBox.isSelected(),
			slayerBox.isSelected(),
			plugin.ownedItems(),
			profile,
			20).withSellOwnedItems(sellBox.isSelected()).withEncounterContext(encounterContext())
			.withCombatContext(new CombatContext(liveHpBox.isSelected() && useMyLevelsBox.isSelected() ? snapshot.getCurrentLevels().getHitpoints() : (int) playerHp.getValue(),
				useMyLevelsBox.isSelected() ? snapshot.getBaseLevels().getHitpoints() : (int) maxPlayerHp.getValue(),
				(int) soulStacks.getValue(), markBox.isSelected(), diaryBox.isSelected(), wildernessBox.isSelected()));
		plugin.optimize(activeRequest, runGeneration);
	}

	private PrayerBonuses prayerBonuses(PrayerProfile profile, PlayerLevels levels, ProfileSnapshot snapshot)
	{
		PrayerProfile selected = profile == null ? PrayerProfile.BEST_AVAILABLE : profile;
		switch (selected)
		{
			case NONE:
				return PrayerBonuses.NONE;
			case CURRENT_ACTIVE:
				return PrayerBonuses.fromActive(snapshot == null ? java.util.Collections.emptySet() : snapshot.getActivePrayers());
			case BEST_AVAILABLE:
			default:
				return useMyLevelsBox.isSelected() && snapshot != null
					? snapshot.getAvailablePrayers() : PrayerBonuses.bestAvailable(levels);
		}
	}

	private void showLoadout(DpsResult result)
	{
		equipmentLayout.setLoadout(result);
		loadoutDetails.removeAll();
		if (displayedRequest != null)
		{
			CombatContext state = displayedRequest.getCombatContext();
			loadoutDetails.add(detailRow("State", state.getCurrentHp() + "/" + state.getMaxHp()
				+ " HP; " + state.getSoulreaperStacks() + " stacks", "", false));
		}
		if (!result.getSpellName().isEmpty())
		{
			loadoutDetails.add(detailRow("Spell", result.getSpellName(), "", false));
		}
		if (result.getBaselineDps() > 0)
		{
			double gain = (result.getDps() / result.getBaselineDps() - 1) * 100;
			loadoutDetails.add(detailRow("Gain", String.format("%+.2f%% vs keeping your gear", gain), "", false));
		}
		TradePlan trade = result.getTradePlan();
		if (trade != null)
		{
			loadoutDetails.add(detailRow("Buy", formatGp(trade.getBuyCost()) + " GP", "", false));
			loadoutDetails.add(detailRow("Sell net", formatGp(trade.getNetProceeds()) + " GP after tax", "", false));
			loadoutDetails.add(detailRow("Cash", formatGp(trade.getCashNeeded()) + " GP needed", "", false));
			for (TradePlan.Entry sale : trade.getSales())
			{
				loadoutDetails.add(detailRow("Sell", sale.getQuantity() + " × " + sale.getItem().label(), formatGp(sale.getTotal()), false));
			}
		}
		List<String> notes = new ArrayList<>(CombatMechanics.warnings(result.getLoadout()));
		if (displayedRequest != null) { notes.addAll(EncounterRules.warnings(displayedRequest.getMonster(), displayedRequest.getEncounterContext())); }
		notes.add("Sustained DPS for this phase; movement, supplies and special-attack rotations excluded.");
		notes.add("Fast search: best found, not an exhaustive proof of optimal gear.");
		for (String note : notes)
		{
			JLabel warning = label("<html>" + note + "</html>");
			warning.setForeground(new Color(250, 204, 21));
			loadoutDetails.add(warning);
		}
		for (GearSlot slot : GearSlot.values())
		{
			GearItem item = result.getLoadout().get(slot);
			loadoutDetails.add(detailRow(slot == GearSlot.AMMO && (result.getLoadout().getWeapon() != null && result.getLoadout().getWeapon().getNormalizedName().contains("blowpipe")) ? "Darts" : slotLabel(slot), item == null ? "Empty" : item.label(), ownedText(item), item != null && plugin.ownsItem(item.getId())));
		}
		loadoutDetails.revalidate();
		loadoutDetails.repaint();
	}

	private JPanel detailRow(String slot, String item, String status, boolean owned)
	{
		JPanel panel = new JPanel(new BorderLayout(5, 0));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(4, 6, 4, 6));
		JLabel left = new JLabel(slot);
		left.setPreferredSize(new Dimension(58, 18));
		left.setFont(FontManager.getRunescapeSmallFont());
		left.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JLabel center = new JLabel(item);
		center.setFont(FontManager.getRunescapeSmallFont());
		center.setForeground(Color.WHITE);
		JLabel right = new JLabel(status);
		right.setHorizontalAlignment(SwingConstants.RIGHT);
		right.setPreferredSize(new Dimension(92, 18));
		right.setFont(FontManager.getRunescapeSmallFont());
		right.setForeground(owned ? new Color(94, 234, 212) : ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(left, BorderLayout.WEST);
		panel.add(center, BorderLayout.CENTER);
		panel.add(right, BorderLayout.EAST);
		return panel;
	}

	private String ownedText(GearItem item)
	{
		if (item == null)
		{
			return "";
		}
		return plugin.ownsItem(item.getId()) ? "Already owned" : "Buy: " + formatGp(item.getPriceOrZero());
	}

	private static String slotLabel(GearSlot slot)
	{
		String text = slot.getJsonName();
		return Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private final class EquipmentLayoutPanel extends JPanel
	{
		private final Map<GearSlot, EquipmentSlotPanel> slots = new EnumMap<>(GearSlot.class);

		private EquipmentLayoutPanel()
		{
			setLayout(null);
			setPreferredSize(new Dimension(262, 240));
			setMinimumSize(new Dimension(262, 240));
			setOpaque(false);
			addSlot(GearSlot.HEAD, 110, 8);
			addSlot(GearSlot.CAPE, 64, 50);
			addSlot(GearSlot.NECK, 110, 50);
			addSlot(GearSlot.AMMO, 156, 50);
			addSlot(GearSlot.WEAPON, 42, 96);
			addSlot(GearSlot.BODY, 110, 96);
			addSlot(GearSlot.SHIELD, 178, 96);
			addSlot(GearSlot.LEGS, 110, 141);
			addSlot(GearSlot.HANDS, 64, 185);
			addSlot(GearSlot.FEET, 110, 185);
			addSlot(GearSlot.RING, 156, 185);
		}

		private void addSlot(GearSlot slot, int x, int y)
		{
			EquipmentSlotPanel panel = new EquipmentSlotPanel(slot);
			panel.setBounds(x, y, 42, 38);
			slots.put(slot, panel);
			add(panel);
		}

		private void clear()
		{
			for (EquipmentSlotPanel panel : slots.values())
			{
				panel.setItem(null, false);
			}
			repaint();
		}

		private void setLoadout(DpsResult result)
		{
			for (Map.Entry<GearSlot, EquipmentSlotPanel> entry : slots.entrySet())
			{
				GearItem item = result.getLoadout().get(entry.getKey());
				entry.getValue().setItem(item, item != null && plugin.ownsItem(item.getId()));
			}
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setColor(new Color(48, 43, 36));
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(new Color(76, 69, 58));
			g.fillRoundRect(8, 8, getWidth() - 16, getHeight() - 16, 6, 6);
			g.setColor(new Color(31, 28, 24));
			g.drawRoundRect(8, 8, getWidth() - 17, getHeight() - 17, 6, 6);
			g.setStroke(new BasicStroke(3.0f));
			g.setColor(new Color(33, 31, 28));
			g.drawLine(131, 46, 131, 50);
			g.drawLine(131, 88, 131, 96);
			g.drawLine(131, 134, 131, 141);
			g.drawLine(131, 179, 131, 185);
			g.drawLine(106, 69, 92, 69);
			g.drawLine(152, 69, 156, 69);
			g.drawLine(106, 116, 84, 116);
			g.drawLine(152, 116, 178, 116);
			g.drawLine(106, 204, 92, 204);
			g.drawLine(152, 204, 156, 204);
			g.dispose();
		}
	}

	private final class EquipmentSlotPanel extends JPanel
	{
		private final GearSlot slot;
		private final JLabel icon = new JLabel();

		private EquipmentSlotPanel(GearSlot slot)
		{
			this.slot = slot;
			setLayout(new BorderLayout());
			setBackground(new Color(87, 88, 82));
			setBorder(BorderFactory.createLineBorder(new Color(31, 30, 28)));
			icon.setHorizontalAlignment(SwingConstants.CENTER);
			icon.setVerticalAlignment(SwingConstants.CENTER);
			icon.setFont(FontManager.getRunescapeSmallFont());
			icon.setForeground(new Color(38, 38, 36));
			add(icon, BorderLayout.CENTER);
			setToolTipText(slotLabel(slot));
		}

		private void setItem(GearItem item, boolean owned)
		{
			icon.setText("");
			icon.setIcon(null);
			if (item == null)
			{
				icon.setText(emptySlotText(slot));
				setToolTipText(slotLabel(slot) + ": Empty");
				setBorder(BorderFactory.createLineBorder(new Color(31, 30, 28)));
				return;
			}
			AsyncBufferedImage image = itemManager.getImage(item.getId());
			icon.setIcon(new ImageIcon(image));
			setToolTipText(slotLabel(slot) + ": " + item.label() + (owned ? " (Already owned)" : ""));
			setBorder(BorderFactory.createLineBorder(owned ? new Color(94, 234, 212) : new Color(32, 33, 34)));
		}
	}

	private static String emptySlotText(GearSlot slot)
	{
		switch (slot)
		{
			case HEAD:
				return "H";
			case CAPE:
				return "C";
			case NECK:
				return "N";
			case AMMO:
				return "A";
			case WEAPON:
				return "W";
			case BODY:
				return "B";
			case SHIELD:
				return "S";
			case LEGS:
				return "L";
			case HANDS:
				return "G";
			case FEET:
				return "F";
			case RING:
			default:
				return "R";
		}
	}

	private void updateSearchResults()
	{
		if (data == null)
		{
			return;
		}
		setMonsterMatches(data.searchMonsters(monsterSearch.getText(), 25));
	}

	private void setMonsterMatches(List<MonsterStats> matches)
	{
		DefaultComboBoxModel<MonsterEntry> model = new DefaultComboBoxModel<>();
		for (MonsterStats monster : matches)
		{
			model.addElement(new MonsterEntry(monster));
		}
		monsterBox.setModel(model);
	}

	private void scheduleSearch()
	{
		if (searchTimer != null)
		{
			searchTimer.stop();
		}
		searchTimer = new Timer(140, e -> updateSearchResults());
		searchTimer.setRepeats(false);
		searchTimer.start();
	}

	private static JPanel section()
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(8, 8, 8, 8)));
		return panel;
	}

	private static JLabel label(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(Color.WHITE);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	private static JButton button(String text)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		return button;
	}

	private static GridBagConstraints constraints()
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1.0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new java.awt.Insets(3, 0, 3, 0);
		return c;
	}

	private static void addRow(JPanel panel, GridBagConstraints c, String label, java.awt.Component component)
	{
		c.gridx = 0;
		c.weightx = 0.0;
		c.gridwidth = 1;
		panel.add(label(label), c);
		c.gridx = 1;
		c.weightx = 1.0;
		panel.add(component, c);
		c.gridx = 0;
		c.gridy++;
	}

	private static int parseBudget(String text)
	{
		String value = text == null ? "" : text.trim().toLowerCase().replace(",", "").replace("_", "");
		if (value.isEmpty())
		{
			return 0;
		}
		double multiplier = 1.0;
		if (value.endsWith("b"))
		{
			multiplier = 1_000_000_000.0;
			value = value.substring(0, value.length() - 1);
		}
		else if (value.endsWith("m"))
		{
			multiplier = 1_000_000.0;
			value = value.substring(0, value.length() - 1);
		}
		else if (value.endsWith("k"))
		{
			multiplier = 1_000.0;
			value = value.substring(0, value.length() - 1);
		}
		try
		{
			double gp = Double.parseDouble(value) * multiplier;
			if (!Double.isFinite(gp) || gp < 0 || gp > Integer.MAX_VALUE) { throw new IllegalArgumentException(); }
			return (int) Math.round(gp);
		}
		catch (NumberFormatException ex)
		{
			throw new IllegalArgumentException("Invalid cash budget", ex);
		}
	}

	private static String formatGp(long value)
	{
		if (value >= 1_000_000)
		{
			return String.format("%.2fm", value / 1_000_000.0);
		}
		if (value >= 1_000)
		{
			return String.format("%.1fk", value / 1_000.0);
		}
		return String.valueOf(value);
	}

	private static final class MonsterEntry
	{
		private final MonsterStats monster;

		private MonsterEntry(MonsterStats monster)
		{
			this.monster = monster;
		}

		@Override
		public String toString()
		{
			return monster.label();
		}
	}
}
