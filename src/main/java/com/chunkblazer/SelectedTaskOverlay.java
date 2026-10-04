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
import java.awt.BasicStroke;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * On-screen box showing the task the player selected in the side panel, with a
 * progress bar that updates live. It re-reads the task every frame, so any
 * progress change made by the task modules shows up immediately.
 * Alt + drag to move it; click the X (or right-click, then Deselect) to stop tracking.
 */
@Singleton
public class SelectedTaskOverlay extends OverlayPanel
{
	private static final Color FLAME = new Color(255, 140, 0);
	private static final Color BAR_BACKGROUND = new Color(60, 60, 60, 200);
	private static final int CLOSE_SIZE = 9;
	private static final int CLOSE_MARGIN = 5;
	private static final Color CLOSE_IDLE = new Color(200, 200, 200);
	private static final Color CLOSE_HOVER = new Color(255, 90, 90);

	// Where the X was last drawn, relative to the overlay's top-left. Null while hidden.
	private volatile Rectangle closeButton;
	private volatile boolean closeHovered;

	private final Client client;
	private final ChunkBlazerPlugin plugin;
	private final ChunkBlazerConfig config;

	@Inject
	public SelectedTaskOverlay(Client client, ChunkBlazerPlugin plugin, ChunkBlazerConfig config)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(190, 0));

		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Deselect", "ChunkBlazer task",
				e -> plugin.clearSelectedTask());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.taskTrackerStyle() != TaskTrackerStyle.VANI || client.getGameState() != GameState.LOGGED_IN)
		{
			closeButton = null;
			closeHovered = false;
			return null;
		}

		NuzlockeTask task = plugin.getSelectedTask();
		if (task == null)
		{
			closeButton = null;
			closeHovered = false;
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
				.text("ChunkBlazer Task")
				.color(FLAME)
				.build());

		// LineComponent wraps long text onto extra lines automatically.
		panelComponent.getChildren().add(LineComponent.builder()
				.left(task.getName())
				.leftColor(Color.WHITE)
				.build());

		String info = NuzlockeTask.displayCategory(task.getCategory()) + " | " + task.getBasePoints() + " pts";
		panelComponent.getChildren().add(LineComponent.builder()
				.left(info)
				.leftColor(new Color(255, 200, 100))
				.build());

		int progress = task.getCurrentProgress();
		int target = Math.max(1, task.getTargetQuantity());

		ProgressBarComponent bar = new ProgressBarComponent();
		bar.setMinimum(0);
		bar.setMaximum(target);
		bar.setValue(Math.min(progress, target));
		bar.setForegroundColor(FLAME);
		bar.setBackgroundColor(BAR_BACKGROUND);
		bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.FULL); // shows "3/10"
		panelComponent.getChildren().add(bar);

		Dimension size = super.render(graphics);
		if (size == null)
		{
			closeButton = null;
			closeHovered = false;
			return null;
		}

		// Close X in the top-right corner, drawn over the panel just painted.
		Rectangle box = new Rectangle(size.width - CLOSE_SIZE - CLOSE_MARGIN, CLOSE_MARGIN, CLOSE_SIZE, CLOSE_SIZE);

		// Hover test using the game's own mouse position, which is in the same
		// coordinate space the overlay is drawn in (works in stretched/resized
		// modes too). The click handler just asks "is the X hovered right now?".
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		Rectangle bounds = getBounds();
		closeHovered = mouse != null && bounds != null
				&& new Rectangle(bounds.x + box.x - 4, bounds.y + box.y - 4, box.width + 8, box.height + 8)
				.contains(mouse.getX(), mouse.getY());

		graphics.setColor(closeHovered ? CLOSE_HOVER : CLOSE_IDLE);
		graphics.setStroke(new BasicStroke(2));
		graphics.drawLine(box.x, box.y, box.x + box.width, box.y + box.height);
		graphics.drawLine(box.x, box.y + box.height, box.x + box.width, box.y);
		closeButton = box;
		return size;
	}

	/** True while the mouse is over the close X (updated every frame in render). */
	boolean isCloseHovered()
	{
		return closeButton != null && closeHovered;
	}
}
