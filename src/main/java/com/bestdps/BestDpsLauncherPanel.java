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

	BestDpsLauncherPanel(BestDpsPlugin plugin)
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(BestDpsTheme.BG);
		setBorder(new EmptyBorder(10, 10, 12, 10));

		JPanel content = BestDpsTheme.panel(null);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED,
				BestDpsTheme.LINE, BestDpsTheme.RAISED, BestDpsTheme.BG, BestDpsTheme.BG),
			new EmptyBorder(10, 10, 10, 10)));

		JLabel title = BestDpsTheme.label("Best DPS");
		title.setFont(BestDpsTheme.TITLE);
		title.setForeground(BestDpsTheme.ACCENT);
		addRow(content, title);
		content.add(Box.createVerticalStrut(12));

		JPanel bank = BestDpsTheme.panel(null);
		bank.setLayout(new BoxLayout(bank, BoxLayout.Y_AXIS));
		bankLabel.setFont(BestDpsTheme.BODY.deriveFont(Font.BOLD));
		addRow(bank, bankLabel);
		bank.add(Box.createVerticalStrut(4));
		addRow(bank, bankDetail);
		addRow(content, bank);
		content.add(Box.createVerticalStrut(12));

		JButton open = BestDpsTheme.button("Open optimizer", true);
		open.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 44, 31));
		open.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));
		open.setToolTipText("Open the gear calculator");
		open.addActionListener(event -> plugin.openWindowFromUi());
		addRow(content, open);
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
			bankLabel.setText(fresh ? "Bank read" : "Saved bank");
			bankLabel.setForeground(fresh ? BestDpsTheme.POSITIVE : BestDpsTheme.ACCENT);
			bankDetail.setText(observedItems < 0 ? "Owned gear included" : observedItems + " item types observed");
			String lastRead = updatedAt > 0 ? "Last bank read: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(updatedAt)) + ". " : "";
			bankLabel.setToolTipText(lastRead + "Open your bank to refresh quantities.");
			bankDetail.setToolTipText(bankLabel.getToolTipText());
		}
		else
		{
			bankLabel.setText("Bank not read");
			bankLabel.setForeground(BestDpsTheme.ACCENT);
			bankDetail.setText(wrap("Open your bank to include stored gear."));
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
		return "<html><body style='width:" + (PluginPanel.PANEL_WIDTH - 52) + "px'>" + text + "</body></html>";
	}
}
