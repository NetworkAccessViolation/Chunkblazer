package com.chunkblazer.modules;

import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import com.chunkblazer.TaskConstraints;
import net.runelite.client.chat.ChatMessageManager;
import com.chunkblazer.NuzlockeTask;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ThievingModule.
 * Tests NPC pickpocket detection.
 */
@ExtendWith(MockitoExtension.class)
class ThievingModuleTest extends AbstractTaskModuleTest
{
	@Mock
	private ChatMessageManager chatMessageManager;

	@InjectMocks
	private ThievingModule thievingModule;

	@Mock
	private NPC targetNpc;

	@BeforeEach
	void setUp() throws Exception
	{
		setupCommonMocks();

		injectField(thievingModule, "client", client);
		injectField(thievingModule, "clientThread", clientThread);
		injectField(thievingModule, "eventBus", eventBus);
		injectField(thievingModule, "config", config);

		thievingModule.setCompletionCallback(completionCallback);
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
		assertEquals("THIEVING", thievingModule.getCompletionType());
	}

	@Test
	void testCanHandle_ThievingType()
	{
		NuzlockeTask task = createTestTask("Pickpocket Man", "pickpocket_man", "THIEVING", 10);
		assertTrue(thievingModule.canHandle(task));
	}

	@Test
	void testCanHandle_WrongType()
	{
		NuzlockeTask task = createTestTask("Kill Man", "kill_man", "NPC_KILL", 10);
		assertFalse(thievingModule.canHandle(task));
	}

	@Test
	void testAddActiveTask()
	{
		NuzlockeTask task = createTaskWithNpc("Pickpocket Man", "pickpocket_man", "THIEVING", 10, Arrays.asList(3106, 3107));

		when(client.getSkillExperience(Skill.THIEVING)).thenReturn(1000);

		thievingModule.addActiveTask(task);

		assertEquals(1, thievingModule.getActiveTasks().size());
	}

	@Test
	void testOnTaskCleared()
	{
		NuzlockeTask task = createTaskWithNpc("Pickpocket Man", "pickpocket_man", "THIEVING", 10, Arrays.asList(3106));

		when(client.getSkillExperience(Skill.THIEVING)).thenReturn(1000);

		thievingModule.addActiveTask(task);
		thievingModule.onTaskCleared();

		assertTrue(thievingModule.getActiveTasks().isEmpty());
	}

	@Test
	void testStartUpRegistersEventBus()
	{
		thievingModule.startUp();
		verify(eventBus).register(thievingModule);
	}

	@Test
	void testShutDownUnregistersEventBus()
	{
		thievingModule.shutDown();
		verify(eventBus).unregister(thievingModule);
	}

	// --- GameObject (stall/chest) tests for bug #23 -------------------------------------------
	// Repro: user has Seed Stall (id 7053) and Fortunato's Market Stall (id 14011) tasks active,
	// steals from an unrelated Wine merchant's stall — pre-fix, BOTH tasks completed in lockstep
	// because XP gain with no NPC match fell through to "credit all tasks with no NPC".

	private static final int SEED_STALL_ID = 7053;
	private static final int FORTUNATO_STALL_ID = 14011;
	private static final int WINE_STALL_ID = 14009; // arbitrary unrelated stall

	private MenuOptionClicked mockObjectClick(int objectId, MenuAction action)
	{
		// Lenient stubs because non-object actions short-circuit before getId/getMenuOption
		// are read, and Mockito would otherwise flag those as unnecessary.
		MenuOptionClicked event = mock(MenuOptionClicked.class);
		when(event.getMenuAction()).thenReturn(action);
		lenient().when(event.getId()).thenReturn(objectId);
		lenient().when(event.getMenuOption()).thenReturn("Steal-from");
		return event;
	}

	private StatChanged mockThievingXp(int xp)
	{
		StatChanged event = mock(StatChanged.class);
		when(event.getSkill()).thenReturn(Skill.THIEVING);
		when(event.getXp()).thenReturn(xp);
		return event;
	}

