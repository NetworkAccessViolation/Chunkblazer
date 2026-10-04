package com.chunkblazer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.Text;

/**
 * Outlines every NPC and object in the scene that an active (unfinished) task
 * needs, and adds a "Tasks" right-click submenu on them listing those tasks.
 *
 * Two kinds of match:
 *  - by id: NPCs via TargetNpc npc ids, objects via RequiredObject ids (kills,
 *    pickpocketing, agility, stalls, construction) — the same ids the task
 *    modules use to credit progress.
 *  - by station: skilling tasks done AT something (cook at a range, smelt at a
 *    furnace, smith at an anvil...) carry no object id, so they are matched to
 *    objects by name / right-click action. See {@link Station}.
 *
 * The outline is the normal colour if at least one of the target's tasks is
 * doable (level requirement met), and the "unavailable" colour if none are.
 */
@Singleton
public class TaskTargetHighlighter extends Overlay
{
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 4;

	/**
	 * Skilling stations. An object counts as a station if its name matches one of
	 * {@code names} exactly, or it has one of {@code actions} in its right-click menu.
	 * To support another station, add it here and map tasks to it in stationFor().
	 */
	enum Station
	{
		COOKING(names("range", "cooking range", "fire", "stove", "clay oven", "cooking pot"), names("cook")),
		CHURN(names("dairy churn"), names("churn")),
		FURNACE(names("furnace"), names("smelt")),
		ANVIL(names("anvil"), names("smith")),
		SPINNING_WHEEL(names("spinning wheel"), names("spin")),
		POTTERY(names("potter's wheel", "pottery oven"), names()),
		RUNE_ALTAR(names(), names("craft-rune"));

		final Set<String> names;
		final Set<String> actions;

		Station(Set<String> names, Set<String> actions)
		{
			this.names = names;
			this.actions = actions;
		}

		private static Set<String> names(String... values)
		{
			return new HashSet<>(Arrays.asList(values));
		}
	}

	private static final Pattern JEWELLERY = Pattern.compile("\\b(ring|necklace|amulet|bracelet)\\b");
	private static final Pattern POTTERY_ITEM = Pattern.compile("\\b(pot|bowl|pie dish|vase|plant pot)\\b");

	private final Client client;
	private final ChunkBlazerPlugin plugin;
	private final ChunkBlazerConfig config;
	private final ModelOutlineRenderer outlineRenderer;

	// What the active tasks want. Rebuilt every game tick (cheap: ~100 tasks), so
	// newly unlocked or completed tasks show up within a tick.
	private Map<Integer, List<NuzlockeTask>> npcTasks = Collections.emptyMap();
	private Map<Integer, List<NuzlockeTask>> objectTasks = Collections.emptyMap();
	private Map<Station, List<NuzlockeTask>> stationTasks = Collections.emptyMap();

	// Which stations each object id is (name/action lookup is cached per id).
	private final Map<Integer, Set<Station>> stationCache = new HashMap<>();

	// Objects in the scene that currently have at least one task. Kept up to date
	// by spawn/despawn events and fully rescanned when the wanted set changes.
	private final Set<TileObject> trackedObjects = new HashSet<>();

	@Inject
	public TaskTargetHighlighter(Client client, ChunkBlazerPlugin plugin, ChunkBlazerConfig config, ModelOutlineRenderer outlineRenderer)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.outlineRenderer = outlineRenderer;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/** Drop all state; called on plugin shutdown. */
	public void reset()
	{
		npcTasks = Collections.emptyMap();
		objectTasks = Collections.emptyMap();
		stationTasks = Collections.emptyMap();
		stationCache.clear();
		trackedObjects.clear();
	}

	// --- Index of what the active tasks want ---------------------------------

	@Subscribe
	public void onGameTick(GameTick event)
	{
		rebuildIndex();
	}

