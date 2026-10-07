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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.Text;
import net.runelite.api.gameval.InterfaceID;

/**
 * Outlines every NPC and object in the scene that an active (unfinished) task
 * needs, and adds a "Tasks" right-click submenu on them listing those tasks.
 *
 * Two kinds of match:
 *  - by id: NPCs via TargetNpc npc ids, objects via RequiredObject ids (kills,
 *    pickpocketing, agility, stalls, construction): the same ids the task
 *    modules use to credit progress.
 *  - by rule: tasks with no target id are matched by what the thing is CALLED and
 *    what it can DO (its right-click options). Stations (cook at a range, smith at
 *    an anvil), resource nodes (oak trees, iron rocks) and fishing spots work this
 *    way. Requiring the right option is what keeps decorative look-alikes out: an
 *    ornamental cooking pot has no "Cook" option, a tree stump has no "Chop down",
 *    and a fishing spot only counts for lobsters if it offers "Cage".
 *
 * The outline is the normal colour if at least one of the target's tasks is
 * doable (level requirement met), and the "unavailable" colour if none are.
 * Archived tasks (see TaskArchive) are left out entirely: no outline, no menu entry.
 *
 * Which targets get an outline is the Outline Mode (task window cogwheel): all task
 * targets, only those of saved tasks, only those with a task you have the level
 * for, or none. The right-click Tasks menu always lists every task.
 *
 * Inventory tools get a Tasks menu too (knife: fletching, tinderbox: firemaking,
 * pestle and mortar: herblore...; see TaskTargetExtras.matchesItem).
 *
 * Auto-tracking (setting "Auto-Track Tasks"): using a task target (attack, talk,
 * chop, mine, cook on it...) tracks that target's lowest-points task in the task box.
 * It only ever replaces a task it tracked itself: a task you chose (right-click menu,
 * task window, tracker or side panel) stays until you change or clear it.
 */
@Singleton
public class TaskTargetHighlighter extends Overlay
{
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 4;

	/**
	 * "Something with this name and one of these right-click options." Rules are
	 * compared by {@code key}, so the same rule built for two tasks is one entry.
	 */
	static final class Rule
	{
		final String key;
		final Predicate<String> nameTest;
		final Set<String> actions;

		Rule(String key, Predicate<String> nameTest, String... actions)
		{
			this.key = key;
			this.nameTest = nameTest;
			this.actions = new HashSet<>(Arrays.asList(actions));
		}

		/** Name must pass; if the rule lists options, the target must offer at least one. */
		boolean matches(Info info)
		{
			return nameTest.test(info.name)
				&& (actions.isEmpty() || !Collections.disjoint(actions, info.actions));
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Rule && ((Rule) o).key.equals(key);
		}

		@Override
		public int hashCode()
		{
			return key.hashCode();
		}
	}

	/** Lowercased name and right-click options of an object or NPC. */
	static final class Info
	{
		final String name;
		final Set<String> actions;

		Info(String name, Set<String> actions)
		{
			this.name = name;
			this.actions = actions;
		}
	}

	private static final Predicate<String> ANY_NAME = name -> true;

	/**
	 * Objects that look like a station or node but can't actually be used: decorative
	 * fire pits, ornamental props, etc. Most are already filtered out by requiring the
	 * right option, but things matched by name alone (fires, pottery) can't be, so
	 * their ids go here. Find an id with Developer Tools > Game Objects.
	 */
	private static final Set<Integer> NOT_USABLE_OBJECT_IDS = new HashSet<>(Arrays.<Integer>asList(
		26577 // fire pit near the H.A.M. camp: "The fire already seems to be in use."
	));

	/** A boss chunk (region) to list tasks from, optionally only task ids containing a keyword. */
	static final class Entrance
	{
		final int region;
		final Set<String> taskIdKeywords;

		Entrance(int region, String... taskIdKeywords)
		{
			this.region = region;
			this.taskIdKeywords = new HashSet<>(Arrays.asList(taskIdKeywords));
		}

