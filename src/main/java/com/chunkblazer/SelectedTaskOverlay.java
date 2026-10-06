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

import com.chunkblazer.modules.NPCKillModule;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.BasicStroke;
import java.util.ArrayList;
import java.util.List;
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
 *
 * Timed kill tasks ("Defeat a level 5 frog in 21 seconds") show a countdown instead
 * of the usual progress bar. It starts when the kill tracker starts its own clock
 * (our first hit on a matching NPC) and changes colour as time runs out. Tasks with
 * a 1-tick limit ("in the first hit") keep the normal bar, as there's nothing to time.
 */
@Singleton
public class SelectedTaskOverlay extends OverlayPanel
{
	private static final Color FLAME = new Color(255, 140, 0);
	private static final Color BAR_BACKGROUND = new Color(60, 60, 60, 200);
	private static final Color REQUIREMENT_TEXT = new Color(200, 200, 200);
	private static final Color MISSING_LEVEL = new Color(255, 90, 90);
	private static final int CLOSE_SIZE = 9;
	private static final int CLOSE_MARGIN = 5;
	private static final Color CLOSE_IDLE = new Color(200, 200, 200);
	private static final Color CLOSE_HOVER = new Color(255, 90, 90);

	// Countdown colours, from plenty of time left to out of time.
	private static final Color TIMER_OK = new Color(90, 200, 90);
	private static final Color TIMER_WARN = new Color(230, 200, 60);
	private static final Color TIMER_LATE = new Color(255, 140, 0);
	private static final Color TIMER_FAIL = new Color(220, 50, 50);
	private static final int TICK_MS = 600;

	// Where the X was last drawn, relative to the overlay's top-left. Null while hidden.
	private volatile Rectangle closeButton;
	private volatile boolean closeHovered;

	// The game only advances in 0.6s ticks; remembering when the current tick began
	// lets the countdown move smoothly between them instead of jumping.
	private int lastTick = -1;
	private long lastTickAt;

	private final Client client;
	private final ChunkBlazerPlugin plugin;
	private final ChunkBlazerConfig config;
	private final NPCKillModule kills;

