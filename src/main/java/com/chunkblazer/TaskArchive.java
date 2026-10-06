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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * Tasks the player has archived: put aside, out of the way. Archived tasks still
 * exist and still make progress if done, but they're hidden from the task window's
 * Active and Saved tabs, they stop outlining NPCs and objects, and they drop out of
 * the right-click Tasks menu. Stored per account, like saved tasks.
 *
 * Shared by the task window, the task box and the highlighter, so all of them agree
 * on what's archived. Also draws the book icon they use.
 */
@Singleton
public class TaskArchive
{
	private static final String CONFIG_GROUP = "chunkblazer";
	private static final String ARCHIVE_KEY = "archivedTasks";

	private final ConfigManager configManager;

	// Parsed form of the stored list, rebuilt only when the stored string changes.
	// The highlighter asks about every task each tick, so re-splitting would be waste.
	private String cachedRaw;
	private Set<String> cachedIds = Collections.emptySet();

	@Inject
	TaskArchive(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** Archived task ids for the current account (empty when no account is loaded). */
	public synchronized Set<String> ids()
	{
		String raw = configManager.getRSProfileConfiguration(CONFIG_GROUP, ARCHIVE_KEY);
		if (raw == null)
		{
			raw = "";
		}
		if (!raw.equals(cachedRaw))
		{
			Set<String> parsed = new LinkedHashSet<>();
			for (String id : raw.split(","))
			{
				if (!id.trim().isEmpty())
				{
					parsed.add(id.trim());
				}
			}
			cachedIds = Collections.unmodifiableSet(parsed);
			cachedRaw = raw;
		}
		return cachedIds;
	}

	public boolean isArchived(NuzlockeTask task)
	{
		return task != null && task.getTaskId() != null && ids().contains(task.getTaskId());
	}

	public void archive(String taskId)
	{
		set(taskId, true);
	}

	public void restore(String taskId)
	{
		set(taskId, false);
	}

	public void toggle(String taskId)
	{
		set(taskId, !ids().contains(taskId));
	}

	private synchronized void set(String taskId, boolean archived)
	{
		if (taskId == null || configManager.getRSProfileKey() == null)
		{
			return;
		}
		Set<String> updated = new LinkedHashSet<>(ids());
		boolean changed = archived ? updated.add(taskId) : updated.remove(taskId);
		if (changed)
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, ARCHIVE_KEY, String.join(",", updated));
		}
	}

	/**
	 * A small closed book: a cover, a spine, and page edges. Filled (solid cover) when
	 * the task is archived, outlined when it isn't. (x, y) is the top-left corner.
	 */
	public static void drawBook(Graphics2D graphics, int x, int y, int width, int height, Color color, boolean filled)
	{
		Stroke previous = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1.2f));
		graphics.setColor(color);
		if (filled)
		{
			graphics.fillRect(x, y, width, height);
			graphics.setColor(new Color(20, 18, 15));
			graphics.drawLine(x + 2, y, x + 2, y + height);
			graphics.drawLine(x + width - 2, y + 2, x + width - 2, y + height - 2);
		}
		else
		{
			graphics.drawRect(x, y, width, height);
			graphics.drawLine(x + 2, y, x + 2, y + height);
			graphics.drawLine(x + width - 2, y + 2, x + width - 2, y + height - 2);
		}
		graphics.setStroke(previous);
	}
}
