package com.bestdps;

import com.bestdps.calc.CandidateMode;
import com.bestdps.calc.CombatStyle;
import com.bestdps.calc.BoostProfile;
import com.bestdps.calc.DpsResult;
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
		useMyLevelsBox.setSelected(config.useMyLevels());
		setStatus("Loaded " + data.getGearItems().size() + " items and " + data.getMonsters().size() + " monsters.");
		updateSearchResults();
	}

	void setStatus(String message)
	{
		statusLabel.setText(message);
	}

	void updateBankStatus(boolean bankScanned, int observedItems)
	{
		if (bankScanned)
		{
			bankLabel.setText("Bank read: " + observedItems + " item types");
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
		profileLabel.setText(snapshot.getSource() + ": "
			+ snapshot.getBaseLevels().getAttack() + "/"
			+ snapshot.getBaseLevels().getStrength() + "/"
			+ snapshot.getBaseLevels().getDefence() + " melee, "
			+ snapshot.getBaseLevels().getRanged() + " ranged, "
			+ snapshot.getBaseLevels().getMagic() + " magic");
		profileLabel.setForeground(new Color(94, 234, 212));
	}

	void showResults(int resultGeneration, List<DpsResult> results)
	{
		if (resultGeneration != generation)
		{
			return;
		}
		currentResults.clear();
		currentResults.addAll(results);
		resultModel.setRowCount(0);
		for (DpsResult result : results)
		{
			GearItem weapon = result.getLoadout().getWeapon();
			resultModel.addRow(new Object[]{
				String.format("%.3f", result.getDps()),
				formatGp(result.getPurchaseCost()),
				result.getAttackType(),
				String.format("%.1f%%", result.getAccuracy() * 100.0),
				result.getMaxHit(),
				weapon == null ? "" : weapon.getName()
			});
		}
		setStatus(results.isEmpty() ? "No legal setup found." : "Found " + results.size() + " setups.");
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
		left.add(settingsPanel(), c);
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

	private JPanel settingsPanel()
	{
		JPanel panel = section();
		panel.setPreferredSize(new Dimension(CONTROL_WIDTH, 232));
		panel.setLayout(new GridBagLayout());
		GridBagConstraints c = constraints();
		addRow(panel, c, "Style", styleBox);
		addRow(panel, c, "Boosts", boostBox);
		addRow(panel, c, "Prayers", prayerBox);
		addRow(panel, c, "Budget", budgetField);
		untradeablesBox.setOpaque(false);
		untradeablesBox.setForeground(Color.WHITE);
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
		return panel;
	}

	private JPanel statusPanel()
	{
		JPanel panel = section();
		panel.setPreferredSize(new Dimension(CONTROL_WIDTH, 98));
		panel.setLayout(new GridLayout(0, 1, 0, 4));
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		bankLabel.setForeground(new Color(250, 204, 21));
		profileLabel.setForeground(new Color(250, 204, 21));
		panel.add(statusLabel);
		panel.add(profileLabel);
		panel.add(bankLabel);
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
		CombatStyle style = (CombatStyle) styleBox.getSelectedItem();
		int budget = parseBudget(budgetField.getText());
		int runGeneration = ++generation;
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
		RequirementProfile profile = useMyLevelsBox.isSelected() ? snapshot.getRequirements() : RequirementProfile.MAXED;
		PlayerLevels base = useMyLevelsBox.isSelected() ? snapshot.getBaseLevels() : PlayerLevels.MAXED;
		PlayerLevels current = useMyLevelsBox.isSelected() ? snapshot.getCurrentLevels() : PlayerLevels.MAXED;
		BoostProfile boost = (BoostProfile) boostBox.getSelectedItem();
		PlayerLevels levels = base.boosted(boost, current);
		PrayerBonuses prayers = prayerBonuses((PrayerProfile) prayerBox.getSelectedItem(), levels, snapshot);
		plugin.optimize(new OptimizationRequest(
			entry.monster,
			style,
			levels,
			prayers,
			null,
			budget,
			CandidateMode.OWNED_OR_BUDGET,
			untradeablesBox.isSelected(),
			slayerBox.isSelected(),
			plugin.ownedItems(),
			profile,
			20), runGeneration);
	}

	private static PrayerBonuses prayerBonuses(PrayerProfile profile, PlayerLevels levels, ProfileSnapshot snapshot)
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
				return PrayerBonuses.bestAvailable(levels);
		}
	}

	private void showLoadout(DpsResult result)
	{
		equipmentLayout.setLoadout(result);
		loadoutDetails.removeAll();
		if (!result.getSpellName().isEmpty())
		{
			loadoutDetails.add(detailRow("Spell", result.getSpellName(), "", false));
		}
		for (GearSlot slot : GearSlot.values())
		{
			GearItem item = result.getLoadout().get(slot);
			loadoutDetails.add(detailRow(slotLabel(slot), item == null ? "Empty" : item.label(), ownedText(item), item != null && plugin.ownsItem(item.getId())));
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
		if (value.endsWith("m"))
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
			return Math.max(0, (int) Math.round(Double.parseDouble(value) * multiplier));
		}
		catch (NumberFormatException ex)
		{
			return 0;
		}
	}

	private static String formatGp(int value)
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
