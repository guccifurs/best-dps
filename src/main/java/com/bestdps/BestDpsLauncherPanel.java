package com.bestdps;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.text.DateFormat;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

final class BestDpsLauncherPanel extends PluginPanel
{
	private final JLabel bankLabel = BestDpsTheme.label("Bank not read");
	private final JLabel bankDetail = BestDpsTheme.muted("");
	private final JLabel bankHelp = BestDpsTheme.muted("");

	BestDpsLauncherPanel(BestDpsPlugin plugin)
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(BestDpsTheme.BG);
		setBorder(new EmptyBorder(20, 14, 18, 14));

		JPanel content = BestDpsTheme.panel(null);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(BestDpsTheme.BG);

		JLabel title = BestDpsTheme.label("Best DPS");
		title.setFont(BestDpsTheme.TITLE);
		title.setForeground(BestDpsTheme.ACCENT);
		addRow(content, title);
		content.add(Box.createVerticalStrut(4));
		addRow(content, BestDpsTheme.muted("Equipment workbench"));
		content.add(Box.createVerticalStrut(20));
		JLabel description = BestDpsTheme.label(wrap("Find gear for your target, using what you own and what you can spend."));
		addRow(content, description);
		content.add(Box.createVerticalStrut(18));

		JButton open = BestDpsTheme.button("Open optimizer", true);
		open.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 28, 42));
		open.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
		open.setToolTipText("Open targets, gear search and loadout comparison in a separate window");
		open.addActionListener(event -> plugin.openWindowFromUi());
		addRow(content, open);
		content.add(Box.createVerticalStrut(24));

		JPanel bank = BestDpsTheme.panel(null);
		bank.setLayout(new BoxLayout(bank, BoxLayout.Y_AXIS));
		bank.setBackground(BestDpsTheme.BG);
		bank.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, BestDpsTheme.LINE),
			new EmptyBorder(16, 0, 0, 0)));
		bankLabel.setFont(BestDpsTheme.BODY.deriveFont(Font.BOLD));
		addRow(bank, bankLabel);
		bank.add(Box.createVerticalStrut(6));
		addRow(bank, bankDetail);
		bank.add(Box.createVerticalStrut(8));
		addRow(bank, bankHelp);
		addRow(content, bank);
		add(content, BorderLayout.NORTH);
		updateBankStatus(false);
	}

	void updateBankStatus(boolean bankScanned)
	{
		updateBankStatus(bankScanned, -1, false, 0);
	}

	void updateBankStatus(boolean bankScanned, int observedItems, boolean fresh, long updatedAt)
	{
		if (bankScanned)
		{
			bankLabel.setText(fresh ? "Bank ready" : "Saved bank available");
			bankLabel.setForeground(fresh ? BestDpsTheme.POSITIVE : BestDpsTheme.ACCENT);
			bankDetail.setText(observedItems < 0 ? "Owned gear included" : observedItems + " observed item types");
			bankHelp.setText(wrap(fresh ? "Reopen your bank after changing its contents." : "Open your bank to refresh saved quantities."));
			String lastRead = updatedAt > 0 ? "Last bank read: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(updatedAt)) : "";
			bankLabel.setToolTipText(lastRead.isEmpty() ? null : lastRead);
			bankDetail.setToolTipText(lastRead.isEmpty() ? null : lastRead);
		}
		else
		{
			bankLabel.setText("Bank not read");
			bankLabel.setForeground(BestDpsTheme.ACCENT);
			bankDetail.setText("Banked gear is not included yet");
			bankHelp.setText(wrap("Log in and open your bank once to include your stored gear."));
			bankLabel.setToolTipText(null);
			bankDetail.setToolTipText(null);
		}
		revalidate();
		repaint();
	}

	private static void addRow(JPanel panel, JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(component);
	}

	private static String wrap(String text)
	{
		return "<html><body style='width:" + (PluginPanel.PANEL_WIDTH - 36) + "px'>" + text + "</body></html>";
	}
}
