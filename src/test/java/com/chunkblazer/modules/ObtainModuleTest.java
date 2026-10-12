package com.chunkblazer.modules;

import com.google.gson.Gson;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.game.ItemManager;
import com.chunkblazer.NuzlockeTask;
import com.chunkblazer.RequiredItem;
import com.chunkblazer.api.ChunkBlazerApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ObtainModule.
 * Tests item acquisition detection for OBTAIN and skilling completion types.
 */
@ExtendWith(MockitoExtension.class)
class ObtainModuleTest extends AbstractTaskModuleTest
{
	@Mock
	private ItemManager itemManager;

	@Mock
	private ChatMessageManager chatMessageManager;

	@Mock
	private ChunkBlazerApiClient apiClient;

	@InjectMocks
	private ObtainModule obtainModule;

	@Mock
	private ItemContainer inventoryContainer;

	@BeforeEach
	void setUp() throws Exception
	{
		setupCommonMocks();

		// Inject mocks
		injectField(obtainModule, "client", client);
		injectField(obtainModule, "clientThread", clientThread);
		injectField(obtainModule, "eventBus", eventBus);
		injectField(obtainModule, "config", config);
		injectField(obtainModule, "itemManager", itemManager);

		obtainModule.setCompletionCallback(completionCallback);

		// Setup item manager to return same ID (no noted items)
		lenient().when(itemManager.canonicalize(anyInt())).thenAnswer(inv -> inv.getArgument(0));
	}

	private void injectField(Object target, String fieldName, Object value) throws Exception
	{
		Field field = findField(target.getClass(), fieldName);
		if (field != null)
		{
			field.setAccessible(true);
			field.set(target, value);
		}
	}

	private Field findField(Class<?> clazz, String fieldName)
	{
		while (clazz != null)
		{
			try
			{
				return clazz.getDeclaredField(fieldName);
			}
			catch (NoSuchFieldException e)
			{
				clazz = clazz.getSuperclass();
			}
		}
		return null;
	}

	@Test
	void testGetCompletionType()
	{
		assertEquals("OBTAIN", obtainModule.getCompletionType());
	}

	@Test
	void testCanHandle_ObtainType()
	{
		NuzlockeTask task = createTestTask("Obtain Logs", "obtain_logs", "OBTAIN", 5);
		assertTrue(obtainModule.canHandle(task));
	}

	@Test
	void testCanHandle_CookingType()
	{
		NuzlockeTask task = createTestTask("Cook Shrimp", "cook_shrimp", "COOKING", 10);
		assertTrue(obtainModule.canHandle(task));
	}

	@Test
	void testCanHandle_CraftingType()
	{
		NuzlockeTask task = createTestTask("Craft Bowstring", "craft_bowstring", "CRAFTING", 5);
		assertTrue(obtainModule.canHandle(task));
	}

	@Test
	void testCanHandle_SmithingType()
	{
		NuzlockeTask task = createTestTask("Smith Bronze Bar", "smith_bronze", "SMITHING", 10);
		assertTrue(obtainModule.canHandle(task));
	}

	@Test
	void testCanHandle_MiningType()
	{
		NuzlockeTask task = createTestTask("Mine Copper", "mine_copper", "MINING", 15);
		assertTrue(obtainModule.canHandle(task));
	}

	@Test
	void testCanHandle_WoodcuttingType()
	{
		NuzlockeTask task = createTestTask("Chop Logs", "chop_logs", "WOODCUTTING", 20);
		assertTrue(obtainModule.canHandle(task));
	}

	@Test
	void testCanHandle_FishingType()
	{
		NuzlockeTask task = createTestTask("Catch Shrimp", "catch_shrimp", "FISHING", 10);
		assertTrue(obtainModule.canHandle(task));
	}

	@Test
	void testCanHandle_FletchingType()
	{
		NuzlockeTask task = createTestTask("Fletch Arrows", "fletch_arrows", "FLETCHING", 50);
		assertTrue(obtainModule.canHandle(task));
	}

