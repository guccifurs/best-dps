package com.bestdps;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

final class BestDpsLauncherPanel extends PluginPanel
{
	private final JLabel bankLabel = new JLabel("Bank not scanned");

	BestDpsLauncherPanel(BestDpsPlugin plugin)
	{
		super(false);
		setLayout(new BorderLayout(0, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel card = new JPanel(new BorderLayout(0, 8));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(45, 55, 72)),
			new EmptyBorder(12, 12, 12, 12)));

		JLabel title = new JLabel("Best DPS");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		card.add(title, BorderLayout.NORTH);

		JLabel hint = new JLabel("<html>Open the full optimizer window for gear search, results, and loadouts.</html>");
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hint.setFont(FontManager.getRunescapeSmallFont());
		card.add(hint, BorderLayout.CENTER);

		JButton open = new JButton("Open optimizer");
		open.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 36, 30));
		open.setFocusPainted(false);
		open.addActionListener(e -> plugin.openWindowFromUi());
		card.add(open, BorderLayout.SOUTH);

		bankLabel.setForeground(new Color(250, 204, 21));
		bankLabel.setFont(FontManager.getRunescapeSmallFont());

		add(card, BorderLayout.NORTH);
		add(bankLabel, BorderLayout.SOUTH);
	}

	void updateBankStatus(boolean bankScanned)
	{
		if (bankScanned)
		{
			bankLabel.setText("Bank scanned");
			bankLabel.setForeground(new Color(94, 234, 212));
		}
		else
		{
			bankLabel.setText("Bank not scanned");
			bankLabel.setForeground(new Color(250, 204, 21));
		}
	}
}
