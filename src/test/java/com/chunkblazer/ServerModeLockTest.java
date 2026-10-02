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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.chunkblazer.api.PlayerLoginResponse;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Login reconciliation of the mode lock: the server's lock wins a mismatch. */
@ExtendWith(MockitoExtension.class)
class ServerModeLockTest
{
	private static final String RSN = "Chunkatar";

	@Mock
	private ChunkBlazerConfig config;
	@Mock
	private ConfigManager configManager;
	@Mock
	private Client client;
	@Mock
	private ClientThread clientThread;

	private ChunkBlazerPlugin plugin;

	@BeforeEach
	void setUp() throws Exception
	{
		plugin = new ChunkBlazerPlugin();
		set("config", config);
		set("configManager", configManager);
		set("client", client);
		set("clientThread", clientThread);
		RsProfileTestSupport.install(configManager, config);
		Player p = mock(Player.class);
		lenient().when(p.getName()).thenReturn(RSN);
		lenient().when(client.getLocalPlayer()).thenReturn(p);
	}

	@Test
	void adoptsWhenUnlocked()
	{
		plugin.adoptServerModeLock(login("CASUAL", true), RSN);
		assertLocked(GameMode.CASUAL);
		verify(clientThread, never()).invoke(any(Runnable.class));
	}

	@Test
	void serverOverridesLocalCasual()
	{
		plugin.adoptServerModeLock(login("CASUAL", true), RSN);
		plugin.adoptServerModeLock(login("NUZLOCKE", true), RSN);
		assertLocked(GameMode.NUZLOCKE);
		verify(clientThread).invoke(any(Runnable.class));
	}

	@Test
	void matchingLockIsQuiet()
	{
		plugin.adoptServerModeLock(login("NUZLOCKE", true), RSN);
		plugin.adoptServerModeLock(login("NUZLOCKE", true), RSN);
		assertLocked(GameMode.NUZLOCKE);
		verify(clientThread, never()).invoke(any(Runnable.class));
	}

	@Test
	void unlockedServerKeepsLocal()
	{
		plugin.adoptServerModeLock(login("CASUAL", true), RSN);
		plugin.adoptServerModeLock(login(null, false), RSN);
		assertLocked(GameMode.CASUAL);
	}

	private void assertLocked(GameMode mode)
	{
		assertTrue(plugin.isModeLocked());
		assertEquals(mode, plugin.getGameMode());
	}

	private static PlayerLoginResponse login(String mode, boolean locked)
	{
		PlayerLoginResponse.PlayerData d = new PlayerLoginResponse.PlayerData();
		d.setGameMode(mode);
		d.setModeLocked(locked);
		PlayerLoginResponse r = new PlayerLoginResponse();
		r.setStatus("ok");
		r.setPlayer(d);
		return r;
	}

	private void set(String name, Object value) throws Exception
	{
		Field f = ChunkBlazerPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(plugin, value);
	}
}