	@Test
	void testCanHandle_HerbloreType()
	{
		NuzlockeTask task = createTestTask("Make Potion", "make_potion", "HERBLORE", 5);
		assertTrue(obtainModule.canHandle(task));
	}

	@Test
	void testCanHandle_RunecraftingType()
	{
		NuzlockeTask task = createTestTask("Craft Runes", "craft_runes", "RUNECRAFTING", 100);
		assertTrue(obtainModule.canHandle(task));
	}

	@Test
	void testCanHandle_HunterType()
	{
		NuzlockeTask task = createTestTask("Catch Chinchompa", "catch_chin", "HUNTER", 10);
		assertTrue(obtainModule.canHandle(task));
	}

	@Test
	void testCanHandle_WrongType()
	{
		NuzlockeTask task = createTestTask("Kill Monster", "kill_monster", "NPC_KILL", 5);
		assertFalse(obtainModule.canHandle(task));
	}

	@Test
	void testAddActiveTask()
	{
		NuzlockeTask task = createTaskWithItems("Obtain Logs", "obtain_logs", "OBTAIN", 5, Arrays.asList(1511));

		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventoryContainer);
		when(inventoryContainer.getItems()).thenReturn(new Item[0]);

		obtainModule.addActiveTask(task);

		assertEquals(1, obtainModule.getActiveTasks().size());
	}

	@Test
	void testOnTaskCleared()
	{
		NuzlockeTask task = createTaskWithItems("Obtain Logs", "obtain_logs", "OBTAIN", 5, Arrays.asList(1511));

		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventoryContainer);
		when(inventoryContainer.getItems()).thenReturn(new Item[0]);

		obtainModule.addActiveTask(task);
		obtainModule.onTaskCleared();

		assertTrue(obtainModule.getActiveTasks().isEmpty());
	}

	@Test
	void testStartUpRegistersEventBus()
	{
		obtainModule.startUp();
		verify(eventBus).register(obtainModule);
	}

	@Test
	void testShutDownUnregistersEventBus()
	{
		obtainModule.shutDown();
		verify(eventBus).unregister(obtainModule);
	}

	@Test
	void testTaskProgressTracking()
	{
		NuzlockeTask task = createTaskWithItems("Obtain 10 Logs", "obtain_logs", "OBTAIN", 10, Arrays.asList(1511));

		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventoryContainer);
		when(inventoryContainer.getItems()).thenReturn(new Item[0]);

		obtainModule.addActiveTask(task);

		// Simulate progress
		task.setCurrentProgress(5);
		assertEquals(5, task.getCurrentProgress());
		assertFalse(task.isCompleted());

		// Complete task
		task.setCurrentProgress(10);
		task.setCompleted(true);
		assertTrue(task.isCompleted());
	}

	/**
	 * A bank withdrawal in the same tick as a skilling XP drop isn't production:
	 * the last batch made counts, the stack withdrawn right after it doesn't.
	 * Fletching, because it has no per-tick cap to hide the bug.
	 */
	@Test
	void bankWithdrawSameTickNotCredited()
	{
		NuzlockeTask task = skillingTask("FLETCHING", Skill.FLETCHING, ARROW_SHAFT, 500);
		Item[] afterFletch = {item(ARROW_SHAFT, 15)};
		Item[] afterWithdraw = {item(ARROW_SHAFT, 315)};

		when(inventoryContainer.getItems()).thenReturn(afterFletch);
		obtainModule.onStatChanged(new StatChanged(Skill.FLETCHING, 5, 1, 1));
		assertEquals(15, task.getCurrentProgress());

		// Same tick: the bank is open and 300 more come out of it.
		when(client.getWidget(InterfaceID.Bankmain.UNIVERSE)).thenReturn(mock(Widget.class));
		when(inventoryContainer.getItems()).thenReturn(afterWithdraw);
		obtainModule.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, inventoryContainer));

		assertEquals(15, task.getCurrentProgress());
	}

	/**
	 * Cooking never makes more than one item per tick, so a bigger rise in a tick
	 * with Cooking XP (a shop buy, trade or pickup landing alongside it) counts as
	 * one. The next tick's cook still counts normally.
	 */
	@Test
	void cookingCappedAtOnePerTick()
	{
		NuzlockeTask task = skillingTask("COOKING", Skill.COOKING, COOKED_FOOD, 50);
		Item[] tickOne = {item(COOKED_FOOD, 5)};
		Item[] tickTwo = {item(COOKED_FOOD, 6)};

		when(inventoryContainer.getItems()).thenReturn(tickOne);
		obtainModule.onStatChanged(new StatChanged(Skill.COOKING, 30, 1, 1));
		assertEquals(1, task.getCurrentProgress());
		assertFalse(task.isCompleted()); // bug #27: one cook used to finish a ranged task

		obtainModule.onGameTick(new GameTick());
		when(inventoryContainer.getItems()).thenReturn(tickTwo);
		obtainModule.onStatChanged(new StatChanged(Skill.COOKING, 60, 1, 1));
		assertEquals(2, task.getCurrentProgress());
	}

	@Mock
	private ItemContainer bagContainer;

	/**
	 * A bag's first contents in a session arrive whole: one pickpocket into a seed box
	 * already holding 40 seeds must not read as 41 made. They're the baseline; the bag's
	 * next change on an XP drop is real production.
	 */
	@Test
	void bagFirstContentsAreBaselineThenChangesCredit()
	{
		lenient().when(client.getItemContainer(anyInt())).thenReturn(null);
		NuzlockeTask task = skillingTask("FLETCHING", Skill.FLETCHING, ARROW_SHAFT, 500);
		when(client.getItemContainer(InventoryID.SEED_BOX)).thenReturn(bagContainer);

		Item i1 = item(ARROW_SHAFT, 40);
		when(bagContainer.getItems()).thenReturn(new Item[]{i1});
		obtainModule.onStatChanged(new StatChanged(Skill.FLETCHING, 5, 1, 1));
		obtainModule.onItemContainerChanged(new ItemContainerChanged(InventoryID.SEED_BOX, bagContainer));
		assertEquals(0, task.getCurrentProgress(), "the bag's existing contents aren't production");
		obtainModule.onGameTick(new GameTick());

		Item i2 = item(ARROW_SHAFT, 55);
		when(bagContainer.getItems()).thenReturn(new Item[]{i2});
		obtainModule.onStatChanged(new StatChanged(Skill.FLETCHING, 10, 1, 1));
		assertEquals(15, task.getCurrentProgress(), "made straight into the bag");
	}

	/** Moving made items from the pack into a bag nets to zero, so nothing is credited. */
	@Test
	void movingIntoABagIsNotProduction()
	{
		lenient().when(client.getItemContainer(anyInt())).thenReturn(null);
		NuzlockeTask task = skillingTask("FLETCHING", Skill.FLETCHING, ARROW_SHAFT, 500);
		when(client.getItemContainer(InventoryID.SEED_BOX)).thenReturn(bagContainer);
		when(bagContainer.getItems()).thenReturn(new Item[0]);
		obtainModule.onItemContainerChanged(new ItemContainerChanged(InventoryID.SEED_BOX, bagContainer));
		Item i3 = item(ARROW_SHAFT, 15);
		when(inventoryContainer.getItems()).thenReturn(new Item[]{i3});
		obtainModule.onStatChanged(new StatChanged(Skill.FLETCHING, 5, 1, 1));
		obtainModule.onGameTick(new GameTick());
		assertEquals(15, task.getCurrentProgress());

		when(inventoryContainer.getItems()).thenReturn(new Item[0]);
		Item i4 = item(ARROW_SHAFT, 15);
		when(bagContainer.getItems()).thenReturn(new Item[]{i4});
		obtainModule.onStatChanged(new StatChanged(Skill.FLETCHING, 10, 1, 1));
		obtainModule.onItemContainerChanged(new ItemContainerChanged(InventoryID.SEED_BOX, bagContainer));
		assertEquals(15, task.getCurrentProgress());
	}

	/** An obtain task counts an item kept in a looting bag. */
	@Test
	void obtainCountsAnItemInALootingBag()
	{
		lenient().when(client.getItemContainer(anyInt())).thenReturn(null);
		NuzlockeTask task = new NuzlockeTask();
		task.setTaskId("test_obtain_bag");
		task.setCompletionType("OBTAIN");
		RequiredItem required = new RequiredItem();
		required.setItemIds(Arrays.asList(COOKED_FOOD));
		required.setRolledQuantity(1);
		task.setRequiredItems(Collections.singletonList(required));
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventoryContainer);
		when(inventoryContainer.getItems()).thenReturn(new Item[0]);
		obtainModule.addActiveTask(task);

		when(client.getItemContainer(InventoryID.LOOTING_BAG)).thenReturn(bagContainer);
		Item i5 = item(COOKED_FOOD, 1);
		when(bagContainer.getItems()).thenReturn(new Item[]{i5});
		obtainModule.onItemContainerChanged(new ItemContainerChanged(InventoryID.LOOTING_BAG, bagContainer));

		assertTrue(task.isCompleted());
	}

	private static final int RAW_TROUT = 335;

	private void chat(String message)
	{
		ChatMessage m = new ChatMessage();
		m.setType(ChatMessageType.SPAM);
		m.setMessage(message);
		obtainModule.onChatMessage(m);
	}

	private NuzlockeTask troutTask()
	{
		lenient().when(client.isClientThread()).thenReturn(true);
		net.runelite.api.ItemComposition comp = mock(net.runelite.api.ItemComposition.class);
		lenient().when(comp.getName()).thenReturn("Raw trout");
		lenient().when(itemManager.getItemComposition(RAW_TROUT)).thenReturn(comp);
		return skillingTask("FISHING", Skill.FISHING, RAW_TROUT, 10);
	}

	/** A trout caught into a fish barrel never reaches the pack; the catch message counts it. */
	@Test
	void catchMessageCreditsAFishThatWentIntoABarrel()
	{
		NuzlockeTask task = troutTask();
		obtainModule.onStatChanged(new StatChanged(Skill.FISHING, 50, 1, 1));
		chat("You catch a trout.");
		obtainModule.onGameTick(new GameTick());
		assertEquals(1, task.getCurrentProgress());

		obtainModule.onStatChanged(new StatChanged(Skill.FISHING, 100, 1, 1));
		chat("You catch a trout.");
		chat("Rada's blessing enabled you to catch an extra fish.");
		obtainModule.onGameTick(new GameTick());
		assertEquals(3, task.getCurrentProgress());
	}

	/** A fish that reached the pack is counted once, not again for its message. */
	@Test
	void catchIntoThePackIsNotCountedTwice()
	{
		NuzlockeTask task = troutTask();
		Item trout = item(RAW_TROUT, 1);
		when(inventoryContainer.getItems()).thenReturn(new Item[]{trout});
		obtainModule.onStatChanged(new StatChanged(Skill.FISHING, 50, 1, 1));
		chat("You catch a trout.");
		obtainModule.onGameTick(new GameTick());
		assertEquals(1, task.getCurrentProgress());
	}

	/** No Fishing XP, no credit: the message alone proves nothing. */
	@Test
	void catchMessageWithoutXpDoesNotCredit()
	{
		NuzlockeTask task = troutTask();
		chat("You catch a trout.");
		obtainModule.onGameTick(new GameTick());
		assertEquals(0, task.getCurrentProgress());
	}

	// Any cooked food behaves the same; this is a lobster.
	private static final int COOKED_FOOD = 379;
	private static final int ARROW_SHAFT = 52;

	/** An active skilling task with an empty inventory and its skill's XP baseline seeded. */
	private NuzlockeTask skillingTask(String completionType, Skill skill, int itemId, int quantity)
	{
		NuzlockeTask task = new NuzlockeTask();
		task.setTaskId("test_" + completionType.toLowerCase());
		task.setCompletionType(completionType);
		RequiredItem required = new RequiredItem();
		required.setItemIds(Arrays.asList(itemId));
		required.setRolledQuantity(quantity);
		task.setRequiredItems(Collections.singletonList(required));

		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventoryContainer);
		when(inventoryContainer.getItems()).thenReturn(new Item[0]);
		obtainModule.addActiveTask(task);
		obtainModule.onStatChanged(new StatChanged(skill, 0, 1, 1));
		return task;
	}

	private Item item(int itemId, int quantity)
	{
		Item item = mock(Item.class);
		when(item.getId()).thenReturn(itemId);
		when(item.getQuantity()).thenReturn(quantity);
		return item;
	}

	/**
	 * Mike's obtain_set bug: duplicate copies of one set piece must not count
	 * toward the other pieces. With 3 Splitbark helms held, set progress is 1
	 * (the helm slot, capped at its quantity of 1) — not 3. The set completes
	 * only once every distinct piece is held.
	 */
	@Test
	void testObtainSet_DuplicatesFillOwnSlot()
	{
		NuzlockeTask task = new NuzlockeTask();
		task.setName("Obtain a Splitbark Set");
		task.setTaskId("obtain_splitbark_set");
		task.setCompletionType("OBTAIN");
		task.setCurrentProgress(0);
		task.setCompleted(false);
		task.setRequiredItems(Arrays.asList(
			pieceOf(3385), pieceOf(3387), pieceOf(3389), pieceOf(3391), pieceOf(3393)));
		task.setTargetQuantity(5);

		// 3 unstacked helms in inventory, nothing else. addActiveTask runs
		// checkTaskProgress inline for OBTAIN tasks (clientThread mock).
		// Build the mock items BEFORE the thenReturn call — stubbing a new
		// mock inside another stubbing trips UnfinishedStubbingException.
		Item[] threeHelms = {itemOf(3385), itemOf(3385), itemOf(3385)};
		when(client.getItemContainer(InventoryID.INV)).thenReturn(inventoryContainer);
		when(inventoryContainer.getItems()).thenReturn(threeHelms);

		obtainModule.addActiveTask(task);

		assertEquals(1, task.getCurrentProgress(),
			"3 helms fill the helm slot once — progress must be 1/5, not 3/5");
		assertFalse(task.isCompleted());

		// One of each remaining piece arrives: now the set is complete.
		Item[] fullSetPlusSpares = {
			itemOf(3385), itemOf(3385), itemOf(3385),
			itemOf(3387), itemOf(3389), itemOf(3391), itemOf(3393)};
		when(inventoryContainer.getItems()).thenReturn(fullSetPlusSpares);
		obtainModule.checkProgress();

		assertEquals(5, task.getCurrentProgress());
		assertTrue(task.isCompleted(), "holding every distinct piece must complete the set");
	}

	private static RequiredItem pieceOf(int itemId)
	{
		RequiredItem piece = new RequiredItem();
		piece.setItemIds(Arrays.asList(itemId));
		return piece;
	}

	private Item itemOf(int itemId)
	{
		Item item = mock(Item.class);
		lenient().when(item.getId()).thenReturn(itemId);
		lenient().when(item.getQuantity()).thenReturn(1);
		return item;
	}

	/**
	 * Companion for #27: prove that a JSON {@code quantity: [5, 25]} field
	 * deserializes into a {@link RequiredItem} whose
	 * {@link RequiredItem#getRequiredQuantity()} returns a value in [5, 25],
	 * not the fallback default of 1. Catches the case where the deserializer
	 * silently misses the range and the slot ends up sized at 1.
	 */
	@Test
	void testRequiredItem_ParsesRange()
	{
		String json = "{ \"item\": \"Shrimp\", \"item_ids\": [315], \"quantity\": [5, 25] }";
		RequiredItem item = new Gson().fromJson(json, RequiredItem.class);

		assertNotNull(item.getQuantityRange(), "quantity_range should populate from quantity:[a,b]");
		assertEquals(2, item.getQuantityRange().size());
		assertEquals(5, (int) item.getQuantityRange().get(0));
		assertEquals(25, (int) item.getQuantityRange().get(1));
		assertNull(item.getQuantity(), "quantity should be null when only a range was provided");

		int rolled = item.getRequiredQuantity();
		assertTrue(rolled >= 5 && rolled <= 25,
			"getRequiredQuantity should roll within [5, 25] but got " + rolled);

		// Subsequent calls must return the cached roll — otherwise modules
		// reading this later (panel, chatbox, ObtainModule) see a different
		// number than the assignment system used.
		assertEquals(rolled, item.getRequiredQuantity(),
			"second call to getRequiredQuantity must return the cached roll");
	}

	/**
	 * FullOfSodium's cold-start bug (reported by AzSki, 2026-07-19): log out,
	 * CLOSE the client, log back in, and a "Runecraft a Law Rune" task completes
	 * from runes the player merely held. Confirmed on Earth, Law, Blood, Nature —
	 * all quantity-1 tasks.
	 *
	 * Traced sequence before the fix:
	 *   1. addActiveTask's deferred seed ran while the client was still empty:
	 *      the inventory container was null (snapshot {563=0}) and
	 *      getSkillExperience returned 0 (previousXp seeded to 0).
	 *   2. The login StatChanged then arrived with the real Runecraft XP. Because
	 *      previousXp was 0 rather than NULL, it cleared the "first sighting"
	 *      guard in onStatChanged and read as a genuine gain.
	 *   3. That flagged skillsXpGainedThisTick, so the 28 runes already in the
	 *      inventory scored as a delta from the 0 snapshot -> +1 -> complete.
	 *
	 * Both halves had to be wrong at once, which is why it only reproduced on a
	 * cold client — a warm relog keeps previousXp in memory.
	 *
	 * The fix makes the seed wait for the login sync. Removing that readiness
	 * gate makes this test fail with progress=1, completed=true.
	 */
	@Test
	void coldStartDoesNotCreditRunesAlreadyHeld()
	{
		NuzlockeTask task = createTestTask("Runecraft a Law Rune", "runecraft_law_rune", "RUNECRAFTING", 1);
		RequiredItem law = new RequiredItem();
		law.setItemIds(Arrays.asList(563));
		law.setRolledQuantity(1);
		task.setRequiredItems(Collections.singletonList(law));

		// COLD START: logged in, but nothing has synced — no inventory container
		// and the skill table still reads zero.
		lenient().when(client.getItemContainer(InventoryID.INV)).thenReturn(null);
		lenient().when(client.getSkillExperience(Skill.RUNECRAFT)).thenReturn(0);

		obtainModule.addActiveTask(task);

		// The login sync lands: real Runecraft XP, and an inventory that happens
		// to hold a stack of law runes bought from another player.
		Item runes = mock(Item.class);
		lenient().when(runes.getId()).thenReturn(563);
		lenient().when(runes.getQuantity()).thenReturn(28);
		lenient().when(inventoryContainer.getItems()).thenReturn(new Item[]{runes});
		lenient().when(client.getItemContainer(InventoryID.INV)).thenReturn(inventoryContainer);
		lenient().when(client.getSkillExperience(Skill.RUNECRAFT)).thenReturn(284838);

		// The deferred seed now runs against a synced client: snapshot records the
		// 28 runes, and the XP baseline is the player's real XP.
		tickClientThread();

		obtainModule.onStatChanged(new StatChanged(Skill.RUNECRAFT, 284838, 60, 60));
		obtainModule.onItemContainerChanged(
			new ItemContainerChanged(InventoryID.INV, inventoryContainer));

		assertEquals(0, task.getCurrentProgress(),
			"holding runes at login must not count as crafting them");
		assertFalse(task.isCompleted(), "cold start must not auto-complete a runecraft task");
	}
}
