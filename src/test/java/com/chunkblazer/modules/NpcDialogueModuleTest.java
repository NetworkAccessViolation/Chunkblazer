package com.chunkblazer.modules;

import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NpcDialogueModule.
 * Tests NPC dialogue/conversation detection.
 */
@ExtendWith(MockitoExtension.class)
class NpcDialogueModuleTest extends AbstractTaskModuleTest
{
	@Mock
	private ChatMessageManager chatMessageManager;

	@InjectMocks
	private NpcDialogueModule npcDialogueModule;

	@Mock
	private NPC targetNpc;

	@BeforeEach
	void setUp() throws Exception
	{
		setupCommonMocks();

		injectField(npcDialogueModule, "client", client);
		injectField(npcDialogueModule, "clientThread", clientThread);
		injectField(npcDialogueModule, "eventBus", eventBus);
		injectField(npcDialogueModule, "config", config);

		npcDialogueModule.setCompletionCallback(completionCallback);
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
		assertEquals("NPC_DIALOGUE", npcDialogueModule.getCompletionType());
	}

	@Test
	void testCanHandle_NpcDialogueType()
	{
		NuzlockeTask task = createTestTask("Talk to Guard", "talk_guard", "NPC_DIALOGUE", 1);
		assertTrue(npcDialogueModule.canHandle(task));
	}

	@Test
	void testCanHandle_WrongType()
	{
		NuzlockeTask task = createTestTask("Kill Guard", "kill_guard", "NPC_KILL", 5);
		assertFalse(npcDialogueModule.canHandle(task));
	}

	@Test
	void testAddActiveTask()
	{
		NuzlockeTask task = createTaskWithNpc("Talk to Prince Itzla", "talk_prince", "NPC_DIALOGUE", 1, Arrays.asList(13784, 13785));

		npcDialogueModule.addActiveTask(task);

		assertEquals(1, npcDialogueModule.getActiveTasks().size());
	}

	@Test
	void testOnTaskCleared()
	{
		NuzlockeTask task = createTaskWithNpc("Talk to Prince Itzla", "talk_prince", "NPC_DIALOGUE", 1, Arrays.asList(13784));

		npcDialogueModule.addActiveTask(task);
		npcDialogueModule.onTaskCleared();

		assertTrue(npcDialogueModule.getActiveTasks().isEmpty());
	}

	@Test
	void testStartUpRegistersEventBus()
	{
		npcDialogueModule.startUp();
		verify(eventBus).register(npcDialogueModule);
	}

	@Test
	void testShutDownUnregistersEventBus()
	{
		npcDialogueModule.shutDown();
		verify(eventBus).unregister(npcDialogueModule);
	}

	@Test
	void testTaskCompletion()
	{
		NuzlockeTask task = createTaskWithNpc("Talk to Prince", "talk_prince", "NPC_DIALOGUE", 1, Arrays.asList(13784));

		npcDialogueModule.addActiveTask(task);

		// Simulate dialogue completion
		task.setCurrentProgress(1);
		task.setCompleted(true);

		assertTrue(task.isCompleted());
		assertEquals(1, task.getCurrentProgress());
	}

	private static final int ESTATE_AGENT = 3097;

	@Mock
	private Player localPlayer;

	@Mock
	private Widget npcChat;

	/** Click the NPC on {@code clickTick}, open its dialogue on {@code openTick}. */
	private void talk(int clickTick, int openTick, boolean stillInteracting)
	{
		when(targetNpc.getId()).thenReturn(ESTATE_AGENT);
		lenient().when(client.getLocalPlayer()).thenReturn(localPlayer);

		when(client.getTickCount()).thenReturn(clickTick);
		npcDialogueModule.onInteractingChanged(new InteractingChanged(localPlayer, targetNpc));
		npcDialogueModule.onGameTick(new GameTick()); // dialogue still closed

		when(client.getTickCount()).thenReturn(openTick);
		lenient().when(localPlayer.getInteracting()).thenReturn(stillInteracting ? targetNpc : null);
		when(client.getWidget(InterfaceID.ChatLeft.TEXT)).thenReturn(npcChat);
		lenient().when(npcChat.isHidden()).thenReturn(false);
		npcDialogueModule.onGameTick(new GameTick());
	}

	@Test
	void talkRightAwayCredits()
	{
		NuzlockeTask task = createTaskWithNpc("Talk to the Estate Agent", "talk_to_estate_agent", "NPC_DIALOGUE", 1, Arrays.asList(ESTATE_AGENT));
		npcDialogueModule.addActiveTask(task);
		talk(100, 102, true);
		assertTrue(task.isCompleted());
	}

	/** The report: clicked Talk-to from across the room, the walk outlasted the 5-tick window. */
	@Test
	void longWalkStillCredits()
	{
		NuzlockeTask task = createTaskWithNpc("Talk to the Estate Agent", "talk_to_estate_agent", "NPC_DIALOGUE", 1, Arrays.asList(ESTATE_AGENT));
		npcDialogueModule.addActiveTask(task);
		talk(100, 112, true);
		assertTrue(task.isCompleted(), "the dialogue is still with the NPC we walked to");
	}

	@Test
	void unrelatedDialogueLaterDoesNotCredit()
	{
		NuzlockeTask task = createTaskWithNpc("Talk to the Estate Agent", "talk_to_estate_agent", "NPC_DIALOGUE", 1, Arrays.asList(ESTATE_AGENT));
		npcDialogueModule.addActiveTask(task);
		talk(100, 112, false);
		assertFalse(task.isCompleted(), "a dialogue long after, with nobody targeted, isn't the NPC");
	}
}
