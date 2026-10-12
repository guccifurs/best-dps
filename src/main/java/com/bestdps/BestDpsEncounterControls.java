package com.bestdps;

import com.bestdps.calc.CombatContext;
import com.bestdps.calc.CombatStyle;
import com.bestdps.calc.EncounterContext;
import com.bestdps.calc.EncounterRules;
import com.bestdps.calc.ProfileSnapshot;
import com.bestdps.data.MonsterStats;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.List;
import java.text.ParseException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

/** Compact assumption controls; their parent owns the scroll pane and starts searches. */
final class BestDpsEncounterControls extends JPanel
{
	private static final int CONTENT_WIDTH = 214;
	private final JSpinner raidLevel = spinner(0, 0, 600, 5);
	private final JSpinner partySize = spinner(1, 1, 100, 1);
	private final JSpinner pathLevel = spinner(0, 0, 6, 1);
	private final JCheckBox challengeBox = checkbox("Challenge mode", false);
	private final JSpinner partyCombat = spinner(126, 3, 126, 1);
	private final JSpinner partyHp = spinner(99, 10, 99, 1);
	private final JSpinner targetHp = spinner(100, 1, 100, 5);
	private final JSpinner defenceOverride = spinner(-1, -1, 2000, 1);
	private final JCheckBox allRaidSettings = checkbox("Show all raid settings", false);
	private final JCheckBox liveHpBox = checkbox("Use live HP", true);
	private final JSpinner playerHp = spinner(99, 1, 126, 1);
	private final JSpinner maxPlayerHp = spinner(99, 10, 99, 1);
	private final JSpinner soulStacks = spinner(0, 0, 5, 1);
	private final JCheckBox markBox = checkbox("Mark of Darkness active", false);
	private final JCheckBox diaryBox = checkbox("Kandarin hard diary", false);
	private final JCheckBox wildernessBox = checkbox("In the Wilderness", false);
	private final JPanel invocationRow = field("ToA invocation", raidLevel);
	private final JPanel partyRow = field("Party size", partySize);
	private final JPanel pathRow = field("ToA path level", pathLevel);
	private final JPanel combatRow = field("CoX highest combat", partyCombat);
	private final JPanel partyHpRow = field("CoX highest HP", partyHp);
	private final JLabel encounterSummary = BestDpsTheme.muted("Solo · 100% HP");
	private final JLabel effectsSummary = BestDpsTheme.muted("99 / 99 HP · 0 effects");
	private final JLabel phaseNote = BestDpsTheme.muted("");
	private MonsterStats target;
	private CombatStyle style = CombatStyle.ANY;
	private ProfileSnapshot profile;
	private boolean useMyLevels;
	private boolean updating;
	private Runnable changeCallback = () -> { };

	BestDpsEncounterControls()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(BestDpsTheme.BG);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMinimumSize(new Dimension(0, 0));

		JPanel encounter = stack();
		for (JPanel row : new JPanel[]{invocationRow, partyRow, pathRow, combatRow, partyHpRow})
		{
			encounter.add(row);
		}
		encounter.add(challengeBox);
		encounter.add(field("Target HP %", targetHp));
		encounter.add(field("Current Defence", defenceOverride));
		defenceOverride.setToolTipText("-1 uses normal Defence. Enter remaining Defence after drains, not the reduction amount.");
		encounter.add(note("Defence: -1 = normal level."));
		encounter.add(allRaidSettings);
		phaseNote.setAlignmentX(Component.LEFT_ALIGNMENT);
		phaseNote.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
		encounter.add(phaseNote);
		encounter.add(Box.createVerticalStrut(4));

		JPanel effects = stack();
		effects.add(liveHpBox);
		effects.add(field("Current HP", playerHp));
		effects.add(field("Base HP", maxPlayerHp));
		effects.add(field("Soulreaper stacks", soulStacks));
		effects.add(markBox);
		effects.add(diaryBox);
		effects.add(wildernessBox);
		effects.add(note("Set effects for this phase. Charged gear assumes usable charges."));
		effects.add(Box.createVerticalStrut(8));
		diaryBox.setToolTipText("Kandarin hard diary complete: increases enchanted bolt activation chance.");
		liveHpBox.setToolTipText("Uses your current in-game HP when My levels & unlocks is enabled.");

		add(disclosure("Encounter settings", encounterSummary, encounter));
		add(Box.createVerticalStrut(8));
		add(disclosure("Combat effects", effectsSummary, effects));