	@Test
	void testStallTheft_OnlyMatchingObjectIdCredited()
	{
		// Both stall tasks active.
		NuzlockeTask seedTask = createTaskWithRequiredObject(
			"Steal from a Seed Stall", "steal_seed_stall", "THIEVING", 1, Collections.singletonList(SEED_STALL_ID));
		NuzlockeTask fortunatoTask = createTaskWithRequiredObject(
			"Steal from Fortunato's Market Stall", "steal_market_stall_draynor", "THIEVING", 1, Collections.singletonList(FORTUNATO_STALL_ID));

		when(client.getSkillExperience(Skill.THIEVING)).thenReturn(1000);
		thievingModule.addActiveTask(seedTask);
		thievingModule.addActiveTask(fortunatoTask);

		// Player clicks Steal-from on the Seed Stall, then gains thieving XP a tick later.
		when(client.getTickCount()).thenReturn(100);
		thievingModule.onMenuOptionClicked(mockObjectClick(SEED_STALL_ID, MenuAction.GAME_OBJECT_SECOND_OPTION));

		when(client.getTickCount()).thenReturn(101);
		thievingModule.onStatChanged(mockThievingXp(1010));

		// Only the Seed Stall task should progress.
		verify(completionCallback).onProgressUpdated(eq(seedTask), eq(1));
		verify(completionCallback, never()).onProgressUpdated(eq(fortunatoTask), anyInt());
	}

	@Test
	void testStallTheft_UnrelatedStallCreditsNothing()
	{
		// This is Mike's exact bug: stealing from a Wine merchant stall (no matching task)
		// must not credit Seed Stall or Fortunato's.
		NuzlockeTask seedTask = createTaskWithRequiredObject(
			"Steal from a Seed Stall", "steal_seed_stall", "THIEVING", 1, Collections.singletonList(SEED_STALL_ID));
		NuzlockeTask fortunatoTask = createTaskWithRequiredObject(
			"Steal from Fortunato's Market Stall", "steal_market_stall_draynor", "THIEVING", 1, Collections.singletonList(FORTUNATO_STALL_ID));

		when(client.getSkillExperience(Skill.THIEVING)).thenReturn(1000);
		thievingModule.addActiveTask(seedTask);
		thievingModule.addActiveTask(fortunatoTask);

		// Wine stall is not in watchedObjectIds, so the click is ignored.
		when(client.getTickCount()).thenReturn(100);
		thievingModule.onMenuOptionClicked(mockObjectClick(WINE_STALL_ID, MenuAction.GAME_OBJECT_SECOND_OPTION));

		when(client.getTickCount()).thenReturn(101);
		thievingModule.onStatChanged(mockThievingXp(1010));

		// Neither task should progress — the old fallback would have credited both.
		verify(completionCallback, never()).onProgressUpdated(any(NuzlockeTask.class), anyInt());
	}

	@Test
	void testStallTheft_NonObjectMenuActionIgnored()
	{
		// A non-object menu action (e.g. walking, examining) must not register as a theft
		// even if the target ID happens to match a watched stall.
		NuzlockeTask seedTask = createTaskWithRequiredObject(
			"Steal from a Seed Stall", "steal_seed_stall", "THIEVING", 1, Collections.singletonList(SEED_STALL_ID));

		when(client.getSkillExperience(Skill.THIEVING)).thenReturn(1000);
		thievingModule.addActiveTask(seedTask);

		when(client.getTickCount()).thenReturn(100);
		// Examine (not an interaction) with the stall's id should be filtered out.
		thievingModule.onMenuOptionClicked(mockObjectClick(SEED_STALL_ID, MenuAction.EXAMINE_OBJECT));

		when(client.getTickCount()).thenReturn(101);
		thievingModule.onStatChanged(mockThievingXp(1010));

		verify(completionCallback, never()).onProgressUpdated(any(NuzlockeTask.class), anyInt());
	}

