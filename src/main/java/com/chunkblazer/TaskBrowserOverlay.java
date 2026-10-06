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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Leagues-style task window, opened from the Chunks minimap orb.
 *
 * Tabs: Active (everything you can work on now), Saved (tasks you've starred) and
 * Archived (tasks put aside with the book button; see TaskArchive).
 * Search matches name, description, category and chunk. "Current chunk" limits the
 * list to the chunk you're standing in and follows you as you move. Holding the map
 * unlock key (Shift by default) and clicking an unlocked chunk on the world map opens
 * the window on that chunk's tasks. Filter narrows the list (a
 * skill, talk-to tasks, quests, boss chunks...); Sort only changes the order, each
 * option ascending or descending. Click a star to save a task, the arrow to expand
 * its requirements, the row to track it (or stop tracking it). Mouse wheel scrolls; Esc closes.
 *
 * New tasks: when a chunk task you haven't seen joins your active list (a chunk
 * unlock, or flipping its card), the Points orb pulses with a "New tasks!" pointer.
 * Opening the window then shows just those tasks in a New tab; closing it marks
 * them seen, and the window goes back to normal. Seen tasks are stored per account.
 *
 * Drag the "ChunkBlazer Tasks" title to move the window; it stays where you put it
 * until the client restarts (kept on screen if the window is resized).
 *
 * Opened, closed and registered by ChunkBlazerOrbWidget. Hover is worked out while
 * drawing (from the game's own mouse position), so clicks never depend on mouse
 * coordinates lining up with the drawing in stretched/resized modes.
 */
@Singleton
public class TaskBrowserOverlay extends Overlay
{
	private static final String CONFIG_GROUP = "chunkblazer";
	private static final String SAVED_KEY = "savedTasks";
	private static final String SEEN_KEY = "seenTasks";

	// New-task alert: wait this many ticks after login before comparing (the task list
	// is still loading), how long the "New tasks!" pointer shows, and the pulse speed.
	private static final int SETTLE_TICKS = 10;
	private static final long HINT_MS = 6000;
	private static final long HINT_FADE_MS = 1500;
	private static final long PULSE_MS = 1200;

	private static final int MAX_WIDTH = 460;
	private static final int MAX_HEIGHT = 400;
	private static final int HEADER = 30;
	private static final int TABS = 26;
	private static final int ROW = 38;
	private static final int PAD = 8;
	// Width kept empty where a one-off task would have had its progress bar.
	private static final int ONE_OFF_BLANK = 100;
	private static final int MENU_ROW = 20;
	private static final int SKILL_CELL = 30;
	private static final int SKILL_COLUMNS = 6;
	private static final long ROW_REFRESH_MS = 1000;
	private static final int DETAIL_LINE = 13;
	private static final int MAX_SEARCH = 40;

	private static final Color BACKGROUND = new Color(38, 33, 27, 242);
	private static final Color MENU_BACKGROUND = new Color(28, 24, 20, 250);
	private static final Color BORDER = new Color(110, 95, 65);
	private static final Color TITLE = new Color(255, 152, 31);
	private static final Color TAB_ON = new Color(70, 60, 45);
	private static final Color TAB_OFF = new Color(48, 42, 34);
	private static final Color ROW_ALT = new Color(255, 255, 255, 10);
	private static final Color ROW_HOVER = new Color(255, 255, 255, 28);
	private static final Color SUBTEXT = new Color(170, 160, 140);
	private static final Color POINTS = new Color(255, 200, 100);
	private static final Color NO_LEVEL = new Color(255, 90, 90);
	private static final Color STAR_ON = new Color(255, 215, 0);
	private static final Color STAR_OFF = new Color(120, 110, 90);
	private static final Color BAR_BACK = new Color(20, 18, 15);
	private static final Color BAR_FILL = new Color(255, 140, 0);
	private static final Color SEARCH_BACK = new Color(20, 18, 15);
	private static final Color DETAIL = new Color(200, 195, 180);
	private static final Color TRACKED_FILL = new Color(255, 140, 0, 45);
	private static final Color BOOK_ON = new Color(190, 140, 90);

	enum Tab
	{
		ACTIVE, SAVED, ARCHIVED, NEW
	}

	/** Ordering only. Each has a natural default direction. */
	enum SortField
	{
		POINTS("Points", false),
		PROGRESS("Progress", false),
		LEVEL("Level needed", true),
		CHUNK("Chunk", true),
		NAME("Name", true);

		final String label;
		final boolean ascendingByDefault;

		SortField(String label, boolean ascendingByDefault)
		{
			this.label = label;
			this.ascendingByDefault = ascendingByDefault;
		}
	}

	/** What's shown. Quests and Progression are the chunk-independent Global tasks. */
	enum Filter
	{
		ALL("All tasks"),
		SKILL("Skill..."),
		TIER("Tier..."),
		COMBAT("Kills & combat"),
		OBTAIN("Obtain items"),
		EQUIP("Equip items"),
		TALK("Talk to"),
		ACHIEVEMENTS("Raids & combat achievements"),
		BOSS("Boss chunks"),
		QUESTS("Quests (global)"),
		PROGRESSION("Level ups (global)");

		final String label;

		Filter(String label)
		{
			this.label = label;
		}
	}

	enum Menu
	{
		NONE, SORT, FILTER, SKILLS, TIERS
	}

	/**
	 * One line in the list: a task, or the header of a skill's level-up tasks. Level-ups
	 * are grouped so the list shows "Mining levels" once instead of every rung; the
	 * header expands to show them.
	 */
	private static final class Entry
	{
		final NuzlockeTask task;
		final String group;
		final List<NuzlockeTask> members;
		final boolean child;

		private Entry(NuzlockeTask task, String group, List<NuzlockeTask> members, boolean child)
		{
			this.task = task;
			this.group = group;
			this.members = members;
			this.child = child;
		}

		static Entry task(NuzlockeTask task, boolean child)
		{
			return new Entry(task, null, null, child);
		}

		static Entry header(String group, List<NuzlockeTask> members)
		{
			return new Entry(null, group, members, false);
		}
	}

	/** A clickable area drawn this frame. Checked in order, so earlier wins. */
	private static final class Hit
	{
		final Shape area;
		final Runnable action;

		Hit(Shape area, Runnable action)
		{
			this.area = area;
			this.action = action;
		}
	}

	private final Client client;
	private final ChunkBlazerPlugin plugin;
	private final ConfigManager configManager;
	private final OverlayManager overlayManager;
	private final MouseManager mouseManager;
	private final SkillIconManager skillIcons;
	private final KeyManager keyManager;
	private final EventBus eventBus;
	private final ChunkBlazerConfig config;
	private final ChunkBlazerWorldMapOverlay worldMap;
	private final TaskArchive archive;
	private final ChatboxPanelManager chatboxPanelManager;
	private final ClientThread clientThread;
	private final SavedTaskTracker savedTracker;

	private volatile boolean open;
	private volatile String search = "";
	private volatile boolean searchFocused;
	private volatile boolean currentChunkOnly;
	// A chunk picked from the world map; while set, only its tasks are listed.
	private volatile String pinnedChunk;
	private volatile boolean unlockKeyHeld;
	private int lastRegionSeen = -1;
	private final Set<String> expanded = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private volatile Tab tab = Tab.ACTIVE;
	private volatile SortField sortField = SortField.POINTS;
	private volatile boolean ascending = SortField.POINTS.ascendingByDefault;
	private volatile Filter filter = Filter.ALL;
	private volatile Skill filterSkill;
	private volatile TaskCardTier filterTier;
	private final Set<String> expandedGroups = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private volatile Menu menu = Menu.NONE;
	private volatile int scroll;

	// New-task alert state. newIds: active tasks not seen yet. shownNew: the ones the
	// New tab is showing (snapshot taken on opening). taskButton: where the Points orb
	// is on screen, reported every frame by ChunkBlazerOrbWidget.
	private final Set<String> newIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private volatile Set<String> shownNew = Collections.emptySet();
	private volatile Rectangle taskButton;
	private volatile long newArrivedAt;
	private int ticksLoggedIn;

	private final List<Hit> hits = new ArrayList<>();
	private final List<Hit> menuHits = new ArrayList<>();
	private volatile Runnable hoveredAction;
	private volatile boolean mouseInWindow;

	// Moving the window by its title. windowX/windowY are where it was last drawn;
	// customX/customY are where the player put it (unset = centred in the game view).
	private volatile boolean titleHovered;
	private volatile boolean dragging;
	// Where the drag started (mouse, from the event) and where the window was then.
	// The drag events are swallowed so the game doesn't react, which also means the
	// game's own mouse position stops updating; so movement is read from the events.
	private volatile int pressX;
	private volatile int pressY;
	private volatile int startX;
	private volatile int startY;
	private volatile int windowX;
	private volatile int windowY;
	private volatile boolean moved;
	private volatile int customX;
	private volatile int customY;

	private List<Entry> rows = new ArrayList<>();
	private final Map<String, String> chunkNames = new HashMap<>();
	private final Map<String, List<String>> requirements = new HashMap<>();
	private long rowsBuiltAt;

	private final MouseAdapter mouse = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			if (!open || !mouseInWindow)
			{
				return event;
			}
			if (event.getButton() == MouseEvent.BUTTON1 && titleHovered)
			{
				// Grab the title: the window then moves as far as the mouse does.
				pressX = event.getX();
				pressY = event.getY();
				startX = windowX;
				startY = windowY;
				dragging = true;
				event.consume();
				return event;
			}
			Runnable action = hoveredAction;
			if (event.getButton() == MouseEvent.BUTTON1 && action != null)
			{
				action.run();
			}
			// Clicks inside the window never reach the game (no walking behind it).
			event.consume();
			return event;
		}

		@Override
		public MouseEvent mouseDragged(MouseEvent event)
		{
			if (dragging)
			{
				customX = startX + event.getX() - pressX;
				customY = startY + event.getY() - pressY;
				moved = true;
				event.consume();
			}
			return event;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent event)
		{
			if (dragging)
			{
				dragging = false;
				event.consume();
			}
			return event;
		}
	};

	private final MouseWheelListener wheel = event ->
	{
		if (open && mouseInWindow && menu == Menu.NONE)
		{
			scroll += event.getWheelRotation() * ROW;
			event.consume();
		}
		return event;
	};

	/**
	 * Esc closes the window. Search typing happens in a chatbox input (see startSearch),
	 * not here, so other plugins that remap keys (Key Remapping's WASD camera) don't
	 * eat the letters. While that input is open, Esc closes the input, not the window.
	 */
	private final KeyListener keys = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent event)
		{
		}

		@Override
		public void keyPressed(KeyEvent event)
		{
			if (config.worldMapUnlockKey().matches(event))
			{
				unlockKeyHeld = true;
			}
			if (open && !searchFocused && event.getKeyCode() == KeyEvent.VK_ESCAPE)
			{
				close();
				event.consume();
			}
		}

		@Override
		public void keyReleased(KeyEvent event)
		{
			if (config.worldMapUnlockKey().matches(event))
			{
				unlockKeyHeld = false;
			}
		}
	};

	@Inject
	public TaskBrowserOverlay(Client client, ChunkBlazerPlugin plugin, ConfigManager configManager,
		OverlayManager overlayManager, MouseManager mouseManager, SkillIconManager skillIcons, KeyManager keyManager,
		EventBus eventBus, ChunkBlazerConfig config, ChunkBlazerWorldMapOverlay worldMap, TaskArchive archive,
		ChatboxPanelManager chatboxPanelManager, ClientThread clientThread, SavedTaskTracker savedTracker)
	{
		this.client = client;
		this.plugin = plugin;
		this.configManager = configManager;
		this.overlayManager = overlayManager;
		this.mouseManager = mouseManager;
		this.skillIcons = skillIcons;
		this.keyManager = keyManager;
		this.eventBus = eventBus;
		this.config = config;
		this.worldMap = worldMap;
		this.archive = archive;
		this.chatboxPanelManager = chatboxPanelManager;
		this.clientThread = clientThread;
		this.savedTracker = savedTracker;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGH);
	}

	public void startUp()
	{
		overlayManager.add(this);
		mouseManager.registerMouseListener(mouse);
		mouseManager.registerMouseWheelListener(wheel);
		keyManager.registerKeyListener(keys);
		eventBus.register(this);
		savedTracker.startUp();
	}

	public void shutDown()
	{
		open = false;
		overlayManager.remove(this);
		mouseManager.unregisterMouseListener(mouse);
		mouseManager.unregisterMouseWheelListener(wheel);
		keyManager.unregisterKeyListener(keys);
		eventBus.unregister(this);
		savedTracker.shutDown();
	}

	/** Open or close the window (the Points orb's "Tasks" option). */
	public void toggle()
	{
		if (open)
		{
			close();
			return;
		}
		open = true;
		pinnedChunk = null;
		menu = Menu.NONE;
		stopSearch();
		scroll = 0;
		rowsBuiltAt = 0;
		if (!newIds.isEmpty())
		{
			// Open on just the tasks that arrived since last time.
			shownNew = Collections.unmodifiableSet(new LinkedHashSet<>(newIds));
			tab = Tab.NEW;
		}
	}

	private void close()
	{
		open = false;
		menu = Menu.NONE;
		stopSearch();
		pinnedChunk = null;
		finishNew();
	}

	/** Leaving the New tab: those tasks are now seen, and the window is back to normal. */
	private void finishNew()
	{
		if (tab != Tab.NEW)
		{
			return;
		}
		Set<String> shown = shownNew;
		markSeen(shown);
		newIds.removeAll(shown);
		shownNew = Collections.emptySet();
		tab = Tab.ACTIVE;
		rowsBuiltAt = 0;
	}

	/** Where the Points orb (the button that opens this window) is on screen; null if hidden. */
	public void setTaskButtonBounds(Rectangle bounds)
	{
		taskButton = bounds;
	}

	/**
	 * Map unlock key + click on an UNLOCKED chunk on the world map: show that chunk's
	 * tasks. (The same key + click on an unlockable chunk is the plugin's unlock, which
	 * this leaves alone.)
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!unlockKeyHeld || client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) == null)
		{
			return;
		}
		int regionId = worldMap.getHoveredRegionId();
		if (regionId <= 0 || !plugin.isRegionUnlocked(regionId) || plugin.isFreeRegion(regionId))
		{
			return;
		}
		String name = chunkNameFor(regionId);
		if (name.isEmpty())
		{
			return;
		}
		event.consume();
		finishNew();
		pinnedChunk = name;
		currentChunkOnly = false;
		tab = Tab.ACTIVE;
		filter = Filter.ALL;
		filterSkill = null;
		search = "";
		menu = Menu.NONE;
		open = true;
		refresh();
	}

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		if (!event.isFocused())
		{
			unlockKeyHeld = false;
		}
	}

	// --- New tasks -----------------------------------------------------------

	/**
	 * Each tick, find active chunk tasks that haven't been seen. The very first time an
	 * account runs this there's nothing stored, so everything it already has counts as
	 * seen (no flood of "new" tasks); from then on, anything else is new.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || configManager.getRSProfileKey() == null)
		{
			return;
		}
		if (++ticksLoggedIn < SETTLE_TICKS)
		{
			return;
		}

		List<NuzlockeTask> active = pool(false);
		Set<String> seen = seenIds();
		if (seen == null)
		{
			Set<String> all = new LinkedHashSet<>();
			for (NuzlockeTask task : active)
			{
				all.add(task.getTaskId());
			}
			markSeen(all);
			return;
		}

		Set<String> activeIds = new java.util.HashSet<>();
		boolean arrived = false;
		for (NuzlockeTask task : active)
		{
			String id = task.getTaskId();
			activeIds.add(id);
			if (!seen.contains(id) && newIds.add(id))
			{
				arrived = true;
			}
		}
		// Finished (or otherwise gone) before being looked at: no longer new.
		newIds.retainAll(activeIds);
		if (arrived)
		{
			newArrivedAt = System.currentTimeMillis();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
			case CONNECTION_LOST:
				// Re-worked out after the next login, for whichever account it is.
				ticksLoggedIn = 0;
				newIds.clear();
				break;
			default:
				break;
		}
	}

	/** Task ids this account has seen, or null if nothing has ever been stored. */
	private Set<String> seenIds()
	{
		String raw = configManager.getRSProfileConfiguration(CONFIG_GROUP, SEEN_KEY);
		if (raw == null)
		{
			return null;
		}
		Set<String> ids = new java.util.HashSet<>();
		for (String id : raw.split(","))
		{
			if (!id.trim().isEmpty())
			{
				ids.add(id.trim());
			}
		}
		return ids;
	}

	private void markSeen(Set<String> ids)
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		Set<String> seen = seenIds();
		Set<String> updated = seen == null ? new LinkedHashSet<>() : new LinkedHashSet<>(seen);
		updated.addAll(ids);
		configManager.setRSProfileConfiguration(CONFIG_GROUP, SEEN_KEY, String.join(",", updated));
	}

	/**
	 * Type the search in a chatbox input, like the bank search. Key Remapping steps
	 * aside while a chatbox input is open, so every key types normally. The list
	 * filters live as you type; Enter keeps the search, Esc closes the input.
	 */
	private void startSearch()
	{
		if (searchFocused)
		{
			return;
		}
		searchFocused = true;
		String current = search;
		clientThread.invoke(() -> chatboxPanelManager.openTextInput("Search tasks")
			.value(current)
			.onChanged(text ->
			{
				search = text.length() > MAX_SEARCH ? text.substring(0, MAX_SEARCH) : text;
				refresh();
			})
			.onDone((Consumer<String>) text -> searchFocused = false)
			.onClose(() -> searchFocused = false)
			.build());
	}

	/** Close the chatbox search input if it's open (the search text is kept). */
	private void stopSearch()
	{
		if (searchFocused)
		{
			searchFocused = false;
			clientThread.invoke(() -> chatboxPanelManager.close());
		}
	}

	private void refresh()
	{
		scroll = 0;
		rowsBuiltAt = 0;
	}

	// --- Saved tasks (stored per account) -----------------------------------

	private Set<String> savedIds()
	{
		String raw = configManager.getRSProfileConfiguration(CONFIG_GROUP, SAVED_KEY);
		Set<String> ids = new LinkedHashSet<>();
		if (raw != null && !raw.isEmpty())
		{
			for (String id : raw.split(","))
			{
				if (!id.trim().isEmpty())
				{
					ids.add(id.trim());
				}
			}
		}
		return ids;
	}

	private void toggleSaved(String taskId)
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		Set<String> ids = savedIds();
		if (!ids.remove(taskId))
		{
			ids.add(taskId);
		}
		configManager.setRSProfileConfiguration(CONFIG_GROUP, SAVED_KEY, String.join(",", ids));
		rowsBuiltAt = 0;
	}

	/** Archive or restore a task. Archiving the task you're tracking also stops tracking it. */
	private void toggleArchived(NuzlockeTask task)
	{
		boolean archiving = !archive.isArchived(task);
		archive.toggle(task.getTaskId());
		NuzlockeTask tracked = plugin.getSelectedTask();
		if (archiving && tracked != null && task.getTaskId().equals(tracked.getTaskId()))
		{
			plugin.clearSelectedTask();
		}
		rowsBuiltAt = 0;
	}

	// --- Which tasks, in what order -----------------------------------------

	/** Unfinished chunk tasks, plus unfinished Global tasks (quests, level-ups). */
	private List<NuzlockeTask> pool(boolean includeGlobal)
	{
		List<NuzlockeTask> pool = new ArrayList<>();
		for (NuzlockeTask task : plugin.getActiveTasks())
		{
			if (task != null && task.getTaskId() != null && !task.isCompleted())
			{
				pool.add(task);
			}
		}
		if (includeGlobal)
		{
			Set<String> completed = plugin.getCompletedTaskIdSet();
			for (NuzlockeTask task : plugin.getVisibleGlobalTasks())
			{
				if (task != null && task.getTaskId() != null && !completed.contains(task.getTaskId()))
				{
					pool.add(task);
				}
			}
		}
		return pool;
	}

	private Predicate<NuzlockeTask> filterTest()
	{
		Filter current = filter;
		Skill skill = filterSkill;
		TaskCardTier tier = filterTier;
		switch (current)
		{
			case TIER:
				return task -> tier == null || TaskCardTier.fromTask(task) == tier;
			case SKILL:
				return task -> skill == null || isSkillTask(task, skill);
			case COMBAT:
				return task -> typeIs(task, "NPC_KILL", "SLAYER") || categoryIs(task, "combat");
			case OBTAIN:
				return task -> typeIs(task, "OBTAIN");
			case EQUIP:
				return task -> typeIs(task, "EQUIP");
			case TALK:
				return task -> typeIs(task, "NPC_DIALOGUE");
			case ACHIEVEMENTS:
				return task -> typeIs(task, "COMBAT_ACHIEVEMENT", "RAID_CHALLENGE");
			case BOSS:
				return plugin::isBossTask;
			case QUESTS:
				return task -> typeIs(task, "QUEST_CHECK");
			case PROGRESSION:
				return task -> typeIs(task, "SKILL_THRESHOLD");
			default:
				return task -> true;
		}
	}

	private static boolean typeIs(NuzlockeTask task, String... types)
	{
		String type = task.getCompletionType();
		if (type == null)
		{
			return false;
		}
		for (String t : types)
		{
			if (type.equalsIgnoreCase(t))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean categoryIs(NuzlockeTask task, String category)
	{
		return task.getCategory() != null && task.getCategory().toLowerCase().contains(category);
	}

	/** Task trains or is about this skill: its category, its type, or a level-up rung for it. */
	private static boolean isSkillTask(NuzlockeTask task, Skill skill)
	{
		String name = skill.getName().toLowerCase();
		if (task.getCategory() != null && task.getCategory().toLowerCase().contains(name))
		{
			return true;
		}
		if (task.getCompletionType() != null && task.getCompletionType().toLowerCase().startsWith(name))
		{
			return true;
		}
		TaskConstraints c = task.getConstraints();
		return c != null && c.getRequiredSkill() != null && c.getRequiredSkill().equalsIgnoreCase(skill.name());
	}

	private Comparator<NuzlockeTask> sorter()
	{
		Comparator<NuzlockeTask> byName = Comparator.comparing(t -> t.getName() == null ? "" : t.getName());
		Comparator<NuzlockeTask> main;
		switch (sortField)
		{
			case PROGRESS:
				main = Comparator.comparingDouble(TaskBrowserOverlay::fraction);
				break;
			case LEVEL:
				main = Comparator.comparingInt(NuzlockeTask::getLevelRequirement);
				break;
			case CHUNK:
				main = Comparator.comparing((NuzlockeTask t) -> chunkNames.getOrDefault(t.getTaskId(), ""));
				break;
			case NAME:
				main = byName;
				break;
			default:
				main = Comparator.comparingInt(NuzlockeTask::getBasePoints);
				break;
		}
		return (ascending ? main : main.reversed()).thenComparing(byName);
	}

	private List<Entry> currentRows()
	{
		long now = System.currentTimeMillis();
		if (now - rowsBuiltAt < ROW_REFRESH_MS)
		{
			return rows;
		}
		rowsBuiltAt = now;

		if (tab == Tab.NEW)
		{
			// Just the newly arrived tasks: no filter, archive or chunk limits.
			Set<String> shown = shownNew;
			String query = search.trim().toLowerCase();
			List<NuzlockeTask> list = new ArrayList<>();
			for (NuzlockeTask task : pool(false))
			{
				if (!shown.contains(task.getTaskId()))
				{
					continue;
				}
				chunkNames.computeIfAbsent(task.getTaskId(), id -> chunkName(task));
				if (query.isEmpty() || matches(task, query))
				{
					list.add(task);
				}
			}
			list.sort(sorter());
			rows = group(list);
			return rows;
		}

		Set<String> saved = savedIds();
		boolean globalFilter = filter == Filter.QUESTS || filter == Filter.PROGRESSION || filter == Filter.SKILL;
		Predicate<NuzlockeTask> test = filterTest();
		String query = search.trim().toLowerCase();
		String here = pinnedChunk != null ? pinnedChunk : currentChunkOnly ? currentChunkName() : null;
		List<NuzlockeTask> list = new ArrayList<>();
		Set<String> archived = archive.ids();
		for (NuzlockeTask task : pool(globalFilter || tab != Tab.ACTIVE))
		{
			// Archived tasks live only in their own tab.
			if ((tab == Tab.ARCHIVED) != archived.contains(task.getTaskId()))
			{
				continue;
			}
			if (tab == Tab.SAVED && !saved.contains(task.getTaskId()))
			{
				continue;
			}
			// Saved and archived tasks always show: the filter only applies to the Active tab.
			if (tab == Tab.ACTIVE && !test.test(task))
			{
				continue;
			}
			chunkNames.computeIfAbsent(task.getTaskId(), id -> chunkName(task));
			if (!query.isEmpty() && !matches(task, query))
			{
				continue;
			}
			if (here != null && !here.equals(chunkNames.get(task.getTaskId())))
			{
				continue;
			}
			list.add(task);
		}
		list.sort(sorter());
		rows = group(list);
		return rows;
	}

	/** The skill a level-up task belongs to (e.g. "MINING"), or null for any other task. */
	private static String levelUpSkill(NuzlockeTask task)
	{
		if (!typeIs(task, "SKILL_THRESHOLD") || task.getConstraints() == null
			|| task.getConstraints().getRequiredSkill() == null)
		{
			return null;
		}
		return task.getConstraints().getRequiredSkill().toUpperCase();
	}

	private static int requiredLevel(NuzlockeTask task)
	{
		return task.getConstraints() == null ? 0 : task.getConstraints().getRequiredLevel();
	}

	/**
	 * Fold each skill's level-up tasks into one header, placed where its first rung
	 * sorted, with the rungs (lowest level first) listed under it when expanded.
	 */
	private List<Entry> group(List<NuzlockeTask> sorted)
	{
		Map<String, List<NuzlockeTask>> bySkill = new LinkedHashMap<>();
		for (NuzlockeTask task : sorted)
		{
			String skill = levelUpSkill(task);
			if (skill != null)
			{
				bySkill.computeIfAbsent(skill, k -> new ArrayList<>()).add(task);
			}
		}
		for (List<NuzlockeTask> members : bySkill.values())
		{
			members.sort(Comparator.comparingInt(TaskBrowserOverlay::requiredLevel));
		}

		List<Entry> entries = new ArrayList<>();
		Set<String> placed = new java.util.HashSet<>();
		for (NuzlockeTask task : sorted)
		{
			String skill = levelUpSkill(task);
			if (skill == null)
			{
				entries.add(Entry.task(task, false));
				continue;
			}
			if (!placed.add(skill))
			{
				continue;
			}
			List<NuzlockeTask> members = bySkill.get(skill);
			entries.add(Entry.header(skill, members));
			if (expandedGroups.contains(skill))
			{
				for (NuzlockeTask member : members)
				{
					entries.add(Entry.task(member, true));
				}
			}
		}
		return entries;
	}

	/** Name of the chunk the player is standing in ("" if it isn't a known chunk). */
	private String currentChunkName()
	{
		return chunkNameFor(plugin.getCurrentRegionId());
	}

	private String chunkNameFor(int regionId)
	{
		String name = plugin.getRegionName(regionId);
		if (name == null || name.startsWith("Unknown Region"))
		{
			return "";
		}
		return name.replaceAll("\\s*\\(\\d+\\)$", "").trim();
	}

	private boolean matches(NuzlockeTask task, String query)
	{
		return contains(task.getName(), query)
			|| contains(task.getDescription(), query)
			|| contains(NuzlockeTask.displayCategory(task.getCategory()), query)
			|| contains(chunkNames.get(task.getTaskId()), query);
	}

	private static boolean contains(String text, String query)
	{
		return text != null && text.toLowerCase().contains(query);
	}

	/** Plain-English requirements: the description if it adds anything, else common constraints. */
	private List<String> requirementsFor(NuzlockeTask task)
	{
		return requirements.computeIfAbsent(task.getTaskId(), id -> SelectedTaskOverlay.requirementLines(task));
	}
	private String chunkName(NuzlockeTask task)
	{
		if (plugin.isGlobalTask(task.getTaskId()))
		{
			return "Global";
		}
		String name = plugin.getTaskRegionName(task);
		return name == null ? "" : name.replaceAll("\\s*\\(\\d+\\)$", "").trim();
	}

	private static double fraction(NuzlockeTask task)
	{
		int target = Math.max(1, task.getTargetQuantity());
		return Math.min(1.0, task.getCurrentProgress() / (double) target);
	}

	// --- Drawing --------------------------------------------------------------

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!open || client.getGameState() != GameState.LOGGED_IN)
		{
			hoveredAction = null;
			mouseInWindow = false;
			if (!open && client.getGameState() == GameState.LOGGED_IN && !newIds.isEmpty())
			{
				drawNewTaskAlert(graphics);
			}
			return null;
		}

		net.runelite.api.Point mousePoint = client.getMouseCanvasPosition();
		int mx = mousePoint == null ? -1 : mousePoint.getX();
		int my = mousePoint == null ? -1 : mousePoint.getY();
		hits.clear();
		menuHits.clear();

		// Walked into another chunk: rebuild straight away if the list follows you.
		int region = plugin.getCurrentRegionId();
		if (region != lastRegionSeen)
		{
			lastRegionSeen = region;
			if (currentChunkOnly)
			{
				refresh();
			}
		}

		int width = Math.min(MAX_WIDTH, client.getViewportWidth() - 20);
		int height = Math.min(MAX_HEIGHT, client.getViewportHeight() - 20);
		int x = client.getViewportXOffset() + (client.getViewportWidth() - width) / 2;
		int y = client.getViewportYOffset() + (client.getViewportHeight() - height) / 2;
		if (moved)
		{
			// Where the player dragged it, kept fully on screen.
			x = Math.max(0, Math.min(customX, client.getCanvasWidth() - width - 1));
			y = Math.max(0, Math.min(customY, client.getCanvasHeight() - height - 1));
		}
		windowX = x;
		windowY = y;
		Rectangle window = new Rectangle(x, y, width, height);
		boolean menuOpen = menu != Menu.NONE;

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(BACKGROUND);
		graphics.fillRect(x, y, width, height);
		graphics.setColor(BORDER);
		graphics.drawRect(x, y, width, height);

		Font bold = FontManager.getRunescapeBoldFont();
		Font regular = FontManager.getRunescapeFont();
		Font small = FontManager.getRunescapeSmallFont();

		// Header: title + close X.
		graphics.setFont(bold);
		// The title is the handle for moving the window: it brightens on hover.
		Rectangle titleArea = new Rectangle(x, y, PAD + graphics.getFontMetrics().stringWidth("ChunkBlazer Tasks") + 8, HEADER);
		titleHovered = !menuOpen && titleArea.contains(mx, my);
		graphics.setColor(titleHovered || dragging ? Color.WHITE : TITLE);
		graphics.drawString("ChunkBlazer Tasks", x + PAD, y + 20);
		Rectangle close = new Rectangle(x + width - 24, y + 7, 16, 16);
		hits.add(new Hit(close, this::close));
		graphics.setColor(!menuOpen && close.contains(mx, my) ? NO_LEVEL : SUBTEXT);
		graphics.drawLine(close.x + 3, close.y + 3, close.x + 13, close.y + 13);
		graphics.drawLine(close.x + 3, close.y + 13, close.x + 13, close.y + 3);

		// Search box, between the title and the close X.
		int searchX = x + PAD + graphics.getFontMetrics(bold).stringWidth("ChunkBlazer Tasks") + 12;
		String chunkLabel = pinnedChunk != null ? fit(graphics.getFontMetrics(small), pinnedChunk, 120) : "Current chunk";
		int chunkToggleWidth = graphics.getFontMetrics(small).stringWidth(chunkLabel) + 18;
		Rectangle chunkToggle = new Rectangle(close.x - 8 - chunkToggleWidth, y + 6, chunkToggleWidth, 19);
		Rectangle searchBox = new Rectangle(searchX, y + 6, chunkToggle.x - 8 - searchX, 19);
		drawSearch(graphics, small, searchBox, mx, my, menuOpen);
		drawChunkToggle(graphics, small, chunkToggle, chunkLabel, mx, my, menuOpen);

		// Tabs, then Filter and Sort buttons.
		int barY = y + HEADER;
		int buttonHeight = TABS - 4;
		int tabWidth = 80;
		int tabsWidth = tabWidth * 3 + 8;
		if (tab == Tab.NEW)
		{
			// Only the new tasks are on show; closing the window brings the usual tabs back.
			drawTab(graphics, small, new Rectangle(x + PAD, barY, tabsWidth, buttonHeight),
				"New tasks (" + shownNew.size() + ")", Tab.NEW, mx, my, menuOpen);
		}
		else
		{
			drawTab(graphics, small, new Rectangle(x + PAD, barY, tabWidth, buttonHeight),
				"Active (" + countActive() + ")", Tab.ACTIVE, mx, my, menuOpen);
			drawTab(graphics, small, new Rectangle(x + PAD + tabWidth + 4, barY, tabWidth, buttonHeight),
				"Saved (" + countSaved() + ")", Tab.SAVED, mx, my, menuOpen);
			drawTab(graphics, small, new Rectangle(x + PAD + (tabWidth + 4) * 2, barY, tabWidth, buttonHeight),
				"Archived (" + countArchived() + ")", Tab.ARCHIVED, mx, my, menuOpen);
		}

		int buttonWidth = Math.max(70, (width - PAD * 2 - tabsWidth - 8) / 2);
		Rectangle sortButton = new Rectangle(x + width - PAD - buttonWidth, barY, buttonWidth, buttonHeight);
		Rectangle filterButton = new Rectangle(sortButton.x - 4 - buttonWidth, barY, buttonWidth, buttonHeight);
		String filterLabel;
		if (tab != Tab.ACTIVE)
		{
			filterLabel = "off";
		}
		else if (filter == Filter.SKILL && filterSkill != null)
		{
			filterLabel = filterSkill.getName();
		}
		else if (filter == Filter.TIER && filterTier != null)
		{
			filterLabel = filterTier.getDisplayName();
		}
		else
		{
			filterLabel = shortLabel(filter);
		}
		// The Saved and Archived tabs show all their tasks, so the filter button does nothing there.
		Runnable openFilter = tab != Tab.ACTIVE ? () ->
		{
		} : () -> menu = menu == Menu.NONE ? Menu.FILTER : Menu.NONE;
		drawButton(graphics, small, filterButton, "Filter: " + filterLabel,
			menu == Menu.FILTER || menu == Menu.SKILLS || menu == Menu.TIERS, mx, my, menuOpen, openFilter);
		if (tab == Tab.ACTIVE && filter != Filter.ALL)
		{
			drawActiveFilter(graphics, small, filterButton, filterLabel, mx, my, menuOpen);
		}
		drawButton(graphics, small, sortButton, "Sort: " + sortField.label, menu == Menu.SORT,
			mx, my, menuOpen, () -> menu = menu == Menu.NONE ? Menu.SORT : Menu.NONE);
		drawArrow(graphics, sortButton.x + sortButton.width - 10, sortButton.y + sortButton.height / 2, ascending, SUBTEXT);

		// Task list. Expanded rows are taller (their requirements are shown underneath).
		Rectangle list = new Rectangle(x + 1, barY + TABS, width - 2, height - HEADER - TABS - 1);
		List<Entry> entries = currentRows();
		graphics.setFont(small);
		FontMetrics detailMetrics = graphics.getFontMetrics();
		int detailWidth = list.width - 6 - 48;
		List<List<String>> details = new ArrayList<>();
		int contentHeight = 0;
		for (Entry entry : entries)
		{
			List<String> wrapped = new ArrayList<>();
			if (entry.task != null && expanded.contains(entry.task.getTaskId()))
			{
				for (String line : requirementsFor(entry.task))
				{
					wrapped.addAll(wrap(detailMetrics, line, detailWidth));
				}
			}
			details.add(wrapped);
			contentHeight += ROW + (wrapped.isEmpty() ? 0 : wrapped.size() * DETAIL_LINE + 4);
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - list.height)));

		Set<String> saved = savedIds();
		Shape oldClip = graphics.getClip();
		graphics.clip(list);
		if (entries.isEmpty())
		{
			graphics.setFont(regular);
			graphics.setColor(SUBTEXT);
			String message;
			if (pinnedChunk != null)
			{
				message = "No tasks left in " + pinnedChunk + ".";
			}
			else if (currentChunkOnly)
			{
				String here = currentChunkName();
				message = here.isEmpty() ? "You're not in a chunk with tasks." : "No tasks here in " + here + ".";
			}
			else if (tab == Tab.NEW && search.isEmpty())
			{
				message = "No new tasks left to show.";
			}
			else if (tab == Tab.ARCHIVED && search.isEmpty())
			{
				message = "Click a task's book icon to archive it here.";
			}
			else if (tab == Tab.SAVED && search.isEmpty())
			{
				message = "Star tasks in the Active tab to save them here.";
			}
			else
			{
				message = "No tasks match.";
			}
			drawCentered(graphics, message, list);
		}
		int rowY = list.y - scroll;
		for (int i = 0; i < entries.size(); i++)
		{
			Entry entry = entries.get(i);
			List<String> wrapped = details.get(i);
			int rowHeight = ROW + (wrapped.isEmpty() ? 0 : wrapped.size() * DETAIL_LINE + 4);
			if (rowY + rowHeight >= list.y && rowY <= list.y + list.height)
			{
				int indent = entry.child ? 16 : 0;
				Rectangle area = new Rectangle(list.x + indent, rowY, list.width - 6 - indent, rowHeight);
				if (entry.task == null)
				{
					drawGroupRow(graphics, entry, area, i, list, mx, my, regular, small, menuOpen);
				}
				else
				{
					drawRow(graphics, entry.task, area, wrapped, i, saved, list, mx, my, regular, small, menuOpen);
				}
			}
			rowY += rowHeight;
		}
		graphics.setClip(oldClip);

		if (contentHeight > list.height)
		{
			int thumbHeight = Math.max(20, list.height * list.height / contentHeight);
			int thumbY = list.y + (list.height - thumbHeight) * scroll / Math.max(1, contentHeight - list.height);
			graphics.setColor(TAB_OFF);
			graphics.fillRect(list.x + list.width - 5, list.y, 4, list.height);
			graphics.setColor(BORDER);
			graphics.fillRect(list.x + list.width - 5, thumbY, 4, thumbHeight);
		}

		// Dropdowns go on top, and their clicks win over anything underneath.
		if (menu == Menu.SORT)
		{
			drawSortMenu(graphics, small, sortButton, mx, my);
		}
		else if (menu == Menu.FILTER)
		{
			drawFilterMenu(graphics, small, filterButton, mx, my);
		}
		else if (menu == Menu.SKILLS)
		{
			drawSkillMenu(graphics, small, filterButton, mx, my);
		}
		else if (menu == Menu.TIERS)
		{
			drawTierMenu(graphics, small, filterButton, mx, my);
		}

		// Work out what the mouse is over, for the click handler. With a menu open,
		// clicking anywhere outside it just closes it.
		mouseInWindow = window.contains(mx, my) || (menuOpen && insideAny(menuHits, mx, my));
		Runnable hovered = firstHit(menuHits, mx, my);
		if (hovered == null)
		{
			hovered = menuOpen ? () -> menu = Menu.NONE : firstHit(hits, mx, my);
		}
		hoveredAction = hovered;
		return null;
	}

	/**
	 * While the window is closed and there are new tasks: a pulsing orange glow around
	 * the Points orb, and for a few seconds after they arrive, a "New tasks!" pointer
	 * that fades out. The glow keeps going until the window is opened.
	 */
	private void drawNewTaskAlert(Graphics2D graphics)
	{
		Rectangle b = taskButton;
		if (b == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		double pulse = 0.5 + 0.5 * Math.sin(now * 2 * Math.PI / PULSE_MS);

		Composite previousComposite = graphics.getComposite();
		Stroke previousStroke = graphics.getStroke();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		graphics.setColor(new Color(255, 152, 31, (int) (35 * pulse)));
		graphics.fillRoundRect(b.x - 3, b.y - 3, b.width + 6, b.height + 6, 14, 14);
		graphics.setColor(new Color(255, 152, 31, (int) (90 + 150 * pulse)));
		graphics.setStroke(new BasicStroke(2.5f));
		graphics.drawRoundRect(b.x - 3, b.y - 3, b.width + 6, b.height + 6, 14, 14);

		long age = now - newArrivedAt;
		if (age < HINT_MS)
		{
			float alpha = age < HINT_MS - HINT_FADE_MS ? 1f : (HINT_MS - age) / (float) HINT_FADE_MS;
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));

			int count = newIds.size();
			String text = count == 1 ? "1 new task!" : count + " new tasks!";
			graphics.setFont(FontManager.getRunescapeBoldFont());
			FontMetrics fm = graphics.getFontMetrics();

			// Arrow pointing right at the orb, gently bobbing towards it.
			int bob = (int) Math.round(3 * Math.sin(now / 150.0));
			int tipX = b.x - 6 + bob;
			int cy = b.y + b.height / 2;
			graphics.setStroke(new BasicStroke(1f));
			graphics.setColor(TITLE);
			graphics.fillPolygon(new Polygon(
				new int[]{tipX, tipX - 11, tipX - 11},
				new int[]{cy, cy - 7, cy + 7}, 3));
			graphics.fillRect(tipX - 19, cy - 2, 9, 5);

			int boxWidth = fm.stringWidth(text) + 12;
			int boxHeight = fm.getHeight() + 4;
			int boxX = tipX - 19 - boxWidth;
			int boxY = cy - boxHeight / 2;
			graphics.setColor(MENU_BACKGROUND);
			graphics.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
			graphics.setColor(TITLE);
			graphics.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 8, 8);
			graphics.setColor(Color.WHITE);
			graphics.drawString(text, boxX + 6, boxY + (boxHeight + fm.getAscent()) / 2 - 2);
		}

		graphics.setComposite(previousComposite);
		graphics.setStroke(previousStroke);
	}

	private static Runnable firstHit(List<Hit> list, int mx, int my)
	{
		for (Hit hit : list)
		{
			if (hit.area.contains(mx, my))
			{
				return hit.action;
			}
		}
		return null;
	}

	private static boolean insideAny(List<Hit> list, int mx, int my)
	{
		return firstHit(list, mx, my) != null;
	}

	private static String shortLabel(Filter f)
	{
		switch (f)
		{
			case ALL:
				return "All";
			case TIER:
				return "Tier";
			case COMBAT:
				return "Combat";
			case OBTAIN:
				return "Obtain";
			case EQUIP:
				return "Equip";
			case TALK:
				return "Talk to";
			case ACHIEVEMENTS:
				return "Raids & CAs";
			case BOSS:
				return "Bosses";
			case QUESTS:
				return "Quests";
			case PROGRESSION:
				return "Level ups";
			default:
				return "Skill";
		}
	}

	/** "Current chunk" checkbox: show only the chunk you're standing in. */
	private void drawChunkToggle(Graphics2D graphics, Font font, Rectangle area, String label,
		int mx, int my, boolean menuOpen)
	{
		boolean hover = !menuOpen && area.contains(mx, my);
		if (pinnedChunk != null)
		{
			// Showing a chunk picked from the world map: its name with an X to go back.
			hits.add(0, new Hit(area, () ->
			{
				pinnedChunk = null;
				refresh();
			}));
			graphics.setFont(font);
			FontMetrics fm = graphics.getFontMetrics();
			graphics.setColor(TITLE);
			graphics.drawString(label, area.x + 2, area.y + (area.height + fm.getAscent()) / 2 - 1);
			int crossX = area.x + area.width - 9;
			int crossY = area.y + area.height / 2;
			graphics.setColor(hover ? NO_LEVEL : SUBTEXT);
			graphics.drawLine(crossX - 3, crossY - 3, crossX + 3, crossY + 3);
			graphics.drawLine(crossX - 3, crossY + 3, crossX + 3, crossY - 3);
			return;
		}
		hits.add(0, new Hit(area, () ->
		{
			currentChunkOnly = !currentChunkOnly;
			refresh();
		}));
		int box = 10;
		int boxX = area.x + 2;
		int boxY = area.y + (area.height - box) / 2;
		graphics.setColor(SEARCH_BACK);
		graphics.fillRect(boxX, boxY, box, box);
		graphics.setColor(hover || currentChunkOnly ? TITLE : BORDER);
		graphics.drawRect(boxX, boxY, box, box);
		if (currentChunkOnly)
		{
			graphics.fillRect(boxX + 3, boxY + 3, box - 5, box - 5);
		}
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics();
		graphics.setColor(currentChunkOnly || hover ? Color.WHITE : SUBTEXT);
		graphics.drawString(label, boxX + box + 5, area.y + (area.height + fm.getAscent()) / 2 - 1);
	}

	private void drawSearch(Graphics2D graphics, Font font, Rectangle box, int mx, int my, boolean menuOpen)
	{
		hits.add(0, new Hit(box, this::startSearch));
		Rectangle clear = new Rectangle(box.x + box.width - 16, box.y + 2, 14, box.height - 4);
		if (!search.isEmpty())
		{
			hits.add(0, new Hit(clear, () ->
			{
				search = "";
				stopSearch();
				refresh();
			}));
		}

		graphics.setColor(SEARCH_BACK);
		graphics.fillRect(box.x, box.y, box.width, box.height);
		graphics.setColor(searchFocused || (!menuOpen && box.contains(mx, my)) ? TITLE : BORDER);
		graphics.drawRect(box.x, box.y, box.width, box.height);

		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics();
		int textY = box.y + (box.height + fm.getAscent()) / 2 - 1;
		if (search.isEmpty() && !searchFocused)
		{
			graphics.setColor(STAR_OFF);
			graphics.drawString("Search tasks...", box.x + 5, textY);
			return;
		}
		String shown = search;
		while (!shown.isEmpty() && fm.stringWidth(shown) > box.width - 26)
		{
			shown = shown.substring(1);
		}
		graphics.setColor(Color.WHITE);
		graphics.drawString(shown, box.x + 5, textY);
		if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0)
		{
			int caretX = box.x + 5 + fm.stringWidth(shown) + 1;
			graphics.drawLine(caretX, box.y + 4, caretX, box.y + box.height - 4);
		}
		if (!search.isEmpty())
		{
			graphics.setColor(clear.contains(mx, my) ? NO_LEVEL : SUBTEXT);
			graphics.drawLine(clear.x + 3, clear.y + 4, clear.x + 10, clear.y + clear.height - 4);
			graphics.drawLine(clear.x + 3, clear.y + clear.height - 4, clear.x + 10, clear.y + 4);
		}
	}

	/** Split text into lines that fit the width, breaking between words. */
	private static List<String> wrap(FontMetrics fm, String text, int width)
	{
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" "))
		{
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (fm.stringWidth(candidate) > width && line.length() > 0)
			{
				lines.add(line.toString());
				line = new StringBuilder(word);
			}
			else
			{
				line = new StringBuilder(candidate);
			}
		}
		if (line.length() > 0)
		{
			lines.add(line.toString());
		}
		return lines;
	}

	private void drawTab(Graphics2D graphics, Font font, Rectangle area, String label, Tab which,
		int mx, int my, boolean menuOpen)
	{
		hits.add(new Hit(area, () ->
		{
			tab = which;
			refresh();
		}));
		graphics.setColor(tab == which ? TAB_ON : TAB_OFF);
		graphics.fillRect(area.x, area.y, area.width, area.height);
		if (tab == which)
		{
			graphics.setColor(TITLE);
			graphics.fillRect(area.x, area.y + area.height - 2, area.width, 2);
		}
		graphics.setFont(font);
		graphics.setColor(tab == which || (!menuOpen && area.contains(mx, my)) ? Color.WHITE : SUBTEXT);
		drawCentered(graphics, label, area);
	}

	private void drawButton(Graphics2D graphics, Font font, Rectangle area, String label, boolean active,
		int mx, int my, boolean menuOpen, Runnable action)
	{
		hits.add(new Hit(area, action));
		graphics.setColor(active || (!menuOpen && area.contains(mx, my)) ? TAB_ON : TAB_OFF);
		graphics.fillRect(area.x, area.y, area.width, area.height);
		graphics.setFont(font);
		graphics.setColor(active ? Color.WHITE : SUBTEXT);
		FontMetrics fm = graphics.getFontMetrics();
		String text = fit(fm, label, area.width - 18);
		graphics.drawString(text, area.x + 6, area.y + (area.height + fm.getAscent()) / 2 - 2);
	}

	/**
	 * A filter is narrowing the list: outline the Filter button in orange, colour its
	 * label, and add a small x that clears it, so it's obvious why tasks are missing.
	 */
	private void drawActiveFilter(Graphics2D graphics, Font font, Rectangle button, String label,
		int mx, int my, boolean menuOpen)
	{
		Rectangle clear = new Rectangle(button.x + button.width - 16, button.y + 2, 14, button.height - 4);
		hits.add(0, new Hit(clear, () ->
		{
			filter = Filter.ALL;
			filterSkill = null;
			filterTier = null;
			refresh();
		}));

		graphics.setColor(TAB_ON);
		graphics.fillRect(button.x, button.y, button.width, button.height);
		graphics.setColor(TITLE);
		graphics.drawRect(button.x, button.y, button.width - 1, button.height - 1);
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics();
		graphics.drawString(fit(fm, "Filter: " + label, button.width - 24), button.x + 6,
			button.y + (button.height + fm.getAscent()) / 2 - 2);

		boolean hover = !menuOpen && clear.contains(mx, my);
		graphics.setColor(hover ? NO_LEVEL : SUBTEXT);
		int cx = clear.x + clear.width / 2;
		int cy = clear.y + clear.height / 2;
		graphics.drawLine(cx - 3, cy - 3, cx + 3, cy + 3);
		graphics.drawLine(cx - 3, cy + 3, cx + 3, cy - 3);
	}

	/** Sort dropdown: each option with an up and a down arrow. Clicking the name uses its usual direction. */
	private void drawSortMenu(Graphics2D graphics, Font font, Rectangle button, int mx, int my)
	{
		SortField[] fields = SortField.values();
		Rectangle box = new Rectangle(button.x, button.y + button.height + 2, button.width, fields.length * MENU_ROW + 4);
		drawMenuBox(graphics, box);
		menuHits.add(new Hit(box, () ->
		{
		}));
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics();
		for (int i = 0; i < fields.length; i++)
		{
			SortField field = fields[i];
			Rectangle row = new Rectangle(box.x + 2, box.y + 2 + i * MENU_ROW, box.width - 4, MENU_ROW);
			Rectangle down = new Rectangle(row.x + row.width - 20, row.y, 18, MENU_ROW);
			Rectangle up = new Rectangle(down.x - 20, row.y, 18, MENU_ROW);
			menuHits.add(0, new Hit(up, () -> chooseSort(field, true)));
			menuHits.add(0, new Hit(down, () -> chooseSort(field, false)));
			menuHits.add(menuHits.size() - 1, new Hit(row, () -> chooseSort(field, field.ascendingByDefault)));

			boolean selected = field == sortField;
			if (row.contains(mx, my))
			{
				graphics.setColor(ROW_HOVER);
				graphics.fillRect(row.x, row.y, row.width, row.height);
			}
			graphics.setColor(selected ? Color.WHITE : SUBTEXT);
			graphics.drawString(field.label, row.x + 6, row.y + (MENU_ROW + fm.getAscent()) / 2 - 2);
			drawArrow(graphics, up.x + 9, up.y + MENU_ROW / 2, true,
				selected && ascending ? TITLE : up.contains(mx, my) ? Color.WHITE : STAR_OFF);
			drawArrow(graphics, down.x + 9, down.y + MENU_ROW / 2, false,
				selected && !ascending ? TITLE : down.contains(mx, my) ? Color.WHITE : STAR_OFF);
		}
	}

	private void chooseSort(SortField field, boolean asc)
	{
		sortField = field;
		ascending = asc;
		menu = Menu.NONE;
		refresh();
	}

	private void drawFilterMenu(Graphics2D graphics, Font font, Rectangle button, int mx, int my)
	{
		Filter[] filters = Filter.values();
		int boxWidth = Math.max(button.width, 170);
		Rectangle box = new Rectangle(button.x + button.width - boxWidth, button.y + button.height + 2,
			boxWidth, filters.length * MENU_ROW + 4);
		drawMenuBox(graphics, box);
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics();
		for (int i = 0; i < filters.length; i++)
		{
			Filter f = filters[i];
			Rectangle row = new Rectangle(box.x + 2, box.y + 2 + i * MENU_ROW, box.width - 4, MENU_ROW);
			menuHits.add(new Hit(row, () ->
			{
				if (f == Filter.SKILL)
				{
					menu = Menu.SKILLS;
					return;
				}
				if (f == Filter.TIER)
				{
					menu = Menu.TIERS;
					return;
				}
				filter = f;
				filterSkill = null;
				filterTier = null;
				menu = Menu.NONE;
				refresh();
			}));
			if (row.contains(mx, my))
			{
				graphics.setColor(ROW_HOVER);
				graphics.fillRect(row.x, row.y, row.width, row.height);
			}
			graphics.setColor(f == filter ? Color.WHITE : SUBTEXT);
			graphics.drawString(f.label, row.x + 6, row.y + (MENU_ROW + fm.getAscent()) / 2 - 2);
			if (f == Filter.SKILL || f == Filter.TIER)
			{
				drawArrowRight(graphics, row.x + row.width - 10, row.y + MENU_ROW / 2, SUBTEXT);
			}
		}
		menuHits.add(new Hit(box, () ->
		{
		}));
	}

	/** Grid of skill icons; clicking one filters to that skill. */
	private void drawSkillMenu(Graphics2D graphics, Font font, Rectangle button, int mx, int my)
	{
		List<Skill> skills = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				skills.add(skill);
			}
		}
		int rowsNeeded = (skills.size() + SKILL_COLUMNS - 1) / SKILL_COLUMNS;
		int boxWidth = SKILL_COLUMNS * SKILL_CELL + 8;
		int boxHeight = rowsNeeded * SKILL_CELL + 8 + 18;
		Rectangle box = new Rectangle(button.x + button.width - boxWidth, button.y + button.height + 2, boxWidth, boxHeight);
		drawMenuBox(graphics, box);

		String hoveredName = "Pick a skill";
		for (int i = 0; i < skills.size(); i++)
		{
			Skill skill = skills.get(i);
			Rectangle cell = new Rectangle(box.x + 4 + (i % SKILL_COLUMNS) * SKILL_CELL,
				box.y + 4 + (i / SKILL_COLUMNS) * SKILL_CELL, SKILL_CELL, SKILL_CELL);
			menuHits.add(new Hit(cell, () ->
			{
				filter = Filter.SKILL;
				filterSkill = skill;
				filterTier = null;
				menu = Menu.NONE;
				refresh();
			}));
			boolean hover = cell.contains(mx, my);
			if (hover || skill == filterSkill)
			{
				graphics.setColor(hover ? ROW_HOVER : TAB_ON);
				graphics.fillRect(cell.x, cell.y, cell.width, cell.height);
			}
			if (hover)
			{
				hoveredName = skill.getName();
			}
			BufferedImage icon = skillIcons.getSkillImage(skill, true);
			if (icon != null)
			{
				graphics.drawImage(icon, cell.x + (cell.width - icon.getWidth()) / 2,
					cell.y + (cell.height - icon.getHeight()) / 2, null);
			}
		}
		graphics.setFont(font);
		graphics.setColor(SUBTEXT);
		drawCentered(graphics, hoveredName, new Rectangle(box.x, box.y + box.height - 20, box.width, 18));
		menuHits.add(new Hit(box, () ->
		{
		}));
	}

	/** Tier picker: Easy to Master, each with its card colour. */
	private void drawTierMenu(Graphics2D graphics, Font font, Rectangle button, int mx, int my)
	{
		TaskCardTier[] tiers = TaskCardTier.values();
		int boxWidth = Math.max(button.width, 140);
		Rectangle box = new Rectangle(button.x + button.width - boxWidth, button.y + button.height + 2,
			boxWidth, tiers.length * MENU_ROW + 4);
		drawMenuBox(graphics, box);
		graphics.setFont(font);
		FontMetrics fm = graphics.getFontMetrics();
		for (int i = 0; i < tiers.length; i++)
		{
			TaskCardTier tier = tiers[i];
			Rectangle row = new Rectangle(box.x + 2, box.y + 2 + i * MENU_ROW, box.width - 4, MENU_ROW);
			menuHits.add(new Hit(row, () ->
			{
				filter = Filter.TIER;
				filterTier = tier;
				filterSkill = null;
				menu = Menu.NONE;
				refresh();
			}));
			if (row.contains(mx, my) || tier == filterTier)
			{
				graphics.setColor(row.contains(mx, my) ? ROW_HOVER : TAB_ON);
				graphics.fillRect(row.x, row.y, row.width, row.height);
			}
			graphics.setColor(tier.getAccent());
			graphics.fillRect(row.x + 6, row.y + (MENU_ROW - 10) / 2, 10, 10);
			graphics.setColor(tier == filterTier ? Color.WHITE : SUBTEXT);
			int points = tier.ordinal() + 1;
			graphics.drawString(tier.getDisplayName() + " (" + points + (points == 1 ? " pt)" : " pts)"),
				row.x + 22, row.y + (MENU_ROW + fm.getAscent()) / 2 - 2);
		}
		menuHits.add(new Hit(box, () ->
		{
		}));
	}

	/** Header for one skill's level-up tasks; click to expand or collapse its rungs. */
	private void drawGroupRow(Graphics2D graphics, Entry entry, Rectangle row, int index, Rectangle list,
		int mx, int my, Font regular, Font small, boolean menuOpen)
	{
		boolean hover = !menuOpen && list.contains(mx, my) && row.contains(mx, my);
		if (hover)
		{
			graphics.setColor(ROW_HOVER);
			graphics.fillRect(row.x, row.y, row.width, row.height);
		}
		else if (index % 2 == 1)
		{
			graphics.setColor(ROW_ALT);
			graphics.fillRect(row.x, row.y, row.width, row.height);
		}
		if (list.contains(mx, my))
		{
			hits.add(new Hit(row.intersection(list), () ->
			{
				if (!expandedGroups.remove(entry.group))
				{
					expandedGroups.add(entry.group);
				}
				rowsBuiltAt = 0;
			}));
		}

		boolean isExpanded = expandedGroups.contains(entry.group);
		Color arrowColor = hover ? Color.WHITE : SUBTEXT;
		if (isExpanded)
		{
			drawArrow(graphics, row.x + 9, row.y + ROW / 2, false, arrowColor);
		}
		else
		{
			drawArrowRight(graphics, row.x + 8, row.y + ROW / 2, arrowColor);
		}

		int textX = row.x + 42;
		try
		{
			BufferedImage icon = skillIcons.getSkillImage(Skill.valueOf(entry.group), true);
			if (icon != null)
			{
				graphics.drawImage(icon, row.x + 18 + (18 - icon.getWidth()) / 2,
					row.y + (ROW - icon.getHeight()) / 2, null);
			}
		}
		catch (IllegalArgumentException ignored)
		{
			// Not a skill name we know; just leave the icon out.
		}

		String skillName = entry.group.charAt(0) + entry.group.substring(1).toLowerCase();
		int points = 0;
		for (NuzlockeTask member : entry.members)
		{
			points += member.getBasePoints();
		}
		int rightEdge = row.x + row.width - 8;

		graphics.setFont(regular);
		FontMetrics fm = graphics.getFontMetrics();
		String pointsText = points + " pts";
		graphics.setColor(POINTS);
		graphics.drawString(pointsText, rightEdge - fm.stringWidth(pointsText), row.y + 16);
		graphics.setColor(Color.WHITE);
		graphics.drawString(skillName + " levels", textX, row.y + 16);

		graphics.setFont(small);
		graphics.setColor(SUBTEXT);
		int left = entry.members.size();
		String info = left + (left == 1 ? " level-up left" : " level-ups left")
			+ " - next: level " + requiredLevel(entry.members.get(0));
		graphics.drawString(info, textX, row.y + 30);
	}

	private void drawMenuBox(Graphics2D graphics, Rectangle box)
	{
		graphics.setColor(MENU_BACKGROUND);
		graphics.fillRect(box.x, box.y, box.width, box.height);
		graphics.setColor(BORDER);
		graphics.drawRect(box.x, box.y, box.width, box.height);
	}

	private void drawRow(Graphics2D graphics, NuzlockeTask task, Rectangle row, List<String> detail, int index,
		Set<String> saved, Rectangle list, int mx, int my, Font regular, Font small, boolean menuOpen)
	{
		boolean mouseOverList = !menuOpen && list.contains(mx, my);
		String taskId = task.getTaskId();
		NuzlockeTask tracked = plugin.getSelectedTask();
		boolean isTracked = tracked != null && taskId != null && taskId.equals(tracked.getTaskId());
		if (isTracked)
		{
			// The task shown in the task box: warm fill, orange edge bar and outline.
			graphics.setColor(TRACKED_FILL);
			graphics.fillRect(row.x, row.y, row.width, row.height);
			graphics.setColor(TITLE);
			graphics.fillRect(row.x, row.y, 3, row.height);
			graphics.drawRect(row.x, row.y, row.width - 1, row.height - 1);
		}
		else if (mouseOverList && row.contains(mx, my))
		{
			graphics.setColor(ROW_HOVER);
			graphics.fillRect(row.x, row.y, row.width, row.height);
		}
		else if (index % 2 == 1)
		{
			graphics.setColor(ROW_ALT);
			graphics.fillRect(row.x, row.y, row.width, row.height);
		}

		boolean isSaved = saved.contains(taskId);

		// Expand arrow (only for tasks with requirements), the star, then the archive
		// book. All are added before the row so they win the click.
		Rectangle arrow = new Rectangle(row.x + 2, row.y + (ROW - 18) / 2, 14, 18);
		Rectangle star = new Rectangle(row.x + 18, row.y + (ROW - 18) / 2, 18, 18);
		Rectangle book = new Rectangle(row.x + 38, row.y + (ROW - 18) / 2, 14, 18);
		boolean isArchived = archive.isArchived(task);
		boolean expandable = !requirementsFor(task).isEmpty();
		if (list.contains(mx, my))
		{
			if (expandable)
			{
				hits.add(new Hit(arrow.intersection(list), () ->
				{
					if (!expanded.remove(taskId))
					{
						expanded.add(taskId);
					}
				}));
			}
			hits.add(new Hit(star.intersection(list), () -> toggleSaved(taskId)));
			hits.add(new Hit(book.intersection(list), () -> toggleArchived(task)));
			// Clicking the tracked task again stops tracking it; any other row tracks that task.
			hits.add(new Hit(row.intersection(list), () ->
			{
				if (isTracked)
				{
					plugin.clearSelectedTask();
				}
				else
				{
					plugin.selectTaskFromGame(task);
				}
			}));
		}
		if (expandable)
		{
			Color arrowColor = mouseOverList && arrow.contains(mx, my) ? Color.WHITE : SUBTEXT;
			if (expanded.contains(taskId))
			{
				drawArrow(graphics, arrow.x + 7, arrow.y + 9, false, arrowColor);
			}
			else
			{
				drawArrowRight(graphics, arrow.x + 6, arrow.y + 9, arrowColor);
			}
		}
		boolean starHover = mouseOverList && star.contains(mx, my);
		drawStar(graphics, star.x + 9, star.y + 9, 8, isSaved || starHover ? STAR_ON : STAR_OFF, isSaved);
		boolean bookHover = mouseOverList && book.contains(mx, my);
		TaskArchive.drawBook(graphics, book.x + 2, book.y + 3, 10, 12,
			isArchived || bookHover ? BOOK_ON : STAR_OFF, isArchived);

		int textX = book.x + book.width + 6;
		int rightEdge = row.x + row.width - 8;

		// Line 1: name, points.
		graphics.setFont(regular);
		FontMetrics fm = graphics.getFontMetrics();
		String points = task.getBasePoints() + (task.getBasePoints() == 1 ? " pt" : " pts");
		int pointsWidth = fm.stringWidth(points);
		graphics.setColor(POINTS);
		graphics.drawString(points, rightEdge - pointsWidth, row.y + 16);
		boolean canDo = plugin.meetsLevelRequirement(task);
		graphics.setColor(canDo ? Color.WHITE : NO_LEVEL);
		String name = task.getName() == null ? taskId : task.getName();
		if (!canDo)
		{
			name += " (Lvl " + task.getLevelRequirement() + ")";
		}
		graphics.drawString(fit(fm, name, rightEdge - pointsWidth - 10 - textX), textX, row.y + 16);

		// Line 2: category and chunk, then progress. Counted tasks get a bar and
		// "7/19"; one-off tasks (target of 1) leave that space blank, since a "0/1"
		// bar says nothing useful.
		graphics.setFont(small);
		FontMetrics sm = graphics.getFontMetrics();
		int target = Math.max(1, task.getTargetQuantity());
		String chunk = chunkNames.getOrDefault(taskId, "");
		String info = NuzlockeTask.displayCategory(task.getCategory()) + (chunk.isEmpty() ? "" : " - " + chunk);

		int progressStart;
		if (target <= 1)
		{
			// Same space a bar would take, left empty so the rows still line up.
			progressStart = rightEdge - ONE_OFF_BLANK;
		}
		else
		{
			String progress = Math.min(task.getCurrentProgress(), target) + "/" + target;
			int progressWidth = sm.stringWidth(progress);
			graphics.setColor(SUBTEXT);
			graphics.drawString(progress, rightEdge - progressWidth, row.y + 30);

			int barWidth = 70;
			int barX = rightEdge - progressWidth - barWidth - 6;
			graphics.setColor(BAR_BACK);
			graphics.fillRect(barX, row.y + 24, barWidth, 6);
			graphics.setColor(BAR_FILL);
			graphics.fillRect(barX, row.y + 24, (int) Math.round(barWidth * fraction(task)), 6);
			progressStart = barX;
		}
		graphics.setColor(SUBTEXT);
		graphics.drawString(fit(sm, info, progressStart - 10 - textX), textX, row.y + 30);

		// Expanded requirements underneath.
		if (!detail.isEmpty())
		{
			graphics.setFont(small);
			graphics.setColor(DETAIL);
			int lineY = row.y + ROW + sm.getAscent() - 2;
			for (String line : detail)
			{
				graphics.drawString(line, textX, lineY);
				lineY += DETAIL_LINE;
			}
		}
	}

	private int countSaved()
	{
		Set<String> saved = savedIds();
		Set<String> archived = archive.ids();
		int count = 0;
		for (NuzlockeTask task : pool(true))
		{
			if (saved.contains(task.getTaskId()) && !archived.contains(task.getTaskId()))
			{
				count++;
			}
		}
		return count;
	}

	/** Chunk tasks you can work on, not counting archived ones. */
	private int countActive()
	{
		Set<String> archived = archive.ids();
		int count = 0;
		for (NuzlockeTask task : pool(false))
		{
			if (!archived.contains(task.getTaskId()))
			{
				count++;
			}
		}
		return count;
	}

	private int countArchived()
	{
		Set<String> archived = archive.ids();
		int count = 0;
		for (NuzlockeTask task : pool(true))
		{
			if (archived.contains(task.getTaskId()))
			{
				count++;
			}
		}
		return count;
	}

	private static void drawCentered(Graphics2D graphics, String text, Rectangle area)
	{
		FontMetrics fm = graphics.getFontMetrics();
		graphics.drawString(text, area.x + (area.width - fm.stringWidth(text)) / 2,
			area.y + (area.height + fm.getAscent()) / 2 - 2);
	}

	/** Shorten text with "..." so it fits in the given width. */
	private static String fit(FontMetrics fm, String text, int width)
	{
		if (width <= 0)
		{
			return "";
		}
		if (fm.stringWidth(text) <= width)
		{
			return text;
		}
		String ellipsis = "...";
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end) + ellipsis) > width)
		{
			end--;
		}
		return text.substring(0, end) + ellipsis;
	}

	/** Small up (ascending) or down (descending) triangle centred on (cx, cy). */
	private static void drawArrow(Graphics2D graphics, int cx, int cy, boolean up, Color color)
	{
		graphics.setColor(color);
		Polygon arrow = up
			? new Polygon(new int[]{cx - 4, cx + 4, cx}, new int[]{cy + 2, cy + 2, cy - 3}, 3)
			: new Polygon(new int[]{cx - 4, cx + 4, cx}, new int[]{cy - 2, cy - 2, cy + 3}, 3);
		graphics.fillPolygon(arrow);
	}

	private static void drawArrowRight(Graphics2D graphics, int cx, int cy, Color color)
	{
		graphics.setColor(color);
		graphics.fillPolygon(new Polygon(new int[]{cx - 2, cx - 2, cx + 3}, new int[]{cy - 4, cy + 4, cy}, 3));
	}

	private static void drawStar(Graphics2D graphics, int cx, int cy, int radius, Color color, boolean filled)
	{
		int[] xs = new int[10];
		int[] ys = new int[10];
		for (int i = 0; i < 10; i++)
		{
			double r = i % 2 == 0 ? radius : radius * 0.45;
			double angle = Math.PI / 2 + i * Math.PI / 5;
			xs[i] = cx + (int) Math.round(r * Math.cos(angle));
			ys[i] = cy - (int) Math.round(r * Math.sin(angle));
		}
		Polygon star = new Polygon(xs, ys, 10);
		graphics.setColor(color);
		if (filled)
		{
			graphics.fillPolygon(star);
		}
		else
		{
			graphics.drawPolygon(star);
		}
	}
}
