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
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.FontManager;
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
 * Click the book in the top-left corner to archive the task (see TaskArchive), which
 * also stops tracking it.
 *
 * Timed kill tasks ("Kill a Frog in 21 seconds") show a live countdown instead of a
 * progress bar, starting on your first hit (the same tick the kill tracker starts
 * timing): green, then yellow, orange and red, and "Too slow" once time runs out.
 * Multi-kill timed tasks also show "Kills x/y". Single-action tasks, including
 * 1-tick tasks, get no bar at all.
 */
@Singleton
public class SelectedTaskOverlay extends OverlayPanel
{
	private static final Color BAR_BACKGROUND = new Color(60, 60, 60, 200);
	private static final Color REQUIREMENT_TEXT = new Color(200, 200, 200);
	private static final Color MISSING_LEVEL = new Color(255, 90, 90);
	private static final int CLOSE_SIZE = 9;
	private static final int CLOSE_MARGIN = 5;
	private static final Color CLOSE_IDLE = new Color(200, 200, 200);
	private static final Color CLOSE_HOVER = new Color(255, 90, 90);
	private static final Color ARCHIVE_HOVER = new Color(190, 140, 90);

	// Countdown colours, from plenty of time left to out of time.
	private static final Color TIMER_OK = new Color(90, 200, 90);
	private static final Color TIMER_WARN = new Color(230, 200, 60);
	private static final Color TIMER_LATE = new Color(255, 140, 0);
	private static final Color TIMER_FAIL = new Color(220, 50, 50);
	private static final int TICK_MS = 600;

	// Where the X was last drawn, relative to the overlay's top-left. Null while hidden.
	private volatile Rectangle closeButton;
	private volatile boolean closeHovered;
	// Same for the archive book in the opposite corner, plus the task it would archive.
	private volatile Rectangle archiveButton;
	private volatile boolean archiveHovered;
	private volatile String shownTaskId;

	// The game only advances in 0.6s ticks; remembering when the current tick began
	// lets the countdown move smoothly between them instead of jumping.
	private int lastTick = -1;
	private long lastTickAt;

	// Compact layout: how far it has opened into the full box (0 shut, 1 open), when that
	// was last moved on, and the two sizes it moves between.
	private float expand;
	private long expandAt;
	private Dimension compactSize = new Dimension(120, 17);
	private Dimension fullSize = new Dimension(190, 60);

	private final Client client;
	private final ChunkBlazerPlugin plugin;
	private final ChunkBlazerConfig config;
	private final TaskArchive archive;
	private final NPCKillModule killModule;