	@Test
	void testStallTheft_LateXpDoesNotCredit()
	{
		// If the XP gain arrives more than INTERACTION_TIMEOUT_TICKS (5) after the click,
		// it must not be associated with that click.
		NuzlockeTask seedTask = createTaskWithRequiredObject(
			"Steal from a Seed Stall", "steal_seed_stall", "THIEVING", 1, Collections.singletonList(SEED_STALL_ID));

		when(client.getSkillExperience(Skill.THIEVING)).thenReturn(1000);
		thievingModule.addActiveTask(seedTask);

		when(client.getTickCount()).thenReturn(100);
		thievingModule.onMenuOptionClicked(mockObjectClick(SEED_STALL_ID, MenuAction.GAME_OBJECT_SECOND_OPTION));

		// 10 ticks later, well past the 5-tick timeout. XP source is some unrelated thieving activity.
		when(client.getTickCount()).thenReturn(110);
		thievingModule.onStatChanged(mockThievingXp(1010));

		verify(completionCallback, never()).onProgressUpdated(any(NuzlockeTask.class), anyInt());
	}

	// --- Pickpocket arming (donko526: click a guard, steal from a stall) ----------------

	private static final int VARROCK_GUARD = 3010;

	private MenuOptionClicked npcClick(int npcId, String option)
	{
		MenuOptionClicked event = mock(MenuOptionClicked.class);
		net.runelite.api.MenuEntry entry = mock(net.runelite.api.MenuEntry.class);
		net.runelite.api.NPC npc = mock(net.runelite.api.NPC.class);
		when(event.getMenuAction()).thenReturn(MenuAction.NPC_SECOND_OPTION);
		lenient().when(event.getMenuOption()).thenReturn(option);
		lenient().when(event.getMenuEntry()).thenReturn(entry);
		lenient().when(entry.getNpc()).thenReturn(npc);
		lenient().when(npc.getId()).thenReturn(npcId);
		return event;
	}

	private NuzlockeTask guardTask()
	{
		NuzlockeTask task = createTaskWithNpc("Pickpocket a Varrock Guard", "pickpocket_varrock_guard", "THIEVING", 1,
			Collections.singletonList(VARROCK_GUARD));
		when(client.getSkillExperience(Skill.THIEVING)).thenReturn(1000);
		thievingModule.addActiveTask(task);
		return task;
	}

	@Test
	void pickpocketClickThenXpCredits()
	{
		NuzlockeTask task = guardTask();
		when(client.getTickCount()).thenReturn(100);
		thievingModule.onMenuOptionClicked(npcClick(VARROCK_GUARD, "Pickpocket"));
		when(client.getTickCount()).thenReturn(102);
		thievingModule.onStatChanged(mockThievingXp(1040));
		verify(completionCallback).onProgressUpdated(eq(task), eq(1));
	}

	/** The report: click the guard (attack), steal from a stall, the stall's XP counted as a pickpocket. */
	@Test
	void attackingGuardThenStealingFromStallDoesNotCreditPickpocket()
	{
		NuzlockeTask task = guardTask();
		when(client.getTickCount()).thenReturn(100);
		thievingModule.onMenuOptionClicked(npcClick(VARROCK_GUARD, "Attack"));
		thievingModule.onMenuOptionClicked(mockObjectClick(WINE_STALL_ID, MenuAction.GAME_OBJECT_SECOND_OPTION));
		when(client.getTickCount()).thenReturn(102);
		thievingModule.onStatChanged(mockThievingXp(1016));
		verify(completionCallback, never()).onProgressUpdated(eq(task), anyInt());
	}

	@Test
	void pickpocketClickThenStallTheftDoesNotCreditPickpocket()
	{
		NuzlockeTask task = guardTask();
		when(client.getTickCount()).thenReturn(100);
		thievingModule.onMenuOptionClicked(npcClick(VARROCK_GUARD, "Pickpocket"));
		thievingModule.onMenuOptionClicked(mockObjectClick(WINE_STALL_ID, MenuAction.GAME_OBJECT_SECOND_OPTION));
		when(client.getTickCount()).thenReturn(102);
		thievingModule.onStatChanged(mockThievingXp(1016));
		verify(completionCallback, never()).onProgressUpdated(eq(task), anyInt());
	}

