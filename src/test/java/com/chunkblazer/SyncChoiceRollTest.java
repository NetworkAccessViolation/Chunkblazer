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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A fresh install deals Lumbridge only once the player has picked Enable Sync or Play offline. */
@ExtendWith(MockitoExtension.class)
class SyncChoiceRollTest
{
	@Mock
	private ChunkBlazerConfig config;
	@Mock
	private ConfigManager configManager;

	private ChunkBlazerPlugin plugin;
	private Map<String, String> state;

	@BeforeEach
	void setUp() throws Exception
	{
		plugin = new ChunkBlazerPlugin();
		set(plugin, "config", config);
		set(plugin, "configManager", configManager);
		state = RsProfileTestSupport.install(configManager, config);
		lenient().when(config.showTaskCards()).thenReturn(true);

		List<NuzlockeTask> tasks = new ArrayList<>();
		for (String id : new String[]{"chop_tree", "burn_log", "pickpocket_man", "equip_steel_battleaxe", "Spin_Wool"})
		{
			NuzlockeTask t = new NuzlockeTask();
			t.setTaskId(id);
			set(t, "assignmentWeight", 8);
			tasks.add(t);
		}
		NuzlockeChunk lumbridge = new NuzlockeChunk();
		set(lumbridge, "tasks", tasks);
		chunks().put(12850, lumbridge);
	}

	@Test
	void newDeviceWaitsForChoice()
	{
		plugin.ensureStartingChunkUnlocked();

		assertNull(state.get("regionRolledTasks"));
		assertNull(state.get("unrevealedTasks"));
	}

	@Test
	void playOfflineDealsLumbridge()
	{
		when(configManager.getConfiguration("chunkblazer", "playOffline")).thenReturn("true");

		plugin.ensureStartingChunkUnlocked();

		assertTrue(state.get("regionRolledTasks").startsWith("12850:"));
		assertFalse(state.get("unrevealedTasks").isEmpty());
	}

	@Test
	void existingOfflinePlayerNotBlocked()
	{
		state.put("regionRolledTasks", "12851:kill_cow");

		assertTrue(plugin.canRollMissing());
	}

	@Test
	void syncWaitsForRestore()
	{
		when(config.apiEnabled()).thenReturn(true);

		assertFalse(plugin.canRollMissing());
	}

	@SuppressWarnings("unchecked")
	private Map<Integer, NuzlockeChunk> chunks() throws Exception
	{
		Field f = ChunkBlazerPlugin.class.getDeclaredField("chunksByRegionId");
		f.setAccessible(true);
		return (Map<Integer, NuzlockeChunk>) f.get(plugin);
	}

	private static void set(Object target, String name, Object value) throws Exception
	{
		Field f = target.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}
}
