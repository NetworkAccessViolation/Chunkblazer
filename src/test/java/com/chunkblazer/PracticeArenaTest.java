package com.chunkblazer;

import java.lang.reflect.Field;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.VarbitID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GitHub #49: Pete Kayer's PvP tutorial lends level 99 stats, so turning on Piety there
 * completed a prayer task the account couldn't really do. LMS does the same.
 */
@ExtendWith(MockitoExtension.class)
class PracticeArenaTest
{
	@Mock
	private Client client;

	@Mock
	private WorldView worldView;

	@Mock
	private IndexedObjectSet<NPC> npcs;

	private ChunkBlazerPlugin plugin;

	@BeforeEach
	void setUp() throws Exception
	{
		plugin = new ChunkBlazerPlugin();
		Field f = ChunkBlazerPlugin.class.getDeclaredField("client");
		f.setAccessible(true);
		f.set(plugin, client);
		lenient().when(client.getTopLevelWorldView()).thenReturn(worldView);
		lenient().doReturn(npcs).when(worldView).npcs();
	}

	private void nearby(int... ids)
	{
		NPC[] list = new NPC[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			list[i] = mock(NPC.class);
			when(list[i].getId()).thenReturn(ids[i]);
		}
		when(npcs.iterator()).thenReturn(Arrays.asList(list).iterator());
	}

	@Test
	void lmsGameCounts()
	{
		when(client.getVarbitValue(VarbitID.BR_INGAME)).thenReturn(1);
		assertTrue(plugin.inPracticeArena());
	}

	@Test
	void tutorialInstanceCounts()
	{
		nearby(3106, 16590); // a man, and a medium-kit opponent
		assertTrue(plugin.inPracticeArena());
	}

	@Test
	void peteInFeroxDoesNot()
	{
		nearby(16576);
		assertFalse(plugin.inPracticeArena());
	}
}
