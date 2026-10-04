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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A chunk never rolls a task that's already out (active or face-down) in another chunk. */
@ExtendWith(MockitoExtension.class)
class DuplicateRollTest
{
	private static final int CHUNK_A = 12341;
	private static final int CHUNK_B = 12342;

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
		chunks().put(CHUNK_A, chunk("mine_coal"));
		chunks().put(CHUNK_B, chunk("mine_coal", "mine_iron", "mine_tin", "mine_clay", "mine_copper", "mine_silver"));
	}

	@Test
	void sharedTaskNotRolledTwice() throws Exception
	{
		for (int i = 0; i < 30; i++)
		{
			state.put("regionRolledTasks", CHUNK_A + ":mine_coal");
			Set<String> rolled = roll(CHUNK_B);
			assertFalse(rolled.isEmpty());
			assertFalse(rolled.contains("mine_coal"), "chunk B re-rolled chunk A's coal task: " + rolled);
		}
	}

	@Test
	void onlySharedTaskLeavesNothing() throws Exception
	{
		chunks().put(CHUNK_B, chunk("mine_coal"));
		state.put("regionRolledTasks", CHUNK_A + ":mine_coal");
		assertTrue(roll(CHUNK_B).isEmpty());
	}

	@SuppressWarnings("unchecked")
	private Set<String> roll(int region) throws Exception
	{
		Method m = ChunkBlazerPlugin.class.getDeclaredMethod("rollTasksForRegion", int.class, boolean.class, boolean.class);
		m.setAccessible(true);
		return (Set<String>) m.invoke(plugin, region, false, false);
	}

	private static NuzlockeChunk chunk(String... taskIds) throws Exception
	{
		List<NuzlockeTask> tasks = new ArrayList<>();
		for (String id : taskIds)
		{
			NuzlockeTask t = new NuzlockeTask();
			t.setTaskId(id);
			set(t, "assignmentWeight", 8);
			tasks.add(t);
		}
		NuzlockeChunk c = new NuzlockeChunk();
		set(c, "tasks", tasks);
		return c;
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