		for (JSpinner input : new JSpinner[]{raidLevel, partySize, pathLevel, partyCombat, partyHp,
			targetHp, defenceOverride, playerHp, maxPlayerHp, soulStacks})
		{
			input.addChangeListener(event -> changed());
		}
		for (JCheckBox input : new JCheckBox[]{challengeBox, markBox, diaryBox, wildernessBox})
		{
			input.addActionListener(event -> changed());
		}
		liveHpBox.addActionListener(event -> {
			updateHpInputs();
			changed();
		});
		allRaidSettings.addActionListener(event -> updateTargetFields());
		setProfile(null, false);
		setTarget(null, CombatStyle.ANY);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	void commitInputs()
	{
		JSpinner[] inputs = {raidLevel, partySize, pathLevel, partyCombat, partyHp,
			targetHp, defenceOverride, playerHp, maxPlayerHp, soulStacks};
		String[] names = {"ToA invocation", "Party size", "ToA path level", "CoX highest combat", "CoX highest HP",
			"Target HP %", "Current Defence", "Current HP", "Base HP", "Soulreaper stacks"};
		for (int i = 0; i < inputs.length; i++)
		{
			JSpinner input = inputs[i];
			if (!input.isEnabled()) { continue; }
			try
			{
				input.commitEdit();
			}
			catch (ParseException exception)
			{
				SpinnerNumberModel model = (SpinnerNumberModel) input.getModel();
				throw new IllegalArgumentException(names[i] + ": enter " + model.getMinimum()
					+ " to " + model.getMaximum() + ".", exception);
			}
		}
	}

	EncounterContext encounterContext()
	{
		return new EncounterContext(value(raidLevel), value(partySize), value(pathLevel), challengeBox.isSelected(),
			value(partyCombat), value(partyHp), value(targetHp), value(defenceOverride));
	}

	CombatContext combatContext(ProfileSnapshot snapshot, boolean useMyLevels)
	{
		ProfileSnapshot source = snapshot == null ? ProfileSnapshot.MAXED : snapshot;
		int current = liveHpBox.isSelected() && useMyLevels ? source.getCurrentLevels().getHitpoints() : value(playerHp);
		int maximum = useMyLevels ? source.getBaseLevels().getHitpoints() : value(maxPlayerHp);
		return new CombatContext(current, maximum, value(soulStacks), markBox.isSelected(),
			diaryBox.isSelected(), wildernessBox.isSelected());
	}

	void setTarget(MonsterStats monster, CombatStyle style)
	{
		this.target = monster;
		this.style = style == null ? CombatStyle.ANY : style;
		updateTargetFields();
		updatePhaseNote();
		updateSummaries();
	}

	void setProfile(ProfileSnapshot snapshot, boolean useMyLevels)
	{
		this.profile = snapshot;
		this.useMyLevels = useMyLevels;
		updateHpInputs();
		updateSummaries();
	}

	void onChange(Runnable callback)
	{
		changeCallback = callback == null ? () -> { } : callback;
	}

	private void updateHpInputs()
	{
		updating = true;
		try
		{
			liveHpBox.setEnabled(useMyLevels);
			maxPlayerHp.setEnabled(!useMyLevels);
			playerHp.setEnabled(!useMyLevels || !liveHpBox.isSelected());
			if (profile != null && useMyLevels)
			{
				maxPlayerHp.setValue(Math.max(10, Math.min(99, profile.getBaseLevels().getHitpoints())));
				if (liveHpBox.isSelected())
				{
					playerHp.setValue(Math.max(1, Math.min(126, profile.getCurrentLevels().getHitpoints())));
				}
			}
		}
		finally
		{
			updating = false;
		}
	}

	private void updateTargetFields()
	{
		boolean all = allRaidSettings.isSelected() || target == null;
		boolean toa = target != null && EncounterRules.isTombsOfAmascut(target);
		boolean cox = target != null && target.hasAttribute("xerician");
		boolean tob = target != null && EncounterRules.isTheatreOfBlood(target);
		// Explicit access remains available for new NPCs whose raid tags are not yet in the dataset.
		invocationRow.setVisible(all || toa);
		pathRow.setVisible(all || toa);
		partyRow.setVisible(all || toa || cox || tob);
		combatRow.setVisible(all || cox);
		partyHpRow.setVisible(all || cox);
		challengeBox.setVisible(all || cox);
		revalidate();
		repaint();
	}

	private void updatePhaseNote()
	{
		String text = "Choose an NPC to see its phase rules.";
		String tooltip = text;
		boolean blocked = false;
		if (target != null)
		{
			String reason = EncounterRules.blockingReason(target, style);
			List<String> warnings = EncounterRules.warnings(target, encounterContext());
			blocked = !reason.isEmpty();
			text = blocked ? reason : "Selected phase only. Movement and whole-fight switches are excluded.";
			tooltip = String.join("; ", warnings);
		}
		phaseNote.setForeground(blocked ? BestDpsTheme.ACCENT : BestDpsTheme.MUTED);
		phaseNote.setText(wrapped(text, CONTENT_WIDTH - 40));
		phaseNote.setToolTipText(wrapped(tooltip, 320));
	}

