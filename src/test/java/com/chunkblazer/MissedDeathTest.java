package com.chunkblazer;

import com.chunkblazer.modules.TaskModuleManager;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.game.NpcUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Amoxliatl's health bar never sets the dead flag, so the client sends no ActorDeath
 * for it and its kill task, challenges and boss token never fired. A despawn that
 * RuneLite's isDying calls a death, with no ActorDeath before it, is replayed once.
 */
@ExtendWith(MockitoExtension.class)
class MissedDeathTest
{
	@Mock
	private Client client;

	@Mock
	private NpcUtil npcUtil;

	@Mock
	private TaskModuleManager modules;

	@Mock
	private NPC npc;

	private ChunkBlazerPlugin plugin;

	@BeforeEach
	void setUp() throws Exception
	{
		plugin = new ChunkBlazerPlugin();
		set("client", client);
		set("npcUtil", npcUtil);
		set("taskModuleManager", modules);
	}

	private void set(String name, Object value) throws Exception
	{
		Field f = ChunkBlazerPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(plugin, value);
	}

	@Test
	void unreportedDeathIsReplayedToTheModules()
	{
		when(npcUtil.isDying(npc)).thenReturn(true);
		plugin.onNpcDespawned(new NpcDespawned(npc));
		verify(modules).onMissedDeath(any(ActorDeath.class));
	}

	@Test
	void reportedDeathIsNotCountedTwice()
	{
		plugin.onActorDeath(new ActorDeath(npc));
		plugin.onNpcDespawned(new NpcDespawned(npc));
		verify(modules, never()).onMissedDeath(any());
	}

	@Test
	void despawnWithoutDyingIsNotADeath()
	{
		when(npcUtil.isDying(npc)).thenReturn(false);
		plugin.onNpcDespawned(new NpcDespawned(npc));
		verify(modules, never()).onMissedDeath(any());
	}
}