		boolean includes(NuzlockeTask task)
		{
			if (taskIdKeywords.isEmpty())
			{
				return true;
			}
			String id = task.getTaskId() == null ? "" : task.getTaskId().toLowerCase();
			for (String keyword : taskIdKeywords)
			{
				if (id.contains(keyword))
				{
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Boss entrances: object id -> the boss chunk whose tasks it lists, so boss tasks
	 * (mostly done inside instances) show on the thing you use to get in. Chunks with
	 * more than one boss pass task id keywords so each entrance lists only its boss.
	 * Commented-out lines are entrances whose object id hasn't been found yet.
	 */
	private static final Map<Integer, Entrance> BOSS_ENTRANCES = new HashMap<>();
	private static final Set<Integer> BOSS_REGIONS = new HashSet<>();

	static
	{
		// Misthalin
		BOSS_ENTRANCES.put(60760, new Entrance(12851)); // Brutus: pen gate
		BOSS_ENTRANCES.put(60763, new Entrance(12851)); // Brutus: pen gate
		BOSS_ENTRANCES.put(14203, new Entrance(12854, "scurrius", "bone_mace", "bone_shortbow", "bone_staff")); // Scurrius: lair entrance
		BOSS_ENTRANCES.put(32534, new Entrance(12854, "bryophyta")); // Bryophyta: gate
		BOSS_ENTRANCES.put(29486, new Entrance(12342)); // Obor: gate
		BOSS_ENTRANCES.put(29487, new Entrance(12342)); // Obor: gate

		// Asgarnia
		BOSS_ENTRANCES.put(12202, new Entrance(11828)); // Giant Mole: mole hill
		// BOSS_ENTRANCES.put(?, new Entrance(11824)); // Royal Titans

		// Desert
		BOSS_ENTRANCES.put(19053, new Entrance(12848)); // Kalphite Queen: tunnel entrance
		BOSS_ENTRANCES.put(23609, new Entrance(12848)); // Kalphite Queen: entrance
		BOSS_ENTRANCES.put(46089, new Entrance(13354)); // Tombs of Amascut: entrance
		BOSS_ENTRANCES.put(49212, new Entrance(13870, "leviathan_")); // Leviathan: rowboat
		BOSS_ENTRANCES.put(49481, new Entrance(13870, "whisperer_")); // The Whisperer: teleporter
		BOSS_ENTRANCES.put(49513, new Entrance(13870, "duke_")); // Duke Sucellus: heavy door
		BOSS_ENTRANCES.put(49495, new Entrance(13870, "vardorvis_")); // Vardorvis: rocks

		// Fremennik
		BOSS_ENTRANCES.put(31990, new Entrance(9023)); // Vorkath: ice chunks
		BOSS_ENTRANCES.put(3831, new Entrance(10042)); // Dagannoth Kings: kings' ladder
		BOSS_ENTRANCES.put(46596, new Entrance(11325)); // Phantom Muspah: crevice
		BOSS_ENTRANCES.put(26503, new Entrance(11578, "gwd_graardor_")); // General Graardor: big door
		BOSS_ENTRANCES.put(26504, new Entrance(11578, "gwd_zilyana_")); // Commander Zilyana: big door
		BOSS_ENTRANCES.put(26502, new Entrance(11578, "gwd_kreearra_")); // Kree'arra: big door
		BOSS_ENTRANCES.put(26505, new Entrance(11578, "gwd_kril_")); // K'ril Tsutsaroth: big door
		BOSS_ENTRANCES.put(42934, new Entrance(11578, "gwd_nex_")); // Nex: door

		// Karamja
		BOSS_ENTRANCES.put(30352, new Entrance(11313, "inferno_")); // The Inferno: entrance
		BOSS_ENTRANCES.put(11833, new Entrance(11313, "jad_")); // TzHaar Fight Cave: cave entrance

		// Morytania
		BOSS_ENTRANCES.put(32637, new Entrance(14899)); // Phosani's Nightmare: entrance stairs
		BOSS_ENTRANCES.put(32653, new Entrance(14642)); // Theatre of Blood: big entrance
		BOSS_ENTRANCES.put(20666, new Entrance(14131)); // Barrows: warning sign
		BOSS_ENTRANCES.put(61048, new Entrance(14132, "maggot_king")); // Maggot King (Castle Drakan): darkwood trees

		// Tirannwn
		BOSS_ENTRANCES.put(46241, new Entrance(8751)); // Zulrah: sacrificial boat
		BOSS_ENTRANCES.put(46242, new Entrance(8751)); // Zulrah: sacrificial boat
		BOSS_ENTRANCES.put(10068, new Entrance(8751)); // Zulrah: sacrificial boat
		BOSS_ENTRANCES.put(37340, new Entrance(12895)); // The (Corrupted) Gauntlet: entrance

		// Varlamore
		BOSS_ENTRANCES.put(55355, new Entrance(6706)); // Amoxliatl: door
		// BOSS_ENTRANCES.put(?, new Entrance(5680)); // Moons of Peril (Cam Torum)
		BOSS_ENTRANCES.put(50751, new Entrance(7216)); // Fortis Colosseum: entrance
		BOSS_ENTRANCES.put(57289, new Entrance(5167)); // Doom of Mokhaiotl: gap
		BOSS_ENTRANCES.put(55401, new Entrance(5939)); // The Hueycoatl: entrance
		// BOSS_ENTRANCES.put(?, new Entrance(5689)); // Yama

		// Zeah
		BOSS_ENTRANCES.put(34858, new Entrance(6711)); // Sarachnis: thick web
		BOSS_ENTRANCES.put(29777, new Entrance(4919)); // Chambers of Xeric: big door

		for (Entrance entrance : BOSS_ENTRANCES.values())
		{
			BOSS_REGIONS.add(entrance.region);
		}
	}

	// --- Station rules (things you use, rather than gather from) ---
	// Any object offering the option counts. Fires and pottery have no option of
	// their own (you use an item on them), so those are matched by exact name.
	private static final Rule COOK = new Rule("station:cook", ANY_NAME, "cook");
	private static final Rule FIRE = new Rule("station:fire", "fire"::equals);
	private static final Rule CHURN = new Rule("station:churn", ANY_NAME, "churn");
	private static final Rule SMELT = new Rule("station:smelt", ANY_NAME, "smelt");
	private static final Rule SMITH = new Rule("station:smith", ANY_NAME, "smith");
	private static final Rule SPIN = new Rule("station:spin", ANY_NAME, "spin");
	private static final Rule POTTERY = new Rule("station:pottery",
		name -> name.equals("potter's wheel") || name.equals("pottery oven"));
	private static final Rule RUNE_ALTAR = new Rule("station:craft-rune", ANY_NAME, "craft-rune");

	private static final Pattern JEWELLERY = Pattern.compile("\\b(ring|necklace|amulet|bracelet)\\b");
	private static final Pattern POTTERY_ITEM = Pattern.compile("\\b(pot|bowl|pie dish|vase|plant pot)\\b");

	private static final String[] CHOP = {"chop down", "chop", "cut"};
	private static final Set<String> PLAIN_TREES = new HashSet<>(Arrays.asList(
		"tree", "dead tree", "dying tree", "evergreen", "evergreen tree", "jungle tree"));

	/**
	 * Fish (matched against the task's item name) to the fishing-spot option that
	 * catches it. Checked top to bottom, so more specific names come first
	 * ("karambwanji" before "karambwan", "leaping trout" before "trout").
	 */
	private static final Map<String, String[]> FISH = new LinkedHashMap<>();

	static
	{
		FISH.put("leaping", new String[]{"use-rod"});
		FISH.put("dark crab", new String[]{"cage"});
		FISH.put("lobster", new String[]{"cage"});
		FISH.put("karambwanji", new String[]{"net", "small net"});
		FISH.put("karambwan", new String[]{"fish"});
		FISH.put("shrimp", new String[]{"net", "small net"});
		FISH.put("anchov", new String[]{"net", "small net"});
		FISH.put("monkfish", new String[]{"net"});
		FISH.put("sardine", new String[]{"bait"});
		FISH.put("herring", new String[]{"bait"});
		FISH.put("anglerfish", new String[]{"bait"});
		FISH.put("eel", new String[]{"bait"});
		FISH.put("pike", new String[]{"bait"});
		FISH.put("trout", new String[]{"lure"});
		FISH.put("salmon", new String[]{"lure"});
		FISH.put("rainbow fish", new String[]{"lure"});
		FISH.put("mackerel", new String[]{"big net"});
		FISH.put("cod", new String[]{"big net"});
		FISH.put("bass", new String[]{"big net"});
		FISH.put("shark", new String[]{"harpoon"});
		FISH.put("tuna", new String[]{"harpoon"});
		FISH.put("swordfish", new String[]{"harpoon"});
	}

	private final Client client;
	private final ChunkBlazerPlugin plugin;
	private final ChunkBlazerConfig config;
	private final ModelOutlineRenderer outlineRenderer;
	private final TaskArchive archive;
	private final TaskItemOverlay itemOverlay;

	// The task auto-tracking last put in the task box. If the tracked task is anything
	// else, the player chose it, and auto-tracking leaves it alone.
	private volatile String autoTrackedId;

	// What the active tasks want. Rebuilt every game tick (cheap: ~100 tasks), so
	// newly unlocked or completed tasks show up within a tick.
	private Map<Integer, List<NuzlockeTask>> npcTasks = Collections.emptyMap();
	private Map<Integer, List<NuzlockeTask>> objectTasks = Collections.emptyMap();
	private Map<Rule, List<NuzlockeTask>> npcRules = Collections.emptyMap();
	private Map<Rule, List<NuzlockeTask>> objectRules = Collections.emptyMap();
	private Map<Integer, List<NuzlockeTask>> bossRegionTasks = Collections.emptyMap();

	// Name/options per id (fixed per id, kept for the session) and the resulting
	// task list per id (cleared whenever the index is rebuilt).
	private final Map<Integer, Info> objectInfoCache = new HashMap<>();
	private final Map<Integer, Info> npcInfoCache = new HashMap<>();
	private final Map<Integer, List<NuzlockeTask>> objectMatchCache = new HashMap<>();
	private final Map<Integer, List<NuzlockeTask>> npcMatchCache = new HashMap<>();

	// Objects in the scene that currently have at least one task. Kept up to date
	// by spawn/despawn events and fully rescanned when the wanted set changes.
	private final Set<TileObject> trackedObjects = new HashSet<>();

	private final ConfigManager configManager;

	// Saved (starred) task ids, re-read only when the stored list changes.
	private String savedRaw;
	private Set<String> savedIds = Collections.emptySet();

	@Inject
	public TaskTargetHighlighter(Client client, ChunkBlazerPlugin plugin, ChunkBlazerConfig config,
		ModelOutlineRenderer outlineRenderer, TaskArchive archive, ConfigManager configManager,
		TaskItemOverlay itemOverlay)
	{
		this.itemOverlay = itemOverlay;
		this.configManager = configManager;
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.outlineRenderer = outlineRenderer;
		this.archive = archive;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/** Drop all state; called on plugin shutdown. */
	public void reset()
	{
		npcTasks = Collections.emptyMap();
		objectTasks = Collections.emptyMap();
		npcRules = Collections.emptyMap();
		objectRules = Collections.emptyMap();
		bossRegionTasks = Collections.emptyMap();
		objectInfoCache.clear();
		npcInfoCache.clear();
		objectMatchCache.clear();
		npcMatchCache.clear();
		trackedObjects.clear();
	}

	// --- Index of what the active tasks want ---------------------------------

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!isEnabled())
		{
			// Drop everything so a switch back to this style starts from a fresh scan.
			if (!npcTasks.isEmpty() || !objectTasks.isEmpty() || !npcRules.isEmpty()
				|| !objectRules.isEmpty() || !bossRegionTasks.isEmpty() || !trackedObjects.isEmpty())
			{
				reset();
			}
			return;
		}
		rebuildIndex();
	}

	/** The outlines have their own checkbox; the Tasks menu belongs to the Yellow paint style. */
	private boolean isEnabled()
	{
		return config.taskOutlineMode() != OutlineMode.OFF || isMenuEnabled() || config.autoTrackTasks();
	}

	private boolean isMenuEnabled()
	{
		return config.taskTrackerStyle() == TaskTrackerStyle.VANI && config.taskRightClickMenu();
	}

	private void rebuildIndex()
	{
		Map<Integer, List<NuzlockeTask>> npcs = new HashMap<>();
		Map<Integer, List<NuzlockeTask>> objects = new HashMap<>();
		Map<Rule, List<NuzlockeTask>> npcRuleMap = new HashMap<>();
		Map<Rule, List<NuzlockeTask>> objectRuleMap = new HashMap<>();
		Map<Integer, List<NuzlockeTask>> bossTasks = new HashMap<>();

		Set<String> archived = archive.ids();
		for (NuzlockeTask task : plugin.getActiveTasks())
		{
			// Archived tasks are put aside: they don't outline anything or appear in menus.
			if (task == null || task.isCompleted() || archived.contains(task.getTaskId()))
			{
				continue;
			}

			if (!BOSS_ENTRANCES.isEmpty() && task.getTaskId() != null)
			{
				int region = plugin.findRegionForTask(task.getTaskId());
				if (BOSS_REGIONS.contains(region))
				{
					addTask(bossTasks, region, task);
				}
			}

			TargetNpc target = task.getTargetNpc();
			if (target != null && target.getNpcIds() != null)
			{
				for (Integer id : target.getNpcIds())
				{
					addTask(npcs, id, task);
				}
			}

			if (task.getRequiredObjects() != null)
			{
				for (RequiredObject ro : task.getRequiredObjects())
				{
					if (ro != null && ro.getObjectIds() != null)
					{
						for (Integer id : ro.getObjectIds())
						{
							addTask(objects, id, task);
						}
					}
				}
			}

			for (Rule rule : objectRulesFor(task))
			{
				addTask(objectRuleMap, rule, task);
			}
			Rule npcRule = npcRuleFor(task);
			if (npcRule != null)
			{
				addTask(npcRuleMap, npcRule, task);
			}

			// UI-only extras for tasks with no ids of their own (see TaskTargetExtras).
			if (!hasOwnTargets(task))
			{
				for (Integer id : TaskTargetExtras.objectIds(task))
				{
					addTask(objects, id, task);
				}
				for (Integer id : TaskTargetExtras.npcIds(task))
				{
					addTask(npcs, id, task);
				}
				for (Rule rule : TaskTargetExtras.objectRules(task))
				{
					addTask(objectRuleMap, rule, task);
				}
				Rule extraNpcRule = TaskTargetExtras.npcRule(task);
				if (extraNpcRule != null)
				{
					addTask(npcRuleMap, extraNpcRule, task);
				}
			}
		}

		boolean objectsChanged = !objects.keySet().equals(objectTasks.keySet())
			|| !objectRuleMap.keySet().equals(objectRules.keySet())
			|| !bossTasks.keySet().equals(bossRegionTasks.keySet());
		npcTasks = npcs;
		objectTasks = objects;
		npcRules = npcRuleMap;
		objectRules = objectRuleMap;
		bossRegionTasks = bossTasks;
		objectMatchCache.clear();
		npcMatchCache.clear();
		if (objectsChanged)
		{
			rescanScene();
		}
	}

	/** True if the task data itself names the NPCs or objects this task needs. */
	private static boolean hasOwnTargets(NuzlockeTask task)
	{
		TargetNpc target = task.getTargetNpc();
		if (target != null && target.getNpcIds() != null && !target.getNpcIds().isEmpty())
		{
			return true;
		}
		if (task.getRequiredObjects() != null)
		{
			for (RequiredObject ro : task.getRequiredObjects())
			{
				if (ro != null && ro.getObjectIds() != null && !ro.getObjectIds().isEmpty())
				{
					return true;
				}
			}
		}
		return false;
	}

	private static <K> void addTask(Map<K, List<NuzlockeTask>> map, K key, NuzlockeTask task)
	{
		if (key == null)
		{
			return;
		}
		List<NuzlockeTask> list = map.computeIfAbsent(key, k -> new ArrayList<>());
		if (!list.contains(task))
		{
			list.add(task);
		}
	}

	// --- Which rules a task needs ---------------------------------------------

	/** Object rules for a task: the station it's done at, or the node it's gathered from. */
	static List<Rule> objectRulesFor(NuzlockeTask task)
	{
		String type = task.getCompletionType() == null ? "" : task.getCompletionType().toUpperCase();
		String name = task.getName() == null ? "" : task.getName().toLowerCase();
		String verb = name.contains(" ") ? name.substring(0, name.indexOf(' ')) : name;

		switch (type)
		{
			case "COOKING":
				if (verb.equals("cook") || verb.equals("bake"))
				{
					return Arrays.asList(COOK, FIRE);
				}
				return verb.equals("churn") ? Collections.singletonList(CHURN) : Collections.emptyList();
			case "SMITHING":
				if (verb.equals("smelt"))
				{
					return Collections.singletonList(SMELT);
				}
				return verb.equals("smith") ? Collections.singletonList(SMITH) : Collections.emptyList();
			case "CRAFTING":
				if (verb.equals("spin"))
				{
					return Collections.singletonList(SPIN);
				}
				if (JEWELLERY.matcher(name).find())
				{
					return Collections.singletonList(SMELT);
				}
				return POTTERY_ITEM.matcher(name).find() ? Collections.singletonList(POTTERY) : Collections.emptyList();
			case "RUNECRAFTING":
				return Collections.singletonList(RUNE_ALTAR);
			case "WOODCUTTING":
				return ruleOrNone(treeRule(itemName(task)));
			case "MINING":
				return ruleOrNone(rockRule(itemName(task)));
			default:
				return Collections.emptyList();
		}
	}

	/** NPC rule for a task: currently only fishing spots. */
	static Rule npcRuleFor(NuzlockeTask task)
	{
		if (!"FISHING".equalsIgnoreCase(task.getCompletionType()))
		{
			return null;
		}
		String fish = itemName(task);
		for (Map.Entry<String, String[]> entry : FISH.entrySet())
		{
			if (fish.contains(entry.getKey()))
			{
				return new Rule("fish:" + entry.getKey(), name -> name.contains("fishing spot"), entry.getValue());
			}
		}
		return null;
	}

	/** "Oak Logs" -> anything named "oak..." that can be chopped. Plain "Logs" -> normal trees. */
	static Rule treeRule(String item)
	{
		if (item.equals("logs"))
		{
			return new Rule("tree:plain", PLAIN_TREES::contains, CHOP);
		}
		String wood = item.replaceAll("\\s*logs?$", "").trim();
		if (wood.isEmpty() || item.equals(wood))
		{
			return null; // not a log (branches, mushrooms, thatch spars...)
		}
		return new Rule("tree:" + wood, name -> name.startsWith(wood), CHOP);
	}

	/** "Iron Ore" -> "Iron rocks" that can be mined; uncut gems -> "Gem rocks". */
	static Rule rockRule(String item)
	{
		if (item.isEmpty())
		{
			return null;
		}
		if (item.startsWith("uncut "))
		{
			return new Rule("rock:gem", "gem rocks"::equals, "mine");
		}
		if (item.contains("essence"))
		{
			String key = item.startsWith("dense") ? "dense" : "essence";
			Predicate<String> test = key.equals("dense")
				? name -> name.startsWith("dense")
				: name -> name.contains("essence");
			return new Rule("rock:" + key, test, "mine", "chip");
		}
		String rock = item.replaceAll("^perfect\\s+", "").replaceAll("\\s+(ore|shards)$", "").trim();
		return new Rule("rock:" + rock, name -> name.startsWith(rock), "mine");
	}

	private static List<Rule> ruleOrNone(Rule rule)
	{
		return rule == null ? Collections.emptyList() : Collections.singletonList(rule);
	}

	/** The task's first required item, lowercased ("" if none). */
	private static String itemName(NuzlockeTask task)
	{
		if (task.getRequiredItems() == null || task.getRequiredItems().isEmpty()
			|| task.getRequiredItems().get(0).getItem() == null)
		{
			return "";
		}
		return task.getRequiredItems().get(0).getItem().toLowerCase().trim();
	}

	// --- Looking up what a target is ---------------------------------------

	private static Info info(String rawName, String[] rawActions)
	{
		String name = rawName == null ? "" : Text.removeTags(rawName).toLowerCase();
		Set<String> actions = new HashSet<>();
		if (rawActions != null)
		{
			for (String action : rawActions)
			{
				if (action != null)
				{
					actions.add(Text.removeTags(action).toLowerCase());
				}
			}
		}
		return new Info(name, actions);
	}

	private Info objectInfo(int id)
	{
		return objectInfoCache.computeIfAbsent(id, key ->
		{
			ObjectComposition comp = client.getObjectDefinition(key);
			if (comp == null)
			{
				return info(null, null);
			}
			if (comp.getImpostorIds() != null)
			{
				ObjectComposition impostor = comp.getImpostor();
				if (impostor != null)
				{
					comp = impostor;
				}
			}
			return info(comp.getName(), comp.getActions());
		});
	}

	private Info npcInfo(NPC npc)
	{
		return npcInfoCache.computeIfAbsent(npc.getId(), key ->
		{
			NPCComposition comp = npc.getTransformedComposition();
			if (comp == null)
			{
				comp = npc.getComposition();
			}
			return comp == null ? info(null, null) : info(comp.getName(), comp.getActions());
		});
	}

	/** Every active task this object id is relevant to (by id or by rule). */
	private List<NuzlockeTask> tasksForObject(int id)
	{
		return objectMatchCache.computeIfAbsent(id, key ->
		{
			List<NuzlockeTask> result = new ArrayList<>();
			List<NuzlockeTask> direct = objectTasks.get(key);
			if (direct != null)
			{
				result.addAll(direct);
			}
			if (!objectRules.isEmpty() && !NOT_USABLE_OBJECT_IDS.contains(key))
			{
				collectRuleMatches(objectRules, objectInfo(key), result);
			}
			Entrance entrance = BOSS_ENTRANCES.get(key);
			List<NuzlockeTask> bossTasks = entrance == null ? null : bossRegionTasks.get(entrance.region);
			if (bossTasks != null)
			{
				for (NuzlockeTask task : bossTasks)
				{
					if (entrance.includes(task) && !result.contains(task))
					{
						result.add(task);
					}
				}
			}
			return result;
		});
	}

	/** Every active task this NPC is relevant to (by id or by rule). */
	private List<NuzlockeTask> tasksForNpc(NPC npc)
	{
		return npcMatchCache.computeIfAbsent(npc.getId(), key ->
		{
			List<NuzlockeTask> result = new ArrayList<>();
			List<NuzlockeTask> direct = npcTasks.get(key);
			if (direct != null)
			{
				result.addAll(direct);
			}
			if (!npcRules.isEmpty())
			{
				collectRuleMatches(npcRules, npcInfo(npc), result);
			}
			return result;
		});
	}

	private static void collectRuleMatches(Map<Rule, List<NuzlockeTask>> rules, Info info, List<NuzlockeTask> result)
	{
		for (Map.Entry<Rule, List<NuzlockeTask>> entry : rules.entrySet())
		{
			if (entry.getKey().matches(info))
			{
				for (NuzlockeTask task : entry.getValue())
				{
					if (!result.contains(task))
					{
						result.add(task);
					}
				}
			}
		}
	}

	private boolean anyDoable(List<NuzlockeTask> tasks)
	{
		for (NuzlockeTask task : tasks)
		{
			if (plugin.meetsLevelRequirement(task))
			{
				return true;
			}
		}
		return false;
	}

	// --- Object tracking ----------------------------------------------------

	private void rescanScene()
	{
		trackedObjects.clear();
		if (objectTasks.isEmpty() && objectRules.isEmpty() && bossRegionTasks.isEmpty())
		{
			return;
		}
		Scene scene = client.getScene();
		if (scene == null)
		{
			return;
		}
		for (Tile[][] plane : scene.getTiles())
		{
			for (Tile[] row : plane)
			{
				for (Tile tile : row)
				{
					if (tile == null)
					{
						continue;
					}
					GameObject[] gameObjects = tile.getGameObjects();
					if (gameObjects != null)
					{
						for (GameObject go : gameObjects)
						{
							consider(go);
						}
					}
					consider(tile.getWallObject());
					consider(tile.getDecorativeObject());
					consider(tile.getGroundObject());
				}
			}
		}
	}

	private void consider(TileObject object)
	{
		if (object != null && !tasksForObject(object.getId()).isEmpty())
		{
			trackedObjects.add(object);
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		consider(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		trackedObjects.remove(event.getGameObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		consider(event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		trackedObjects.remove(event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		consider(event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		trackedObjects.remove(event.getDecorativeObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		consider(event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		trackedObjects.remove(event.getGroundObject());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// A new scene is loading; its objects arrive again as spawn events.
		if (event.getGameState() == GameState.LOADING)
		{
			trackedObjects.clear();
		}
	}

	// --- Drawing ------------------------------------------------------------

	@Override
	public Dimension render(Graphics2D graphics)
	{
		OutlineMode mode = config.taskOutlineMode();
		if (mode == OutlineMode.OFF)
		{
			return null;
		}
		Color doable = config.taskHighlightColor();
		Color unavailable = config.taskHighlightUnavailableColor();
		Set<String> saved = mode == OutlineMode.SAVED ? savedTaskIds() : Collections.emptySet();

		if (!npcTasks.isEmpty() || !npcRules.isEmpty())
		{
			for (NPC npc : client.getNpcs())
			{
				if (npc == null)
				{
					continue;
				}
				Color color = outlineColor(tasksForNpc(npc), mode, saved, doable, unavailable);
				if (color != null)
				{
					outlineRenderer.drawOutline(npc, OUTLINE_WIDTH, color, OUTLINE_FEATHER);
				}
			}
		}

		int plane = client.getPlane();
		for (TileObject object : trackedObjects)
		{
			if (object.getPlane() != plane)
			{
				continue;
			}
			Color color = outlineColor(tasksForObject(object.getId()), mode, saved, doable, unavailable);
			if (color != null)
			{
				outlineRenderer.drawOutline(object, OUTLINE_WIDTH, color, OUTLINE_FEATHER);
			}
		}
		return null;
	}

	/**
	 * The outline colour for a target with these tasks, or null for no outline:
	 * All tasks: any task (normal colour if one is doable, the "level too low" colour if not).
	 * Saved tasks: only saved ones count, coloured the same way.
	 * Have requirements: only when one of them is doable, in the normal colour.
	 */
	private Color outlineColor(List<NuzlockeTask> tasks, OutlineMode mode, Set<String> saved,
		Color doable, Color unavailable)
	{
		if (tasks.isEmpty())
		{
			return null;
		}
		switch (mode)
		{
			case SAVED:
				List<NuzlockeTask> savedTasks = new ArrayList<>();
				for (NuzlockeTask task : tasks)
				{
					if (saved.contains(task.getTaskId()))
					{
						savedTasks.add(task);
					}
				}
				if (savedTasks.isEmpty())
				{
					return null;
				}
				return anyDoable(savedTasks) ? doable : unavailable;
			case CAN_DO:
				return anyDoable(tasks) ? doable : null;
			default:
				return anyDoable(tasks) ? doable : unavailable;
		}
	}

	/** Saved (starred) task ids for this account, the same list as the task window's Saved tab. */
	private Set<String> savedTaskIds()
	{
		String raw = configManager.getRSProfileConfiguration("chunkblazer", "savedTasks");
		if (raw == null)
		{
			raw = "";
		}
		if (!raw.equals(savedRaw))
		{
			Set<String> ids = new HashSet<>();
			for (String id : raw.split(","))
			{
				if (!id.trim().isEmpty())
				{
					ids.add(id.trim());
				}
			}
			savedIds = ids;
			savedRaw = raw;
		}
		return savedIds;
	}

	/** The Examine option on an item in the inventory (one per item, like NPCs and objects). */
	private static boolean isInventoryExamine(MenuEntry entry)
	{
		return entry.getItemId() > 0
			&& entry.getParam1() == InterfaceID.Inventory.ITEMS
			&& "examine".equalsIgnoreCase(Text.removeTags(entry.getOption()));
	}

	/** Tasks listed on this inventory item: tools, bones and equip gear (see TaskItemOverlay). */
	private List<NuzlockeTask> tasksForItem(int itemId)
	{
		return itemOverlay.tasksFor(itemId);
	}

	// --- Auto-tracking -------------------------------------------------------

	/**
	 * Using an NPC or object that has tasks tracks one of them. Our own Tasks submenu
	 * entries are RUNELITE-type, so picking a task from it (a manual choice) never
	 * lands here.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!config.autoTrackTasks())
		{
			return;
		}
		MenuAction action = event.getMenuAction();
		List<NuzlockeTask> tasks;
		if (isNpcAction(action))
		{
			NPC npc = event.getMenuEntry().getNpc();
			if (npc == null)
			{
				return;
			}
			tasks = tasksForNpc(npc);
		}
		else if (isObjectAction(action))
		{
			tasks = tasksForObject(event.getId());
		}
		else
		{
			return;
		}
		autoTrack(tasks);
	}

	private static boolean isNpcAction(MenuAction action)
	{
		switch (action)
		{
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case WIDGET_TARGET_ON_NPC:
				return true;
			default:
				return false;
		}
	}

	private static boolean isObjectAction(MenuAction action)
	{
		switch (action)
		{
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			case WIDGET_TARGET_ON_GAME_OBJECT:
				return true;
			default:
				return false;
		}
	}

	/**
	 * Track the lowest-points task you have the level for (then the one furthest along).
	 * Never replaces a task you chose yourself; only one auto-tracking put there (or an
	 * empty task box). Also leaves it alone if it already belongs to this target.
	 */
	private void autoTrack(List<NuzlockeTask> tasks)
	{
		if (tasks == null || tasks.isEmpty())
		{
			return;
		}
		NuzlockeTask tracked = plugin.getSelectedTask();
		if (tracked != null && tracked.getTaskId() != null)
		{
			if (!tracked.getTaskId().equals(autoTrackedId))
			{
				return; // chosen by the player: keep it
			}
			for (NuzlockeTask task : tasks)
			{
				if (tracked.getTaskId().equals(task.getTaskId()))
				{
					return;
				}
			}
		}

		NuzlockeTask pick = null;
		Comparator<NuzlockeTask> order = Comparator.comparingInt(NuzlockeTask::getBasePoints)
			.thenComparing(Comparator.comparingDouble(TaskTargetHighlighter::fraction).reversed())
			.thenComparing(t -> t.getName() == null ? "" : t.getName());
		for (NuzlockeTask task : tasks)
		{
			if (plugin.meetsLevelRequirement(task) && (pick == null || order.compare(task, pick) < 0))
			{
				pick = task;
			}
		}
		if (pick != null)
		{
			autoTrackedId = pick.getTaskId();
			plugin.selectTaskFromGame(pick);
		}
	}

	private static double fraction(NuzlockeTask task)
	{
		int target = Math.max(1, task.getTargetQuantity());
		return Math.min(1.0, task.getCurrentProgress() / (double) target);
	}

	// --- Right-click "Tasks" submenu ----------------------------------------

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!isMenuEnabled())
		{
			return;
		}

		// Hook the Examine entry: every NPC/object has exactly one, so the Tasks
		// entry is added once per target.
		MenuEntry entry = event.getMenuEntry();
		List<NuzlockeTask> tasks;
		if (entry.getType() == MenuAction.EXAMINE_NPC)
		{
			NPC npc = entry.getNpc();
			if (npc == null)
			{
				return;
			}
			tasks = tasksForNpc(npc);
		}
		else if (entry.getType() == MenuAction.EXAMINE_OBJECT)
		{
			tasks = tasksForObject(event.getIdentifier());
		}
		else if (isInventoryExamine(entry))
		{
			tasks = tasksForItem(entry.getItemId());
		}
		else
		{
			return;
		}

		if (tasks == null || tasks.isEmpty())
		{
			return;
		}

		// Index 1 = just above "Cancel", so it never becomes the left-click action.
		MenuEntry parent = client.createMenuEntry(1)
			.setOption(anyDoable(tasks) ? "<col=ff9040>Tasks</col>" : "<col=ff5050>Tasks</col>")
			.setTarget(entry.getTarget())
			.setType(MenuAction.RUNELITE);

		Menu submenu = parent.createSubMenu();
		for (NuzlockeTask task : tasks)
		{
			boolean canDo = plugin.meetsLevelRequirement(task);
			String option = canDo
				? task.getName()
				: "<col=ff5050>" + task.getName() + " (Lvl " + task.getLevelRequirement() + ")</col>";
			submenu.createMenuEntry(0)
				.setOption(option)
				.setTarget("<col=ffff00>" + task.getCurrentProgress() + "/" + task.getTargetQuantity() + "</col>")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> plugin.selectTaskFromGame(task));
		}
	}

}