	@Inject
	public SelectedTaskOverlay(Client client, ChunkBlazerPlugin plugin, ChunkBlazerConfig config, TaskArchive archive,
		NPCKillModule killModule)
	{
		super(plugin);
		this.killModule = killModule;
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.archive = archive;

		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(190, 0));
		// A few pixels between rows, so wrapped text never touches the progress bar.
		panelComponent.setGap(new Point(0, 3));
		panelComponent.setBackgroundColor(new Color(30, 26, 22, 215));

		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Deselect", "ChunkBlazer task",
				e -> plugin.clearSelectedTask());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		TaskTrackerStyle style = config.taskTrackerStyle();
		if (style != TaskTrackerStyle.VANI && style != TaskTrackerStyle.COMPACT || client.getGameState() != GameState.LOGGED_IN)
		{
			hideButtons();
			return null;
		}

		NuzlockeTask task = plugin.getSelectedTask();
		if (task == null)
		{
			hideButtons();
			return null;
		}

		Color tier = TaskCardTier.fromTask(task).getAccent();
		// Compact: one small line, out of the way. Hovering it opens the full box (what to do,
		// requirements, missing levels); timed tasks keep the full box for their countdown.
		net.runelite.api.Point at = client.getMouseCanvasPosition();
		Rectangle shown = getBounds();
		boolean hovered = at != null && shown != null && shown.contains(at.getX(), at.getY());
		// Opens quickly under the mouse (0.12s), closes slower once it leaves (0.4s).
		long now = System.currentTimeMillis();
		long step = Math.min(100, now - expandAt);
		expandAt = now;
		expand = hovered ? Math.min(1, expand + step / 120f) : Math.max(0, expand - step / 400f);
		boolean compact = style == TaskTrackerStyle.COMPACT && timeLimitTicks(task) == 0;
		if (compact && expand == 0)
		{
			hideButtons();
			compactSize = drawCompact(graphics, task, tier);
			return compactSize;
		}
		// Part way open: show the full box cut to a size between the two.
		float e = expand * (2 - expand);
		int w = Math.round(compactSize.width + (fullSize.width - compactSize.width) * e);
		int h = Math.round(compactSize.height + (fullSize.height - compactSize.height) * e);
		java.awt.Shape oldClip = graphics.getClip();
		if (compact && expand < 1)
		{
			graphics.clip(new Rectangle(0, 0, w, h));
		}

		// The heading is the task's category and level, in its tier colour (which is its points).
		panelComponent.getChildren().add(TitleComponent.builder()
				.text(NuzlockeTask.displayCategory(task.getCategory())
					+ (task.getLevelRequirement() > 1 ? "  Lvl " + task.getLevelRequirement() : ""))
				.color(tier)
				.build());

		// LineComponent wraps long text onto extra lines automatically.
		panelComponent.getChildren().add(LineComponent.builder()
				.left(task.getName())
				.leftColor(Color.WHITE)
				.build());

		// What the task actually asks for, when the name alone doesn't say.
		for (String requirement : requirementLines(task))
		{
			panelComponent.getChildren().add(LineComponent.builder()
					.left(requirement)
					.leftColor(REQUIREMENT_TEXT)
					.build());
		}
		// Every level you're missing, e.g. "20 Defence, 20 Ranged".
		String missing = TaskTargetExtras.missingRequirement(client, task);
		if (missing != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
					.left(missing)
					.leftColor(MISSING_LEVEL)
					.build());
		}
		else if (!plugin.meetsLevelRequirement(task))
		{
			panelComponent.getChildren().add(LineComponent.builder()
					.left(task.getLevelRequirement() + " "
							+ NuzlockeTask.displayCategory(task.getCategory()))
					.leftColor(MISSING_LEVEL)
					.build());
		}

		int progress = task.getCurrentProgress();
		int target = Math.max(1, task.getTargetQuantity());

		int limitTicks = timeLimitTicks(task);
		if (limitTicks > 0)
		{
			// Timed kill task: "Kills x/y" for multi-kill ones, then the countdown.
			if (target > 1)
			{
				panelComponent.getChildren().add(LineComponent.builder()
						.left("Kills")
						.right(Math.min(progress, target) + "/" + target)
						.leftColor(REQUIREMENT_TEXT)
						.rightColor(Color.WHITE)
						.build());
			}
			panelComponent.getChildren().add(countdownBar(task, limitTicks));
		}
		// One-off tasks (a target of 1, 1-tick tasks included) get no bar: "0/1" says nothing useful.
		else if (target > 1)
		{
			ProgressBarComponent bar = new ProgressBarComponent();
			bar.setMinimum(0);
			bar.setMaximum(target);
			bar.setValue(Math.min(progress, target));
			bar.setForegroundColor(tier);
			bar.setBackgroundColor(BAR_BACKGROUND);
			bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.FULL); // shows "3/10"
			panelComponent.getChildren().add(bar);
		}

		Dimension size = super.render(graphics);
		if (size == null)
		{
			graphics.setClip(oldClip);
			hideButtons();
			return null;
		}

		// Close X in the top-right corner and the archive book in the top-left, drawn
		// over the panel just painted.
		Rectangle box = new Rectangle(size.width - CLOSE_SIZE - CLOSE_MARGIN, CLOSE_MARGIN, CLOSE_SIZE, CLOSE_SIZE);
		Rectangle book = new Rectangle(CLOSE_MARGIN, CLOSE_MARGIN - 1, CLOSE_SIZE, CLOSE_SIZE + 2);

		// Hover test using the game's own mouse position, which is in the same
		// coordinate space the overlay is drawn in (works in stretched/resized
		// modes too). The click handler just asks "is the X hovered right now?".
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		Rectangle bounds = getBounds();
		closeHovered = isHovered(mouse, bounds, box);
		archiveHovered = isHovered(mouse, bounds, book);

		graphics.setColor(closeHovered ? CLOSE_HOVER : CLOSE_IDLE);
		graphics.setStroke(new BasicStroke(2));
		graphics.drawLine(box.x, box.y, box.x + box.width, box.y + box.height);
		graphics.drawLine(box.x, box.y + box.height, box.x + box.width, box.y);
		TaskArchive.drawBook(graphics, book.x, book.y, book.width, book.height,
				archiveHovered ? ARCHIVE_HOVER : CLOSE_IDLE, false);

		closeButton = box;
		archiveButton = book;
		shownTaskId = task.getTaskId();
		fullSize = size;
		if (compact && expand < 1)
		{
			graphics.setClip(oldClip);
			return new Dimension(w, h);
		}
		return size;
	}

	/**
	 * Compact layout: a small rounded strip with the tier colour at the left, the name
	 * (red if you can't do it yet), its count on the right and a thin bar along the
	 * bottom. Right-click it to deselect, as with the full box.
	 */
	private Dimension drawCompact(Graphics2D graphics, NuzlockeTask task, Color tier)
	{
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = graphics.getFontMetrics();
		int target = Math.max(1, task.getTargetQuantity());
		int done = Math.min(task.getCurrentProgress(), target);
		String count = target > 1 ? done + "/" + target : "";
		// The whole name, however long: the strip grows to fit it.
		String name = task.getName() == null ? "" : task.getName();
		int width = 15 + fm.stringWidth(name) + (count.isEmpty() ? 0 : 8 + fm.stringWidth(count));
		int height = 18;
		graphics.setColor(new Color(20, 18, 15, 175));
		graphics.fillRoundRect(0, 0, width, height, 9, 9);
		graphics.setColor(tier);
		graphics.fillRoundRect(3, 4, 3, height - 8, 3, 3);
		if (target > 1)
		{
			graphics.fillRect(9, height - 3, (width - 15) * done / target, 2);
		}
		boolean ready = TaskTargetExtras.missingRequirement(client, task) == null && plugin.meetsLevelRequirement(task);
		graphics.setColor(ready ? Color.WHITE : MISSING_LEVEL);
		graphics.drawString(name, 9, 14);
		graphics.setColor(REQUIREMENT_TEXT);
		graphics.drawString(count, width - 6 - fm.stringWidth(count), 14);
		return new Dimension(width, height);
	}

	/**
	 * The task's time limit in ticks, if it's a timed kill task that gets a countdown;
	 * 0 otherwise. 1-tick tasks ("in the first hit") are single actions, so they get none.
	 */
	private static int timeLimitTicks(NuzlockeTask task)
	{
		TaskConstraints c = task.getConstraints();
		if (c == null || c.getTimeInTicks() == null || c.getTimeInTicks() <= 1 || task.getTargetNpc() == null)
		{
			return 0;
		}
		String type = task.getCompletionType();
		return "NPC_KILL".equalsIgnoreCase(type) || "SLAYER".equalsIgnoreCase(type) ? c.getTimeInTicks() : 0;
	}

	/**
	 * Countdown bar for a timed kill: full and waiting until the first hit, then
	 * draining smoothly between game ticks, changing colour as time runs low.
	 */
	private ProgressBarComponent countdownBar(NuzlockeTask task, int limitTicks)
	{
		int tick = client.getTickCount();
		long now = System.currentTimeMillis();
		if (tick != lastTick)
		{
			lastTick = tick;
			lastTickAt = now;
		}

		long limitMs = (long) limitTicks * TICK_MS;
		int startTick = killModule.getActiveFightStartTick(task.getTargetNpc());
		long remaining;
		String label;
		if (startTick < 0)
		{
			remaining = limitMs;
			label = formatSeconds(limitMs) + " (starts on first hit)";
		}
		else
		{
			long elapsed = (long) (tick - startTick) * TICK_MS + Math.min(TICK_MS, now - lastTickAt);
			remaining = Math.max(0, limitMs - elapsed);
			label = remaining > 0 ? formatSeconds(remaining) : "Too slow";
		}

		double fraction = limitMs > 0 ? remaining / (double) limitMs : 0;
		Color color;
		if (remaining <= 0)
		{
			color = TIMER_FAIL;
		}
		else if (fraction > 0.5)
		{
			color = TIMER_OK;
		}
		else if (fraction > 0.25)
		{
			color = TIMER_WARN;
		}
		else
		{
			color = TIMER_LATE;
		}

		ProgressBarComponent bar = new ProgressBarComponent();
		bar.setMinimum(0);
		bar.setMaximum(limitMs);
		bar.setValue(remaining);
		bar.setForegroundColor(color);
		bar.setBackgroundColor(BAR_BACKGROUND);
		bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
		bar.setCenterLabel(label);
		return bar;
	}

	private static String formatSeconds(long ms)
	{
		return String.format("%.1fs", ms / 1000.0);
	}

	/** Mouse over a corner button, with a few pixels of slack around it. */
	private static boolean isHovered(net.runelite.api.Point mouse, Rectangle bounds, Rectangle button)
	{
		return mouse != null && bounds != null
				&& new Rectangle(bounds.x + button.x - 4, bounds.y + button.y - 4, button.width + 8, button.height + 8)
				.contains(mouse.getX(), mouse.getY());
	}

	private void hideButtons()
	{
		closeButton = null;
		closeHovered = false;
		archiveButton = null;
		archiveHovered = false;
		shownTaskId = null;
	}

	/** True while the mouse is over the close X (updated every frame in render). */
	boolean isCloseHovered()
	{
		return closeButton != null && closeHovered;
	}

	/** True while the mouse is over the archive book (updated every frame in render). */
	boolean isArchiveHovered()
	{
		return archiveButton != null && archiveHovered;
	}

	/** Archive the task this box is showing and stop tracking it. */
	void archiveShownTask()
	{
		String taskId = shownTaskId;
		if (taskId != null)
		{
			archive.archive(taskId);
			plugin.clearSelectedTask();
		}
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
