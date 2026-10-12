package com.bestdps;

import com.bestdps.calc.BoostProfile;
import com.bestdps.calc.CandidateMode;
import com.bestdps.calc.CombatStyle;
import com.bestdps.calc.DpsResult;
import com.bestdps.calc.EncounterRules;
import com.bestdps.calc.OptimizationRequest;
import com.bestdps.calc.PlayerLevels;
import com.bestdps.calc.PrayerBonuses;
import com.bestdps.calc.PrayerProfile;
import com.bestdps.calc.ProfileSnapshot;
import com.bestdps.calc.RequirementProfile;
import com.bestdps.data.BestDpsData;
import com.bestdps.data.MonsterStats;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;

/** The search controls and lifecycle. Result presentation lives beside, not below, the search. */
final class BestDpsPanel extends JPanel
{
	private final BestDpsPlugin plugin;
	private final BestDpsConfig config;
	private final JTextField monsterSearch = new PlaceholderField("NPC name or ID");
	private final JComboBox<MonsterEntry> monsterBox = new JComboBox<>();
	private final JComboBox<CombatStyle> styleBox = new JComboBox<>(CombatStyle.values());
	private final JComboBox<BoostProfile> boostBox = new JComboBox<>(BoostProfile.values());
	private final JComboBox<PrayerProfile> prayerBox = new JComboBox<>(PrayerProfile.values());
	private final JTextField budgetField = new JTextField("10m");
	private final JCheckBox untradeablesBox = new JCheckBox("Include owned untradeables");
	private final JCheckBox useMyLevelsBox = new JCheckBox("Use my levels & unlocks");
	private final JComboBox<CandidateMode> sourceBox = new JComboBox<>(new CandidateMode[]{
		CandidateMode.OWNED_OR_BUDGET, CandidateMode.OWNED_ONLY, CandidateMode.BUDGET});
	private final JCheckBox sellBox = new JCheckBox("Allow gear sales");
	private final JCheckBox slayerBox = new JCheckBox("On a Slayer task");
	private final BestDpsEncounterControls encounterControls = new BestDpsEncounterControls();
	private final BestDpsResultsPanel results;
	private final JButton findButton = BestDpsTheme.button("Find best gear", true);
	private final JButton cancelButton = BestDpsTheme.button("Cancel", false);
	private final JButton refreshButton = BestDpsTheme.button("Refresh prices", false);
	private final JLabel statusLabel = BestDpsTheme.muted("Loading combat data…");
	private final JLabel bankLabel = BestDpsTheme.muted("Bank not read");
	private final JLabel profileLabel = BestDpsTheme.muted("Account levels");
	private final JLabel priceLabel = BestDpsTheme.muted("Price estimates");
	private final JLabel targetNote = BestDpsTheme.muted("Choose the exact variant or phase you want to fight.");
	private final JProgressBar progress = new JProgressBar();
	private BestDpsData data;
	private OptimizationRequest activeRequest;
	private int generation;
	private boolean initializing = true;
	private boolean searching;
	private boolean hasResults;
	private Timer searchTimer;

	BestDpsPanel(BestDpsPlugin plugin, ItemManager itemManager, BestDpsConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		this.results = new BestDpsResultsPanel(itemManager);
		build();
		wireControls();
		BestDpsTheme.style(this);
		initializing = false;
		updateSourceControls();
		updateFindButton();
	}

	void setData(BestDpsData data)
	{
		initializing = true;
		this.data = data;
		styleBox.setSelectedItem(config.defaultStyle());
		boostBox.setSelectedItem(config.defaultBoostProfile());
		prayerBox.setSelectedItem(config.defaultPrayerProfile());
		budgetField.setText(formatGp(config.budget()));
		untradeablesBox.setSelected(config.includeUntradeables());
		sellBox.setSelected(config.sellOwnedItems());
		useMyLevelsBox.setSelected(config.useMyLevels());
		encounterControls.setProfile(plugin.getProfileSnapshot(), useMyLevelsBox.isSelected());
		updateSearchResults();
		initializing = false;
		updateSourceControls();
		setStatus(String.format(Locale.ROOT, "%,d items · %,d NPC states", data.getGearItems().size(), data.getMonsters().size()));
		updateFindButton();
	}

