/*
 * Copyright (c) 2026, Vani-Lab
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.chunkblazer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;

/**
 * The game's look for the side panel (Swing): stone behind everything, framed sections as
 * dark stone in a bevelled black frame, stone buttons, sunken fields and dropdowns, and the
 * game's orange for grey text.
 */
final class PanelStyle
{
	static final Color DARK_STONE = new Color(46, 40, 31);
	static final Color FIELD = new Color(30, 26, 21);
	static final Color ORANGE = new Color(255, 152, 31);
	static final Color YELLOW = new Color(255, 255, 0);

	private PanelStyle()
	{
	}

	/** A bevelled stone edge: black outside, light top-left, dark bottom-right. */
	static final class StoneBorder extends AbstractBorder
	{
		private final Insets padding;

		StoneBorder(Insets padding)
		{
			this.padding = padding;
		}

		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
		{
			g.setColor(Color.BLACK);
			g.drawRect(x, y, width - 1, height - 1);
			Skin.bevel((java.awt.Graphics2D) g, new java.awt.Rectangle(x + 1, y + 1, width - 2, height - 2), true);
		}

		@Override
		public Insets getBorderInsets(Component c, Insets insets)
		{
			insets.set(3 + padding.top, 3 + padding.left, 3 + padding.bottom, 3 + padding.right);
			return insets;
		}
	}

	/** Restyle {@code root} and everything in it. */
	static void apply(Container root)
	{
		if (root instanceof JComponent)
		{
			style((JComponent) root);
		}
		for (Component child : root.getComponents())
		{
			if (child instanceof Container)
			{
				apply((Container) child);
			}
		}
		// The scroll pane round the panel shows below it.
		if (root.getParent() instanceof JViewport)
		{
			root.getParent().setBackground(new Color(62, 53, 41));
		}
	}

	private static void style(JComponent c)
	{
		if (c instanceof IconTextField)
		{
			c.setBackground(FIELD);
			((IconTextField) c).setHoverBackgroundColor(new Color(44, 38, 30));
		}
		else if (c instanceof JPanel)
		{
			panel((JPanel) c);
		}
		else if (c instanceof JComboBox || c instanceof JTextField)
		{
			if (c instanceof JComboBox)
			{
				// The plain combo, with a stone arrow button, drops the look-and-feel's blue ring.
				((JComboBox<?>) c).setUI(new BasicComboBoxUI()
				{
					@Override
					protected JButton createArrowButton()
					{
						return new BasicArrowButton(BasicArrowButton.SOUTH, new Color(84, 74, 58), Color.BLACK, ORANGE, new Color(120, 108, 88));
					}
				});
			}
			// A plain black edge, like the game's boxes.
			c.setBorder(c instanceof JTextField ? new CompoundBorder(new LineBorder(Color.BLACK), new EmptyBorder(2, 4, 2, 4))
				: new LineBorder(Color.BLACK));
			c.setBackground(FIELD);
			c.setForeground(c instanceof JTextField ? Color.WHITE : ORANGE);
		}
		else if (c instanceof JRadioButton || c instanceof JList)
		{
			c.setOpaque(false);
			c.setForeground(Color.WHITE);
		}
		else if (c instanceof JButton)
		{
			JButton b = (JButton) c;
			Color fg = b.getForeground();
			if (b.isContentAreaFilled())
			{
				b.setBackground(new Color(84, 74, 58));
				b.setBorder(new StoneBorder(new Insets(3, 8, 3, 8)));
				b.setFocusPainted(false);
			}
			// Text links stay red when they warn; everything else is orange.
			b.setForeground(fg != null && fg.getRed() > fg.getGreen() + 30 ? new Color(255, 90, 70) : ORANGE);
		}
		else if (c instanceof JLabel || c instanceof JTextArea)
		{
			c.setForeground(text(c.getForeground()));
		}
	}

	/** Plain backing panels show the stone; framed sections become dark stone in a bevelled frame. */
	private static void panel(JPanel c)
	{
		Color bg = c.getBackground();
		Border border = c.getBorder();
		Border outer = border instanceof CompoundBorder ? ((CompoundBorder) border).getOutsideBorder() : border;
		Border inner = border instanceof CompoundBorder ? ((CompoundBorder) border).getInsideBorder() : null;
		if (outer instanceof LineBorder)
		{
			c.setBorder(new StoneBorder(inner instanceof EmptyBorder ? ((EmptyBorder) inner).getBorderInsets() : new Insets(4, 4, 4, 4)));
			c.setBackground(DARK_STONE);
		}
		else if (bg == null || bg.equals(ColorScheme.DARK_GRAY_COLOR) || bg.getAlpha() < 255)
		{
			c.setOpaque(false);
		}
		else if (bg.equals(ColorScheme.DARKER_GRAY_COLOR))
		{
			c.setBackground(DARK_STONE);
		}
	}

	/** Grey text becomes the game's orange; colours that mean something stay. */
	private static Color text(Color c)
	{
		if (c == null)
		{
			return ORANGE;
		}
		int spread = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue())) - Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));
		return spread < 24 && c.getRed() < 235 && c.getRed() > 90 ? ORANGE : c;
	}
}
