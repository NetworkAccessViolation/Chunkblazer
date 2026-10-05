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
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Polygon;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * A see-through wall standing on every border between an unlocked chunk and a locked
 * one, so it's obvious in the game world where you can and can't go. It fades from
 * the configured colour at the ground to clear at the top.
 *
 * Only border edges are drawn (a few hundred short segments near the player), not
 * every tile, so it stays cheap; per-tile shading was too slow in Java2D.
 */
@Singleton
public class ChunkWallOverlay extends Overlay
{
	private static final int REGION_MASK = 63;
	private static final int LOCAL_TILE = Perspective.LOCAL_TILE_SIZE;
	// Wall height in local units (128 per tile): about three tiles tall.
	private static final int WALL_HEIGHT = 3 * LOCAL_TILE;
	// Only build walls within this many tiles of the player.
	private static final int DRAW_DISTANCE = 40;

	private final Client client;
	private final ChunkBlazerPlugin plugin;
	private final ChunkBlazerConfig config;

	@Inject
	public ChunkWallOverlay(Client client, ChunkBlazerPlugin plugin, ChunkBlazerConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showChunkWalls() || client.isInInstancedRegion())
		{
			return null;
		}
		Player local = client.getLocalPlayer();
		LocalPoint playerPos = local == null ? null : local.getLocalLocation();
		if (playerPos == null)
		{
			return null;
		}

		int plane = client.getPlane();
		int baseX = client.getBaseX();
		int baseY = client.getBaseY();
		int last = Constants.SCENE_SIZE - 1;
		int minX = Math.max(1, playerPos.getSceneX() - DRAW_DISTANCE);
		int maxX = Math.min(last, playerPos.getSceneX() + DRAW_DISTANCE);
		int minY = Math.max(1, playerPos.getSceneY() - DRAW_DISTANCE);
		int maxY = Math.min(last, playerPos.getSceneY() + DRAW_DISTANCE);

		Color base = config.chunkWallColor();
		Color clear = new Color(base.getRed(), base.getGreen(), base.getBlue(), 0);
		Color line = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.min(255, base.getAlpha() * 2));
		Map<Integer, Boolean> unlocked = new HashMap<>();
		Paint previousPaint = graphics.getPaint();

		for (int sx = minX; sx < maxX; sx++)
		{
			for (int sy = minY; sy < maxY; sy++)
			{
				int worldX = baseX + sx;
				int worldY = baseY + sy;

				// West edge of this tile is a chunk border: compare the chunks either side.
				if ((worldX & REGION_MASK) == 0
					&& isUnlocked(unlocked, worldX, worldY) != isUnlocked(unlocked, worldX - 1, worldY))
				{
					drawWall(graphics, plane, sx, sy, sx, sy + 1, base, clear, line);
				}
				// South edge of this tile is a chunk border.
				if ((worldY & REGION_MASK) == 0
					&& isUnlocked(unlocked, worldX, worldY) != isUnlocked(unlocked, worldX, worldY - 1))
				{
					drawWall(graphics, plane, sx, sy, sx + 1, sy, base, clear, line);
				}
			}
		}

		graphics.setPaint(previousPaint);
		return null;
	}

	private boolean isUnlocked(Map<Integer, Boolean> cache, int worldX, int worldY)
	{
		int regionId = ((worldX >> 6) << 8) | (worldY >> 6);
		return cache.computeIfAbsent(regionId, plugin::isRegionUnlocked);
	}

	/** One wall panel standing on the edge between two scene grid corners. */
	private void drawWall(Graphics2D graphics, int plane, int cx1, int cy1, int cx2, int cy2,
		Color base, Color clear, Color line)
	{
		LocalPoint a = new LocalPoint(cx1 * LOCAL_TILE, cy1 * LOCAL_TILE);
		LocalPoint b = new LocalPoint(cx2 * LOCAL_TILE, cy2 * LOCAL_TILE);
		Point groundA = Perspective.localToCanvas(client, a, plane);
		Point groundB = Perspective.localToCanvas(client, b, plane);
		Point topA = Perspective.localToCanvas(client, a, plane, WALL_HEIGHT);
		Point topB = Perspective.localToCanvas(client, b, plane, WALL_HEIGHT);
		if (groundA == null || groundB == null || topA == null || topB == null)
		{
			return;
		}

		Polygon panel = new Polygon(
			new int[]{groundA.getX(), groundB.getX(), topB.getX(), topA.getX()},
			new int[]{groundA.getY(), groundB.getY(), topB.getY(), topA.getY()},
			4);

		// Solid at the ground, fading to clear at the top.
		float groundMidX = (groundA.getX() + groundB.getX()) / 2f;
		float groundMidY = (groundA.getY() + groundB.getY()) / 2f;
		float topMidX = (topA.getX() + topB.getX()) / 2f;
		float topMidY = (topA.getY() + topB.getY()) / 2f;
		graphics.setPaint(new GradientPaint(groundMidX, groundMidY, base, topMidX, topMidY, clear));
		graphics.fillPolygon(panel);

		// A stronger line along the ground so the exact border is clear.
		graphics.setPaint(line);
		graphics.drawLine(groundA.getX(), groundA.getY(), groundB.getX(), groundB.getY());
	}
}
