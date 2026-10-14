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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
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
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.util.LinkBrowser;

/**
 * Leagues-style task window, opened from the Points orb (ChunkBlazerOrbWidget).
 *
 * Tabs: Active, Saved (starred), Archived (book button) and, after new tasks arrive,
 * New. Search matches name, description, category and chunk; "Current chunk" follows
 * you. The tasks key + click on an unlocked chunk on the world map opens its tasks.
 * Filter: a skill, a tier, task types (any ticked type) and conditions (all ticked
 * conditions); the eye button hides a type or condition. Sort only changes the order.
 * Settings live in RuneLite's plugin config. Drag the title to move the window.
 *
 * Hover is worked out while drawing, from the game's own mouse position, so clicks
 * line up in stretched and resized modes.
 */
@Singleton
public class TaskBrowserOverlay extends Overlay
{
	private static final String CONFIG_GROUP = "chunkblazer";
	private static final String SEEN_KEY = "seenTasks";

	// New-task alert: ticks to wait after login, and the glow's pulse speed.
	private static final int SETTLE_TICKS = 10;
	private static final long PULSE_MS = 1200;

	private static final int MAX_WIDTH = 600;
	private static final int MAX_HEIGHT = 440;
	private static final int HEADER = 40;
	private static final int TABS = 32;
	private static final int ROW = 40;
	// Each extra line of a long task name.
	private static final int NAME_LINE = 15;
	// Between task cards; the tabs and tier filter down the left; the hint line at the foot.
	private static final int ROW_GAP = 4;
	private static final int SIDEBAR = 132;
	private static final int FOOTER = 20;
	private static final int PAD = 8;
	private static final long ROW_REFRESH_MS = 1000;
	private static final int DETAIL_LINE = 13;
	private static final int SECTION_TEXT = 11;
	private static final int QUEST_CHECKS_PER_TICK = 15;
	private static final int MAX_SEARCH = 40;
	// Arrow directions for arrow().
	private static final int UP = 0;
	private static final int DOWN = 1;
	private static final int RIGHT = 2;

	// The game's interface colours (Skin).
	private static final Color TITLE = new Color(255, 152, 31);
	private static final Color ROW_ALT = new Color(0, 0, 0, 30);
	private static final Color ROW_HOVER = new Color(255, 255, 255, 30);
	private static final Color SUBTEXT = new Color(255, 176, 64);
	private static final Color POINTS = new Color(255, 255, 0);
	private static final Color NO_LEVEL = new Color(255, 70, 60);
	private static final Color STAR_ON = new Color(255, 255, 0);
	private static final Color STAR_OFF = new Color(120, 106, 84);
	private static final Color DARK = new Color(28, 24, 19);
	private static final Color DETAIL = Color.WHITE;
	private static final Color TRACKED_FILL = new Color(255, 152, 31, 46);
	private static final Color BOOK_ON = new Color(255, 176, 64);
	private static final Color SIDEBAR_FILL = new Color(0, 0, 0, 44);
	private static final Color CARD_HOVER = new Color(255, 255, 255, 34);
	private static final Color HERE = new Color(0, 230, 0);
	private static final Color FIELD = new Color(30, 26, 21);
	private static final Color LABEL = new Color(170, 150, 112);

	enum Tab
	{
		ACTIVE, SAVED, ARCHIVED, NEW
	}

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

	/**
	 * Tickable filters. Types are what a task is, so ticking several shows any of them
	 * (Combat + Talk to); conditions narrow whatever is shown, so all ticked ones must
	 * hold (Quests + Can do = ready quests). Hiding either removes those tasks.
	 */
	enum Filter
	{
		COMBAT("Kills & combat", "Combat", false),
		OBTAIN("Obtain items", "Obtain", false),
		EQUIP("Equip items", "Equip", false),
		TALK("Talk to", "Talk to", false),
		ACHIEVEMENTS("Raids & combat achievements", "Raids & CAs", false),
		QUESTS("Quests (global)", "Quests", false),
		PROGRESSION("Level ups (global)", "Level ups", false),
		CAN_DO("Can do now", "Can do", true),
		OFFLINE("Works offline (mobile)", "Offline", true),
		BOSS("Boss chunks", "Bosses", true);

		final String label;
		final String shortLabel;
		final boolean condition;

		Filter(String label, String shortLabel, boolean condition)
		{
			this.label = label;
			this.shortLabel = shortLabel;
			this.condition = condition;
		}
	}

	/** A list line: a task, or the header of one skill's level-up tasks (group set). */
	private static final class Entry
	{
		final NuzlockeTask task;
		final String group;
		final List<NuzlockeTask> members;
		final boolean child;

		Entry(NuzlockeTask task, String group, List<NuzlockeTask> members, boolean child)
		{
			this.task = task;
			this.group = group;
			this.members = members;
			this.child = child;
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
	private final TaskItemOverlay itemOverlay;
	private final ChunkNameBanner banner;
	private final WorldMapLegendOverlay legend;
	private final QuestRequirements quests;

	private volatile boolean open;
	private volatile String search = "";
	private volatile boolean searchFocused;
	private volatile boolean currentChunkOnly;
	// A chunk picked on the world map; while set, only its tasks are listed.
	private volatile String pinnedChunk;
	private volatile boolean tasksKeyHeld;
	private int lastRegionSeen = -1;
	private final Set<String> expanded = ConcurrentHashMap.newKeySet();
	private final Set<String> expandedGroups = ConcurrentHashMap.newKeySet();
	private volatile Tab tab = Tab.ACTIVE;
	private volatile SortField sortField = SortField.POINTS;
	private volatile boolean ascending = SortField.POINTS.ascendingByDefault;
	// Filters. The sets are replaced, never changed in place, so a list build sees one state.
	private volatile Set<Filter> filterOn = Collections.emptySet();
	private volatile Set<Filter> filterHidden = Collections.emptySet();
	private volatile Skill filterSkill;
	private volatile TaskCardTier filterTier;
	private volatile int scroll;
	// A quest link was clicked: scroll to and briefly outline this task.
	// The quest whose requirements are shown over the side panel (null when closed).
	private volatile NuzlockeTask questPane;
	// Stands in for the saved-tasks list in the pane, so Back and closing work the same.
	private static final NuzlockeTask SAVED_PANE = new NuzlockeTask();
	private long questPaneAt;
	// Quests opened from inside the pane, newest first, for its Back link.
	private final Deque<NuzlockeTask> questBack = new ConcurrentLinkedDeque<>();
	// The saved-list row under the mouse, for right-click to unsave.
	private volatile String paneRowId;
	// Where the side panel was when the box was last drawn, to notice it moving.
	private Rectangle paneSide;

	// New-task alert: unseen active tasks, the snapshot the New tab shows, the Points orb.
	private final Set<String> newIds = ConcurrentHashMap.newKeySet();
	private volatile Set<String> shownNew = Collections.emptySet();
	private volatile Rectangle taskButton;
	private int ticksLoggedIn;

	private final List<Hit> hits = new ArrayList<>();
	private volatile Runnable hoveredAction;
	private volatile boolean mouseInWindow;
	// Where the window or box was last drawn, on screen (null when neither is shown).
	private volatile Rectangle shownArea;
	// A hint to show by the mouse this frame (set while drawing, drawn last by drawTip).
	private String tip;
	private String tipQuest;
	private long tipQuestAt;
	private boolean tipQuestFound;

	// Dragging the window by its title. Drag events are swallowed, so movement is read
	// from the events, not the game's mouse position.
	private volatile boolean titleHovered;
	private volatile boolean dragging;
	private volatile boolean moved;
	private volatile int pressX;
	private volatile int pressY;
	private volatile int startX;
	private volatile int startY;
	private volatile int windowX;
	private volatile int windowY;
	private volatile int customX;
	private volatile int customY;
	// The window's Interface Size while it's being drawn (1 for the quest/saved box).

	// The redesigned window: where the sidebar's tab marker is (it slides to the chosen tab),
	// the header's numbers and the tier counts (read once a second), and the ChunkBlazer icon.
	private float tabMarkerY = -1;
	private long statsAt;
	private int statDone;
	private int statChunks;
	private final Map<TaskCardTier, Integer> tierCounts = new EnumMap<>(TaskCardTier.class);
	private static BufferedImage logo;

	private List<Entry> rows = new ArrayList<>();
	private final Map<String, String> chunkNames = new HashMap<>();
	private final Map<String, List<String>> requirements = new HashMap<>();
	private long rowsBuiltAt;

	// The mouse as the window and pane see it; the game stops following it over them.
	private volatile java.awt.Point paneMouse = new java.awt.Point(-1, -1);

	private final MouseAdapter mouse = new MouseAdapter()
	{
		// Over the task window or the quest/saved box, the game is told the mouse left the
		// screen, so nothing under it (inventory items, NPCs...) keeps hover text or a tooltip.
		// (Swallowing the move left the game's mouse where it entered, stuck on what was there.)
		// The last drawn area is checked too: mouseInWindow is a frame behind on the way in.
		@Override
		public MouseEvent mouseMoved(MouseEvent event)
		{
			paneMouse = event.getPoint();
			Rectangle area = shownArea;
			if ((open || questPane != null) && (mouseInWindow || area != null && area.contains(event.getPoint())))
			{
				return new MouseEvent(event.getComponent(), event.getID(), event.getWhen(), event.getModifiersEx(),
					-1, -1, 0, false);
			}
			return event;
		}

		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			// Alt + click is RuneLite moving the free-standing quest/saved box.
			if (!open && questPane == null || !mouseInWindow || event.isAltDown())
			{
				return event;
			}
			if (event.getButton() == MouseEvent.BUTTON1 && titleHovered)
			{
				pressX = event.getX();
				pressY = event.getY();
				startX = windowX;
				startY = windowY;
				dragging = true;
			}
			else if (event.getButton() == MouseEvent.BUTTON1 && hoveredAction != null)
			{
				hoveredAction.run();
			}
			else if (event.getButton() == MouseEvent.BUTTON3 && !open && paneRowId != null)
			{
				// Right-click unsaves in the saved list, as it unstars in the side panel.
				toggleSaved(paneRowId);
			}
			// Clicks inside the window never reach the game.
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
		if ((open || questPane != null) && mouseInWindow)
		{
			scroll += event.getWheelRotation() * ROW;
			event.consume();
		}
		return event;
	};

	// Esc closes the window and the quest/saved box (F-keys close the box too). Search typing goes through a chatbox
	// input (startSearch), so key-remapping plugins don't eat the letters.
	private final KeyListener keys = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent event)
		{
		}

		@Override
		public void keyPressed(KeyEvent event)
		{
			if (config.worldMapTasksKey().matches(event))
			{
				tasksKeyHeld = true;
			}
			// Esc closes the quest/saved box, and so do the F-key tab hotkeys while it covers the
			// inventory, so it never blocks a prayer switch. Typing in chat and moving the camera
			// leave it open (switching tab any other way closes it too: onVarClientIntChanged).
			int code = event.getKeyCode();
			if (code == KeyEvent.VK_ESCAPE || code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12 && !config.movableTaskBox())
			{
				questPane = null;
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
			if (config.worldMapTasksKey().matches(event))
			{
				tasksKeyHeld = false;
			}
		}
	};

	@Inject
	public TaskBrowserOverlay(Client client, ChunkBlazerPlugin plugin, ConfigManager configManager,
		OverlayManager overlayManager, MouseManager mouseManager, SkillIconManager skillIcons, KeyManager keyManager,
		EventBus eventBus, ChunkBlazerConfig config, ChunkBlazerWorldMapOverlay worldMap, TaskArchive archive,
		ChatboxPanelManager chatboxPanelManager, ClientThread clientThread, SavedTaskTracker savedTracker,
		TaskItemOverlay itemOverlay, ChunkNameBanner banner, WorldMapLegendOverlay legend,
		QuestRequirements quests)
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
		this.itemOverlay = itemOverlay;
		this.banner = banner;
		this.legend = legend;
		this.quests = quests;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGH);
		freeBox = new Overlay(plugin)
		{
			@Override
			public String getName()
			{
				return "ChunkBlazerTaskBox";
			}

			@Override
			public Dimension render(Graphics2D graphics)
			{
				Rectangle at = getBounds();
				if (!config.movableTaskBox() || open || questPane == null || at == null
					|| client.getGameState() != GameState.LOGGED_IN)
				{
					return null;
				}
				Rectangle size = sidePanel();
				drawQuestPane(graphics, new Rectangle(0, 0, size.width, size.height), at.x, at.y);
				return new Dimension(size.width, size.height);
			}
		};
		freeBox.setPosition(OverlayPosition.BOTTOM_RIGHT);
		freeBox.setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	// With "Movable Quest/Saved Box" on, the box is its own overlay, so RuneLite's
	// Alt + drag moves it and remembers where; it keeps the inventory's size.
	private final Overlay freeBox;

	/** Registers this window and the other UI overlays (item outlines, banner, map legend). */
	public void startUp()
	{
		overlayManager.add(this);
		overlayManager.add(freeBox);
		mouseManager.registerMouseListener(mouse);
		mouseManager.registerMouseWheelListener(wheel);
		keyManager.registerKeyListener(keys);
		eventBus.register(this);
		quests.load();
		savedTracker.startUp();
		overlayManager.add(itemOverlay);
		overlayManager.add(banner);
		overlayManager.add(legend);
	}

	public void shutDown()
	{
		open = false;
		overlayManager.remove(this);
		overlayManager.remove(freeBox);
		mouseManager.unregisterMouseListener(mouse);
		mouseManager.unregisterMouseWheelListener(wheel);
		keyManager.unregisterKeyListener(keys);
		eventBus.unregister(this);
		savedTracker.shutDown();
		overlayManager.remove(itemOverlay);
		overlayManager.remove(banner);
		overlayManager.remove(legend);
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
		questPane = null;
		pinnedChunk = null;
		stopSearch();
		refresh();
		if (!newIds.isEmpty())
		{
			shownNew = Collections.unmodifiableSet(new LinkedHashSet<>(newIds));
			tab = Tab.NEW;
		}
	}

