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
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

/**
 * The task-reveal cards: btwinnn's original art ({@code Task_Cards/front_<tier>.png} and
 * {@code back_<tier>.png}, 356 x 200, the tier already painted on the title plate), with the
 * task written on the face's grey panel.
 */
final class CardArt
{
	private static final BufferedImage[] FRONT = new BufferedImage[5];
	private static final BufferedImage[] BACK = new BufferedImage[5];

	static
	{
		for (TaskCardTier tier : TaskCardTier.values())
		{
			String name = tier.name().toLowerCase();
			FRONT[tier.ordinal()] = ImageUtil.loadImageResource(CardArt.class, "Task_Cards/front_" + name + ".png");
			BACK[tier.ordinal()] = ImageUtil.loadImageResource(CardArt.class, "Task_Cards/back_" + name + ".png");
		}
	}

	private CardArt()
	{
	}

	/**
	 * Draw one card in {@code b}: its back, or its face with the task's name, quantity and
	 * description centred on the grey panel. The panel was measured off the art: x 44 to 312,
	 * y 90 to 180, the same on every tier.
	 */
	static void paint(Graphics2D g, Rectangle b, TaskCardTier tier, boolean front, String name, String description, int quantity)
	{
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage((front ? FRONT : BACK)[tier.ordinal()], b.x, b.y, b.width, b.height, null);
		if (!front)
		{
			return;
		}
		g.setFont(FontManager.getRunescapeSmallFont());
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		FontMetrics fm = g.getFontMetrics();
		int width = Math.round(b.width * 0.72f);
		int room = Math.round(b.height * 0.45f) / fm.getHeight();
		boolean hasDescription = description != null && !description.trim().isEmpty();
		int count = quantity > 1 ? 1 : 0;
		// The name comes first; the description (after a blank line) gets what's left.
		List<String> names = wrap(fm, name, width, Math.max(1, room - count - (hasDescription ? 2 : 0)));
		List<String> notes = hasDescription ? wrap(fm, description.trim(), width, room - names.size() - count - 1)
			: new ArrayList<>();
		int lines = names.size() + count + (notes.isEmpty() ? 0 : notes.size() + 1);
		int cx = b.x + b.width / 2;
		int y = b.y + Math.round(b.height * 0.675f) - lines * fm.getHeight() / 2 + fm.getAscent();
		for (String line : names)
		{
			Skin.centred(g, line, cx, y, Color.WHITE);
			y += fm.getHeight();
		}
		if (count > 0)
		{
			Skin.centred(g, "Quantity: " + quantity, cx, y, new Color(255, 200, 60));
			y += fm.getHeight();
		}
		y += fm.getHeight();
		for (String line : notes)
		{
			Skin.centred(g, line, cx, y, new Color(205, 205, 205));
			y += fm.getHeight();
		}
	}

	private static List<String> wrap(FontMetrics fm, String text, int width, int maxLines)
	{
		List<String> lines = new ArrayList<>();
		if (text == null || maxLines <= 0)
		{
			return lines;
		}
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" "))
		{
			if (line.length() > 0 && fm.stringWidth(line + " " + word) > width)
			{
				lines.add(line.toString());
				line.setLength(0);
			}
			line.append(line.length() == 0 ? "" : " ").append(word);
		}
		if (line.length() > 0)
		{
			lines.add(line.toString());
		}
		if (lines.size() > maxLines)
		{
			List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
			cut.set(maxLines - 1, Skin.fit(fm, cut.get(maxLines - 1) + " " + lines.get(maxLines), width));
			return cut;
		}
		return lines;
	}
}
