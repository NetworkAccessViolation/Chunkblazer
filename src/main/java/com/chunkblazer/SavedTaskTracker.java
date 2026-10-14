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
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * The saved-tasks bubble: a round task-list button with a count of your saved (starred)
 * tasks. Clicking it opens the saved list over the side panel, like a quest's
 * requirements (TaskBrowserOverlay draws that). Alt + drag to move it. Registered by TaskBrowserOverlay.
 */
@Singleton
public class SavedTaskTracker extends Overlay
{
	private static final int SIZE = 32;

	private final Client client;
	private final ChunkBlazerConfig config;
	private final OverlayManager overlayManager;
	private final MouseManager mouseManager;
	// The task window, which draws the saved list (a Provider: the window also uses this bubble).
	private final Provider<TaskBrowserOverlay> browser;
	private volatile boolean hovered;

	private final MouseAdapter mouse = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			// Alt + drag moves the bubble; leave those clicks to RuneLite.
			if (hovered && !event.isAltDown() && event.getButton() == MouseEvent.BUTTON1)
			{
				browser.get().toggleSavedPane();
				event.consume();
			}
			return event;
		}
	};

	@Inject
	public SavedTaskTracker(Client client, ChunkBlazerPlugin plugin, ChunkBlazerConfig config,
		OverlayManager overlayManager, MouseManager mouseManager, Provider<TaskBrowserOverlay> browser)
	{
		super(plugin);
		this.client = client;
		this.config = config;
		this.overlayManager = overlayManager;
		this.mouseManager = mouseManager;
		this.browser = browser;
		setPosition(OverlayPosition.BOTTOM_RIGHT);
		setPriority(OverlayPriority.LOW);
	}

	public void startUp()
	{
		overlayManager.add(this);
		mouseManager.registerMouseListener(mouse);
	}

	public void shutDown()
	{
		overlayManager.remove(this);
		mouseManager.unregisterMouseListener(mouse);
		hovered = false;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showSavedTaskTracker() || client.getGameState() != GameState.LOGGED_IN)
		{
			hovered = false;
			return null;
		}
		int d = SIZE;
		int top = d / 6;
		Rectangle bounds = getBounds();
		net.runelite.api.Point m = client.getMouseCanvasPosition();
		// Under an interface (inventory, bank...) the click belongs to that, not the bubble: over the
		// scene the menu has "Walk here" + "Cancel", over an interface it's a widget entry or just "Cancel".
		MenuEntry[] entries = client.getMenuEntries();
		boolean covered = client.isMenuOpen() || entries.length < 2 || entries[entries.length - 1].getWidget() != null;
		hovered = !covered && m != null && bounds != null
			&& new Rectangle(bounds.x, bounds.y + top, d, d).contains(m.getX(), m.getY());
		drawSkinned(graphics, d, top);
		return new Dimension(d + 4, d + top);
	}

	private static final java.awt.image.BufferedImage ORB = Skin.image("orb");
	private static final java.awt.image.BufferedImage ORB_HOVER = Skin.image("orb_hover");

	/** A stone orb like the minimap's with a task sheet in it, and the count in yellow game text. */
	private void drawSkinned(Graphics2D g, int d, int top)
	{
		g.drawImage(hovered ? ORB_HOVER : ORB, 0, top, d, d, null);
		int count = browser.get().savedTasks().size();
		if (count > 0)
		{
			String text = String.valueOf(count);
			g.setFont(Skin.small());
			Skin.text(g, text, d - g.getFontMetrics().stringWidth(text) + 2, 11, new Color(255, 255, 0));
		}
	}
}