	private void close()
	{
		open = false;
		stopSearch();
		pinnedChunk = null;
		finishNew();
	}

	/** Leaving the New tab: its tasks are now seen. */
	private void finishNew()
	{
		if (tab != Tab.NEW)
		{
			return;
		}
		markSeen(shownNew);
		newIds.removeAll(shownNew);
		shownNew = Collections.emptySet();
		tab = Tab.ACTIVE;
		rowsBuiltAt = 0;
	}

	/** Where the Points orb is on screen (null if hidden), for the new-task alert. */
	public void setTaskButtonBounds(Rectangle bounds)
	{
		taskButton = bounds;
	}

	/**
	 * Tasks key + click on an unlocked chunk on the world map: show that chunk's tasks.
	 * Any other click out in the game world (walk, attack, chop...) closes the window,
	 * like the game's own interfaces do. Clicks on interfaces, like the orb, don't.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (open && !tasksKeyHeld && event.getMenuAction() != MenuAction.CANCEL
			&& event.getMenuEntry().getWidget() == null)
		{
			close();
		}
		if (!tasksKeyHeld || client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) == null)
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
		clearFilters();
		search = "";
		open = true;
		refresh();
	}

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		if (!event.isFocused())
		{
			tasksKeyHeld = false;
		}
	}

	// --- New tasks -----------------------------------------------------------

	/**
	 * Find active chunk tasks not seen yet. With nothing stored (first run on an account),
	 * everything present counts as seen, so there's no flood of "new" tasks.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || configManager.getRSProfileKey() == null)
		{
			return;
		}
		// Every tick from login, so the side panel fills as soon as the tasks arrive.
		updateSideList();
		if (++ticksLoggedIn < SETTLE_TICKS)
		{
			return;
		}
		if (open || questPane != null)
		{
			quests.refreshStates(QUEST_CHECKS_PER_TICK);
		}
		Set<String> active = new LinkedHashSet<>();
		for (NuzlockeTask task : pool(false))
		{
			active.add(task.getTaskId());
		}
		Set<String> seen = idSet(SEEN_KEY);
		if (seen == null)
		{
			markSeen(active);
			return;
		}
		for (String id : active)
		{
			if (!seen.contains(id))
			{
				newIds.add(id);
			}
		}
		newIds.retainAll(active);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			ticksLoggedIn = 0;
			newIds.clear();
			quests.reset();
		}
	}

	// --- Side panel list --------------------------------------------------------

	// The side panel's task list: stats, the selected task, then Saved, Chunk, Active and
	// Global sections of stone task cards. Click a heading to fold it. It uses the window's task data, so none of that logic is repeated here.
	private final JList<Object> sideList = new JList<Object>()
	{
		// Full panel width even when every section is folded (BoxLayout stops at preferred).
		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	};
	private final Set<String> sideFolded = ConcurrentHashMap.newKeySet();
	private String sideKey = "";
	private boolean sideAdded;
	private volatile int sideHover = -1;
	// Points, Chunks, Tasks and Tokens, each in its own black-edged box.
	private static final String[] STAT_NAMES = {"Points", "Chunks", "Tasks", "Tokens"};
	private final JLabel[] sideStats = new JLabel[STAT_NAMES.length];
	// Finished cards, by what they show, so a repaint doesn't lay out their HTML again.
	private final Map<String, JLabel> sideCards = new HashMap<>();
	// The saved ids the list was last built with (read once a build, not once a card).
	private volatile Set<String> sideSaved = Collections.emptySet();
	// The filters and sort live here; the window shows what's set. sideSync keeps them in step.
	private final JComboBox<String> sideSkill = new JComboBox<>();
	private final JComboBox<String> sideTier = new JComboBox<>();
	private final JComboBox<String> sideSort = new JComboBox<>();
	private final Map<Filter, JButton> sideBoxes = new EnumMap<>(Filter.class);
	private final JButton sideFold = new JButton();
	private final JButton sideClear = new JButton("Clear");
	// The list's text size (A- / A+), remembered per account.
	private volatile float sideSize = 16f;
	private final JPanel sideFilterBox = new JPanel(new GridLayout(0, 2, 4, 4));
	private boolean sideSyncing;

	/** Once a tick: add the list to the side panel, and refresh it while it's on screen. */
	private void updateSideList()
	{
		ChunkBlazerPanel panel = plugin.getPanel();
		if (!sideAdded && panel != null)
		{
			sideAdded = true;
			String folded = configManager.getRSProfileConfiguration(CONFIG_GROUP, "sideFolded");
			sideFolded.addAll(Arrays.asList((folded == null ? "Active,Global" : folded).split(",")));
			loadFilters();
			sideList.setCellRenderer((list, item, index, selected, focus) -> sideRow(item, index == sideHover));
			java.awt.event.MouseAdapter sideMouse = new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					hoverRow(sideList.locationToIndex(e.getPoint()));
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hoverRow(-1);
				}

				// On press, not click: a click is lost if the mouse moves at all in between.
				@Override
				public void mousePressed(MouseEvent e)
				{
					int i = sideList.locationToIndex(e.getPoint());
					Object item = i < 0 ? null : sideList.getModel().getElementAt(i);
					if (item instanceof String)
					{
						String section = ((String) item).replaceAll(" \\(.*", "");
						if (!sideFolded.remove(section))
						{
							sideFolded.add(section);
						}
						configManager.setRSProfileConfiguration(CONFIG_GROUP, "sideFolded", String.join(",", sideFolded));
					}
					else if (item instanceof NuzlockeTask && SwingUtilities.isRightMouseButton(e))
					{
						toggleSaved(((NuzlockeTask) item).getTaskId());
					}
					else if (item instanceof NuzlockeTask)
					{
						trackOrOpen((NuzlockeTask) item, TaskBrowserOverlay.this::openQuestPane);
					}
					// Redraw now rather than on the next game tick (up to 0.6s away).
					sideKey = "";
					clientThread.invoke(TaskBrowserOverlay.this::updateSideList);
				}
			};
			sideList.addMouseListener(sideMouse);
			sideList.addMouseMotionListener(sideMouse);
			sideList.setBackground(new Color(40, 40, 40));
			JPanel stats = new JPanel(new GridLayout(1, STAT_NAMES.length, 4, 0));
			stats.setOpaque(false);
			for (int i = 0; i < sideStats.length; i++)
			{
				// Plain labels, not HTML, so the game's pixel fonts stay their real size.
				JPanel box = new JPanel(new GridLayout(2, 1));
				box.setBackground(PanelStyle.FIELD);
				box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.BLACK),
					new EmptyBorder(2, 0, 3, 0)));
				sideStats[i] = statLabel("0", FontManager.getRunescapeBoldFont(), PanelStyle.YELLOW);
				box.add(sideStats[i]);
				box.add(statLabel(STAT_NAMES[i], FontManager.getRunescapeSmallFont(), PanelStyle.ORANGE));
				stats.add(box);
			}
			stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, stats.getPreferredSize().height));
			panel.addTaskList(stats);
			panel.addTaskList(sideControls());
			sideFold.addActionListener(e ->
			{
				sideFilterBox.setVisible(!sideFilterBox.isVisible());
				sideSync();
				panel.revalidate();
			});
			panel.addTaskList(sideList);
			panel.setOldSchool();
		}
		if (!sideList.isShowing())
		{
			return;
		}
		Set<String> saved = savedIds();
		Set<String> archived = archive.ids();
		String here = currentChunkName();
		List<NuzlockeTask> chunk = new ArrayList<>();
		List<NuzlockeTask> globals = new ArrayList<>();
		Predicate<NuzlockeTask> test = filterTest(filterOn, filterHidden, filterSkill, filterTier);
		for (NuzlockeTask task : pool(true))
		{
			chunkNames.computeIfAbsent(task.getTaskId(), k -> chunkName(task));
			if (!archived.contains(task.getTaskId()) && test.test(task))
			{
				(plugin.isGlobalTask(task.getTaskId()) ? globals : chunk).add(task);
			}
		}
		Comparator<NuzlockeTask> order = sorter(sortField, ascending);
		chunk.sort(order);
		globals.sort(order);
		NuzlockeTask tracked = plugin.getSelectedTask();
		List<Object> items = new ArrayList<>();
		// Only while something is tracked.
		if (tracked != null)
		{
			addSection(items, "Selected", Collections.singletonList(tracked));
		}
		List<NuzlockeTask> savedTasks = new ArrayList<>(chunk);
		savedTasks.addAll(globals);
		savedTasks.removeIf(t -> !saved.contains(t.getTaskId()));
		addSection(items, "Saved", savedTasks);
		// Saved tasks are listed once, under Saved.
		chunk.removeIf(t -> saved.contains(t.getTaskId()));
		globals.removeIf(t -> saved.contains(t.getTaskId()));
		List<NuzlockeTask> inChunk = new ArrayList<>(chunk);
		inChunk.removeIf(t -> !here.equals(chunkNames.get(t.getTaskId())));
		addSection(items, "Chunk", inChunk);
		addSection(items, "Active", chunk);
		addSection(items, "Global", globals);

		// The stat boxes from the original panel's top row.
		int[] numbers = {plugin.getTotalPoints(), paidChunks(), plugin.getCompletedTaskIdSet().size(), plugin.getBossTokens()};
		String stats = Arrays.toString(numbers);
		// Only rebuild when something changed, so the panel doesn't jump while scrolling.
		StringBuilder key = new StringBuilder(stats + sideSize + filterLabel() + sortField + ascending + saved);
		items.forEach(o -> key.append(o instanceof NuzlockeTask
			? ((NuzlockeTask) o).getTaskId() + ((NuzlockeTask) o).getCurrentProgress() : o));
		if (key.toString().equals(sideKey))
		{
			return;
		}
		sideKey = key.toString();
		SwingUtilities.invokeLater(() ->
		{
			// A whole new model swapped in at once: clearing the old one flashed an empty list.
			DefaultListModel<Object> model = new DefaultListModel<>();
			items.forEach(model::addElement);
			int height = sideList.getPreferredSize().height;
			sideSaved = saved;
			sideList.setModel(model);
			for (int i = 0; i < numbers.length; i++)
			{
				sideStats[i].setText(String.valueOf(numbers[i]));
			}
			sideSync();
			// The panel is only laid out again when the list changed height.
			if (sideList.getPreferredSize().height != height)
			{
				panel.revalidate();
			}
			sideList.repaint();
		});
	}

	/**
	 * The Skill and Tier dropdowns and a button per type or condition, two to a row and folded
	 * under one button. A click cycles a button off, on (\u2713) and hidden (\u2717). Sort sits below.
	 */
	private JPanel sideFilters()
	{
		sideFilterBox.setOpaque(false);
		sideFilterBox.setVisible(false);
		sideSkill.addItem("Skills");
		Arrays.stream(Skill.values()).filter(k -> k != Skill.OVERALL).forEach(k -> sideSkill.addItem(k.getName()));
		sideTier.addItem("Tiers");
		Arrays.stream(TaskCardTier.values()).forEach(t -> sideTier.addItem(t.getDisplayName()));
		// Each sort field twice: its usual direction first, so "Points" high to low leads.
		for (SortField field : SortField.values())
		{
			for (boolean asc : new boolean[]{field.ascendingByDefault, !field.ascendingByDefault})
			{
				sideSort.addItem("Sort: " + field.label + (asc ? " \u25B2" : " \u25BC"));
			}
		}
		sideFilterBox.add(sideSkill);
		sideFilterBox.add(sideTier);
		for (JComboBox<String> combo : Arrays.asList(sideSkill, sideTier, sideSort))
		{
			combo.setFont(FontManager.getRunescapeSmallFont());
			combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			combo.setForeground(Color.WHITE);
			combo.addActionListener(e ->
			{
				if (sideSyncing)
				{
					return;
				}
				int tier = sideTier.getSelectedIndex();
				int sort = sideSort.getSelectedIndex();
				filterSkill = sideSkill.getSelectedIndex() < 1 ? null
					: Skill.valueOf(String.valueOf(sideSkill.getSelectedItem()).toUpperCase());
				filterTier = tier < 1 ? null : TaskCardTier.values()[tier - 1];
				sortField = SortField.values()[sort / 2];
				ascending = sortField.ascendingByDefault == (sort % 2 == 0);
				saveFilters();
				refresh();
				sideChanged();
			});
		}
		for (Filter f : Filter.values())
		{
			JButton button = smooth(new JButton(f.shortLabel));
			button.setFont(FontManager.getRunescapeSmallFont());
			sideBoxes.put(f, button);
			button.addActionListener(e ->
			{
				toggleFilter(f, filterOn.contains(f) || filterHidden.contains(f));
				sideChanged();
			});
			sideFilterBox.add(button);
		}
		sideFold.setFont(FontManager.getRunescapeSmallFont());
		return sideFilterBox;
	}

	/** Filters (with Clear beside it), the filter buttons, then Sort with A- / A+ beside it, spaced out. */
	private JPanel sideControls()
	{
		sideClear.setFont(FontManager.getRunescapeSmallFont());
		sideClear.addActionListener(e ->
		{
			clearFilters();
			refresh();
			sideChanged();
		});
		JPanel sizes = new JPanel(new GridLayout(1, 2, 2, 0));
		sizes.setOpaque(false);
		String size = configManager.getRSProfileConfiguration(CONFIG_GROUP, "sideTextSize");
		sideSize = size == null ? 16f : Float.parseFloat(size);
		for (int step : new int[]{-2, 2})
		{
			JButton button = smooth(new JButton(step < 0 ? "A-" : "A+"));
			button.setFont(FontManager.getRunescapeSmallFont());
			button.addActionListener(e ->
			{
				sideSize = Math.max(12, Math.min(24, sideSize + step));
				configManager.setRSProfileConfiguration(CONFIG_GROUP, "sideTextSize", sideSize);
				sideChanged();
			});
			sizes.add(button);
		}
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setOpaque(false);
		controls.setBorder(new EmptyBorder(6, 0, 8, 0));
		for (JComponent row : Arrays.asList(row(sideFold, sideClear), sideFilters(), row(sideSort, sizes)))
		{
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setBorder(new EmptyBorder(0, 0, 5, 0));
			controls.add(row);
		}
		return controls;
	}

	/** {@code main} filling the width with {@code side} at its right. */
	private static JPanel row(JComponent main, JComponent side)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.add(main);
		row.add(side, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(main.getPreferredSize().height, side.getPreferredSize().height) + 5));
		return row;
	}

	/** Show the current filters and sort on the side panel's controls (the window's tier list sets them too). */
	private void sideSync()
	{
		sideSyncing = true;
		sideSkill.setSelectedItem(filterSkill == null ? "Skills" : filterSkill.getName());
		sideTier.setSelectedIndex(filterTier == null ? 0 : filterTier.ordinal() + 1);
		sideSort.setSelectedIndex(sortField.ordinal() * 2 + (ascending == sortField.ascendingByDefault ? 0 : 1));
		sideBoxes.forEach((f, box) ->
		{
			box.setText((filterOn.contains(f) ? "\u2713 " : filterHidden.contains(f) ? "\u2717 " : "") + f.shortLabel);
			box.setForeground(filterOn.contains(f) ? new Color(120, 220, 120)
				: filterHidden.contains(f) ? new Color(255, 110, 110) : Color.WHITE);
		});
		sideClear.setEnabled(anyFilter());
		sideFold.setText((sideFilterBox.isVisible() ? "\u25BC Filters" : "\u25B6 Filters") + " (" + (filterOn.size()
			+ filterHidden.size() + (filterSkill != null ? 1 : 0) + (filterTier != null ? 1 : 0)) + ")");
		sideSyncing = false;
	}

	/** A filter changed in the side panel: rebuild the list now rather than on the next game tick. */
	private void sideChanged()
	{
		sideKey = "";
		clientThread.invoke(this::updateSideList);
	}

	/**
	 * The RuneScape fonts are pixel fonts on a 16-pixel grid, so they're only crisp when their
	 * size on screen is 16 (or 32) pixels. Any other size, or Windows display scaling (150% on a
	 * 4K screen), squeezes the grid unevenly; drawn as plain pixels, rows and columns drop out
	 * or double up. Smoothed (greyscale) text stays whole at any size and is just as sharp on
	 * the grid. Greyscale, not the coloured ClearType edges Windows would pick.
	 */
	private static <T extends JComponent> T smooth(T component)
	{
		component.putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		return component;
	}

	private static JLabel statLabel(String text, Font font, Color color)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	/** Chunks paid for with points or a boss token; free, charter and starting chunks don't count. */
	private int paidChunks()
	{
		return plugin.countPayableUnlockedChunks() + (int) plugin.getUnlockedRegionIds().stream()
			.filter(id -> id.trim().matches("\\d+") && plugin.isBossRegion(Integer.parseInt(id.trim()))).count();
	}

	/** A header with its count, then (unless folded) all its tasks. */
	private void addSection(List<Object> items, String title, List<NuzlockeTask> tasks)
	{
		items.add(title + " (" + tasks.size() + ")");
		if (sideFolded.contains(title))
		{
			return;
		}
		// Every task, however many; an empty section is just its heading.
		items.addAll(tasks);
	}

	/** Move the hover highlight, repainting just the row it left and the one it's on. */
	private void hoverRow(int index)
	{
		int old = sideHover;
		sideHover = index;
		for (int i : new int[]{old, index})
		{
			Rectangle cell = i < 0 || i == old && i == index ? null : sideList.getCellBounds(i, i);
			if (cell != null)
			{
				sideList.repaint(cell);
			}
		}
	}

	/**
	 * A section heading (a stone bar) or a task card (stone, with its tier and points), made
	 * once for each thing it can show and reused after that.
	 */
	private JLabel sideRow(Object item, boolean hover)
	{
		String key = item instanceof String ? item + "|" + sideFolded.contains(((String) item).replaceAll(" \\(.*", ""))
			: ((NuzlockeTask) item).getTaskId() + "|" + ((NuzlockeTask) item).getCurrentProgress() + "|"
				+ isTracked((NuzlockeTask) item) + sideSaved.contains(((NuzlockeTask) item).getTaskId());
		key += "|" + hover + "|" + sideSize;
		if (sideCards.size() > 2000)
		{
			sideCards.clear();
		}
		return sideCards.computeIfAbsent(key, k ->
		{
			Font small = FontManager.getRunescapeSmallFont().deriveFont(sideSize);
			return item instanceof String ? sideHeader((String) item, hover, small) : sideCard((NuzlockeTask) item, hover, small);
		});
	}

	/**
	 * Old School heading, like the panel's buttons: light raised stone in a black edge, the
	 * name in yellow, and its count in a dark box at the right.
	 */
	private JLabel sideHeader(String title, boolean hover, Font small)
	{
		String name = title.replaceAll(" \\(.*", "");
		String count = title.substring(name.length()).replaceAll("[ ()]", "");
		boolean folded = sideFolded.contains(name);
		JLabel header = new JLabel(name)
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				Rectangle bar = new Rectangle(0, 6, getWidth(), getHeight() - 8);
				g2.setPaint(Skin.stone(false));
				g2.fillRect(bar.x, bar.y, bar.width, bar.height);
				if (hover)
				{
					g2.setColor(new Color(255, 255, 255, 30));
					g2.fillRect(bar.x, bar.y, bar.width, bar.height);
				}
				g2.setColor(Color.BLACK);
				g2.drawRect(bar.x, bar.y, bar.width - 1, bar.height - 1);
				Skin.bevel(g2, new Rectangle(bar.x + 1, bar.y + 1, bar.width - 2, bar.height - 2), true);
				// Drawn, not a text arrow, as the game's fonts have no arrow glyphs.
				int cy = bar.y + bar.height / 2;
				g2.setColor(getForeground());
				g2.fillPolygon(folded ? new int[]{9, 9, 15} : new int[]{8, 16, 12}, folded ? new int[]{cy - 4, cy + 4, cy}
					: new int[]{cy - 2, cy - 2, cy + 3}, 3);
				g2.setFont(small);
				FontMetrics fm = g2.getFontMetrics();
				Rectangle chip = new Rectangle(bar.x + bar.width - fm.stringWidth(count) - 16, bar.y + 4,
					fm.stringWidth(count) + 10, bar.height - 8);
				g2.setColor(PanelStyle.FIELD);
				g2.fillRect(chip.x, chip.y, chip.width, chip.height);
				g2.setColor(Color.BLACK);
				g2.drawRect(chip.x, chip.y, chip.width - 1, chip.height - 1);
				g2.setColor(PanelStyle.ORANGE);
				g2.drawString(count, chip.x + 5, chip.y + (chip.height + fm.getAscent()) / 2 - 1);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		header.setFont(FontManager.getRunescapeBoldFont().deriveFont(sideSize));
		header.setForeground(name.startsWith("Selected") ? Color.WHITE : PanelStyle.YELLOW);
		header.setBorder(new EmptyBorder(9, 22, 4, 4));
		return smooth(header);
	}

	/**
	 * Old School card: dark stone in a bevelled frame, the tier down the left, white name,
	 * yellow category, orange chunk, and a soft green bar in a dark groove.
	 */
	private JLabel sideCard(NuzlockeTask task, boolean hover, Font small)
	{
		boolean selected = isTracked(task);
		boolean global = plugin.isGlobalTask(task.getTaskId());
		int target = task.getTargetQuantity();
		int progress = Math.min(task.getCurrentProgress(), target);
		Color tier = TaskCardTier.fromTask(task).getAccent();
		String description = task.getDescription() == null ? "" : task.getDescription().trim();
		JLabel card = new JLabel("<html><table cellpadding=0 cellspacing=0 width=150><tr><td>"
			+ "<span style='font-family:RuneScape;font-weight:bold'><font color='#ffffff'>"
			+ (sideSaved.contains(task.getTaskId()) ? "<font color='#ffff00'>&#9733;</font> " : "") + (task.getName() == null ? task.getTaskId() : task.getName())
			+ "</font></span><br><font color='#ffff00'>" + NuzlockeTask.displayCategory(task.getCategory())
			+ (task.getLevelRequirement() > 1 ? "  Lvl " + task.getLevelRequirement() : "")
			+ "</font>" + (global ? "" : "<br><font color='#ff981f'>" + chunkNames.getOrDefault(task.getTaskId(), "") + "</font>")
			+ (description.isEmpty() ? "" : "<br><font color='#c8b48c'>" + description + "</font>")
			+ "</td></tr></table></html>")
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				int w = getWidth();
				int h = getHeight() - 3;
				Rectangle box = new Rectangle(0, 0, w, h);
				g2.setPaint(Skin.stone(true));
				g2.fillRect(0, 0, w, h);
				if (selected || hover)
				{
					g2.setColor(selected ? new Color(255, 152, 31, 40) : new Color(255, 255, 255, 22));
					g2.fillRect(0, 0, w, h);
				}
				Skin.bevel(g2, box, true);
				g2.setColor(tier);
				g2.fillRect(3, 3, 3, h - 6);
				banner(g2, w - 20, 3, tier, task.getBasePoints());
				if (selected)
				{
					g2.setColor(PanelStyle.ORANGE);
					g2.drawRect(0, 0, w - 1, h - 1);
				}
				if (!global)
				{
					String count = progress + "/" + target;
					g2.setFont(getFont());
					int textWidth = g2.getFontMetrics().stringWidth(count);
					int barWidth = w - 32 - textWidth;
					Skin.bar(g2, 13, h - 15, barWidth, 7, (float) (progress / (double) Math.max(1, target)), null, 0, false);
					g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
					g2.setColor(Color.BLACK);
					g2.drawString(count, w - 11 - textWidth, h - 6);
					g2.setColor(PanelStyle.YELLOW);
					g2.drawString(count, w - 12 - textWidth, h - 7);
				}
				g2.dispose();
				super.paintComponent(g);
			}
		};
		card.setFont(small);
		card.setBorder(new EmptyBorder(6, 12, global ? 9 : 25, 6));
		return smooth(card);
	}

	/** A small hanging banner in the tier's colour with the task's points on it (side panel cards). */
	private static void banner(Graphics2D g, int x, int y, Color tier, int points)
	{
		int w = 14;
		int h = 18;
		Polygon flag = new Polygon(new int[]{x, x + w, x + w, x + w / 2, x}, new int[]{y, y, y + h, y + h - 5, y + h}, 5);
		g.setColor(tier);
		g.fillPolygon(flag);
		g.setColor(new Color(255, 255, 255, 60));
		g.fillRect(x + 1, y + 1, w - 2, 2);
		g.setColor(Color.BLACK);
		g.drawPolygon(flag);
		g.setFont(FontManager.getRunescapeSmallFont());
		String text = String.valueOf(points);
		int tx = x + (w - g.getFontMetrics().stringWidth(text)) / 2 + 1;
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.drawString(text, tx + 1, y + 12);
		g.setColor(Color.WHITE);
		g.drawString(text, tx, y + 11);
	}

	/** Saved tasks still to do, in the order they were starred. */
	List<NuzlockeTask> savedTasks()
	{
		Map<String, NuzlockeTask> byId = new HashMap<>();
		pool(true).forEach(t -> byId.put(t.getTaskId(), t));
		Set<String> archived = archive.ids();
		List<NuzlockeTask> list = new ArrayList<>();
		for (String id : savedIds())
		{
			if (byId.containsKey(id) && !archived.contains(id))
			{
				list.add(byId.get(id));
			}
		}
		return list;
	}

	/** The saved bubble: open the saved list over the side panel, or close it. */
	void toggleSavedPane()
	{
		if (questPane == SAVED_PANE)
		{
			questPane = null;
		}
		else
		{
			openQuestPane(SAVED_PANE);
		}
	}

	/** A comma-separated id set stored per account, or null if never stored. */
	private Set<String> idSet(String key)
	{
		String raw = configManager.getRSProfileConfiguration(CONFIG_GROUP, key);
		if (raw == null)
		{
			return null;
		}
		Set<String> ids = new LinkedHashSet<>();
		for (String id : raw.split(","))
		{
			if (!id.trim().isEmpty())
			{
				ids.add(id.trim());
			}
		}
		return ids;
	}

	private void storeIds(String key, Set<String> ids)
	{
		if (configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(CONFIG_GROUP, key, String.join(",", ids));
		}
	}

	private void markSeen(Set<String> ids)
	{
		Set<String> seen = idSet(SEEN_KEY);
		Set<String> updated = seen == null ? new LinkedHashSet<>() : seen;
		updated.addAll(ids);
		storeIds(SEEN_KEY, updated);
	}

	private Set<String> savedIds()
	{
		return archive.savedIds();
	}

	private void toggleSaved(String taskId)
	{
		archive.toggleSaved(taskId);
		rowsBuiltAt = 0;
	}

	/**
	 * A click on a task in any list: a quest opens its requirements ({@code openQuest}),
	 * the tracked task stops being tracked, and any other task is tracked.
	 */
	private void trackOrOpen(NuzlockeTask task, Consumer<NuzlockeTask> openQuest)
	{
		if (QuestRequirements.isQuestTask(task))
		{
			openQuest.accept(task);
		}
		else if (isTracked(task))
		{
			plugin.clearSelectedTask();
		}
		else
		{
			plugin.selectTaskFromGame(task);
		}
	}

	/** True if this is the task being tracked. */
	private boolean isTracked(NuzlockeTask task)
	{
		NuzlockeTask tracked = plugin.getSelectedTask();
		return tracked != null && task != null && tracked.getTaskId().equals(task.getTaskId());
	}

	/** Archive or restore a task. Archiving the tracked task also stops tracking it. */
	private void toggleArchived(NuzlockeTask task)
	{
		boolean archiving = !archive.isArchived(task);
		archive.toggle(task.getTaskId());
		if (archiving && isTracked(task))
		{
			plugin.clearSelectedTask();
		}
		rowsBuiltAt = 0;
	}

	// --- Search ---------------------------------------------------------------

	/** Search typed in a chatbox input, like the bank search; the list filters as you type. */
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

	// --- Which tasks, in what order -----------------------------------------

	/** Unfinished chunk tasks, plus unfinished Global tasks (quests, level-ups) if asked. */
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

	private int count(boolean includeGlobal, Predicate<String> test)
	{
		int count = 0;
		for (NuzlockeTask task : pool(includeGlobal))
		{
			count += test.test(task.getTaskId()) ? 1 : 0;
		}
		return count;
	}

	/**
	 * Skill and tier must match, at least one ticked type (if any), every ticked
	 * condition, and no hidden filter.
	 */
	private Predicate<NuzlockeTask> filterTest(Set<Filter> on, Set<Filter> hidden, Skill skill, TaskCardTier tier)
	{
		boolean anyType = on.stream().anyMatch(f -> !f.condition);
		return task -> (skill == null || isSkillTask(task, skill))
			&& (tier == null || TaskCardTier.fromTask(task) == tier)
			&& on.stream().allMatch(f -> !f.condition || matches(f, task))
			&& (!anyType || on.stream().anyMatch(f -> !f.condition && matches(f, task)))
			&& hidden.stream().noneMatch(f -> matches(f, task));
	}

	private boolean matches(Filter f, NuzlockeTask task)
	{
		switch (f)
		{
			case COMBAT:
				return typeIs(task, "NPC_KILL", "SLAYER")
					|| (task.getCategory() != null && task.getCategory().toLowerCase().contains("combat"));
			case OBTAIN:
				return typeIs(task, "OBTAIN");
			case EQUIP:
				return typeIs(task, "EQUIP");
			case TALK:
				return typeIs(task, "NPC_DIALOGUE");
			case ACHIEVEMENTS:
				return typeIs(task, "COMBAT_ACHIEVEMENT", "RAID_CHALLENGE");
			case QUESTS:
				return typeIs(task, "QUEST_CHECK");
			case PROGRESSION:
				return typeIs(task, "SKILL_THRESHOLD");
			case CAN_DO:
				return canDo(task);
			case OFFLINE:
				return isOfflineTrackable(task);
			default:
				return plugin.isBossTask(task);
		}
	}

	/**
	 * True if a task done away from RuneLite (on mobile) still completes at the next
	 * RuneLite login: it's checked against saved game state. Quests (QuestCheckModule
	 * sweep), real levels (ProgressionModule), combat achievement varps, persistent unlock
	 * varbits, items held in inventory/bank/worn (ObtainModule; the bank once opened) and
	 * worn equipment (EquipModule). Kills, skilling XP drops, dialogue and the active-prayer
	 * varbit (it resets) need RuneLite watching.
	 */
	static boolean isOfflineTrackable(NuzlockeTask task)
	{
		return typeIs(task, "QUEST_CHECK", "SKILL_THRESHOLD", "COMBAT_ACHIEVEMENT", "EQUIP", "OBTAIN")
			|| (typeIs(task, "VARBIT_CHECK", "VARP_CHECK") && "unlock".equalsIgnoreCase(task.getCategory()));
	}

	private boolean anyFilter()
	{
		return filterSkill != null || filterTier != null || !filterOn.isEmpty() || !filterHidden.isEmpty();
	}

	private void clearFilters()
	{
		filterOn = Collections.emptySet();
		filterHidden = Collections.emptySet();
		filterSkill = null;
		filterTier = null;
		saveFilters();
	}

	/** Tick or untick a filter (show) or its eye (hide). One undoes the other. */
	private void toggleFilter(Filter f, boolean hide)
	{
		Set<Filter> on = EnumSet.noneOf(Filter.class);
		on.addAll(filterOn);
		Set<Filter> hidden = EnumSet.noneOf(Filter.class);
		hidden.addAll(filterHidden);
		Set<Filter> mine = hide ? hidden : on;
		if (!mine.remove(f))
		{
			mine.add(f);
			(hide ? on : hidden).remove(f);
		}
		filterOn = Collections.unmodifiableSet(on);
		filterHidden = Collections.unmodifiableSet(hidden);
		saveFilters();
		refresh();
	}

	/** Filters and sort are shared by the window and side panel, and remembered per account. */
	private void saveFilters()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		configManager.setRSProfileConfiguration(CONFIG_GROUP, "taskFilters", (filterSkill == null ? "" : filterSkill.name())
			+ ";" + (filterTier == null ? "" : filterTier.name()) + ";" + names(filterOn) + ";" + names(filterHidden)
			+ ";" + sortField.name() + ";" + ascending);
	}

	private static String names(Set<Filter> filters)
	{
		return filters.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
	}

	private void loadFilters()
	{
		String raw = configManager.getRSProfileConfiguration(CONFIG_GROUP, "taskFilters");
		String[] p = raw == null ? new String[0] : raw.split(";", -1);
		if (p.length < 6)
		{
			return;
		}
		try
		{
			filterSkill = p[0].isEmpty() ? null : Skill.valueOf(p[0]);
			filterTier = p[1].isEmpty() ? null : TaskCardTier.valueOf(p[1]);
			filterOn = Collections.unmodifiableSet(filterSet(p[2]));
			filterHidden = Collections.unmodifiableSet(filterSet(p[3]));
			sortField = SortField.valueOf(p[4]);
			ascending = Boolean.parseBoolean(p[5]);
		}
		catch (IllegalArgumentException e)
		{
			clearFilters();
		}
	}

	private static Set<Filter> filterSet(String csv)
	{
		Set<Filter> set = EnumSet.noneOf(Filter.class);
		for (String name : csv.split(","))
		{
			if (!name.isEmpty())
			{
				set.add(Filter.valueOf(name));
			}
		}
		return set;
	}

	/** "Mining + Tier 5 + Combat + no Bosses": every active filter. */
	private String filterLabel()
	{
		List<String> parts = new ArrayList<>();
		if (filterSkill != null)
		{
			parts.add(filterSkill.getName());
		}
		if (filterTier != null)
		{
			parts.add(filterTier.getDisplayName());
		}
		filterOn.forEach(f -> parts.add(f.shortLabel));
		filterHidden.forEach(f -> parts.add("no " + f.shortLabel));
		return parts.isEmpty() ? "All" : String.join(" + ", parts);
	}

	private static boolean typeIs(NuzlockeTask task, String... types)
	{
		String type = task.getCompletionType();
		return type != null && Arrays.stream(types).anyMatch(type::equalsIgnoreCase);
	}

	/** The task trains or needs this skill (exact match: Runecrafting isn't Crafting). */
	private static boolean isSkillTask(NuzlockeTask task, Skill skill)
	{
		if (TaskTargetExtras.categorySkill(task.getCategory()) == skill
			|| TaskTargetExtras.requirements(task).containsKey(skill))
		{
			return true;
		}
		String type = task.getCompletionType();
		if (type != null && type.toLowerCase().startsWith(skill.getName().toLowerCase()))
		{
			return true;
		}
		TaskConstraints c = task.getConstraints();
		return c != null && skill.name().equalsIgnoreCase(c.getRequiredSkill());
	}

	private Comparator<NuzlockeTask> sorter(SortField by, boolean asc)
	{
		Comparator<NuzlockeTask> byName = Comparator.comparing(t -> t.getName() == null ? "" : t.getName());
		Comparator<NuzlockeTask> main;
		switch (by)
		{
			case PROGRESS:
				main = Comparator.comparingDouble(TaskTargetExtras::progress);
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
		return (asc ? main : main.reversed()).thenComparing(byName);
	}

	/** The list for the current tab, search, chunk and filters; rebuilt at most once a second. */
	private List<Entry> currentRows()
	{
		long now = System.currentTimeMillis();
		if (now - rowsBuiltAt < ROW_REFRESH_MS)
		{
			return rows;
		}
		rowsBuiltAt = now;

		String query = search.trim().toLowerCase();
		Set<String> saved = savedIds();
		Set<String> archived = archive.ids();
		Set<String> shown = shownNew;
		Set<Filter> on = filterOn;
		Predicate<NuzlockeTask> test = filterTest(filterOn, filterHidden, filterSkill, filterTier);
		String here = pinnedChunk != null ? pinnedChunk : currentChunkOnly ? currentChunkName() : null;
		// Global tasks (quests, level-ups) join the Active list when asked for, or when searching.
		boolean global = tab == Tab.SAVED || tab == Tab.ARCHIVED || (tab == Tab.ACTIVE && (filterSkill != null
			|| !query.isEmpty() || on.contains(Filter.QUESTS) || on.contains(Filter.PROGRESSION)
			|| on.contains(Filter.OFFLINE)));

		List<NuzlockeTask> list = new ArrayList<>();
		for (NuzlockeTask task : pool(global))
		{
			String id = task.getTaskId();
			chunkNames.computeIfAbsent(id, k -> chunkName(task));
			boolean keep;
			if (tab == Tab.NEW)
			{
				// Just the newly arrived tasks: no filter, archive or chunk limits.
				keep = shown.contains(id);
			}
			else
			{
				keep = (tab == Tab.ARCHIVED) == archived.contains(id)
					&& (tab != Tab.SAVED || saved.contains(id))
					&& (tab != Tab.ACTIVE || test.test(task))
					&& (here == null || here.equals(chunkNames.get(id)));
			}
			if (keep && (query.isEmpty() || matchesSearch(task, query)))
			{
				list.add(task);
			}
		}
		list.sort(sorter(sortField, ascending));
		rows = group(list);
		return rows;
	}

	/** The skill a level-up task belongs to (e.g. "MINING"), or null for any other task. */
	private static String levelUpSkill(NuzlockeTask task)
	{
		TaskConstraints c = task.getConstraints();
		return typeIs(task, "SKILL_THRESHOLD") && c != null && c.getRequiredSkill() != null
			? c.getRequiredSkill().toUpperCase() : null;
	}

	private static int requiredLevel(NuzlockeTask task)
	{
		return task.getConstraints() == null ? 0 : task.getConstraints().getRequiredLevel();
	}

	/** Fold each skill's level-ups into one header where its first rung sorted; rungs under it. */
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
		bySkill.values().forEach(m -> m.sort(Comparator.comparingInt(TaskBrowserOverlay::requiredLevel)));

		List<Entry> entries = new ArrayList<>();
		Set<String> placed = new HashSet<>();
		for (NuzlockeTask task : sorted)
		{
			String skill = levelUpSkill(task);
			if (skill == null)
			{
				entries.add(new Entry(task, null, null, false));
			}
			else if (placed.add(skill))
			{
				List<NuzlockeTask> members = bySkill.get(skill);
				entries.add(new Entry(null, skill, members, false));
				if (expandedGroups.contains(skill))
				{
					members.forEach(m -> entries.add(new Entry(m, null, null, true)));
				}
			}
		}
		return entries;
	}

	private String currentChunkName()
	{
		return chunkNameFor(plugin.getCurrentRegionId());
	}

	/** Chunk name without its "(region id)" suffix; "" if it isn't a known chunk. */
	private String chunkNameFor(int regionId)
	{
		String name = plugin.getRegionName(regionId);
		return name == null || name.startsWith("Unknown Region") ? "" : stripRegionId(name);
	}

	private String chunkName(NuzlockeTask task)
	{
		if (plugin.isGlobalTask(task.getTaskId()))
		{
			return "Global";
		}
		String name = plugin.getTaskRegionName(task);
		return name == null ? "" : stripRegionId(name);
	}

	private static String stripRegionId(String name)
	{
		return name.replaceAll("\\s*\\(\\d+\\)$", "").trim();
	}

	private boolean matchesSearch(NuzlockeTask task, String query)
	{
		for (String text : new String[]{task.getName(), task.getDescription(),
			NuzlockeTask.displayCategory(task.getCategory()), chunkNames.get(task.getTaskId())})
		{
			if (text != null && text.toLowerCase().contains(query))
			{
				return true;
			}
		}
		return false;
	}

	private List<String> requirementsFor(NuzlockeTask task)
	{
		return requirements.computeIfAbsent(task.getTaskId(), id -> SelectedTaskOverlay.requirementLines(task));
	}

	/** Everything under an expanded task: its requirements, then a quest's requirement sections. */
	private List<QuestRequirements.Line> detailLines(NuzlockeTask task)
	{
		List<QuestRequirements.Line> lines = new ArrayList<>();
		requirementsFor(task).forEach(line -> lines.add(new QuestRequirements.Line(line, DETAIL)));
		lines.addAll(quests.lines(task, this::chunkNameFor));
		return lines;
	}

	// Finished task ids for the header's count, read at most every two seconds.
	private Set<String> doneIds = Collections.emptySet();
	private long doneAt;

	private Set<String> doneIds()
	{
		long now = System.currentTimeMillis();
		if (now - doneAt > 2000)
		{
			doneAt = now;
			doneIds = plugin.getCompletedTaskIdSet();
		}
		return doneIds;
	}

	/** Level checks, real requirements (TaskTargetExtras) and, for quests, their requirements. */
	private boolean canDo(NuzlockeTask task)
	{
		return TaskTargetExtras.canDo(client, plugin, task) && quests.isReady(task);
	}

	/** "(20 Defence, 20 Ranged)", "(Lvl 40)" or "(Not ready)" for a task you can't do yet. */
	private String levelNote(NuzlockeTask task)
	{
		String note = TaskTargetExtras.levelNote(client, plugin, task);
		return note != null ? note : "(Not ready)";
	}

	// --- Drawing --------------------------------------------------------------

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		if (questPane == null)
		{
			paneSide = null;
		}
		if (!open && questPane != null && loggedIn)
		{
			// Anything else that moves the side panel out from under it closes it too.
			Rectangle side = sidePanel();
			if (paneSide != null && !paneSide.equals(side))
			{
				questPane = null;
			}
			else if (!config.movableTaskBox())
			{
				drawQuestPane(graphics, side, 0, 0);
			}
			paneSide = side;
			return null;
		}
		if (!open || !loggedIn)
		{
			hoveredAction = null;
			mouseInWindow = false;
			shownArea = null;
			if (!open && loggedIn && !newIds.isEmpty())
			{
				drawNewTaskAlert(graphics);
			}
			return null;
		}

		int mx = paneMouse.x;
		int my = paneMouse.y;
		hits.clear();

		// Walked into another chunk: rebuild now if the list follows you.
		int region = plugin.getCurrentRegionId();
		if (region != lastRegionSeen)
		{
			lastRegionSeen = region;
			if (currentChunkOnly)
			{
				refresh();
			}
		}

		// Centred over the game view; in fixed mode that's always the 512 x 334 box at the top left.
		Rectangle view = client.isResized() ? new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
			client.getViewportWidth(), client.getViewportHeight()) : new Rectangle(4, 4, 512, 334);
		int width = Math.min(MAX_WIDTH, view.width - 20);
		int height = Math.min(MAX_HEIGHT, view.height - 20);
		int x = view.x + (view.width - width) / 2;
		int y = view.y + (view.height - height) / 2;
		if (moved)
		{
			x = Math.max(0, Math.min(customX, client.getCanvasWidth() - width - 1));
			y = Math.max(0, Math.min(customY, client.getCanvasHeight() - height - 1));
		}
		windowX = x;
		windowY = y;
		Rectangle window = new Rectangle(x, y, width, height);

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Font bold = Skin.bold();
		Font regular = Skin.regular();
		Font small = Skin.small();

		// The window: a soft shadow, a rounded body darkening downwards, a lighter header
		// (Classic; Old School's stone frame comes from Skin).
		Skin.stoneFrame(graphics, window, HEADER);

		// Header: the ChunkBlazer icon and title (the drag handle), the account's numbers, close.
		BufferedImage icon = logo();
		int titleX = x + PAD + (icon != null ? 22 : 0);
		if (icon != null)
		{
			graphics.drawImage(icon, x + PAD, y + (HEADER - 16) / 2, null);
		}
		graphics.setFont(bold);
		FontMetrics bm = graphics.getFontMetrics();
		titleHovered = new Rectangle(x, y, titleX - x + bm.stringWidth("ChunkBlazer Tasks") + 10, HEADER).contains(mx, my);
		graphics.setColor(titleHovered || dragging ? Color.WHITE : TITLE);
		Skin.text(graphics, "ChunkBlazer", titleX, y + 25);
		graphics.setColor(new Color(255, 255, 0));
		Skin.text(graphics, "Tasks", titleX + bm.stringWidth("ChunkBlazer "), y + 25);
		Rectangle close = new Rectangle(x + width - 30, y + 10, 20, 20);
		hits.add(new Hit(close, this::close));
		Skin.close(graphics, close, close.contains(mx, my));
		graphics.setFont(small);
		FontMetrics sm = graphics.getFontMetrics();
		long now = System.currentTimeMillis();
		if (now - statsAt > 1000)
		{
			statsAt = now;
			statDone = doneIds().size();
			statChunks = paidChunks();
			countTiers();
		}
		int chipX = close.x - 6;
		for (String[] stat : new String[][]{{statChunks + "", "chunks"}, {statDone + "", "done"},
			{plugin.getTotalPoints() + "", "pts"}})
		{
			chipX -= statChip(graphics, chipX, y + 11, stat[0], stat[1]) + 5;
		}

		// Down the left: the tabs, then a quick tier filter and the current-chunk switch.
		// (A little narrower in the fixed-size client.)
		Rectangle side = new Rectangle(x + 1, y + HEADER + 1, width < 540 ? SIDEBAR - 20 : SIDEBAR, height - HEADER - 2);
		// A sunken stone column, inside the frame.
		Rectangle column = new Rectangle(side.x + 3, side.y + 1, side.width - 3, side.height - 4);
		graphics.setColor(SIDEBAR_FILL);
		graphics.fillRect(column.x, column.y, column.width, column.height);
		Skin.bevel(graphics, column, false);
		int sy = side.y + 10;
		graphics.setFont(small);
		graphics.setColor(LABEL);
		Skin.text(graphics, "LISTS", side.x + PAD + 2, sy + 8);
		sy += 14;
		List<Object[]> tabs = new ArrayList<>();
		if (tab == Tab.NEW)
		{
			tabs.add(new Object[]{Tab.NEW, "New tasks", shownNew.size()});
		}
		else
		{
			Set<String> archived = archive.ids();
			Set<String> saved = savedIds();
			tabs.add(new Object[]{Tab.ACTIVE, "Active", count(false, id -> !archived.contains(id))});
			tabs.add(new Object[]{Tab.SAVED, "Saved", count(true, id -> saved.contains(id) && !archived.contains(id))});
			tabs.add(new Object[]{Tab.ARCHIVED, "Archived", count(true, archived::contains)});
		}
		int chosenY = sy;
		for (Object[] t : tabs)
		{
			if (t[0] == tab)
			{
				chosenY = sy;
			}
			drawTab(graphics, new Rectangle(side.x + 6, sy, side.width - 12, 24), (String) t[1], (Integer) t[2],
				(Tab) t[0], mx, my);
			sy += 27;
		}
		// The marker slides to the chosen tab.
		tabMarkerY = tabMarkerY < 0 ? chosenY : tabMarkerY + (chosenY - tabMarkerY) * 0.3f;
		graphics.setColor(TITLE);
		graphics.fillRect(side.x + 6, Math.round(tabMarkerY) + 4, 3, 16);

		if (tab == Tab.ACTIVE)
		{
			sy += 10;
			graphics.setFont(small);
			graphics.setColor(LABEL);
			Skin.text(graphics, "TIERS", side.x + PAD + 2, sy + 8);
			sy += 13;
			for (TaskCardTier tier : TaskCardTier.values())
			{
				Rectangle row = new Rectangle(side.x + 6, sy, side.width - 12, 19);
				boolean on = filterTier == tier;
				boolean hover = row.contains(mx, my);
				hits.add(new Hit(row, () ->
				{
					filterTier = filterTier == tier ? null : tier;
					saveFilters();
					refresh();
				}));
				if (on || hover)
				{
					graphics.setColor(on ? new Color(tier.getAccent().getRed(), tier.getAccent().getGreen(),
						tier.getAccent().getBlue(), 60) : CARD_HOVER);
					graphics.fillRect(row.x, row.y, row.width, row.height);
				}
				graphics.setColor(tier.getAccent());
				graphics.setColor(Color.BLACK);
				graphics.fillRect(row.x + 6, row.y + 5, 10, 10);
				graphics.setColor(tier.getAccent());
				graphics.fillRect(row.x + 7, row.y + 6, 8, 8);
				graphics.setColor(tier.getAccent().brighter());
				graphics.fillRect(row.x + 7, row.y + 6, 3, 2);
				graphics.setColor(on || hover ? Color.WHITE : SUBTEXT);
				Skin.text(graphics, tier.getDisplayName(), row.x + 21, row.y + 13);
				String n = String.valueOf(tierCounts.getOrDefault(tier, 0));
				graphics.setColor(on ? Color.WHITE : LABEL);
				Skin.text(graphics, n, row.x + row.width - 6 - sm.stringWidth(n), row.y + 13);
				sy += 20;
			}
		}
		// The current-chunk switch (or the chunk picked on the map) sits at the bottom.
		String chunkLabel = pinnedChunk != null ? Skin.fit(sm, pinnedChunk, side.width - 40) : side.width < SIDEBAR ? "This chunk" : "This chunk only";
		drawChunkToggle(graphics, new Rectangle(side.x + 6, side.y + side.height - 30, side.width - 12, 22), chunkLabel, mx, my);

		// The content: search, Filter and Sort along the top, the list, then a hint line.
		int cx = side.x + side.width + PAD;
		int cw = x + width - PAD - cx;
		int barY = y + HEADER + 8;
		int buttonHeight = TABS - 10;
		int buttonWidth = Math.max(78, Math.min(120, cw / 4));
		Rectangle sortButton = new Rectangle(cx + cw - buttonWidth, barY, buttonWidth, buttonHeight);
		Rectangle filterButton = new Rectangle(sortButton.x - 6 - buttonWidth, barY, buttonWidth, buttonHeight);
		graphics.setFont(small);
		drawSearch(graphics, new Rectangle(cx, barY, filterButton.x - 8 - cx, buttonHeight), mx, my);
		// Filters and sort are set in the side panel; here they say what's on, and the x clears.
		// Saved and Archived show all their tasks, so the filter does nothing there.
		boolean filtering = tab == Tab.ACTIVE && anyFilter();
		drawButton(graphics, filterButton, "Filter: " + (tab != Tab.ACTIVE ? "off" : filterLabel()), filtering, mx, my);
		if (filtering)
		{
			// Outlined in orange, so it's clear why tasks are missing.
			graphics.setColor(TITLE);
			graphics.drawRect(filterButton.x, filterButton.y, filterButton.width - 1, filterButton.height - 1);
			Rectangle clear = new Rectangle(filterButton.x + filterButton.width - 16, filterButton.y + 2, 14,
				filterButton.height - 4);
			hits.add(0, new Hit(clear, () ->
			{
				clearFilters();
				refresh();
			}));
			cross(graphics, clear.x + 7, clear.y + clear.height / 2, 3, clear.contains(mx, my) ? NO_LEVEL : SUBTEXT);
		}
		drawButton(graphics, sortButton, "Sort: " + sortField.label, false, mx, my);
		arrow(graphics, sortButton.x + sortButton.width - 10, sortButton.y + sortButton.height / 2,
			ascending ? UP : DOWN, SUBTEXT);

		// Old School: column headings over the list, like the Leagues task table.
		int columns = 16;
		if (columns > 0)
		{
			Rectangle strip = new Rectangle(cx - 2, y + HEADER + TABS - 2, cw + 6, columns);
			graphics.setColor(DARK);
			graphics.fillRect(strip.x, strip.y, strip.width, strip.height);
			Skin.bevel(graphics, strip, true);
			graphics.setFont(small);
			graphics.setColor(TITLE);
			Skin.text(graphics, "Task", strip.x + 52, strip.y + 14);
			Skin.text(graphics, "Points", strip.x + strip.width - 12 - graphics.getFontMetrics().stringWidth("Points"), strip.y + 14);
		}
		Rectangle list = new Rectangle(cx - 2, y + HEADER + TABS + columns, cw + 6, height - HEADER - TABS - FOOTER - 2 - columns);
		int shown = drawList(graphics, list, mx, my, regular, small);

		// The hint line: how many, and what clicks do.
		graphics.setFont(small);
		graphics.setColor(LABEL);
		int footY = y + height - 7;
		Skin.text(graphics, shown + (shown == 1 ? " task" : " tasks"), cx, footY);
		String hint = "Click: track   Shift + click: save   Arrow: details";
		Skin.text(graphics, hint, x + width - PAD - sm.stringWidth(hint), footY);

		finishHover(window, mx, my);
		shownArea = new Rectangle(x, y, width, height);
		drawTip(graphics, mx, my, 0, 0);
		return null;
	}

	/** A header number with its label ("123 pts"), right-aligned to {@code right}; returns its width. */
	private static int statChip(Graphics2D graphics, int right, int y, String value, String label)
	{
		FontMetrics fm = graphics.getFontMetrics();
		int w = fm.stringWidth(value) + fm.stringWidth(label) + 16;
		graphics.setColor(FIELD);
		graphics.fillRect(right - w, y, w, 18);
		Skin.bevel(graphics, new Rectangle(right - w, y, w, 18), false);
		graphics.setColor(POINTS);
		Skin.text(graphics, value, right - w + 6, y + 13);
		graphics.setColor(SUBTEXT);
		Skin.text(graphics, label, right - w + 10 + fm.stringWidth(value), y + 13);
		return w;
	}

	/** Active tasks per tier, for the sidebar's tier filter. */
	private void countTiers()
	{
		tierCounts.clear();
		Set<String> archived = archive.ids();
		for (NuzlockeTask task : pool(false))
		{
			if (!archived.contains(task.getTaskId()))
			{
				tierCounts.merge(TaskCardTier.fromTask(task), 1, Integer::sum);
			}
		}
	}

	/** The ChunkBlazer icon for the header, read once (null if missing). */
	private static BufferedImage logo()
	{
		if (logo == null)
		{
			try (java.io.InputStream in = TaskBrowserOverlay.class.getResourceAsStream("icon.png"))
			{
				logo = in == null ? new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB) : javax.imageio.ImageIO.read(in);
			}
			catch (java.io.IOException e)
			{
				logo = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			}
		}
		return logo.getWidth() > 1 ? logo : null;
	}

	/** The task list as cards, with expanded details, scrolling and the empty-list message. Returns how many tasks. */
	private int drawList(Graphics2D graphics, Rectangle list, int mx, int my, Font regular, Font small)
	{
		List<Entry> entries = currentRows();
		FontMetrics sm = graphics.getFontMetrics(small);
		int detailWidth = list.width - 70;
		FontMetrics rm = graphics.getFontMetrics(regular);
		List<List<QuestRequirements.Line>> details = new ArrayList<>();
		List<Integer> heights = new ArrayList<>();
		int contentHeight = 4;
		int tasks = 0;
		for (Entry entry : entries)
		{
			List<QuestRequirements.Line> wrapped = entry.task != null && expanded.contains(entry.task.getTaskId())
				? wrapDetail(sm, entry.task, detailWidth) : new ArrayList<>();
			details.add(wrapped);
			int nameLines = entry.task == null ? 1 : rowName(rm, entry.task, list.width - 12 - (entry.child ? 18 : 0) - 160).size();
			heights.add(ROW + (nameLines - 1) * NAME_LINE + detailHeight(wrapped));
			contentHeight += heights.get(heights.size() - 1) + ROW_GAP;
			tasks += entry.task != null ? 1 : entry.members.size();
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - list.height)));

		Set<String> saved = savedIds();
		Shape oldClip = graphics.getClip();
		graphics.clip(list);
		if (entries.isEmpty())
		{
			graphics.setFont(regular);
			graphics.setColor(SUBTEXT);
			drawStar(graphics, list.x + list.width / 2, list.y + list.height / 2 - 22, 12, STAR_OFF, false);
			drawCentered(graphics, emptyMessage(), list);
		}
		int rowY = list.y + 4 - scroll;
		for (int i = 0; i < entries.size(); i++)
		{
			Entry entry = entries.get(i);
			int rowHeight = heights.get(i);
			if (rowY + rowHeight >= list.y && rowY <= list.y + list.height)
			{
				int indent = entry.child ? 18 : 0;
				Rectangle area = new Rectangle(list.x + 2 + indent, rowY, list.width - 12 - indent, rowHeight);
				boolean hover = list.contains(mx, my) && area.contains(mx, my);
				// Leagues-style rows: alternately darker, lit under the mouse, a thin line between.
				graphics.setColor(hover ? CARD_HOVER : i % 2 == 0 ? new Color(0, 0, 0, 48) : new Color(0, 0, 0, 22));
				graphics.fillRect(area.x, area.y, area.width, area.height);
				graphics.setColor(new Color(0, 0, 0, 120));
				graphics.drawLine(area.x, area.y + area.height + ROW_GAP / 2, area.x + area.width, area.y + area.height + ROW_GAP / 2);
				if (entry.task == null)
				{
					drawGroupRow(graphics, entry, area, list, mx, my, regular, small);
				}
				else
				{
					drawRow(graphics, entry.task, area, details.get(i), saved, list, mx, my, regular, small);
				}
			}
			rowY += rowHeight + ROW_GAP;
		}
		graphics.setClip(oldClip);
		drawScrollbar(graphics, list, contentHeight);
		return tasks;
	}

	private String emptyMessage()
	{
		if (pinnedChunk != null)
		{
			return "No tasks left in " + pinnedChunk + ".";
		}
		if (currentChunkOnly)
		{
			String here = currentChunkName();
			return here.isEmpty() ? "You're not in a chunk with tasks." : "No tasks here in " + here + ".";
		}
		if (search.isEmpty() && tab != Tab.ACTIVE)
		{
			return tab == Tab.NEW ? "No new tasks left to show."
				: tab == Tab.ARCHIVED ? "Click a task's book icon to archive it here."
				: "Star tasks in the Active tab to save them here.";
		}
		return "No tasks match.";
	}

	/** Work out what a click would do. */
	private void finishHover(Rectangle window, int mx, int my)
	{
		mouseInWindow = window.contains(mx, my);
		hoveredAction = firstHit(hits, mx, my);
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

	/**
	 * While the window is closed with new tasks waiting: a pulsing glow around the Points orb.
	 */
	private void drawNewTaskAlert(Graphics2D graphics)
	{
		Rectangle b = taskButton;
		if (b == null)
		{
			return;
		}
		double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 2 * Math.PI / PULSE_MS);
		Stroke previousStroke = graphics.getStroke();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(new Color(255, 152, 31, (int) (35 * pulse)));
		graphics.fillRect(b.x - 3, b.y - 3, b.width + 6, b.height + 6);
		graphics.setColor(new Color(255, 152, 31, (int) (90 + 150 * pulse)));
		graphics.setStroke(new BasicStroke(2.5f));
		graphics.drawRect(b.x - 3, b.y - 3, b.width + 6, b.height + 6);
		graphics.setStroke(previousStroke);
	}

	// --- Header controls -------------------------------------------------------

	/** "This chunk only" as a switch; or, showing a chunk picked on the map, its name with an x. */
	private void drawChunkToggle(Graphics2D graphics, Rectangle area, String label, int mx, int my)
	{
		boolean hover = area.contains(mx, my);
		hits.add(0, new Hit(area, () ->
		{
			if (pinnedChunk != null)
			{
				pinnedChunk = null;
			}
			else
			{
				currentChunkOnly = !currentChunkOnly;
			}
			refresh();
		}));
		graphics.setFont(Skin.small());
		FontMetrics fm = graphics.getFontMetrics();
		int textY = area.y + (area.height + fm.getAscent()) / 2 - 1;
		if (hover)
		{
			graphics.setColor(CARD_HOVER);
			graphics.fillRect(area.x, area.y, area.width, area.height);
		}
		if (pinnedChunk != null)
		{
			graphics.setColor(TITLE);
			Skin.text(graphics, label, area.x + 6, textY);
			cross(graphics, area.x + area.width - 9, area.y + area.height / 2, 3, hover ? NO_LEVEL : SUBTEXT);
			return;
		}
		// A little switch: the knob slides right and goes orange when on.
		int sw = 22;
		int sx = area.x + 6;
		int sy = area.y + (area.height - 12) / 2;
		graphics.setColor(currentChunkOnly ? new Color(255, 152, 31, 150) : FIELD);
		graphics.fillRect(sx, sy, sw, 12);
		graphics.setColor(currentChunkOnly ? Color.WHITE : SUBTEXT);
		graphics.fillOval(currentChunkOnly ? sx + sw - 11 : sx + 1, sy + 1, 10, 10);
		graphics.setColor(currentChunkOnly || hover ? Color.WHITE : SUBTEXT);
		Skin.text(graphics, label, sx + sw + 6, textY);
	}

	private void drawSearch(Graphics2D graphics, Rectangle box, int mx, int my)
	{
		hits.add(0, new Hit(box, this::startSearch));
		Rectangle clear = new Rectangle(box.x + box.width - 18, box.y + 2, 16, box.height - 4);
		if (!search.isEmpty())
		{
			hits.add(0, new Hit(clear, () ->
			{
				search = "";
				stopSearch();
				refresh();
			}));
		}
		Skin.field(graphics, box, box.contains(mx, my), searchFocused);
		// A magnifying glass.
		int gx = box.x + 8;
		int gy = box.y + box.height / 2;
		graphics.setColor(SUBTEXT);
		graphics.setStroke(new BasicStroke(1.4f));
		graphics.drawOval(gx - 1, gy - 5, 8, 8);
		graphics.drawLine(gx + 6, gy + 2, gx + 9, gy + 5);
		graphics.setStroke(new BasicStroke(1));

		FontMetrics fm = graphics.getFontMetrics();
		int textX = box.x + 22;
		int textY = box.y + (box.height + fm.getAscent()) / 2 - 1;
		if (search.isEmpty() && !searchFocused)
		{
			graphics.setColor(STAR_OFF);
			Skin.text(graphics, Skin.fit(fm, "Search tasks, chunks, categories...", box.width - 30), textX, textY);
			return;
		}
		String shown = search;
		while (!shown.isEmpty() && fm.stringWidth(shown) > box.width - 44)
		{
			shown = shown.substring(1);
		}
		graphics.setColor(Color.WHITE);
		Skin.text(graphics, shown, textX, textY);
		if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0)
		{
			int caretX = textX + 1 + fm.stringWidth(shown);
			graphics.drawLine(caretX, box.y + 4, caretX, box.y + box.height - 5);
		}
		if (!search.isEmpty())
		{
			cross(graphics, clear.x + 8, clear.y + clear.height / 2, 3, clear.contains(mx, my) ? NO_LEVEL : SUBTEXT);
		}
	}

	/** A tab down the sidebar: its name and a count badge. (The marker by the chosen one is drawn after.) */
	private void drawTab(Graphics2D graphics, Rectangle area, String label, int count, Tab which, int mx, int my)
	{
		hits.add(new Hit(area, () ->
		{
			tab = which;
			refresh();
		}));
		boolean chosen = tab == which;
		boolean hover = area.contains(mx, my);
		// Stone tab buttons; the chosen one glows red like the game's open side tab.
		Skin.button(graphics, area, hover, chosen);
		graphics.setFont(Skin.regular());
		FontMetrics fm = graphics.getFontMetrics();
		graphics.setColor(chosen || hover ? Color.WHITE : SUBTEXT);
		Skin.text(graphics, label, area.x + 12, area.y + (area.height + fm.getAscent()) / 2 - 2);
		graphics.setFont(Skin.small());
		fm = graphics.getFontMetrics();
		String n = String.valueOf(count);
		int bw = Math.max(18, fm.stringWidth(n) + 10);
		int bx = area.x + area.width - bw - 4;
		graphics.setColor(chosen ? new Color(255, 152, 31, 170) : FIELD);
		graphics.fillRect(bx, area.y + 5, bw, 14);
		graphics.setColor(chosen ? Color.WHITE : SUBTEXT);
		Skin.text(graphics, n, bx + (bw - fm.stringWidth(n)) / 2, area.y + 16);
	}

	/** A read-out of a setting that's changed in the side panel. */
	private void drawButton(Graphics2D graphics, Rectangle area, String label, boolean active, int mx, int my)
	{
		if (area.contains(mx, my))
		{
			tip = "Filters and sort are set in the ChunkBlazer side panel";
		}
		Skin.field(graphics, area, false, false);
		graphics.setColor(active ? Color.WHITE : SUBTEXT);
		FontMetrics fm = graphics.getFontMetrics();
		Skin.text(graphics, Skin.fit(fm, label, area.width - 22), area.x + 8, area.y + (area.height + fm.getAscent()) / 2 - 1);
	}

	// --- List rows ---------------------------------------------------------------

	/** Header for one skill's level-up tasks; click to show or hide its rungs. */
	private void drawGroupRow(Graphics2D graphics, Entry entry, Rectangle row, Rectangle list, int mx, int my,
		Font regular, Font small)
	{
		boolean hover = list.contains(mx, my) && row.contains(mx, my);
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
		arrow(graphics, row.x + 11, row.y + ROW / 2, expandedGroups.contains(entry.group) ? DOWN : RIGHT,
			hover ? Color.WHITE : SUBTEXT);
		Skill skill = null;
		try
		{
			skill = Skill.valueOf(entry.group);
		}
		catch (IllegalArgumentException ignored)
		{
			// Not a skill we know: no icon.
		}
		drawTaskIcon(graphics, row.x + 22, row.y + (ROW - 22) / 2, skill, null);
		int points = entry.members.stream().mapToInt(NuzlockeTask::getBasePoints).sum();
		int left = entry.members.size();
		String name = entry.group.charAt(0) + entry.group.substring(1).toLowerCase() + " levels";
		String info = left + (left == 1 ? " level-up left" : " level-ups left") + "  ·  next: level "
			+ requiredLevel(entry.members.get(0));
		drawRowText(graphics, row, row.x + 50, name, Color.WHITE, points + " pts", POINTS, info, SUBTEXT, regular, small);
	}

	/**
	 * Line 1: the name (left) and a points chip (right, tinted {@code chipColor}). Line 2: the
	 * info text. Returns where the chip starts, so the row's icons can sit before it.
	 */
	private int drawRowText(Graphics2D graphics, Rectangle row, int textX, String name, Color nameColor, String points,
		Color chipColor, String info, Color infoColor, Font regular, Font small)
	{
		int rightEdge = row.x + row.width - 8;
		graphics.setFont(small);
		FontMetrics sm = graphics.getFontMetrics();
		int chipW = sm.stringWidth(points) + 12;
		int chipX = rightEdge - chipW;
		graphics.setColor(POINTS);
		// A plain number in the Points column, as on the Leagues list.
		Skin.text(graphics, points, rightEdge - sm.stringWidth(points), row.y + 17);
		graphics.setFont(regular);
		FontMetrics fm = graphics.getFontMetrics();
		graphics.setColor(nameColor);
		String[] lines = name.split("\n");
		for (int i = 0; i < lines.length; i++)
		{
			Skin.text(graphics, Skin.fit(fm, lines[i], chipX - 48 - textX), textX, row.y + 17 + i * NAME_LINE);
		}
		if (info != null)
		{
			graphics.setFont(small);
			graphics.setColor(infoColor);
			Skin.text(graphics, Skin.fit(sm, info, rightEdge - 90 - textX), textX, row.y + 32 + (lines.length - 1) * NAME_LINE);
		}
		return chipX;
	}

	/** A skill's icon on a small round plate, or the tier's colour with a letter when there's no skill. */
	private void drawTaskIcon(Graphics2D graphics, int x, int y, Skill skill, NuzlockeTask task)
	{
		// A sunk stone slot, like an inventory or Leagues icon slot.
		graphics.setColor(FIELD);
		graphics.fillRect(x, y, 22, 22);
		Skin.bevel(graphics, new Rectangle(x, y, 22, 22), false);
		BufferedImage image = skill == null ? null : skillIcons.getSkillImage(skill, true);
		if (image != null)
		{
			graphics.drawImage(image, x + (22 - image.getWidth()) / 2, y + (22 - image.getHeight()) / 2, null);
			return;
		}
		Color tier = task == null ? SUBTEXT : TaskCardTier.fromTask(task).getAccent();
		graphics.setColor(new Color(tier.getRed(), tier.getGreen(), tier.getBlue(), 120));
		graphics.fillOval(x + 3, y + 3, 16, 16);
		String letter = task == null || task.getCategory() == null || task.getCategory().isEmpty() ? "?"
			: NuzlockeTask.displayCategory(task.getCategory()).substring(0, 1);
		graphics.setFont(Skin.small());
		graphics.setColor(Color.WHITE);
		drawCentered(graphics, letter, new Rectangle(x, y, 22, 22));
	}

	private void drawRow(Graphics2D graphics, NuzlockeTask task, Rectangle row, List<QuestRequirements.Line> detail,
		Set<String> saved, Rectangle list, int mx, int my, Font regular, Font small)
	{
		boolean inList = list.contains(mx, my);
		boolean hoverRow = inList && row.contains(mx, my);
		String taskId = task.getTaskId();
		boolean isTracked = isTracked(task);
		Color tier = TaskCardTier.fromTask(task).getAccent();
		if (isTracked)
		{
			// The task in the task box: a warm glow and an orange outline.
			graphics.setColor(TRACKED_FILL);
			graphics.fillRect(row.x, row.y, row.width, row.height);
			graphics.setColor(TITLE);
			graphics.drawRect(row.x, row.y, row.width - 1, row.height - 1);
		}
		// Its tier down the left edge.
		graphics.setColor(tier);
		graphics.fillRect(row.x + 2, row.y + 6, 3, row.height - 12);

		// Expand arrow (a quest's opens its pane), and, at the right, star and archive book;
		// added first so they win the click over the row.
		int iconY = row.y + (ROW - 18) / 2;
		Rectangle arrowArea = new Rectangle(row.x + 6, iconY, 14, 18);
		boolean expandable = !requirementsFor(task).isEmpty() || QuestRequirements.isQuestTask(task);
		int textX = row.x + 50;
		boolean canDo = canDo(task);
		List<String> names = rowName(graphics.getFontMetrics(regular), task, row.width - 160);
		String name = String.join("\n", names);
		// Everything under the name moves down a line when the name takes two.
		int lift = (names.size() - 1) * NAME_LINE;
		String chunk = chunkNames.getOrDefault(taskId, "");
		String here = currentChunkName();
		boolean inHere = !chunk.isEmpty() && chunk.equals(here);
		String info = NuzlockeTask.displayCategory(task.getCategory()) + (chunk.isEmpty() ? "" : "  ·  " + (inHere ? "here, " : "") + chunk);
		int chipX = drawRowText(graphics, row, textX, name, canDo ? Color.WHITE : NO_LEVEL,
			task.getBasePoints() + (task.getBasePoints() == 1 ? " pt" : " pts"), tier, info, inHere ? HERE : SUBTEXT, regular, small);

		// A fixed spot from the right edge, so they line up down the list whatever the points say.
		Rectangle book = new Rectangle(row.x + row.width - 70, iconY - 3, 14, 18);
		Rectangle star = new Rectangle(book.x - 20, iconY - 3, 18, 18);
		if (inList)
		{
			if (expandable)
			{
				hits.add(new Hit(arrowArea.intersection(list), () ->
				{
					if (QuestRequirements.isQuestTask(task))
					{
						openQuestPane(task);
					}
					else if (!expanded.remove(taskId))
					{
						expanded.add(taskId);
					}
				}));
			}
			hits.add(new Hit(star.intersection(list), () -> toggleSaved(taskId)));
			hits.add(new Hit(book.intersection(list), () -> toggleArchived(task)));
		}
		drawDetail(graphics, detail, textX, row.y + ROW - 1 + lift, list, mx, my, small);
		if (inList)
		{
			hits.add(new Hit(row.intersection(list), () -> trackOrOpen(task, this::openQuestPane)));
		}
		if (expandable)
		{
			arrow(graphics, arrowArea.x + 7, arrowArea.y + 9, expanded.contains(taskId) ? DOWN : RIGHT,
				inList && arrowArea.contains(mx, my) ? Color.WHITE : SUBTEXT);
		}
		drawTaskIcon(graphics, row.x + 22, row.y + (ROW - 22) / 2, TaskTargetExtras.categorySkill(task.getCategory()), task);
		// Star and book show on hover, and always once they're on.
		boolean isSaved = saved.contains(taskId);
		boolean isArchived = archive.isArchived(task);
		if (hoverRow || isSaved)
		{
			drawStar(graphics, star.x + 9, star.y + 9, 7, isSaved || star.contains(mx, my) ? STAR_ON : STAR_OFF, isSaved);
		}
		if (hoverRow || isArchived)
		{
			TaskArchive.drawBook(graphics, book.x + 2, book.y + 3, 10, 12,
				isArchived || book.contains(mx, my) ? BOOK_ON : STAR_OFF, isArchived);
		}

		// Line 2's right end: "TRACKING", or progress as a small bar and count. One-off
		// tasks (target 1) leave it blank, since a "0/1" bar says nothing.
		graphics.setFont(small);
		FontMetrics sm = graphics.getFontMetrics();
		int rightEdge = row.x + row.width - 8;
		int target = Math.max(1, task.getTargetQuantity());
		if (isTracked)
		{
			String tag = "TRACKING";
			graphics.setColor(TITLE);
			Skin.text(graphics, tag, rightEdge - sm.stringWidth(tag), row.y + 32 + lift);
		}
		else if (target > 1)
		{
			String progress = Math.min(task.getCurrentProgress(), target) + "/" + target;
			int progressWidth = sm.stringWidth(progress);
			graphics.setColor(SUBTEXT);
			Skin.text(graphics, progress, rightEdge - progressWidth, row.y + 32 + lift);
			int barX = rightEdge - progressWidth - 62;
			Skin.bar(graphics, barX, row.y + 26 + lift, 56, 6, (float) TaskTargetExtras.progress(task), tier, 0, false);
		}
	}

	/**
	 * Detail lines from (x, y). Clickable ones get a hit: a section header opens/closes, a
	 * chunk shows on the world map (now, or as soon as it's opened), a quest opens in the pane.
	 */
	private void drawDetail(Graphics2D graphics, List<QuestRequirements.Line> detail, int x, int y, Rectangle clip,
		int mx, int my, Font small)
	{
		graphics.setFont(small);
		FontMetrics sm = graphics.getFontMetrics();
		boolean in = clip.contains(mx, my);
		for (int i = 0; i < detail.size(); i++)
		{
			QuestRequirements.Line line = detail.get(i);
			Rectangle area = detailLineArea(sm, detail, x, y, i);
			if (line.clickable() && in)
			{
				hits.add(new Hit(area.intersection(clip), line.region > 0 ? () -> worldMap.focusRegion(line.region)
					: line.quest != null ? () -> showQuest(line.quest)
					: () -> quests.toggleSection(line.toggle)));
			}
			int lineY = area.y + 1 + sm.getAscent();
			boolean hover = line.clickable() && in && area.contains(mx, my);
			if (hover && line.region > 0)
			{
				tip = worldMap.isMapOpen() ? "Click to show it on the world map" : "Click, then open the world map to see it";
			}
			else if (hover && line.quest != null)
			{
				tip = hasQuestTask(line.quest) ? "Click to see this quest's requirements"
					: "Not in your task list (done, or not a ChunkBlazer quest)";
			}
			// Section headings sit on a faint band; anything clickable lights up under the mouse.
			if (line.header || hover)
			{
				graphics.setColor(hover ? ROW_HOVER : ROW_ALT);
				graphics.fillRect(x - 3, area.y, line.header ? clip.width - 12 : area.width + 6, DETAIL_LINE);
			}
			if (line.header)
			{
				arrow(graphics, x + 3, lineY - 1 - sm.getAscent() / 2, line.open ? DOWN : RIGHT, hover ? Color.WHITE : SUBTEXT);
			}
			int lineX = x + lineIndent(line);
			graphics.setColor(lineColor(line.color));
			Skin.text(graphics, line.text, lineX, lineY);
		}
	}

	/** A requirement's colour in this style: Old School uses the game's own green, red and yellow. */
	private static Color lineColor(Color color)
	{
		if (color == null)
		{
			return color;
		}
		if (color.equals(QuestRequirements.MET))
		{
			return HERE;
		}
		if (color.equals(QuestRequirements.NOT_MET))
		{
			return NO_LEVEL;
		}
		if (color.equals(QuestRequirements.NEXT_TO_YOURS))
		{
			return new Color(255, 255, 0);
		}
		if (color.equals(QuestRequirements.TEXT) || color.equals(DETAIL))
		{
			return DETAIL;
		}
		return color;
	}

	/** A task's detail lines, wrapped to {@code width}. */
	private List<QuestRequirements.Line> wrapDetail(FontMetrics sm, NuzlockeTask task, int width)
	{
		List<QuestRequirements.Line> wrapped = new ArrayList<>();
		for (QuestRequirements.Line line : detailLines(task))
		{
			boolean first = true;
			for (String part : wrap(sm, line.text, width - lineIndent(line)))
			{
				wrapped.add(line.withText(part, first));
				first = false;
			}
		}
		return wrapped;
	}

	/** Where detail line {@code index} is: from x to the end of its text. */
	private static Rectangle detailLineArea(FontMetrics fm, List<QuestRequirements.Line> detail, int x, int top, int index)
	{
		int y = top;

		for (int i = 0; i <= index; i++)
		{
			y += detail.get(i).gap + (i < index ? DETAIL_LINE : 0);
		}
		QuestRequirements.Line line = detail.get(index);
		return new Rectangle(x, y, lineIndent(line) + fm.stringWidth(line.text), DETAIL_LINE);
	}

	private static int lineIndent(QuestRequirements.Line line)
	{
		return line.header ? SECTION_TEXT : line.indent;
	}

	private static int detailHeight(List<QuestRequirements.Line> lines)
	{
		return lines.isEmpty() ? 0 : 4 + lines.stream().mapToInt(l -> DETAIL_LINE + l.gap).sum();
	}

	/** A prerequisite quest was clicked: open it in the pane, or say in chat that it isn't a task right now. */
	private void showQuest(String questName)
	{
		NuzlockeTask found = questTask(questName);
		if (found != null)
		{
			navigateTo(found);
		}
	}

	/** For the hover tip: whether the quest is in your task list, rechecked at most once a second. */
	private boolean hasQuestTask(String questName)
	{
		long now = System.currentTimeMillis();
		if (!questName.equals(tipQuest) || now - tipQuestAt > 1000)
		{
			tipQuest = questName;
			tipQuestAt = now;
			tipQuestFound = questTask(questName) != null;
		}
		return tipQuestFound;
	}

	/** The quest task for this quest (a RuneLite Quest name), or null if it isn't in your task list. */
	private NuzlockeTask questTask(String questName)
	{
		return pool(true).stream()
			.filter(t -> QuestRequirements.isQuestTask(t) && t.getConstraints() != null
				&& questName.equals(t.getConstraints().getQuest()))
			.findFirst().orElse(null);
	}

	/**
	 * Show a quest's requirements over the side panel (inventory, prayer...), not over its
	 * tab buttons, and close the window so the map can be seen. It has its own close
	 * button, and switching tab or pressing any key closes it too.
	 */
	void openQuestPane(NuzlockeTask task)
	{
		// Opened fresh (window, side panel, bubble): no Back trail from whatever was open before.
		questBack.clear();
		paneSide = null;
		showInPane(task);
	}

	/** Follow a link inside the box (a required quest, a saved quest): Back returns here. */
	private void navigateTo(NuzlockeTask task)
	{
		if (questPane != null && questPane != task)
		{
			questBack.push(questPane);
		}
		showInPane(task);
	}

	private void showInPane(NuzlockeTask task)
	{
		close();
		questPane = task;
		questPaneAt = System.currentTimeMillis();
		scroll = 0;
	}

	/** The quest/saved box at {@code pane}; (ox, oy) is where that's drawn on screen when it's moved. */
	private void drawQuestPane(Graphics2D graphics, Rectangle pane, int ox, int oy)
	{
		NuzlockeTask task = questPane;
		int mx = paneMouse.x - ox;
		int my = paneMouse.y - oy;
		hits.clear();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// A rounded card with a darker header band and an orange rule under it.
		boolean savedList = task == SAVED_PANE;
		List<NuzlockeTask> saved = savedList ? savedTasks() : null;
		graphics.setFont(Skin.bold());
		List<String> title = wrap(graphics.getFontMetrics(), savedList ? "Saved tasks (" + saved.size() + ")"
			: task.getName().replaceFirst("^Complete ", ""), pane.width - (questBack.isEmpty() ? 62 : 96));
		int top = pane.y + 14 + title.size() * 15;
		Skin.panel(graphics, new Rectangle(pane.x, pane.y, pane.width, pane.height), top - pane.y, null);
		Rectangle close = new Rectangle(pane.x + pane.width - 21, pane.y + 5, 16, 16);
		hits.add(new Hit(close, () -> questPane = null));
		Skin.close(graphics, close, close.contains(mx, my));
		// The quest's OSRS Wiki page, beside the close button.
		graphics.setFont(Skin.small());
		Rectangle wiki = new Rectangle(close.x - 30, close.y + 1, 27, 14);
		if (!savedList)
		{
			// The wiki's search jumps straight to the page when the name matches, and
			// lists the closest pages when it doesn't (the Recipe for Disaster parts).
			String quest = task.getName().replaceFirst("^Complete ", "").replace("&", "%26").replace(' ', '+');
			hits.add(new Hit(wiki, () -> LinkBrowser.browse("https://oldschool.runescape.wiki/?title=Special:Search&go=Go&search=" + quest)));
			pill(graphics, wiki, "Wiki", wiki.contains(mx, my));
		}
		// Back to the quest this one was opened from.
		Rectangle back = new Rectangle(wiki.x - 33, close.y + 1, 30, 14);
		if (!questBack.isEmpty())
		{
			hits.add(new Hit(back, () ->
			{
				questPane = questBack.pop();
				questPaneAt = System.currentTimeMillis();
				scroll = 0;
			}));
			pill(graphics, back, "Back", back.contains(mx, my));
		}
		// A quick fade and slide in, so moving to a required quest reads as a new page.
		float in = Math.min(1f, (System.currentTimeMillis() - questPaneAt) / 200f);
		Composite oldComposite = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, in));
		graphics.translate((int) (16 * (1 - in) * (1 - in)), 0);
		// The whole name, wrapped, without the "Complete" every quest task starts with.
		graphics.setFont(Skin.bold());
		graphics.setColor(TITLE);
		for (int i = 0; i < title.size(); i++)
		{
			Skin.text(graphics, title.get(i), pane.x + 8, pane.y + 21 + i * 15);
		}

		Font small = Skin.small();
		// The saved list keeps its how-to line pinned at the bottom.
		int foot = savedList && !saved.isEmpty() ? 19 : 0;
		Rectangle body = new Rectangle(pane.x + 1, top + 2, pane.width - 2, pane.y + pane.height - 3 - top - foot);
		if (foot > 0)
		{
			Skin.divider(graphics, pane.x + 6, body.y + body.height, pane.x + pane.width - 8);
			graphics.setFont(small);
			graphics.setColor(SUBTEXT);
			Skin.text(graphics, "Right-click to remove", pane.x + 8, body.y + body.height + 14);
		}
		Shape oldClip = graphics.getClip();
		graphics.clip(body);
		if (savedList)
		{
			drawScrollbar(graphics, body, drawSavedList(graphics, saved, body, mx, my, small));
		}
		else
		{
			List<QuestRequirements.Line> lines = wrapDetail(graphics.getFontMetrics(small), task, pane.width - 12);
			scroll = Math.max(0, Math.min(scroll, detailHeight(lines) - body.height));
			drawDetail(graphics, lines, pane.x + 6, body.y - scroll, body, mx, my, small);
			drawScrollbar(graphics, body, detailHeight(lines));
		}
		graphics.setClip(oldClip);
		graphics.translate(-(int) (16 * (1 - in) * (1 - in)), 0);
		graphics.setComposite(oldComposite);
		finishHover(pane, mx, my);
		shownArea = new Rectangle(pane.x + ox, pane.y + oy, pane.width, pane.height);
		drawTip(graphics, mx, my, ox, oy);
	}

	/**
	 * The hint set while drawing ({@code tip}), in a small box by the mouse, over everything.
	 * Drawn here rather than by RuneLite's tooltips, which follow the game's mouse: over the
	 * window and box the game is told the mouse is off screen. (ox, oy) is where (0, 0) is on screen.
	 */
	private void drawTip(Graphics2D graphics, int mx, int my, int ox, int oy)
	{
		String text = tip;
		tip = null;
		if (text == null)
		{
			return;
		}
		graphics.setFont(Skin.small());
		FontMetrics fm = graphics.getFontMetrics();
		Rectangle box = new Rectangle(0, 0, fm.stringWidth(text) + 10, 17);
		int canvasWidth = client.getCanvasWidth();
		int canvasHeight = client.getCanvasHeight();
		box.x = Math.max(-ox, Math.min(mx + 12, canvasWidth - ox - box.width - 2));
		box.y = my + 20 + box.height > canvasHeight - oy ? my - box.height - 4 : my + 20;
		Skin.tooltip(graphics, box, text);
	}

	/**
	 * The saved list in the pane: name (red if your level's too low), then its chunk or
	 * category and progress. Click to track or untrack (a quest opens its requirements),
	 * Shift + click to unsave. Returns its full height, for the scrollbar.
	 */
	private int drawSavedList(Graphics2D graphics, List<NuzlockeTask> saved, Rectangle body, int mx, int my, Font small)
	{
		graphics.setFont(small);
		FontMetrics fm = graphics.getFontMetrics();
		paneRowId = null;
		if (saved.isEmpty())
		{
			graphics.setColor(SUBTEXT);
			Skin.text(graphics, "Star tasks in the task window", body.x + 5, body.y + 14);
			return 0;
		}
		// Long names wrap onto a second line (and the card grows) rather than being cut off.
		int width = body.width - 28;
		List<List<String>> names = new ArrayList<>();
		int total = 4;
		for (NuzlockeTask task : saved)
		{
			List<String> lines = wrap(fm, task.getName(), width);
			names.add(lines.size() > 2 ? Arrays.asList(lines.get(0), Skin.fit(fm, lines.get(1) + " " + lines.get(2), width)) : lines);
			total += 24 + 12 * names.get(names.size() - 1).size();
		}
		scroll = Math.max(0, Math.min(scroll, total - body.height));
		boolean shift = client.isKeyPressed(KeyCode.KC_SHIFT);
		int y = body.y + 4 - scroll;
		for (int i = 0; i < saved.size(); i++)
		{
			NuzlockeTask task = saved.get(i);
			List<String> name = names.get(i);
			// Each task is a small rounded card: its tier colour down the left, like the side
			// panel's, and a thin progress bar along the bottom for counted tasks.
			Rectangle row = new Rectangle(body.x + 4, y, body.width - 14, 20 + 12 * name.size());
			y += row.height + 4;
			boolean hover = row.contains(mx, my) && body.contains(mx, my);
			boolean isTracked = isTracked(task);
			Color tier = TaskCardTier.fromTask(task).getAccent();
			graphics.setColor(hover && shift ? new Color(255, 70, 70, 50) : hover ? ROW_HOVER : isTracked ? TRACKED_FILL : ROW_ALT);
			graphics.fillRect(row.x, row.y, row.width, row.height);
			graphics.setColor(isTracked ? TITLE : new Color(0, 0, 0, 120));
			graphics.drawRect(row.x, row.y, row.width, row.height);
			graphics.setColor(tier);
			graphics.fillRect(row.x + 2, row.y + 4, 3, row.height - 8);
			int target = Math.max(1, task.getTargetQuantity());
			if (target > 1)
			{
				graphics.setColor(Skin.FILL);
				graphics.fillRect(row.x + 8, row.y + row.height - 3, (row.width - 14) * Math.min(task.getCurrentProgress(), target) / target, 2);
			}
			if (hover)
			{
				paneRowId = task.getTaskId();
			}
			if (body.contains(mx, my))
			{
				hits.add(new Hit(row, () ->
				{
					if (client.isKeyPressed(KeyCode.KC_SHIFT))
					{
						toggleSaved(task.getTaskId());
					}
					else
					{
						trackOrOpen(task, this::navigateTo);
					}
				}));
			}
			String progress = target > 1 ? Math.min(task.getCurrentProgress(), target) + "/" + target : "";
			String where = plugin.isGlobalTask(task.getTaskId()) ? NuzlockeTask.displayCategory(task.getCategory())
				: chunkNames.computeIfAbsent(task.getTaskId(), k -> chunkName(task));
			// Red when you can't do it yet, with why ("(Lvl 40)") ahead of where it is.
			boolean ready = canDo(task);
			graphics.setColor(hover && shift || !ready ? NO_LEVEL : Color.WHITE);
			for (int l = 0; l < name.size(); l++)
			{
				Skin.text(graphics, hover && shift && l == 0 ? Skin.fit(fm, "Remove " + name.get(0), width) : name.get(l),
					row.x + 8, row.y + 14 + 12 * l);
			}
			int lineY = row.y + 15 + 12 * name.size();
			String why = ready ? "" : levelNote(task) + " ";
			graphics.setColor(NO_LEVEL);
			Skin.text(graphics, why, row.x + 8, lineY);
			graphics.setColor(SUBTEXT);
			Skin.text(graphics, Skin.fit(fm, where, row.width - 19 - fm.stringWidth(why) - fm.stringWidth(progress)),
				row.x + 8 + fm.stringWidth(why), lineY);
			Skin.text(graphics, progress, row.x + row.width - 5 - fm.stringWidth(progress), lineY);
		}
		return total;
	}

	/** A small rounded button with a label, lit up while hovered. */
	private static void pill(Graphics2D graphics, Rectangle r, String text, boolean hover)
	{
		Skin.button(graphics, new Rectangle(r.x, r.y, r.width + 1, r.height + 1), hover, false);
		FontMetrics fm = graphics.getFontMetrics();
		graphics.setColor(hover ? Color.WHITE : SUBTEXT);
		Skin.text(graphics, text, r.x + (r.width - fm.stringWidth(text)) / 2 + 1, r.y + 13);
	}

	/** The side panel's area in whichever layout is on; a spot by the right edge if it's closed. */
	private Rectangle sidePanel()
	{
		for (int id : new int[]{InterfaceID.Toplevel.SIDE_PANELS, InterfaceID.ToplevelOsrsStretch.SIDE_PANELS,
			InterfaceID.ToplevelPreEoc.SIDE_PANELS, InterfaceID.ToplevelOsm.SIDE_PANELS})
		{
			Widget panel = client.getWidget(id);
			if (panel != null && !panel.isHidden())
			{
				return panel.getBounds();
			}
		}
		// A bank, shop, Grand Exchange or trade hides the side panels and shows its own
		// inventory in their place: sit over that instead.
		for (int group : new int[]{15, 301, 467, 336})
		{
			Widget side = client.getWidget(group, 0);
			if (side != null && !side.isHidden() && side.getWidth() > 0)
			{
				return side.getBounds();
			}
		}
		return new Rectangle(client.getCanvasWidth() - 214, client.getCanvasHeight() - 340, 204, 275);
	}

	// Interfaces that take over the screen and inventory: bank, deposit box, shop, Grand
	// Exchange and its collection box, seed vault and trade. Opening one closes the box.
	private static final Set<Integer> TAKEOVER = Set.of(12, 192, 300, 465, 402, 631, 335);

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (TAKEOVER.contains(event.getGroupId()))
		{
			questPane = null;
		}
		// Opening the world map closes the task window (the tasks key + click opens it again).
		if (event.getGroupId() == InterfaceID.WORLDMAP && open)
		{
			close();
		}
	}

	/** Switching side tab (prayer, inventory...) closes the quest pane. */
	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		if (event.getIndex() == VarClientID.TOPLEVEL_PANEL && !config.movableTaskBox())
		{
			questPane = null;
		}
	}

	// --- Small drawing helpers ------------------------------------------------

	private static void cross(Graphics2D graphics, int cx, int cy, int r, Color color)
	{
		graphics.setColor(color);
		graphics.drawLine(cx - r, cy - r, cx + r, cy + r);
		graphics.drawLine(cx - r, cy + r, cx + r, cy - r);
	}

	/** A small triangle centred on (cx, cy), pointing UP, DOWN or RIGHT. */
	private static void arrow(Graphics2D graphics, int cx, int cy, int direction, Color color)
	{
		graphics.setColor(color);
		graphics.fillPolygon(direction == RIGHT
			? new Polygon(new int[]{cx - 2, cx - 2, cx + 3}, new int[]{cy - 4, cy + 4, cy}, 3)
			: direction == UP
			? new Polygon(new int[]{cx - 4, cx + 4, cx}, new int[]{cy + 2, cy + 2, cy - 3}, 3)
			: new Polygon(new int[]{cx - 4, cx + 4, cx}, new int[]{cy - 2, cy - 2, cy + 3}, 3));
	}

	private void drawScrollbar(Graphics2D graphics, Rectangle area, int contentHeight)
	{
		if (contentHeight <= area.height)
		{
			return;
		}
		int thumbHeight = Math.max(20, area.height * area.height / contentHeight);
		int thumbY = area.y + (area.height - thumbHeight) * scroll / Math.max(1, contentHeight - area.height);
		Skin.scrollbar(graphics, area, thumbY, thumbHeight);
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
		graphics.setColor(color);
		if (filled)
		{
			graphics.fillPolygon(xs, ys, 10);
		}
		else
		{
			graphics.drawPolygon(xs, ys, 10);
		}
	}

	private static void drawCentered(Graphics2D graphics, String text, Rectangle area)
	{
		FontMetrics fm = graphics.getFontMetrics();
		Skin.text(graphics, text, area.x + (area.width - fm.stringWidth(text)) / 2, area.y + (area.height + fm.getAscent()) / 2 - 1);
	}


	/** Split text into lines that fit the width, breaking between words. */
	/** A task's name (with any missing levels) as its row shows it, wrapped onto up to three lines. */
	private List<String> rowName(FontMetrics fm, NuzlockeTask task, int width)
	{
		String name = (task.getName() == null ? task.getTaskId() : task.getName()) + (canDo(task) ? "" : " " + levelNote(task));
		List<String> lines = wrap(fm, name, width);
		return lines.size() <= 3 ? lines
			: Arrays.asList(lines.get(0), lines.get(1), Skin.fit(fm, String.join(" ", lines.subList(2, lines.size())), width));
	}

	private static List<String> wrap(FontMetrics fm, String text, int width)
	{
		List<String> lines = new ArrayList<>();
		String line = "";
		for (String word : text.split(" "))
		{
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (fm.stringWidth(candidate) > width && !line.isEmpty())
			{
				lines.add(line);
				line = word;
			}
			else
			{
				line = candidate;
			}
		}
		if (!line.isEmpty())
		{
			lines.add(line);
		}
		return lines;
	}
}