	private void updateSummaries()
	{
		String party = value(partySize) == 1 ? "Solo" : value(partySize) + " players";
		String state = party + " · " + value(targetHp) + "% HP";
		if (target != null && EncounterRules.isTombsOfAmascut(target))
		{
			state = value(raidLevel) + " invocation · " + party.toLowerCase(java.util.Locale.ROOT);
		}
		else if (target != null && target.hasAttribute("xerician") && challengeBox.isSelected())
		{
			state = "Challenge · " + party.toLowerCase(java.util.Locale.ROOT);
		}
		encounterSummary.setText(state);
		encounterSummary.setToolTipText(value(targetHp) + "% target HP; "
			+ (value(defenceOverride) < 0 ? "normal Defence" : value(defenceOverride) + " Defence"));
		CombatContext combat = combatContext(profile, useMyLevels);
		int extra = (markBox.isSelected() ? 1 : 0) + (diaryBox.isSelected() ? 1 : 0)
			+ (wildernessBox.isSelected() ? 1 : 0) + (value(soulStacks) > 0 ? 1 : 0);
		effectsSummary.setText(combat.getCurrentHp() + " / " + combat.getMaxHp() + " HP · "
			+ extra + " effect" + (extra == 1 ? "" : "s"));
	}

	private void changed()
	{
		if (updating) { return; }
		updateSummaries();
		updatePhaseNote();
		changeCallback.run();
	}

	private static JPanel disclosure(String title, JLabel summary, JPanel content)
	{
		JPanel section = stack();
		section.setBackground(BestDpsTheme.PANEL);
		section.setOpaque(true);
		section.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BestDpsTheme.LINE),
			BorderFactory.createEmptyBorder(0, 8, 0, 8)));
		JButton header = BestDpsTheme.button("+  " + title, false);
		header.setHorizontalAlignment(SwingConstants.LEFT);
		header.setMargin(new Insets(0, 0, 0, 0));
		header.setBorder(BorderFactory.createEmptyBorder());
		header.setContentAreaFilled(false);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		header.setPreferredSize(new Dimension(CONTENT_WIDTH, 24));
		header.setMinimumSize(new Dimension(0, 24));
		summary.setAlignmentX(Component.LEFT_ALIGNMENT);
		summary.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		summary.setPreferredSize(new Dimension(CONTENT_WIDTH, 16));
		summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		summary.setMinimumSize(new Dimension(0, 16));
		content.setVisible(false);
		header.addActionListener(event -> {
			boolean expanded = !content.isVisible();
			content.setVisible(expanded);
			header.setText((expanded ? "−  " : "+  ") + title);
			section.revalidate();
			section.repaint();
		});
		section.add(header);
		section.add(summary);
		section.add(content);
		return section;
	}

	private static JPanel field(String title, JComponent input)
	{
		JPanel row = BestDpsTheme.panel(new BorderLayout(0, 5));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
		row.add(BestDpsTheme.muted(title), BorderLayout.NORTH);
		row.add(input, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 69));
		return row;
	}

	private static JPanel stack()
	{
		JPanel panel = new JPanel()
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		panel.setBackground(BestDpsTheme.PANEL);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMinimumSize(new Dimension(0, 0));
		return panel;
	}

	private static JLabel note(String text)
	{
		JLabel label = BestDpsTheme.muted(wrapped(text, CONTENT_WIDTH - 28));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
		return label;
	}

	private static JSpinner spinner(int value, int min, int max, int step)
	{
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
		BestDpsTheme.style(spinner);
		spinner.setPreferredSize(new Dimension(CONTENT_WIDTH, 32));
		spinner.setMinimumSize(new Dimension(64, 32));
		spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		return spinner;
	}

	private static JCheckBox checkbox(String text, boolean selected)
	{
		JCheckBox box = new JCheckBox(text, selected);
		BestDpsTheme.style(box);
		box.setOpaque(false);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.setPreferredSize(new Dimension(CONTENT_WIDTH, 32));
		box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		return box;
	}

	private static int value(JSpinner spinner) { return ((Number) spinner.getValue()).intValue(); }

	private static String wrapped(String text, int width)
	{
		String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return "<html><div style='width:" + width + "px'>" + safe + "</div></html>";
	}
}
