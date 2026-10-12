package com.bestdps;

import com.bestdps.calc.CombatContext;
import com.bestdps.calc.CombatMechanics;
import com.bestdps.calc.DpsResult;
import com.bestdps.calc.EncounterContext;
import com.bestdps.calc.EncounterRules;
import com.bestdps.calc.Loadout;
import com.bestdps.calc.OptimizationRequest;
import com.bestdps.calc.OwnedItems;
import com.bestdps.calc.PlayerLevels;
import com.bestdps.calc.TradePlan;
import com.bestdps.data.GearItem;
import com.bestdps.data.GearSlot;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/** A view of one immutable search snapshot; never reads the live bank or places trades. */
final class BestDpsResultsPanel extends JPanel
{
	private final ItemManager itemManager;
	private final Map<Integer, ImageIcon> icons = new HashMap<>();
	private final DefaultListModel<DpsResult> model = new DefaultListModel<>();
	private final JList<DpsResult> alternatives = new JList<>(model);
	private final JLabel status = label("DPS results", BestDpsTheme.BODY.deriveFont(Font.BOLD), BestDpsTheme.TEXT);
	private final JLabel stateNote = label("", BestDpsTheme.SMALL, BestDpsTheme.MUTED);
	private final JProgressBar progress = new JProgressBar();
	private final CardLayout cards = new CardLayout();
	private final JPanel workspace = panel(cards);
	private final JPanel empty = panel(new GridBagLayout());
	private final JPanel details = panel(new BorderLayout(0, 4));
	private final JSplitPane split;
	private final NumberFormat integers = NumberFormat.getIntegerInstance(Locale.UK);
	private OptimizationRequest request;
	private long elapsedMillis;
	private boolean searching;
	private boolean stale;
	private int selectedTab;