	private void rebuildIndex()
	{
		Map<Integer, List<NuzlockeTask>> npcs = new HashMap<>();
		Map<Integer, List<NuzlockeTask>> objects = new HashMap<>();
		Map<Station, List<NuzlockeTask>> stations = new EnumMap<>(Station.class);

		for (NuzlockeTask task : plugin.getActiveTasks())
		{
			if (task == null || task.isCompleted())
			{
				continue;
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

			Station station = stationFor(task);
			if (station != null)
			{
				addTask(stations, station, task);
			}
		}

		boolean changed = !objects.keySet().equals(objectTasks.keySet())
				|| !stations.keySet().equals(stationTasks.keySet());
		npcTasks = npcs;
		objectTasks = objects;
		stationTasks = stations;
		if (changed)
		{
			rescanScene();
		}
	}

	/** Which station (if any) a skilling task is done at, from its type and name. */
	static Station stationFor(NuzlockeTask task)
	{
		String type = task.getCompletionType() == null ? "" : task.getCompletionType().toUpperCase();
		String name = task.getName() == null ? "" : task.getName().toLowerCase();
		String verb = name.contains(" ") ? name.substring(0, name.indexOf(' ')) : name;

		switch (type)
		{
			case "COOKING":
				if (verb.equals("cook") || verb.equals("bake"))
				{
					return Station.COOKING;
				}
				return verb.equals("churn") ? Station.CHURN : null;
			case "SMITHING":
				if (verb.equals("smelt"))
				{
					return Station.FURNACE;
				}
				return verb.equals("smith") ? Station.ANVIL : null;
			case "CRAFTING":
				if (verb.equals("spin"))
				{
					return Station.SPINNING_WHEEL;
				}
				if (JEWELLERY.matcher(name).find())
				{
					return Station.FURNACE;
				}
				return POTTERY_ITEM.matcher(name).find() ? Station.POTTERY : null;
			case "RUNECRAFTING":
				return Station.RUNE_ALTAR;
			default:
				return null;
		}
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

	/** Every active task that this object id is relevant to (direct id + station). */
	private List<NuzlockeTask> tasksForObject(int id)
	{
		List<NuzlockeTask> result = new ArrayList<>();
		List<NuzlockeTask> direct = objectTasks.get(id);
		if (direct != null)
		{
			result.addAll(direct);
		}
		if (!stationTasks.isEmpty())
		{
			for (Station station : stationsFor(id))
			{
				List<NuzlockeTask> list = stationTasks.get(station);
				if (list != null)
				{
					for (NuzlockeTask task : list)
					{
						if (!result.contains(task))
						{
							result.add(task);
						}
					}
				}
			}
		}
		return result;
	}

	private Set<Station> stationsFor(int id)
	{
		return stationCache.computeIfAbsent(id, this::lookupStations);
	}

	private Set<Station> lookupStations(int id)
	{
		Set<Station> found = EnumSet.noneOf(Station.class);
		ObjectComposition comp = client.getObjectDefinition(id);
		if (comp == null)
		{
			return found;
		}
		if (comp.getImpostorIds() != null)
		{
			ObjectComposition impostor = comp.getImpostor();
			if (impostor != null)
			{
				comp = impostor;
			}
		}

		String name = comp.getName() == null ? "" : Text.removeTags(comp.getName()).toLowerCase();
		Set<String> actions = new HashSet<>();
		if (comp.getActions() != null)
		{
			for (String action : comp.getActions())
			{
				if (action != null)
				{
					actions.add(Text.removeTags(action).toLowerCase());
				}
			}
		}

		for (Station station : Station.values())
		{
			if (station.names.contains(name) || !Collections.disjoint(station.actions, actions))
			{
				found.add(station);
			}
		}
		return found;
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
		if (objectTasks.isEmpty() && stationTasks.isEmpty())
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
		if (object == null)
		{
			return;
		}
		int id = object.getId();
		if (objectTasks.containsKey(id) || (!stationTasks.isEmpty() && !stationsFor(id).isEmpty()))
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
		if (!config.highlightTaskTargets())
		{
			return null;
		}
		Color doable = config.taskHighlightColor();
		Color unavailable = config.taskHighlightUnavailableColor();

		if (!npcTasks.isEmpty())
		{
			for (NPC npc : client.getNpcs())
			{
				if (npc == null)
				{
					continue;
				}
				List<NuzlockeTask> tasks = npcTasks.get(npc.getId());
				if (tasks != null && !tasks.isEmpty())
				{
					outlineRenderer.drawOutline(npc, OUTLINE_WIDTH,
							anyDoable(tasks) ? doable : unavailable, OUTLINE_FEATHER);
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
			List<NuzlockeTask> tasks = tasksForObject(object.getId());
			if (!tasks.isEmpty())
			{
				outlineRenderer.drawOutline(object, OUTLINE_WIDTH,
						anyDoable(tasks) ? doable : unavailable, OUTLINE_FEATHER);
			}
		}
		return null;
	}

	// --- Right-click "Tasks" submenu ----------------------------------------

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.taskRightClickMenu())
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
			tasks = npcTasks.get(npc.getId());
		}
		else if (entry.getType() == MenuAction.EXAMINE_OBJECT)
		{
			tasks = tasksForObject(event.getIdentifier());
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