	private static final int MASTER_FARMER = 5730;
	private static final int RANARR_SEED = 5295;
	private static final int POTATO_SEED = 5318;

	@Mock
	private ItemContainer pack;

	@Mock
	private ItemContainer seedBox;

	/** "Pickpocket a Master Farmer for a ranarr seed": a Thieving task that needs one item. */
	private NuzlockeTask ranarrTask()
	{
		NuzlockeTask task = createTaskWithNpc("Pickpocket a Master Farmer for a Ranarr seed",
			"pickpocket_master_farmer_ranarr_seed", "THIEVING", 1, Collections.singletonList(MASTER_FARMER));
		TaskConstraints c = new TaskConstraints();
		c.setDroppedItemIds(Collections.singletonList(RANARR_SEED));
		task.setConstraints(c);
		lenient().when(client.getSkillExperience(Skill.THIEVING)).thenReturn(1000);
		lenient().when(client.getItemContainer(anyInt())).thenReturn(null);
		lenient().when(client.getItemContainer(InventoryID.INV)).thenReturn(pack);
		lenient().when(pack.getItems()).thenReturn(new Item[0]);
		thievingModule.addActiveTask(task);
		return task;
	}

	private Item[] items(int id, int qty)
	{
		Item item = mock(Item.class);
		lenient().when(item.getId()).thenReturn(id);
		lenient().when(item.getQuantity()).thenReturn(qty);
		return new Item[]{item};
	}

	/** Pickpocket, XP, then the seed lands: tick 100 click, 101 XP, 102 seed. */
	private int thievingXp = 1000;

	private void pickpocket(Runnable seedLands)
	{
		when(client.getTickCount()).thenReturn(100);
		thievingModule.onMenuOptionClicked(npcClick(MASTER_FARMER, "Pickpocket"));
		when(client.getTickCount()).thenReturn(101);
		thievingXp += 43;
		thievingModule.onStatChanged(mockThievingXp(thievingXp));
		lenient().when(client.getTickCount()).thenReturn(102);
		seedLands.run();
		thievingModule.onGameTick(new GameTick());
	}

	@Test
	void masterFarmerCreditsOnlyForTheRequiredSeed()
	{
		NuzlockeTask task = ranarrTask();
		Item[] potato = items(POTATO_SEED, 1);
		pickpocket(() ->
		{
			when(pack.getItems()).thenReturn(potato);
		});
		when(client.getTickCount()).thenReturn(110);
		thievingModule.onGameTick(new GameTick());
		verify(completionCallback, never()).onProgressUpdated(eq(task), anyInt());

		Item[] ranarr = items(RANARR_SEED, 1);
		pickpocket(() ->
		{
			when(pack.getItems()).thenReturn(ranarr);
		});
		verify(completionCallback).onProgressUpdated(eq(task), eq(1));
	}

	@Test
	void masterFarmerSeedIntoAnOpenSeedBoxCredits()
	{
		NuzlockeTask task = ranarrTask();
		when(client.getItemContainer(InventoryID.SEED_BOX)).thenReturn(seedBox);
		Item[] before = items(RANARR_SEED, 3);
		Item[] after = items(RANARR_SEED, 4);
		when(seedBox.getItems()).thenReturn(before);
		pickpocket(() ->
		{
			when(seedBox.getItems()).thenReturn(after);
		});
		verify(completionCallback).onProgressUpdated(eq(task), eq(1));
	}

	/** A seed box first seen after the click arrives whole; its ranarrs aren't a new seed. */
	@Test
	void seedBoxUnknownAtTheClickIsNotCounted()
	{
		NuzlockeTask task = ranarrTask();
		Item[] box = items(RANARR_SEED, 5);
		pickpocket(() ->
		{
			lenient().when(client.getItemContainer(InventoryID.SEED_BOX)).thenReturn(seedBox);
			lenient().when(seedBox.getItems()).thenReturn(box);
		});
		verify(completionCallback, never()).onProgressUpdated(eq(task), anyInt());
	}
}