	BestDpsResultsPanel(ItemManager itemManager)
	{
		super(new BorderLayout(0, 7));
		this.itemManager = itemManager;
		setBackground(BestDpsTheme.PANEL);
		setMinimumSize(new Dimension(440, 0));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
		JPanel heading = panel(new BorderLayout(12, 0));
		heading.add(status, BorderLayout.WEST);
		heading.add(stateNote, BorderLayout.CENTER);
		progress.setIndeterminate(true);
		progress.setPreferredSize(new Dimension(76, 4));
		progress.setForeground(BestDpsTheme.ACCENT);
		progress.setBackground(BestDpsTheme.RAISED);
		progress.setBorderPainted(false);
		progress.setVisible(false);
		heading.add(progress, BorderLayout.EAST);
		add(heading, BorderLayout.NORTH);
		alternatives.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		alternatives.setFixedCellHeight(46);
		alternatives.setBackground(BestDpsTheme.PANEL);
		alternatives.setForeground(BestDpsTheme.TEXT);
		alternatives.setCellRenderer(new RankedRenderer());
		alternatives.setVisibleRowCount(8);
		alternatives.getAccessibleContext().setAccessibleName("Ranked gear setups, highest DPS first");
		alternatives.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting() && alternatives.getSelectedValue() != null)
			{
				showSelected(alternatives.getSelectedValue());
			}
		});
		JPanel ranking = panel(new BorderLayout(0, 4));
		ranking.add(label("Ranked setups", BestDpsTheme.SMALL, BestDpsTheme.MUTED), BorderLayout.NORTH);
		ranking.add(scroll(alternatives), BorderLayout.CENTER);
		ranking.setMinimumSize(new Dimension(270, 0));
		details.setMinimumSize(new Dimension(0, 0));
		split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ranking, details);
		split.setUI(new BasicSplitPaneUI()
		{
			@Override
			public BasicSplitPaneDivider createDefaultDivider()
			{
				return new BasicSplitPaneDivider(this)
				{
					@Override
					public void paint(Graphics graphics)
					{
						graphics.setColor(BestDpsTheme.PANEL);
						graphics.fillRect(0, 0, getWidth(), getHeight());
						graphics.setColor(BestDpsTheme.LINE);
						if (splitPane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT)
						{
							graphics.drawLine(getWidth() / 2, 0, getWidth() / 2, getHeight());
						}
						else { graphics.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2); }
					}
				};
			}
		});
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setBackground(BestDpsTheme.PANEL);
		split.setDividerSize(12);
		split.setContinuousLayout(true);
		split.setResizeWeight(0);
		split.setDividerLocation(324);
		split.setMinimumSize(new Dimension(0, 0));
		workspace.add(empty, "empty");
		workspace.add(split, "results");
		add(workspace, BorderLayout.CENTER);
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				boolean narrow = getWidth() < 700;
				int orientation = narrow ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
				if (split.getOrientation() != orientation)
				{
					split.setOrientation(orientation);
					split.setDividerLocation(narrow ? 144 : 324);
				}
			}
		});
		clear("No results", "Select an NPC and gear source, then press Compare gear. Open the bank to include stored items.");
	}

	void showResults(List<DpsResult> results, OptimizationRequest request, long elapsedMillis)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			List<DpsResult> snapshot = results == null ? new ArrayList<>() : new ArrayList<>(results);
			SwingUtilities.invokeLater(() -> showResults(snapshot, request, elapsedMillis));
			return;
		}
		if (results == null || results.isEmpty() || request == null)
		{
			clear("No usable setup found", "Check the NPC phase, combat style and gear source. Open your bank, or increase the budget, then select Compare gear again.");
			return;
		}
		this.request = request;
		this.elapsedMillis = Math.max(0, elapsedMillis);
		searching = false;
		stale = false;
		model.clear();
		for (DpsResult result : results)
		{
			if (result != null && result.getLoadout() != null)
			{
				model.addElement(result);
			}
		}
		if (model.isEmpty())
		{
			clear("No usable setup found", "Choose another style or adjust the budget, then select Compare gear again.");
			return;
		}
		cards.show(workspace, "results");
		alternatives.setSelectedIndex(0);
		alternatives.ensureIndexIsVisible(0);
		updateStatus();
		revalidate();
		repaint();
	}

	void clear(String title, String detail)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> clear(title, detail));
			return;
		}
		model.clear();
		request = null;
		searching = false;
		stale = false;
		details.removeAll();
		showEmpty(title, detail);
		updateStatus();
	}

	void setSearching(boolean searching)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> setSearching(searching));
			return;
		}
		boolean wasSearching = this.searching;
		this.searching = searching;
		if (searching && model.isEmpty())
		{
			showEmpty("Calculating DPS", "Comparing eligible gear for the selected NPC. Cancel stops the search.");
		}
		else if (wasSearching && !searching && model.isEmpty())
		{
			showEmpty("Ready to search", "Check the selected NPC and gear source, then select Compare gear to compare setups.");
		}
		updateStatus();
	}

	void markStale()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::markStale);
			return;
		}
		if (!model.isEmpty()) { stale = true; }
		updateStatus();
	}

	private void updateStatus()
	{
		progress.setVisible(searching);
		if (searching)
		{
			status.setText("Finding setups…");
			stateNote.setText(model.isEmpty() ? "Using the selected inputs" : "Previous results remain visible until the search finishes");
		}
		else if (stale)
		{
			status.setText("Inputs changed");
			stateNote.setText("Previous search · Best found, not proven BiS · Compare gear to refresh");
		}
		else if (!model.isEmpty())
		{
			status.setText("Best found · not proven BiS");
			stateNote.setText(model.size() + (model.size() == 1 ? " setup" : " setups") + " · " + format(elapsedMillis / 1000.0, 2) + " s");
		}
		else
		{
			status.setText("DPS results");
			stateNote.setText("Normal attacks · Selected NPC phase");
		}
		stateNote.setForeground(stale ? BestDpsTheme.ACCENT : BestDpsTheme.MUTED);
	}

	private void showEmpty(String title, String detail)
	{
		empty.removeAll();
		JPanel message = panel(new BorderLayout(0, 6));
		message.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		message.add(text(title, BestDpsTheme.BODY.deriveFont(Font.BOLD, 14), BestDpsTheme.TEXT), BorderLayout.NORTH);
		message.add(text(detail, BestDpsTheme.BODY, BestDpsTheme.MUTED), BorderLayout.CENTER);
		GridBagConstraints c = new GridBagConstraints();
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weighty = 1;
		c.anchor = GridBagConstraints.NORTHWEST;
		c.insets = new Insets(12, 8, 8, 8);
		empty.add(message, c);
		cards.show(workspace, "empty");
		empty.revalidate();
		empty.repaint();
	}

	private void showSelected(DpsResult result)
	{
		details.removeAll();
		JPanel summary = panel(new BorderLayout(0, 3));
		JPanel title = panel(new BorderLayout(6, 0));
		JLabel icon = new JLabel(icon(result.getLoadout().getWeapon()));
		icon.setPreferredSize(new Dimension(36, 32));
		title.add(icon, BorderLayout.WEST);
		JPanel names = panel(new BorderLayout(0, 0));
		JLabel weaponLabel = label(weaponName(result), BestDpsTheme.BODY.deriveFont(Font.BOLD, 15), BestDpsTheme.TEXT);
		weaponLabel.setToolTipText(tooltip(weaponName(result), result.getSpellName()));
		names.add(weaponLabel, BorderLayout.NORTH);
		String attack = result.getAttackType() + " · " + result.getAttackSpeed() + " ticks";
		if (!result.getSpellName().isEmpty()) { attack += " · " + result.getSpellName(); }
		JLabel attackLabel = label(attack, BestDpsTheme.SMALL, BestDpsTheme.MUTED);
		attackLabel.setToolTipText(tooltip(weaponName(result), attack));
		names.add(attackLabel, BorderLayout.CENTER);
		title.add(names, BorderLayout.CENTER);
		summary.add(title, BorderLayout.NORTH);
		JPanel metrics = panel(new GridLayout(1, 4, 12, 0));
		metrics.add(metric("DPS", format(result.getDps(), 3), null, true));
		metrics.add(metric("Accuracy", format(result.getAccuracy() * 100, 1) + "%", null, false));
		metrics.add(metric("Max hit", Integer.toString(result.getMaxHit()), null, false));
		metrics.add(metric("Cash needed", compact(cash(result)), gp(cash(result)), false));
		metrics.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				int rows = metrics.getWidth() < 620 ? 2 : 1;
				if (((GridLayout) metrics.getLayout()).getRows() != rows)
				{
					metrics.setLayout(new GridLayout(rows, rows == 1 ? 4 : 2, 12, 0));
					summary.revalidate();
				}
			}
		});
		summary.add(metrics, BorderLayout.CENTER);
		JPanel notes = panel(new BorderLayout(0, 1));
		if (result.getBaselineDps() > 0)
		{
			double percent = (result.getDps() / result.getBaselineDps() - 1) * 100;
			String gain = (percent > 0 ? "+" : "") + format(percent, 2) + "% DPS vs the no-sale search";
			JLabel gainLabel = label(gain, BestDpsTheme.SMALL, result.getDps() > result.getBaselineDps() ? BestDpsTheme.POSITIVE : BestDpsTheme.MUTED);
			gainLabel.setToolTipText("Same owned items and cash budget, with selling disabled. This is not a comparison with currently worn gear.");
			notes.add(gainLabel, BorderLayout.NORTH);
		}
		notes.add(coverageLabel(result), BorderLayout.SOUTH);
		summary.add(notes, BorderLayout.SOUTH);
		details.add(summary, BorderLayout.NORTH);
		JTabbedPane tabs = new JTabbedPane();
		tabs.setFont(BestDpsTheme.BODY);
		tabs.setForeground(BestDpsTheme.TEXT);
		tabs.setBackground(BestDpsTheme.PANEL);
		tabs.setUI(new ResultTabsUI());
		tabs.setMinimumSize(new Dimension(0, 0));
		tabs.addTab("Equipment", scroll(new EquipmentContent(result)));
		tabs.addTab("Trade plan", scroll(tradeContent(result)));
		tabs.addTab("Assumptions", scroll(assumptionsContent(result)));
		tabs.setSelectedIndex(Math.min(selectedTab, tabs.getTabCount() - 1));
		tabs.addChangeListener(event -> selectedTab = tabs.getSelectedIndex());
		details.add(tabs, BorderLayout.CENTER);
		details.revalidate();
		details.repaint();
	}

	private JPanel metric(String title, String value, String tooltip, boolean primary)
	{
		JPanel metric = panel(new BorderLayout(5, 0));
		metric.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
		metric.add(label(title, BestDpsTheme.SMALL, BestDpsTheme.MUTED), BorderLayout.WEST);
		JLabel number = label(value, BestDpsTheme.DATA.deriveFont(primary ? 18f : 15f), primary ? BestDpsTheme.ACCENT : BestDpsTheme.TEXT);
		number.setToolTipText(tooltip);
		number.setHorizontalAlignment(SwingConstants.RIGHT);
		metric.add(number, BorderLayout.CENTER);
		return metric;
	}

	private JLabel coverageLabel(DpsResult result)
	{
		List<String> warnings = new ArrayList<>();
		List<String> encounter = EncounterRules.warnings(request.getMonster(), request.getEncounterContext());
		for (String warning : encounter)
		{
			if (!warning.startsWith("Sustained attacks in the selected phase;")) { warnings.add(warning); }
		}
		warnings.addAll(CombatMechanics.warnings(result.getLoadout()));
		String warning = warnings.isEmpty() ? "Normal attacks only; movement, mechanics and specials excluded." : warnings.get(0);
		String prefix = warnings.isEmpty() ? "Model limits: " : "Model limits (" + warnings.size() + "): ";
		JLabel coverage = label(prefix + warning, BestDpsTheme.SMALL, BestDpsTheme.ACCENT);
		StringBuilder tooltip = new StringBuilder("<html><b>Model limits · full details in Assumptions</b>");
		for (String item : warnings) { tooltip.append("<br>").append(escape(item)); }
		tooltip.append("<br>").append(escape(encounter.isEmpty() ? warning : encounter.get(0))).append("</html>");
		coverage.setToolTipText(tooltip.toString());
		coverage.getAccessibleContext().setAccessibleDescription("Full combat and encounter limits are listed in the Assumptions tab.");
		return coverage;
	}

	private ScrollContent tradeContent(DpsResult result)
	{
		ScrollContent content = new ScrollContent();
		TradePlan plan = result.getTradePlan();
		if (plan == null)
		{
			addSection(content, "No trade breakdown", "This result has no purchase or sale plan. The displayed cash estimate is " + gp(result.getPurchaseCost()) + ".");
			return content;
		}
		JPanel totals = panel(new GridLayout(1, 3, 10, 0));
		totals.add(metric("Buy total", compact(plan.getBuyCost()), gp(plan.getBuyCost()), false));
		totals.add(metric("Sell net", compact(plan.getNetProceeds()), gp(plan.getNetProceeds()) + " after tax reserve", false));
		totals.add(metric("Cash needed", compact(plan.getCashNeeded()), gp(plan.getCashNeeded()), false));
		content.addBlock(totals);
		addSection(content, "Manual trade suggestions", "Prices are estimates, not guaranteed offers. Selling is " + (request.isSellOwnedItems() ? "enabled for this search." : "off for this search.") + " No trades are placed by this plugin.");
		tradeGroup(content, "Buy", plan.getPurchases(), false);
		tradeGroup(content, "Sell", plan.getSales(), true);
		if (!plan.getSales().isEmpty())
		{
			addSection(content, "Sale values", "Net sale values already include a conservative 2% tax reserve, capped at 5,000,000 GP per item, and a price buffer. Selected gear is retained; only the listed quantities are suggested for sale.");
		}
		addSection(content, "Supply costs", "Equipment prices exclude ongoing runes, ammunition, scales, charges and repair costs. Ammo rows price the displayed quantity, not supplies for a whole trip.");
		return content;
	}

	private void tradeGroup(ScrollContent content, String title, List<TradePlan.Entry> entries, boolean sale)
	{
		content.addBlock(sectionLabel(title + " · " + entries.size() + (entries.size() == 1 ? " item" : " items")));
		if (entries.isEmpty())
		{
			content.addBlock(text(sale ? "No owned items need to be sold." : "No equipment purchases needed.", BestDpsTheme.BODY, BestDpsTheme.MUTED));
			return;
		}
		for (TradePlan.Entry entry : entries)
		{
			String value = gp(entry.getUnitPrice()) + (sale ? " net each" : " each") + (entry.getItem().hasLivePrice() ? "" : " · estimate");
			content.addBlock(itemRow(entry.getItem(), integers.format(entry.getQuantity()) + " × " + entry.getItem().getName(), value, compact(entry.getTotal()), gp(entry.getTotal())));
		}
	}

	private ScrollContent assumptionsContent(DpsResult result)
	{
		ScrollContent content = new ScrollContent();
		addSection(content, "Search snapshot", request.getMonster().label() + " · " + request.getCandidateMode() + " · " + gp(request.getBudget()) + " cash budget.");
		OwnedItems owned = request.getOwnedItems();
		addSection(content, "Owned gear", integers.format(owned.size()) + " distinct item IDs in this search. " + (owned.isBankScanned() ? "Includes the saved bank scan." : "The bank has not been scanned; only captured inventory and equipment are known.") + " Ownership markers use this snapshot.");
		PlayerLevels levels = request.getLevels();
		if (levels != null)
		{
			addSection(content, "Levels used", "Attack " + levels.getAttack() + " · Strength " + levels.getStrength() + " · Defence " + levels.getDefence() + " · Ranged " + levels.getRanged() + " · Magic " + levels.getMagic() + ". Boosts selected for this search are included.");
		}
		CombatContext combat = request.getCombatContext();
		addSection(content, "Combat state", "Player HP " + combat.getCurrentHp() + "/" + combat.getMaxHp() + " · Soulreaper stacks " + combat.getSoulreaperStacks() + ". Slayer task: " + yesNo(request.isOnSlayerTask()) + ". Mark of Darkness: " + yesNo(combat.isMarkOfDarkness()) + ". Kandarin diary: " + yesNo(combat.isKandarinDiary()) + ". Wilderness: " + yesNo(combat.isWilderness()) + ".");
		EncounterContext encounter = request.getEncounterContext();
		addSection(content, "Encounter state", "Target HP " + encounter.getTargetHpPercent() + "%. Raid level " + encounter.getRaidLevel() + " · party " + encounter.getPartySize() + " · path level " + encounter.getPathLevel() + ". Challenge mode: " + yesNo(encounter.isChallengeMode()) + ". Defence override: " + (encounter.getDefenceOverride() < 0 ? "none" : encounter.getDefenceOverride()) + ". Raid settings apply only to matching encounters.");
		Set<String> warnings = new LinkedHashSet<>(EncounterRules.warnings(request.getMonster(), encounter));
		warnings.addAll(CombatMechanics.warnings(result.getLoadout()));
		warnings.add("Fast search reports the best setup found from a bounded search, not an exhaustive proof of optimal gear.");
		warnings.add("DPS assumes continuous attacks in the selected NPC phase. Movement, eating, switches and encounter downtime can change real fight results.");
		for (String warning : warnings) { addSection(content, null, warning); }
		return content;
	}

	private void addSection(ScrollContent content, String title, String body)
	{
		JPanel section = panel(new BorderLayout(0, 6));
		section.setBorder(BorderFactory.createEmptyBorder(6, 0, 8, 0));
		if (title != null) { section.add(sectionLabel(title), BorderLayout.NORTH); }
		section.add(text(body, BestDpsTheme.BODY, BestDpsTheme.MUTED), BorderLayout.CENTER);
		content.addBlock(section);
	}

	private JPanel itemRow(GearItem item, String name, String note, String amount, String amountTooltip)
	{
		JPanel row = panel(new BorderLayout(8, 0));
		row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BestDpsTheme.LINE));
		JLabel icon = new JLabel(icon(item));
		icon.setPreferredSize(new Dimension(36, 32));
		row.add(icon, BorderLayout.WEST);
		JPanel names = panel(new BorderLayout(0, 0));
		JLabel nameLabel = label(name, BestDpsTheme.BODY, BestDpsTheme.TEXT);
		nameLabel.setToolTipText(tooltip(name, note));
		names.add(nameLabel, BorderLayout.NORTH);
		JLabel noteLabel = label(note, BestDpsTheme.SMALL, BestDpsTheme.MUTED);
		noteLabel.setToolTipText(tooltip(name, note));
		names.add(noteLabel, BorderLayout.SOUTH);
		names.setMinimumSize(new Dimension(0, 0));
		row.add(names, BorderLayout.CENTER);
		JLabel value = label(amount, BestDpsTheme.DATA.deriveFont(11f), BestDpsTheme.MUTED);
		value.setToolTipText(amountTooltip);
		row.add(value, BorderLayout.EAST);
		row.setMinimumSize(new Dimension(0, 33));
		return row;
	}

	private ImageIcon icon(GearItem item)
	{
		if (item == null || itemManager == null) { return null; }
		return icons.computeIfAbsent(item.getId(), id -> {
			AsyncBufferedImage image = itemManager.getImage(id);
			image.onLoaded(() -> SwingUtilities.invokeLater(this::repaint));
			return new ImageIcon(image);
		});
	}

	private String ownership(GearItem item)
	{
		if (item == null) { return "Empty"; }
		int quantity = request.getOwnedItems().getQuantity(item.getId());
		return quantity > 0 ? "Owned × " + integers.format(quantity) : "Not owned";
	}

	private String price(GearItem item)
	{
		if (item == null) { return ""; }
		if (!item.isTradeable()) { return "Untradeable"; }
		return item.getPriceOrZero() > 0 ? gp(item.getPriceOrZero()) + (item.hasLivePrice() ? "" : " · estimate") : "Price unavailable";
	}

	private static String weaponName(DpsResult result)
	{
		GearItem weapon = result.getLoadout().getWeapon();
		return weapon == null ? (result.getSpellName().isEmpty() ? "Unarmed" : result.getSpellName()) : weapon.getName();
	}

	private static long cash(DpsResult result)
	{
		return result.getTradePlan() == null ? result.getPurchaseCost() : result.getTradePlan().getCashNeeded();
	}

	private String gp(long value) { return integers.format(value) + " GP"; }
	private static String format(double number, int digits) { return String.format(Locale.UK, "%." + digits + "f", number); }
	private static String yesNo(boolean value) { return value ? "on" : "off"; }
	private static String compact(long value)
	{
		if (value >= 1_000_000_000L) { return format(value / 1_000_000_000.0, 2) + "b"; }
		if (value >= 1_000_000) { return format(value / 1_000_000.0, 2) + "m"; }
		if (value >= 10_000) { return format(value / 1000.0, 1) + "k"; }
		return Long.toString(value);
	}

	private static JPanel panel(java.awt.LayoutManager layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setBackground(BestDpsTheme.PANEL);
		panel.setMinimumSize(new Dimension(0, 0));
		return panel;
	}

	private static JLabel label(String text, Font font, Color color)
	{
		JLabel label = new JLabel(text);
		label.putClientProperty("html.disable", true);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	private static JLabel sectionLabel(String text) { return label(text, BestDpsTheme.BODY.deriveFont(Font.BOLD), BestDpsTheme.TEXT); }
	private static JTextArea text(String value, Font font, Color color)
	{
		JTextArea text = new JTextArea(value);
		text.putClientProperty("bestDps.styled", Boolean.TRUE);
		text.setFont(font);
		text.setForeground(color);
		text.setOpaque(false);
		text.setEditable(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setBorder(BorderFactory.createEmptyBorder());
		text.setMinimumSize(new Dimension(0, 0));
		text.setSelectionColor(BestDpsTheme.LINE);
		text.setSelectedTextColor(BestDpsTheme.TEXT);
		return text;
	}

	private static JScrollPane scroll(Component content)
	{
		JScrollPane scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		BestDpsTheme.style(scroll);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setMinimumSize(new Dimension(0, 0));
		return scroll;
	}

	private static String escape(String value)
	{
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private static String tooltip(String title, String detail) { return "<html>" + escape(title) + "<br>" + escape(detail) + "</html>"; }

	private final class RankedRenderer extends JPanel implements ListCellRenderer<DpsResult>
	{
		private boolean focused;
		private final JLabel rank = label("", BestDpsTheme.DATA.deriveFont(11f), BestDpsTheme.MUTED);
		private final JLabel itemIcon = new JLabel();
		private final JLabel name = label("", BestDpsTheme.BODY, BestDpsTheme.TEXT);
		private final JLabel style = label("", BestDpsTheme.SMALL, BestDpsTheme.MUTED);
		private final JLabel dps = label("", BestDpsTheme.DATA.deriveFont(13f), BestDpsTheme.ACCENT);
		private final JLabel cost = label("", BestDpsTheme.DATA.deriveFont(11f), BestDpsTheme.MUTED);

		private RankedRenderer()
		{
			super(new BorderLayout(6, 0));
			setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 5));
			JPanel identity = panel(new BorderLayout(4, 0));
			identity.setOpaque(false);
			rank.setPreferredSize(new Dimension(18, 16));
			itemIcon.setPreferredSize(new Dimension(36, 32));
			identity.add(rank, BorderLayout.WEST);
			identity.add(itemIcon, BorderLayout.CENTER);
			add(identity, BorderLayout.WEST);
			JPanel words = panel(new BorderLayout(0, 0));
			words.setOpaque(false);
			words.add(name, BorderLayout.NORTH);
			words.add(style, BorderLayout.SOUTH);
			add(words, BorderLayout.CENTER);
			JPanel values = panel(new BorderLayout());
			values.setOpaque(false);
			dps.setHorizontalAlignment(SwingConstants.RIGHT);
			cost.setHorizontalAlignment(SwingConstants.RIGHT);
			values.add(dps, BorderLayout.NORTH);
			values.add(cost, BorderLayout.SOUTH);
			add(values, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends DpsResult> list, DpsResult value, int index, boolean selected, boolean cellHasFocus)
		{
			this.focused = cellHasFocus;
			setBackground(selected ? BestDpsTheme.RAISED : BestDpsTheme.PANEL);
			rank.setText(Integer.toString(index + 1));
			itemIcon.setIcon(icon(value.getLoadout().getWeapon()));
			name.setText(weaponName(value));
			style.setText(value.getAttackType() + (value.getSpellName().isEmpty() ? "" : " · " + value.getSpellName()));
			dps.setText(format(value.getDps(), 3) + " DPS");
			dps.setToolTipText("Damage per second");
			cost.setText(compact(cash(value)) + " GP");
			setToolTipText(tooltip(weaponName(value), format(value.getDps(), 3) + " DPS · " + gp(cash(value)) + " cash needed"));
			getAccessibleContext().setAccessibleName("Rank " + (index + 1) + ", " + weaponName(value) + ", " + value.getAttackType() + ", " + format(value.getDps(), 3) + " DPS, " + gp(cash(value)) + " cash needed");
			return this;
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			graphics.setColor(BestDpsTheme.LINE);
			graphics.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
			if (focused)
			{
				graphics.setColor(BestDpsTheme.ACCENT);
				graphics.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
			}
		}
	}

	private final class EquipmentContent extends ScrollContent
	{
		private final JPanel figure;
		private final JPanel rows;
		private boolean sideBySide;

		private EquipmentContent(DpsResult result)
		{
			setLayout(new GridBagLayout());
			figure = panel(new BorderLayout(0, 4));
			JPanel figureCenter = panel(new GridBagLayout());
			figureCenter.add(new EquipmentFigure(result.getLoadout()));
			figure.add(figureCenter, BorderLayout.NORTH);
			figure.add(text("Orange label: owned. Hover for item details.", BestDpsTheme.SMALL, BestDpsTheme.MUTED), BorderLayout.SOUTH);
			rows = panel(new GridLayout(0, 1));
			for (GearSlot slot : GearSlot.values())
			{
				GearItem item = result.getLoadout().get(slot);
				String slotName = slotName(slot, result.getLoadout());
				String note = slotName + " · " + ownership(item);
				String amount = item == null ? "" : !item.isTradeable() ? "—" : item.getPriceOrZero() <= 0 ? "?" : compact(item.getPriceOrZero());
				rows.add(itemRow(item, item == null ? "Empty" : item.getName(), note, amount, price(item)));
			}
			arrange(false);
			addComponentListener(new ComponentAdapter()
			{
				@Override
				public void componentResized(ComponentEvent event)
				{
					boolean wide = getWidth() >= 530;
					if (wide != sideBySide) { arrange(wide); }
				}
			});
		}

		private void arrange(boolean wide)
		{
			sideBySide = wide;
			removeAll();
			GridBagConstraints c = new GridBagConstraints();
			c.anchor = GridBagConstraints.NORTH;
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridx = 0;
			c.gridy = 0;
			c.weightx = wide ? 0 : 1;
			c.insets = new Insets(0, 0, 16, wide ? 16 : 0);
			figure.setPreferredSize(new Dimension(210, 300));
			add(figure, c);
			c.gridx = wide ? 1 : 0;
			c.gridy = wide ? 0 : 1;
			c.weightx = 1;
			c.insets = new Insets(0, 0, 0, 0);
			add(rows, c);
			c.gridx = 0;
			c.gridy = wide ? 1 : 2;
			c.gridwidth = 2;
			c.weighty = 1;
			add(Box.createVerticalGlue(), c);
			revalidate();
		}
	}

	private final class EquipmentFigure extends JPanel
	{
		private EquipmentFigure(Loadout loadout)
		{
			super(null);
			setBackground(BestDpsTheme.PANEL);
			setPreferredSize(new Dimension(210, 264));
			setMinimumSize(new Dimension(210, 264));
			getAccessibleContext().setAccessibleName("Equipment slots; full item details in the adjacent list");
			GearSlot[] slots = {GearSlot.HEAD, GearSlot.CAPE, GearSlot.NECK, GearSlot.AMMO, GearSlot.WEAPON, GearSlot.BODY, GearSlot.SHIELD, GearSlot.LEGS, GearSlot.HANDS, GearSlot.FEET, GearSlot.RING};
			int[][] positions = {{82, 5}, {29, 56}, {82, 56}, {135, 56}, {0, 107}, {82, 107}, {164, 107}, {82, 158}, {29, 209}, {82, 209}, {135, 209}};
			for (int i = 0; i < slots.length; i++)
			{
				GearItem item = loadout.get(slots[i]);
				boolean owned = item != null && request.getOwnedItems().owns(item.getId());
				JPanel tile = panel(new BorderLayout(0, 0));
				tile.setBackground(BestDpsTheme.BG);
				tile.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED, BestDpsTheme.RAISED.brighter(), BestDpsTheme.RAISED, BestDpsTheme.BG, BestDpsTheme.BG.darker()));
				tile.setBounds(positions[i][0], positions[i][1], 46, 48);
				JLabel image = new JLabel(icon(item), SwingConstants.CENTER);
				String title = slotName(slots[i], loadout);
				String itemName = item == null ? "Empty" : item.getName();
				String tip = tooltip(title + ": " + itemName, ownership(item) + " in the search snapshot" + (item == null ? "" : " · " + price(item)));
				image.setToolTipText(tip);
				image.getAccessibleContext().setAccessibleName(title + ": " + itemName + ", " + ownership(item));
				tile.add(image, BorderLayout.CENTER);
				JLabel caption = label(slots[i] == GearSlot.AMMO && title.equals("Loaded darts") ? "Darts" : title, BestDpsTheme.SMALL.deriveFont(10f), owned ? BestDpsTheme.ACCENT : BestDpsTheme.MUTED);
				caption.setHorizontalAlignment(SwingConstants.CENTER);
				caption.setToolTipText(tip);
				tile.add(caption, BorderLayout.SOUTH);
				add(tile);
			}
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(BestDpsTheme.LINE);
			g.setStroke(new BasicStroke(1f));
			g.drawLine(105, 28, 105, 232);
			g.drawLine(52, 79, 158, 79);
			g.drawLine(23, 130, 187, 130);
			g.drawLine(52, 232, 158, 232);
			g.dispose();
		}
	}

	private static String slotName(GearSlot slot, Loadout loadout)
	{
		if (slot == GearSlot.AMMO && loadout.getWeapon() != null && loadout.getWeapon().getNormalizedName().contains("blowpipe")) { return "Loaded darts"; }
		String name = slot.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private static class ScrollContent extends JPanel implements Scrollable
	{
		private ScrollContent()
		{
			setLayout(new WrappingColumn());
			setBackground(BestDpsTheme.PANEL);
			setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 8));
			setMinimumSize(new Dimension(0, 0));
		}

		private void addBlock(JComponent component)
		{
			component.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(component);
			add(Box.createVerticalStrut(8));
		}

		@Override
		public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 18; }
		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(18, visible.height - 36); }
		@Override
		public boolean getScrollableTracksViewportWidth() { return true; }
		@Override
		public boolean getScrollableTracksViewportHeight() { return false; }
	}

	/** Measure wrapped paragraphs at the viewport width, so resizing cannot clip their last lines. */
	private static final class WrappingColumn implements LayoutManager
	{
		@Override
		public void addLayoutComponent(String name, Component component) { }
		@Override
		public void removeLayoutComponent(Component component) { }
		@Override
		public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, 0); }
		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			Insets insets = parent.getInsets();
			int width = Math.max(1, (parent.getWidth() > 0 ? parent.getWidth() : 440) - insets.left - insets.right);
			int height = insets.top + insets.bottom;
			for (Component child : parent.getComponents())
			{
				if (child.isVisible()) { height += heightAtWidth(child, width); }
			}
			return new Dimension(width + insets.left + insets.right, height);
		}
		@Override
		public void layoutContainer(Container parent)
		{
			Insets insets = parent.getInsets();
			int width = Math.max(1, parent.getWidth() - insets.left - insets.right);
			int y = insets.top;
			for (Component child : parent.getComponents())
			{
				if (!child.isVisible()) { continue; }
				int height = heightAtWidth(child, width);
				child.setBounds(insets.left, y, width, height);
				y += height;
			}
		}
		private static int heightAtWidth(Component child, int width)
		{
			child.setSize(width, Math.max(1, child.getPreferredSize().height));
			if (child instanceof Container) { ((Container) child).doLayout(); }
			return child.getPreferredSize().height;
		}
	}

	private static final class ResultTabsUI extends BasicTabbedPaneUI
	{
		@Override
		protected void installDefaults()
		{
			super.installDefaults();
			tabInsets = new Insets(5, 10, 5, 10);
			contentBorderInsets = new Insets(1, 0, 0, 0);
			tabAreaInsets = new Insets(0, 0, 0, 0);
			selectedTabPadInsets = new Insets(0, 0, 0, 0);
		}
		@Override
		protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int width, int height, boolean selected)
		{
			g.setColor(selected ? BestDpsTheme.RAISED : BestDpsTheme.PANEL);
			g.fillRect(x, y, width, height);
		}
		@Override
		protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int width, int height, boolean selected)
		{
			g.setColor(BestDpsTheme.LINE);
			g.drawLine(x, y, x + width - 1, y);
			g.drawLine(x, y, x, y + height - 1);
			g.setColor(BestDpsTheme.BG.darker());
			g.drawLine(x + width - 1, y, x + width - 1, y + height - 1);
			if (!selected) { g.drawLine(x, y + height - 1, x + width - 1, y + height - 1); }
		}
		@Override
		protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rectangles, int index, Rectangle icon, Rectangle text, boolean selected)
		{
			if (tabPane.hasFocus() && selected)
			{
				Rectangle r = rectangles[index];
				g.setColor(BestDpsTheme.ACCENT);
				g.drawRect(r.x + 2, r.y + 2, r.width - 5, r.height - 5);
			}
		}
		@Override
		protected void paintContentBorder(Graphics g, int placement, int selected)
		{
			g.setColor(BestDpsTheme.LINE);
			int y = calculateTabAreaHeight(placement, runCount, maxTabHeight);
			g.drawLine(0, y, tabPane.getWidth(), y);
		}
	}
}
