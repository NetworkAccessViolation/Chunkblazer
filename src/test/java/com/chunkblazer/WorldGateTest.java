/*
 * Copyright (c) 2026, btwinnn
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chunkblazer.api.ChunkBlazerApiClient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Deadman, Leagues and other special worlds don't count; normal worlds (PvP and Fresh Start too) do. */
@ExtendWith(MockitoExtension.class)
class WorldGateTest
{
	@Mock
	private Client client;
	@Mock
	private ChunkBlazerConfig config;
	@Mock
	private ChunkBlazerApiClient apiClient;

	private ChunkBlazerPlugin plugin;

	@BeforeEach
	void setUp() throws Exception
	{
		plugin = new ChunkBlazerPlugin();
		set("client", client);
		set("config", config);
		set("apiClient", apiClient);
	}

	private boolean counts(WorldType... types)
	{
		EnumSet<WorldType> set = EnumSet.noneOf(WorldType.class);
		for (WorldType t : types)
		{
			set.add(t);
		}
		lenient().when(client.getWorldType()).thenReturn(set);
		return plugin.onCountedWorld();
	}

	@Test
	void normalWorldsCount()
	{
		assertTrue(counts());
		assertTrue(counts(WorldType.MEMBERS));
		assertTrue(counts(WorldType.MEMBERS, WorldType.PVP));
		assertTrue(counts(WorldType.MEMBERS, WorldType.FRESH_START_WORLD));
		assertTrue(counts(WorldType.MEMBERS, WorldType.SKILL_TOTAL));
	}

	@Test
	void specialWorldsDont()
	{
		assertFalse(counts(WorldType.MEMBERS, WorldType.DEADMAN), "Deadman");
		assertFalse(counts(WorldType.MEMBERS, WorldType.SEASONAL), "Leagues");
		assertFalse(counts(WorldType.MEMBERS, WorldType.QUEST_SPEEDRUNNING), "quest speedrunning");
		assertFalse(counts(WorldType.MEMBERS, WorldType.BETA_WORLD), "beta");
		assertFalse(counts(WorldType.MEMBERS, WorldType.PVP_ARENA), "PvP Arena");
		assertFalse(counts(WorldType.MEMBERS, WorldType.TOURNAMENT_WORLD), "tournament");
		assertFalse(counts(WorldType.MEMBERS, WorldType.LAST_MAN_STANDING), "LMS");
	}

	/** On a special world nothing is synced, so its progress can never reach the account's server record. */
	@Test
	void noSyncWhilePaused() throws Exception
	{
		lenient().when(config.apiEnabled()).thenReturn(true);
		lenient().when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		set("countedWorld", false);
		Method sync = ChunkBlazerPlugin.class.getDeclaredMethod("syncToServer");
		sync.setAccessible(true);
		sync.invoke(plugin);
		verifyNoInteractions(apiClient);
	}

	private void set(String name, Object value) throws Exception
	{
		Field f = ChunkBlazerPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(plugin, value);
	}
}