	@Inject
	public SelectedTaskOverlay(Client client, ChunkBlazerPlugin plugin, ChunkBlazerConfig config, NPCKillModule kills)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.kills = kills;

		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(190, 0));
		// A few pixels between rows, so wrapped text never touches the progress bar.
		panelComponent.setGap(new Point(0, 3));

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

		// What the task actually asks for, when the name alone doesn't say.
		for (String requirement : requirementLines(task))
		{
			panelComponent.getChildren().add(LineComponent.builder()
					.left(requirement)
					.leftColor(REQUIREMENT_TEXT)
					.build());
		}
		if (!plugin.meetsLevelRequirement(task))
		{
			panelComponent.getChildren().add(LineComponent.builder()
					.left("Needs level " + task.getLevelRequirement() + " "
							+ NuzlockeTask.displayCategory(task.getCategory()))
					.leftColor(MISSING_LEVEL)
					.build());
		}

		int progress = task.getCurrentProgress();
		int target = Math.max(1, task.getTargetQuantity());

		int limitTicks = timeLimitTicks(task);
		if (limitTicks > 1 && task.getTargetNpc() != null)
		{
			// Timed kill: countdown bar, with the kill count on its own line if the
			// task needs more than one.
			if (target > 1)
			{
				panelComponent.getChildren().add(LineComponent.builder()
						.left("Kills")
						.right(Math.min(progress, target) + "/" + target)
						.leftColor(REQUIREMENT_TEXT)
						.rightColor(Color.WHITE)
						.build());
			}
			panelComponent.getChildren().add(timerBar(task, limitTicks));
		}
		else
		{
			ProgressBarComponent bar = new ProgressBarComponent();
			bar.setMinimum(0);
			bar.setMaximum(target);
			bar.setValue(Math.min(progress, target));
			bar.setForegroundColor(FLAME);
			bar.setBackgroundColor(BAR_BACKGROUND);
			bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.FULL); // shows "3/10"
			panelComponent.getChildren().add(bar);
		}

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

	/** The task's time limit in ticks, or 0 if it has none. */
	private static int timeLimitTicks(NuzlockeTask task)
	{
		TaskConstraints c = task.getConstraints();
		if (c == null || !c.hasTimeLimit() || c.getTimeInTicks() == null)
		{
			return 0;
		}
		return c.getTimeInTicks();
	}

	/**
	 * Countdown for a timed kill. Full and waiting until the fight starts, then
	 * draining live: green, yellow under half, orange under a quarter, red under a
	 * tenth, and "Too slow" once the limit has passed (until that fight ends).
	 */
	private ProgressBarComponent timerBar(NuzlockeTask task, int limitTicks)
	{
		int tick = client.getTickCount();
		long now = System.currentTimeMillis();
		if (tick != lastTick)
		{
			lastTick = tick;
			lastTickAt = now;
		}

		long limitMs = (long) limitTicks * TICK_MS;
		int startTick = kills.getActiveFightStartTick(task.getTargetNpc());

		ProgressBarComponent bar = new ProgressBarComponent();
		bar.setMinimum(0);
		bar.setMaximum(limitMs);
		bar.setBackgroundColor(BAR_BACKGROUND);
		bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);

		if (startTick < 0)
		{
			// Not fighting a matching NPC yet: show the full allowance.
			bar.setValue(limitMs);
			bar.setForegroundColor(TIMER_OK);
			bar.setCenterLabel(seconds(limitMs) + " limit");
			return bar;
		}

		long elapsed = (long) (tick - startTick) * TICK_MS + Math.min(TICK_MS, now - lastTickAt);
		long remaining = limitMs - elapsed;
		if (remaining <= 0)
		{
			bar.setValue(limitMs);
			bar.setForegroundColor(TIMER_FAIL);
			bar.setCenterLabel("Too slow");
			return bar;
		}

		double fraction = remaining / (double) limitMs;
		Color color = fraction > 0.5 ? TIMER_OK
				: fraction > 0.25 ? TIMER_WARN
				: fraction > 0.1 ? TIMER_LATE
				: TIMER_FAIL;
		bar.setValue(remaining);
		bar.setForegroundColor(color);
		bar.setCenterLabel(seconds(remaining) + " left");
		return bar;
	}

	private static String seconds(long millis)
	{
		return String.format("%.1fs", millis / 1000.0);
	}

	/** True while the mouse is over the close X (updated every frame in render). */
	boolean isCloseHovered()
	{
		return closeButton != null && closeHovered;
	}

	/**
	 * Plain-English requirements for the task. Uses the task's description when it
	 * has one (challenges and combat achievements, e.g. "Defeat Scurrius with a Green
	 * d'hide body, chaps, vambraces, and Maple shortbow equipped."); otherwise spells
	 * out the common restrictions from its constraints.
	 */
	static List<String> requirementLines(NuzlockeTask task)
	{
		List<String> lines = new ArrayList<>();
		String description = task.getDescription();
		if (description != null && !description.trim().isEmpty()
				&& !simplify(description).equals(simplify(task.getName())))
		{
			lines.add(description.trim());
			return lines;
		}

		TaskConstraints c = task.getConstraints();
		if (c == null)
		{
			return lines;
		}
		if (c.getTimeInTicks() != null && c.getTimeInTicks() > 0)
		{
			int seconds = (int) Math.round(c.getTimeInTicks() * 0.6);
			lines.add(String.format("Within %d:%02d", seconds / 60, seconds % 60));
		}
		if (Boolean.TRUE.equals(c.getNoPrayer()))
		{
			lines.add("No prayer");
		}
		if (Boolean.TRUE.equals(c.getNoFood()))
		{
			lines.add("No food");
		}
		if (Boolean.TRUE.equals(c.getNoEquipment()) || Boolean.TRUE.equals(c.getEquipNothing()))
		{
			lines.add("Nothing equipped");
		}
		if (c.getMaxCombatLevel() != null)
		{
			lines.add("Combat level " + c.getMaxCombatLevel() + " or lower");
		}
		if (c.getMinCombatLevel() != null)
		{
			lines.add("Combat level " + c.getMinCombatLevel() + " or higher");
		}
		return lines;
	}

	private static String simplify(String text)
	{
		return text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9]", "");
	}
}
