package com.bestdps;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicCheckBoxUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.text.JTextComponent;
import net.runelite.client.ui.FontManager;

/** Component-local styling: the RuneLite client's own look and feel is untouched. */
final class BestDpsTheme
{
	static final Color BG = new Color(0x191C1E);
	static final Color PANEL = new Color(0x212628);
	static final Color RAISED = new Color(0x2A3033);
	static final Color LINE = new Color(0x394246);
	static final Color TEXT = new Color(0xEBEEE7);
	static final Color MUTED = new Color(0xA3B0AA);
	static final Color ACCENT = new Color(0xD8B56C);
	static final Color POSITIVE = new Color(0x8EB9A3);
	static final Color NEGATIVE = new Color(0xDF9A91);
	static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
	static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	static final Font TITLE = FontManager.getRunescapeBoldFont().deriveFont(24f);
	static final Font DATA = new Font(Font.MONOSPACED, Font.PLAIN, 13);
	private static final String STYLED = "bestDps.styled";
	private static final String PRIMARY = "bestDps.primary";

	private BestDpsTheme() { }

	static JPanel panel(LayoutManager layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setBackground(PANEL);
		return panel;
	}

	static JLabel label(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(BODY);
		label.setForeground(TEXT);
		return label;
	}

	static JLabel muted(String text)
	{
		JLabel label = label(text);
		label.setFont(SMALL);
		label.setForeground(MUTED);
		return label;
	}

	static JButton button(String text, boolean primary)
	{
		JButton button = new JButton(text);
		button.putClientProperty(PRIMARY, primary);
		style(button);
		return button;
	}