	void setStatus(String message)
	{
		statusLabel.setText(message);
		statusLabel.setToolTipText(message);
		statusLabel.setForeground(BestDpsTheme.MUTED);
	}

	void updateBankStatus(boolean scanned, int observedItems, boolean fresh, long updatedAt)
	{
		bankLabel.setText(scanned ? (fresh ? "Bank read · " : "Saved bank · ") + observedItems + " items" : "Open bank to sync gear");
		bankLabel.setForeground(scanned && fresh ? BestDpsTheme.POSITIVE : BestDpsTheme.ACCENT);
		bankLabel.setToolTipText(scanned ? "Last bank read: " + new Date(updatedAt)
			+ ". Reopen your bank to refresh quantities." : "Open your bank once to include banked equipment.");
	}

	void updateProfileStatus(ProfileSnapshot snapshot)
	{
		if (snapshot == null || snapshot == ProfileSnapshot.MAXED)
		{
			profileLabel.setText(useMyLevelsBox.isSelected() ? "Log in for your levels" : "Maxed simulation");
			profileLabel.setToolTipText("Turn on Use my levels & unlocks to use your character.");
			return;
		}
		PlayerLevels levels = snapshot.getBaseLevels();
		profileLabel.setText(useMyLevelsBox.isSelected() ? "Your character" : "Maxed simulation");
		profileLabel.setToolTipText("Attack " + levels.getAttack() + " · Strength " + levels.getStrength()
			+ " · Defence " + levels.getDefence() + " · Ranged " + levels.getRanged() + " · Magic " + levels.getMagic());
		encounterControls.setProfile(snapshot, useMyLevelsBox.isSelected());
	}

	void pricesUpdated(BestDpsData updated, String status)
	{
		if (updated != null) { data = updated; }
		priceLabel.setText(status.startsWith("Wiki") ? "Wiki prices · buffered" : "Estimated prices");
		priceLabel.setToolTipText(status + ". Sale plans require fresh quotes and a bank read this session.");
		refreshButton.setEnabled(true);
	}

	void showResults(int resultGeneration, List<DpsResult> found, long elapsedMillis)
	{
		if (resultGeneration != generation) { return; }
		setSearching(false);
		hasResults = !found.isEmpty();
		results.showResults(found, activeRequest, elapsedMillis);
		setStatus(found.isEmpty() ? "No legal setup fits these inputs. Adjust your gear source, cash or combat style."
			: found.size() + " setups compared · " + (elapsedMillis < 1000 ? elapsedMillis + " ms"
				: String.format(Locale.ROOT, "%.2f s", elapsedMillis / 1000.0)) + " · best found");
	}

	void searchFailed(int resultGeneration, String message)
	{
		if (resultGeneration != generation) { return; }
		setSearching(false);
		hasResults = false;
		results.clear("Search could not finish", message);
		showError(message);
	}

	void invalidateResults(String message)
	{
		generation++;
		setSearching(false);
		hasResults = false;
		activeRequest = null;
		results.clear("Ready when you are", message);
		setStatus(message);
	}

	private void build()
	{
		setLayout(new BorderLayout(0, 14));
		setBackground(BestDpsTheme.BG);
		setBorder(new EmptyBorder(18, 20, 12, 20));
		setPreferredSize(new Dimension(1240, 780));
		JPanel top = BestDpsTheme.panel(new BorderLayout(0, 16));
		top.add(header(), BorderLayout.NORTH);
		top.add(targetBar(), BorderLayout.CENTER);
		add(top, BorderLayout.NORTH);

		JPanel workbench = BestDpsTheme.panel(new BorderLayout(16, 0));
		workbench.add(controlsRail(), BorderLayout.WEST);
		workbench.add(results, BorderLayout.CENTER);
		add(workbench, BorderLayout.CENTER);
		add(footer(), BorderLayout.SOUTH);
		results.clear("Find the gear that earns its slot", "Choose an NPC, set your gear source and compare setups. Your bank stays part of the calculation.");
	}

