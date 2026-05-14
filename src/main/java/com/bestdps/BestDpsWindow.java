package com.bestdps;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

final class BestDpsWindow extends JFrame
{
	BestDpsWindow(BestDpsPlugin plugin, BestDpsPanel panel, Window owner)
	{
		super("Best DPS");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(960, 640));
		setPreferredSize(new Dimension(1120, 760));
		setLayout(new BorderLayout());
		add(panel, BorderLayout.CENTER);
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent event)
			{
				plugin.onWindowClosed();
			}
		});
		pack();
		setLocationRelativeTo(owner);
	}
}