	/** Apply to a finished component tree; repeated calls also pick up new children. */
	static void style(Component component)
	{
		if (component instanceof JComponent && !Boolean.TRUE.equals(((JComponent) component).getClientProperty(STYLED)))
		{
			JComponent target = (JComponent) component;
			target.putClientProperty(STYLED, Boolean.TRUE);
			if (component instanceof JCheckBox)
			{
				JCheckBox box = (JCheckBox) component;
				box.setUI(new BasicCheckBoxUI());
				box.setFont(BODY);
				box.setForeground(TEXT);
				box.setOpaque(false);
				box.setFocusPainted(false);
				box.setIcon(new CheckIcon());
				box.setIconTextGap(8);
				box.setBorder(new FocusBorder(4, 3));
				box.setBorderPainted(true);
				box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				controlHeight(box);
				watchFocus(box);
			}
			else if (component instanceof JButton)
			{
				JButton button = (JButton) component;
				boolean primary = Boolean.TRUE.equals(button.getClientProperty(PRIMARY));
				button.setUI(new BasicButtonUI()
				{
					@Override
					public void paint(Graphics graphics, JComponent target)
					{
						AbstractButton current = (AbstractButton) target;
						Color fill = primary ? ACCENT : RAISED;
						if (!current.isEnabled()) { fill = RAISED; }
						else if (current.getModel().isPressed()) { fill = primary ? ACCENT.darker() : LINE; }
						else if (current.getModel().isRollover()) { fill = primary ? new Color(0xE5C681) : new Color(0x343D40); }
						graphics.setColor(fill);
						graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
						super.paint(graphics, target);
					}

					@Override
					protected void paintText(Graphics graphics, JComponent target, Rectangle textRect, String text)
					{
						AbstractButton current = (AbstractButton) target;
						graphics.setColor(current.isEnabled() ? current.getForeground() : MUTED);
						BasicGraphicsUtils.drawStringUnderlineCharAt(graphics, text, current.getDisplayedMnemonicIndex(),
							textRect.x, textRect.y + graphics.getFontMetrics().getAscent());
					}
				});
				button.setFont(BODY.deriveFont(Font.BOLD));
				button.setForeground(primary ? BG : TEXT);
				button.setBackground(primary ? ACCENT : RAISED);
				button.setOpaque(false);
				button.setContentAreaFilled(false);
				button.setFocusPainted(false);
				button.setRolloverEnabled(true);
				button.setBorder(new FocusBorder(6, 12));
				button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				controlHeight(button);
				watchFocus(button);
			}
			else if (component instanceof JTextComponent)
			{
				JTextComponent field = (JTextComponent) component;
				field.setFont(BODY);
				field.setBackground(RAISED);
				field.setForeground(TEXT);
				field.setCaretColor(ACCENT);
				field.setSelectionColor(LINE);
				field.setSelectedTextColor(TEXT);
				field.setDisabledTextColor(MUTED);
				field.setBorder(new FocusBorder(6, 9));
				if (component instanceof JTextField) { controlHeight(field); }
				watchFocus(field);
			}
			else if (component instanceof JComboBox)
			{
				styleCombo((JComboBox<?>) component);
			}
			else if (component instanceof JSpinner)
			{
				JSpinner spinner = (JSpinner) component;
				spinner.setUI(new BasicSpinnerUI()
				{
					@Override
					protected Component createNextButton()
					{
						JButton button = arrow(SwingConstants.NORTH);
						installNextButtonListeners(button);
						return button;
					}

					@Override
					protected Component createPreviousButton()
					{
						JButton button = arrow(SwingConstants.SOUTH);
						installPreviousButtonListeners(button);
						return button;
					}
				});
				spinner.setFont(BODY);
				spinner.setBackground(RAISED);
				spinner.setBorder(new FocusBorder(2, 5));
				controlHeight(spinner);
			}
			else if (component instanceof JScrollPane)
			{
				JScrollPane scroll = (JScrollPane) component;
				scroll.setBackground(PANEL);
				scroll.getViewport().setBackground(PANEL);
				scroll.setBorder(BorderFactory.createLineBorder(LINE));
				scroll.getVerticalScrollBar().setUnitIncrement(18);
			}
			else if (component instanceof JScrollBar)
			{
				JScrollBar bar = (JScrollBar) component;
				bar.setUI(new BasicScrollBarUI()
				{
					@Override
					protected void configureScrollBarColors()
					{
						thumbColor = LINE;
						thumbDarkShadowColor = LINE;
						thumbHighlightColor = LINE;
						thumbLightShadowColor = LINE;
						trackColor = BG;
					}

					@Override
					protected JButton createDecreaseButton(int orientation) { return hiddenArrow(); }

					@Override
					protected JButton createIncreaseButton(int orientation) { return hiddenArrow(); }
				});
				bar.setPreferredSize(bar.getOrientation() == SwingConstants.VERTICAL
					? new Dimension(10, 32) : new Dimension(32, 10));
			}
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents()) { style(child); }
		}
		if (component instanceof JSpinner && ((JSpinner) component).getEditor() instanceof JSpinner.DefaultEditor)
		{
			JTextField field = ((JSpinner.DefaultEditor) ((JSpinner) component).getEditor()).getTextField();
			field.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
			field.setFont(DATA);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void styleCombo(JComboBox<?> combo)
	{
		ListCellRenderer original = combo.getRenderer();
		combo.setUI(new BasicComboBoxUI()
		{
			@Override
			protected JButton createArrowButton() { return arrow(SwingConstants.SOUTH); }
		});
		if (original == null) { original = combo.getRenderer(); }
		final ListCellRenderer renderer = original;
		((JComboBox) combo).setRenderer((JList list, Object value, int index, boolean selected, boolean focus) -> {
			Component cell = renderer.getListCellRendererComponent(list, value, index, selected, focus);
			cell.setFont(BODY);
			cell.setForeground(combo.isEnabled() ? TEXT : MUTED);
			cell.setBackground(selected ? LINE : RAISED);
			if (cell instanceof JComponent)
			{
				((JComponent) cell).setOpaque(true);
				((JComponent) cell).setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
			}
			return cell;
		});
		combo.setFont(BODY);
		combo.setBackground(RAISED);
		combo.setForeground(TEXT);
		combo.setBorder(new FocusBorder(0, 0));
		combo.setMaximumRowCount(14);
		controlHeight(combo);
		watchFocus(combo);
	}

	private static JButton arrow(int direction)
	{
		JButton button = new BasicArrowButton(direction, RAISED, RAISED, MUTED, RAISED);
		button.setBorder(BorderFactory.createEmptyBorder());
		button.setFocusable(false);
		button.putClientProperty(STYLED, Boolean.TRUE);
		return button;
	}

	private static JButton hiddenArrow()
	{
		JButton button = new JButton();
		button.setPreferredSize(new Dimension(0, 0));
		button.setMinimumSize(new Dimension(0, 0));
		button.setMaximumSize(new Dimension(0, 0));
		button.putClientProperty(STYLED, Boolean.TRUE);
		return button;
	}

	private static void controlHeight(JComponent component)
	{
		Dimension preferred = component.getPreferredSize();
		component.setPreferredSize(new Dimension(preferred.width, Math.max(32, preferred.height)));
		Dimension minimum = component.getMinimumSize();
		component.setMinimumSize(new Dimension(minimum.width, 32));
	}

	private static void watchFocus(JComponent component)
	{
		component.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent event) { repaintFocus(component); }

			@Override
			public void focusLost(FocusEvent event) { repaintFocus(component); }
		});
	}

	private static void repaintFocus(Component component)
	{
		component.repaint();
		Container parent = component.getParent();
		if (parent != null)
		{
			parent.repaint();
			if (parent.getParent() != null) { parent.getParent().repaint(); }
		}
	}

	private static final class FocusBorder extends AbstractBorder
	{
		private final int vertical;
		private final int horizontal;

		private FocusBorder(int vertical, int horizontal)
		{
			this.vertical = vertical;
			this.horizontal = horizontal;
		}

		@Override
		public Insets getBorderInsets(Component component) { return new Insets(vertical + 1, horizontal + 1, vertical + 1, horizontal + 1); }

		@Override
		public Insets getBorderInsets(Component component, Insets insets)
		{
			insets.set(vertical + 1, horizontal + 1, vertical + 1, horizontal + 1);
			return insets;
		}

		@Override
		public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height)
		{
			Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
			boolean focused = component == focus || focus != null && component instanceof Container
				&& SwingUtilities.isDescendingFrom(focus, (Container) component);
			if (component instanceof JCheckBox && !focused) { return; }
			Graphics2D g = (Graphics2D) graphics.create();
			boolean primary = component instanceof JComponent && Boolean.TRUE.equals(((JComponent) component).getClientProperty(PRIMARY));
			g.setColor(focused ? primary ? TEXT : ACCENT : LINE);
			g.setStroke(new BasicStroke(focused ? 2f : 1f));
			g.drawRect(x + 1, y + 1, Math.max(0, width - 3), Math.max(0, height - 3));
			g.dispose();
		}
	}

	private static final class CheckIcon implements Icon
	{
		@Override
		public int getIconWidth() { return 16; }

		@Override
		public int getIconHeight() { return 16; }

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			AbstractButton button = (AbstractButton) component;
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(button.isSelected() && button.isEnabled() ? ACCENT : RAISED);
			g.fillRect(x + 1, y + 1, 14, 14);
			g.setColor(button.isEnabled() ? MUTED : LINE);
			g.drawRect(x + 1, y + 1, 14, 14);
			if (button.isSelected())
			{
				g.setColor(button.isEnabled() ? BG : MUTED);
				g.setStroke(new BasicStroke(2f));
				g.drawLine(x + 4, y + 8, x + 7, y + 11);
				g.drawLine(x + 7, y + 11, x + 12, y + 5);
			}
			g.dispose();
		}
	}
}