	private JPanel header()
	{
		JPanel header = BestDpsTheme.panel(new BorderLayout(14, 0));
		JPanel brand = BestDpsTheme.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		JLabel title = BestDpsTheme.label("Best DPS");
		title.setFont(BestDpsTheme.TITLE);
		title.setForeground(BestDpsTheme.ACCENT);
		brand.add(title);
		JLabel sub = BestDpsTheme.muted("  /  Equipment workbench");
		brand.add(sub);
		header.add(brand, BorderLayout.WEST);
		JPanel account = BestDpsTheme.panel(new FlowLayout(FlowLayout.RIGHT, 14, 5));
		account.add(profileLabel);
		account.add(bankLabel);
		header.add(account, BorderLayout.EAST);
		return header;
	}

	private JPanel targetBar()
	{
		JPanel outer = BestDpsTheme.panel(new BorderLayout(0, 7));
		outer.setBackground(BestDpsTheme.PANEL);
		outer.setBorder(new EmptyBorder(12, 14, 10, 14));
		JPanel fields = BestDpsTheme.panel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridy = 0;
		c.gridx = 0;
		c.weightx = 0.30;
		c.insets = new Insets(0, 0, 0, 12);
		fields.add(field("Target", monsterSearch), c);
		c.gridx++;
		c.weightx = 0.50;
		fields.add(field("Variant / phase", monsterBox), c);
		c.gridx++;
		c.weightx = 0.20;
		c.insets = new Insets(0, 0, 0, 0);
		fields.add(field("Combat style", styleBox), c);
		monsterSearch.setPreferredSize(new Dimension(240, 34));
		monsterSearch.setMinimumSize(new Dimension(160, 34));
		monsterBox.setPreferredSize(new Dimension(340, 34));
		monsterBox.setMinimumSize(new Dimension(220, 34));
		monsterBox.setMaximumRowCount(12);
		monsterBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				super.getListCellRendererComponent(list, value, index, selected, focus);
				setText(value == null ? "Select an NPC variant" : value.toString());
				setFont(BestDpsTheme.BODY);
				setBorder(new EmptyBorder(6, 8, 6, 8));
				setBackground(selected ? BestDpsTheme.RAISED : BestDpsTheme.PANEL);
				setForeground(selected ? BestDpsTheme.ACCENT : BestDpsTheme.TEXT);
				setToolTipText(value == null ? null : value.toString());
				return this;
			}
		});
		styleBox.setPreferredSize(new Dimension(170, 34));
		styleBox.setMinimumSize(new Dimension(126, 34));
		outer.add(fields, BorderLayout.CENTER);
		targetNote.setFont(BestDpsTheme.SMALL);
		outer.add(targetNote, BorderLayout.SOUTH);
		return outer;
	}

	private JPanel controlsRail()
	{
		JPanel rail = BestDpsTheme.panel(new BorderLayout(0, 12));
		rail.setPreferredSize(new Dimension(238, 500));
		rail.setMinimumSize(new Dimension(218, 0));
		JPanel content = new ScrollColumn();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		JPanel gear = formSection("Build from");
		sourceBox.setMinimumSize(new Dimension(0, 32));
		sourceBox.setPreferredSize(new Dimension(208, 32));
		for (JComboBox<?> box : new JComboBox<?>[]{boostBox, prayerBox})
		{
			box.setMinimumSize(new Dimension(0, 32));
			box.setPreferredSize(new Dimension(154, 32));
		}
		boostBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				super.getListCellRendererComponent(list, value, index, selected, focus);
				setText(value == BoostProfile.LIVE_CURRENT ? "Live levels" : String.valueOf(value));
				setToolTipText(value == null ? null : value.toString());
				return this;
			}
		});
		sourceBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				super.getListCellRendererComponent(list, value, index, selected, focus);
				setText(value == CandidateMode.OWNED_OR_BUDGET ? "Owned gear + cash"
					: value == CandidateMode.OWNED_ONLY ? "Owned gear only" : "Buy a setup");
				setFont(BestDpsTheme.BODY);
				setBorder(new EmptyBorder(5, 7, 5, 7));
				setBackground(selected ? BestDpsTheme.RAISED : BestDpsTheme.PANEL);
				setForeground(BestDpsTheme.TEXT);
				return this;
			}
		});
		stack(gear, sourceBox, 8);
		stack(gear, field("Cash to spend", budgetField), 7);
		budgetField.setFont(BestDpsTheme.DATA);
		budgetField.setToolTipText("Available cash, excluding sale proceeds. Use k, m or b; maximum 2,147,483,647 GP.");
		stack(gear, sellBox, 0);
		sellBox.setToolTipText("Suggest sales only when they improve DPS. Selected gear is kept; no trades are placed.");
		stack(content, gear, 10);
		JPanel character = formSection("Character & boosts");
		stack(character, useMyLevelsBox, 3);
		stack(character, untradeablesBox, 3);
		stack(character, slayerBox, 9);
		stack(character, inlineField("Boost", boostBox), 8);
		stack(character, inlineField("Prayer", prayerBox), 0);
		stack(content, character, 10);
		stack(content, encounterControls, 0);
		content.add(Box.createVerticalGlue());
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getViewport().setBackground(BestDpsTheme.BG);
		scroll.getVerticalScrollBar().setUnitIncrement(18);
		scroll.setMinimumSize(new Dimension(0, 0));
		rail.add(scroll, BorderLayout.CENTER);
		JPanel actions = BestDpsTheme.panel(new BorderLayout(0, 7));
		findButton.setPreferredSize(new Dimension(218, 40));
		findButton.setToolTipText("Compare setups · Ctrl+Enter");
		cancelButton.setVisible(false);
		actions.add(findButton, BorderLayout.NORTH);
		actions.add(cancelButton, BorderLayout.CENTER);
		JLabel shortcut = BestDpsTheme.muted("Ctrl+Enter to compare");
		shortcut.setHorizontalAlignment(SwingConstants.CENTER);
		shortcut.setFont(BestDpsTheme.SMALL);
		actions.add(shortcut, BorderLayout.SOUTH);
		rail.add(actions, BorderLayout.SOUTH);
		return rail;
	}

	private JPanel footer()
	{
		JPanel footer = BestDpsTheme.panel(new BorderLayout(12, 0));
		footer.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, BestDpsTheme.LINE), new EmptyBorder(8, 0, 0, 0)));
		JPanel state = BestDpsTheme.panel(new BorderLayout(10, 0));
		progress.setIndeterminate(true);
		progress.setForeground(BestDpsTheme.ACCENT);
		progress.setBackground(BestDpsTheme.RAISED);
		progress.setBorderPainted(false);
		progress.setPreferredSize(new Dimension(48, 3));
		progress.setVisible(false);
		state.add(progress, BorderLayout.WEST);
		state.add(statusLabel, BorderLayout.CENTER);
		footer.add(state, BorderLayout.CENTER);
		JPanel prices = BestDpsTheme.panel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		priceLabel.setFont(BestDpsTheme.SMALL);
		prices.add(priceLabel);
		refreshButton.setFont(BestDpsTheme.SMALL);
		refreshButton.setPreferredSize(new Dimension(114, 28));
		prices.add(refreshButton);
		footer.add(prices, BorderLayout.EAST);
		return footer;
	}

	private void wireControls()
	{
		monsterSearch.getDocument().addDocumentListener(onEdit(this::scheduleSearch));
		monsterSearch.addActionListener(e -> { updateSearchResults(); monsterBox.requestFocusInWindow(); });
		monsterBox.addActionListener(e -> { updateTarget(); settingsChanged(); });
		styleBox.addActionListener(e -> { updateTarget(); settingsChanged(); });
		for (JComboBox<?> box : new JComboBox<?>[]{boostBox, prayerBox}) { box.addActionListener(e -> settingsChanged()); }
		sourceBox.addActionListener(e -> { updateSourceControls(); settingsChanged(); });
		budgetField.getDocument().addDocumentListener(onEdit(this::settingsChanged));
		budgetField.addActionListener(e -> startFind());
		sellBox.addActionListener(e -> { plugin.saveSellPreference(sellBox.isSelected()); settingsChanged(); });
		for (JCheckBox box : new JCheckBox[]{untradeablesBox, slayerBox}) { box.addActionListener(e -> settingsChanged()); }
		useMyLevelsBox.addActionListener(e -> {
			encounterControls.setProfile(plugin.getProfileSnapshot(), useMyLevelsBox.isSelected());
			updateProfileStatus(plugin.getProfileSnapshot());
			settingsChanged();
		});
		encounterControls.onChange(() -> { updateTarget(); settingsChanged(); });
		findButton.addActionListener(e -> startFind());
		cancelButton.addActionListener(e -> { plugin.cancelSearch(); invalidateResults("Search cancelled. Adjust your inputs and try again."); });
		refreshButton.addActionListener(e -> {
			refreshButton.setEnabled(false);
			priceLabel.setText("Refreshing prices…");
			plugin.requestPriceRefresh();
		});
		getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("control ENTER"), "find");
		getActionMap().put("find", new AbstractAction() { public void actionPerformed(ActionEvent e) { startFind(); } });
		getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("control L"), "target");
		getActionMap().put("target", new AbstractAction() { public void actionPerformed(ActionEvent e) { monsterSearch.requestFocusInWindow(); monsterSearch.selectAll(); } });
	}

	private void updateSourceControls()
	{
		CandidateMode source = (CandidateMode) sourceBox.getSelectedItem();
		budgetField.setEnabled(source != CandidateMode.OWNED_ONLY);
		sellBox.setEnabled(source == CandidateMode.OWNED_OR_BUDGET);
		sourceBox.setToolTipText(source == CandidateMode.OWNED_ONLY ? "Bank, inventory and worn gear. No purchases or sales."
			: source == CandidateMode.BUDGET ? "Buy tradeable gear within your cash. Owned untradeables stay available."
			: "Keep owned gear. Spend cash on upgrades. Sales stay optional.");
	}

	private void updateTarget()
	{
		MonsterEntry entry = (MonsterEntry) monsterBox.getSelectedItem();
		CombatStyle style = (CombatStyle) styleBox.getSelectedItem();
		encounterControls.setTarget(entry == null ? null : entry.monster, style);
		if (entry == null)
		{
			targetNote.setText("Choose the exact variant or phase you want to fight.");
			targetNote.setForeground(BestDpsTheme.MUTED);
		}
		else
		{
			String blocked = EncounterRules.blockingReason(entry.monster, style);
			targetNote.setText(blocked.isEmpty() ? "NPC " + entry.monster.getId() + " · Selected phase · Continuous attacks" : blocked);
			targetNote.setToolTipText(blocked.isEmpty() ? entry.monster.label() : blocked);
			targetNote.setForeground(blocked.isEmpty() ? BestDpsTheme.MUTED : BestDpsTheme.ACCENT);
		}
		updateFindButton();
	}

	private void settingsChanged()
	{
		if (initializing) { return; }
		if (searching)
		{
			plugin.cancelSearch();
			generation++;
			setSearching(false);
			results.clear("Inputs changed", "Run the comparison again to use these settings.");
		}
		if (hasResults) { results.markStale(); }
		setStatus("Ready to compare · Ctrl+Enter");
		updateFindButton();
	}

	private void startFind()
	{
		if (searching || data == null) { return; }
		MonsterEntry entry = (MonsterEntry) monsterBox.getSelectedItem();
		if (entry == null) { showError("Search for an NPC and choose a variant first."); monsterSearch.requestFocusInWindow(); return; }
		CombatStyle style = (CombatStyle) styleBox.getSelectedItem();
		String blocked = EncounterRules.blockingReason(entry.monster, style);
		if (!blocked.isEmpty()) { showError(blocked); return; }
		if (sellBox.isSelected() && sourceBox.getSelectedItem() == CandidateMode.OWNED_OR_BUDGET && !plugin.isBankFresh())
		{
			showError("Open your bank this session before planning sales."); return;
		}
		int budget;
		try { budget = parseBudget(budgetField.getText()); }
		catch (IllegalArgumentException ex) { showError("Cash must be 0–2,147,483,647 GP. Examples: 500k, 25m, 1b."); budgetField.requestFocusInWindow(); return; }
		try { encounterControls.commitInputs(); }
		catch (IllegalArgumentException ex) { showError(ex.getMessage()); return; }
		plugin.cancelSearch();
		int runGeneration = ++generation;
		hasResults = false;
		setSearching(true);
		setStatus("Comparing legal setups for " + entry.monster.label() + "…");
		boolean needsProfile = useMyLevelsBox.isSelected() || boostBox.getSelectedItem() == BoostProfile.LIVE_CURRENT
			|| prayerBox.getSelectedItem() == PrayerProfile.CURRENT_ACTIVE;
		plugin.profileSnapshot(needsProfile, snapshot -> submitFind(entry, style, budget, snapshot, runGeneration));
	}

	private void submitFind(MonsterEntry entry, CombatStyle style, int budget, ProfileSnapshot snapshot, int runGeneration)
	{
		if (runGeneration != generation) { return; }
		if (useMyLevelsBox.isSelected() && snapshot == ProfileSnapshot.MAXED)
		{
			searchFailed(runGeneration, "Log in for your levels, or turn off Use my levels & unlocks."); return;
		}
		RequirementProfile profile = useMyLevelsBox.isSelected() ? snapshot.getRequirements() : RequirementProfile.MAXED;
		PlayerLevels base = useMyLevelsBox.isSelected() ? snapshot.getBaseLevels() : PlayerLevels.MAXED;
		PlayerLevels current = useMyLevelsBox.isSelected() ? snapshot.getCurrentLevels() : PlayerLevels.MAXED;
		PlayerLevels levels = base.boosted((BoostProfile) boostBox.getSelectedItem(), current);
		try
		{
			activeRequest = new OptimizationRequest(entry.monster, style, levels,
				prayerBonuses((PrayerProfile) prayerBox.getSelectedItem(), levels, snapshot), null, budget,
				(CandidateMode) sourceBox.getSelectedItem(), untradeablesBox.isSelected(), slayerBox.isSelected(),
				plugin.ownedItems(), profile, 20)
				.withSellOwnedItems(sellBox.isSelected()).withEncounterContext(encounterControls.encounterContext())
				.withCombatContext(encounterControls.combatContext(snapshot, useMyLevelsBox.isSelected()));
			plugin.optimize(activeRequest, runGeneration);
		}
		catch (IllegalArgumentException ex) { searchFailed(runGeneration, "Check the encounter and combat values: " + ex.getMessage()); }
	}

	private PrayerBonuses prayerBonuses(PrayerProfile choice, PlayerLevels levels, ProfileSnapshot snapshot)
	{
		PrayerProfile selected = choice == null ? PrayerProfile.BEST_AVAILABLE : choice;
		switch (selected)
		{
			case NONE: return PrayerBonuses.NONE;
			case CURRENT_ACTIVE: return PrayerBonuses.fromActive(snapshot.getActivePrayers());
			default: return useMyLevelsBox.isSelected() ? snapshot.getAvailablePrayers() : PrayerBonuses.bestAvailable(levels);
		}
	}

	private void setSearching(boolean value)
	{
		searching = value;
		findButton.setText(value ? "Comparing…" : "Find best gear");
		cancelButton.setVisible(value);
		progress.setVisible(value);
		results.setSearching(value);
		updateFindButton();
		revalidate();
	}

	private void updateFindButton()
	{
		findButton.setEnabled(data != null && !searching && monsterBox.getSelectedItem() != null);
	}

	private void showError(String message)
	{
		setStatus(message);
		statusLabel.setForeground(BestDpsTheme.ACCENT);
	}

	private void updateSearchResults()
	{
		if (data == null) { return; }
		MonsterEntry previous = (MonsterEntry) monsterBox.getSelectedItem();
		List<MonsterStats> matches = data.searchMonsters(monsterSearch.getText(), 60);
		DefaultComboBoxModel<MonsterEntry> model = new DefaultComboBoxModel<>();
		MonsterEntry selected = null;
		for (MonsterStats monster : matches)
		{
			MonsterEntry match = new MonsterEntry(monster);
			model.addElement(match);
			if (previous != null && monster.getId() == previous.monster.getId() && monster.label().equals(previous.monster.label())) { selected = match; }
		}
		monsterBox.setModel(model);
		if (selected != null) { monsterBox.setSelectedItem(selected); }
		else if (monsterSearch.getText().trim().isEmpty()) { monsterBox.setSelectedIndex(-1); }
		if (matches.isEmpty())
		{
			targetNote.setText("No match. Try another NPC name or ID.");
			targetNote.setForeground(BestDpsTheme.ACCENT);
		}
		else { updateTarget(); }
		updateFindButton();
	}

	private void scheduleSearch()
	{
		if (searchTimer != null) { searchTimer.stop(); }
		searchTimer = new Timer(140, e -> updateSearchResults());
		searchTimer.setRepeats(false);
		searchTimer.start();
		settingsChanged();
	}

	private static JPanel field(String title, Component component)
	{
		JPanel panel = BestDpsTheme.panel(new BorderLayout(0, 5));
		JLabel label = BestDpsTheme.muted(title);
		label.setFont(BestDpsTheme.SMALL);
		label.setLabelFor(component);
		panel.add(label, BorderLayout.NORTH);
		panel.add(component, BorderLayout.CENTER);
		return panel;
	}

	private static JPanel inlineField(String title, Component component)
	{
		JPanel panel = BestDpsTheme.panel(new BorderLayout(6, 0));
		JLabel label = BestDpsTheme.muted(title);
		label.setPreferredSize(new Dimension(36, 32));
		label.setLabelFor(component);
		panel.add(label, BorderLayout.WEST);
		panel.add(component, BorderLayout.CENTER);
		return panel;
	}

	private static JPanel formSection(String heading)
	{
		JPanel panel = BestDpsTheme.panel(null);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(BestDpsTheme.PANEL);
		panel.setBorder(new EmptyBorder(10, 12, 10, 12));
		JLabel title = BestDpsTheme.label(heading);
		title.setFont(BestDpsTheme.BODY.deriveFont(java.awt.Font.BOLD));
		stack(panel, title, 10);
		return panel;
	}

	private static void stack(JPanel parent, JComponent child, int gap)
	{
		BestDpsTheme.style(child);
		if (child instanceof JCheckBox)
		{
			child.setPreferredSize(new Dimension(child.getPreferredSize().width, 26));
			child.setMinimumSize(new Dimension(0, 26));
		}
		child.setAlignmentX(Component.LEFT_ALIGNMENT);
		Dimension size = child.getPreferredSize();
		child.setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
		parent.add(child);
		if (gap > 0) { parent.add(Box.createVerticalStrut(gap)); }
	}

	private static DocumentListener onEdit(Runnable action)
	{
		return new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e) { action.run(); }
			public void removeUpdate(DocumentEvent e) { action.run(); }
			public void changedUpdate(DocumentEvent e) { action.run(); }
		};
	}

	private static int parseBudget(String text)
	{
		String value = text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "");
		if (value.isEmpty()) { return 0; }
		double multiplier = 1.0;
		if (value.endsWith("b")) { multiplier = 1_000_000_000.0; value = value.substring(0, value.length() - 1); }
		else if (value.endsWith("m")) { multiplier = 1_000_000.0; value = value.substring(0, value.length() - 1); }
		else if (value.endsWith("k")) { multiplier = 1_000.0; value = value.substring(0, value.length() - 1); }
		try
		{
			double gp = Double.parseDouble(value) * multiplier;
			if (!Double.isFinite(gp) || gp < 0 || gp > Integer.MAX_VALUE) { throw new IllegalArgumentException(); }
			return (int) Math.round(gp);
		}
		catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid cash budget", ex); }
	}

	private static String formatGp(long value)
	{
		if (value >= 1_000_000) { return String.format(Locale.ROOT, "%.2fm", value / 1_000_000.0); }
		if (value >= 1_000) { return String.format(Locale.ROOT, "%.1fk", value / 1_000.0); }
		return String.valueOf(value);
	}

	private static final class MonsterEntry
	{
		private final MonsterStats monster;
		private MonsterEntry(MonsterStats monster) { this.monster = monster; }
		@Override public String toString() { return monster.label(); }
	}

	private static final class ScrollColumn extends JPanel implements javax.swing.Scrollable
	{
		private ScrollColumn() { setBackground(BestDpsTheme.BG); }
		public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) { return 18; }
		public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) { return Math.max(18, visible.height - 36); }
		public boolean getScrollableTracksViewportWidth() { return true; }
		public boolean getScrollableTracksViewportHeight() { return false; }
	}

	private static final class PlaceholderField extends JTextField
	{
		private final String placeholder;
		private PlaceholderField(String placeholder) { this.placeholder = placeholder; }
		@Override protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			if (getText().isEmpty() && !hasFocus())
			{
				graphics.setColor(BestDpsTheme.MUTED);
				graphics.setFont(getFont());
				graphics.drawString(placeholder, getInsets().left, (getHeight() - graphics.getFontMetrics().getHeight()) / 2 + graphics.getFontMetrics().getAscent());
			}
		}
	}
}
