package com.bestdps;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
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
		GraphicsConfiguration screen = owner == null ? getGraphicsConfiguration() : owner.getGraphicsConfiguration();
		Rectangle usable = new Rectangle(screen.getBounds());
		Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(screen);
		usable.x += insets.left;
		usable.y += insets.top;
		usable.width -= insets.left + insets.right;
		usable.height -= insets.top + insets.bottom;
		setMinimumSize(new Dimension(Math.min(1040, usable.width), Math.min(700, usable.height)));
		setPreferredSize(new Dimension(Math.min(1280, usable.width), Math.min(820, usable.height)));
		setBackground(BestDpsTheme.BG);
		getContentPane().setBackground(BestDpsTheme.BG);
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
		setLocation(usable.x + (usable.width - getWidth()) / 2, usable.y + (usable.height - getHeight()) / 2);
	}
}
