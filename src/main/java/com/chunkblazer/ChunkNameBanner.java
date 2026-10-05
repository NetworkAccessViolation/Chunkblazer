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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;

/**
 * Pokemon-style area sign: when the player walks into a different chunk, a small
 * banner with the chunk's name slides down from the top of the game view, holds for a
 * moment, then slides back up. A second line says whether the chunk is unlocked or,
 * if not, what it costs. Drawn by ChunkBlazerSceneOverlay.
 */
@Singleton
public class ChunkNameBanner
{
	private static final long SLIDE_MS = 350;
	private static final long HOLD_MS = 2500;
	private static final long TOTAL_MS = SLIDE_MS + HOLD_MS + SLIDE_MS;
	private static final int TOP_MARGIN = 12;
	private static final int PAD_X = 18;
	private static final int PAD_Y = 8;

	private static final Color BACKGROUND = new Color(25, 22, 18, 225);
	private static final Color BORDER = new Color(200, 160, 70);
	private static final Color TITLE = new Color(255, 230, 170);

	private final Client client;
	private final ChunkBlazerPlugin plugin;
	private final ChunkBlazerConfig config;

	private int lastRegionId = -1;
	private String lastName;
	private String title;
	private String subtitle;
	private Color subtitleColor;
	private long shownAt;

	@Inject
	public ChunkNameBanner(Client client, ChunkBlazerPlugin plugin, ChunkBlazerConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
	}

	/** Called every frame; watches for a chunk change and draws the banner while it's showing. */
	public void render(Graphics2D graphics)
	{
		if (!config.showChunkNamePopups())
		{
			title = null;
			return;
		}
		checkForNewChunk();
		if (title == null)
		{
			return;
		}

		long age = System.currentTimeMillis() - shownAt;
		if (age >= TOTAL_MS)
		{
			title = null;
			return;
		}
		draw(graphics, progress(age));
	}

	private void checkForNewChunk()
	{
		Player local = client.getLocalPlayer();
		if (local == null || client.isInInstancedRegion())
		{
			return;
		}
		WorldPoint location = local.getWorldLocation();
		if (location == null)
		{
			return;
		}
		int regionId = location.getRegionID();
		if (regionId == lastRegionId)
		{
			return;
		}
		boolean firstLook = lastRegionId == -1;
		lastRegionId = regionId;

		String name = chunkName(regionId);
		// No sign on login, for unnamed areas, or when moving between two regions of
		// the same chunk (a surface and its dungeon share a name).
		if (firstLook || name == null || name.equals(lastName))
		{
			lastName = name;
			return;
		}
		lastName = name;

		boolean unlocked = plugin.isRegionUnlocked(regionId);
		boolean neighbor = !unlocked && plugin.getNeighborRegionIds().contains(regionId);
		ChunkUnlockType type = ChunkUnlockType.of(plugin, regionId, unlocked, neighbor);

		title = name;
		subtitle = statusText(type, ChunkUnlockType.costLabel(plugin, regionId, type));
		subtitleColor = type.color;
		shownAt = System.currentTimeMillis();
	}

	/** "Lumbridge (12850)" -> "Lumbridge"; null for regions without a chunk name. */
	private String chunkName(int regionId)
	{
		String full = plugin.getRegionName(regionId);
		if (full == null || full.startsWith("Unknown Region"))
		{
			return null;
		}
		return full.replaceAll("\\s*\\(\\d+\\)$", "").trim();
	}

	private static String statusText(ChunkUnlockType type, String cost)
	{
		switch (type)
		{
			case UNLOCKED:
				return "Unlocked";
			case LOCKED:
				return "Locked";
			case FREE:
			case CHARTER:
				return "Free to unlock";
			case BOSS:
				return "Unlock with 1 Boss Token";
			default:
				return "Unlock for " + cost;
		}
	}

	/** 0 = hidden above the screen, 1 = fully shown. Eases in and out. */
	private static double progress(long age)
	{
		if (age < SLIDE_MS)
		{
			return ease(age / (double) SLIDE_MS);
		}
		if (age < SLIDE_MS + HOLD_MS)
		{
			return 1;
		}
		return ease(1 - (age - SLIDE_MS - HOLD_MS) / (double) SLIDE_MS);
	}

	private static double ease(double t)
	{
		return 1 - Math.pow(1 - t, 3);
	}

	private void draw(Graphics2D graphics, double shown)
	{
		Font titleFont = FontManager.getRunescapeBoldFont().deriveFont(20f);
		Font subtitleFont = FontManager.getRunescapeSmallFont();
		graphics.setFont(titleFont);
		FontMetrics titleMetrics = graphics.getFontMetrics();
		graphics.setFont(subtitleFont);
		FontMetrics subtitleMetrics = graphics.getFontMetrics();

		int width = Math.max(titleMetrics.stringWidth(title), subtitleMetrics.stringWidth(subtitle)) + PAD_X * 2;
		int height = titleMetrics.getHeight() + subtitleMetrics.getHeight() + PAD_Y * 2;
		int x = client.getViewportXOffset() + (client.getViewportWidth() - width) / 2;
		int shownY = client.getViewportYOffset() + TOP_MARGIN;
		int y = (int) Math.round(shownY - (height + TOP_MARGIN) * (1 - shown));

		Composite previousComposite = graphics.getComposite();
		Object previousAntialias = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, shown))));
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		graphics.setColor(BACKGROUND);
		graphics.fillRoundRect(x, y, width, height, 12, 12);
		graphics.setColor(BORDER);
		graphics.setStroke(new BasicStroke(2f));
		graphics.drawRoundRect(x, y, width, height, 12, 12);

		int titleY = y + PAD_Y + titleMetrics.getAscent();
		graphics.setFont(titleFont);
		graphics.setColor(Color.BLACK);
		graphics.drawString(title, x + (width - titleMetrics.stringWidth(title)) / 2 + 1, titleY + 1);
		graphics.setColor(TITLE);
		graphics.drawString(title, x + (width - titleMetrics.stringWidth(title)) / 2, titleY);

		int subtitleY = titleY + titleMetrics.getDescent() + subtitleMetrics.getAscent();
		graphics.setFont(subtitleFont);
		graphics.setColor(subtitleColor);
		graphics.drawString(subtitle, x + (width - subtitleMetrics.stringWidth(subtitle)) / 2, subtitleY);

		graphics.setComposite(previousComposite);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previousAntialias);
	}
}
