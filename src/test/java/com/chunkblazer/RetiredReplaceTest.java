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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A retired task the player never finished is swapped for another task from the same chunk. */
@ExtendWith(MockitoExtension.class)
class RetiredReplaceTest
{
	private static final int MANOR = 12340;
	private static final String SKELETON = "defeat_draynor_manor_skeleton";

	@Mock
	private ChunkBlazerConfig config;
	@Mock
	private ConfigManager configManager;
	@Mock
	private ClientThread clientThread;

	private ChunkBlazerPlugin plugin;
	private Map<String, String> state;

	@BeforeEach
	void setUp() throws Exception
	{
		plugin = new ChunkBlazerPlugin();
		set(plugin, "config", config);
		set(plugin, "configManager", configManager);
		set(plugin, "clientThread", clientThread);
		state = RsProfileTestSupport.install(configManager, config);
		manor(SKELETON, "defeat_a_ghost", "defeat_rats", "defeat_count_draynor");
	}

	@Test
	void retiredSwappedForSameChunk() throws Exception
	{
		state.put("regionRolledTasks", MANOR + ":" + SKELETON + ",defeat_a_ghost");
		state.put("unrevealedTasks", SKELETON);
		replace();
		Set<String> rolled = rolled();
		assertFalse(rolled.contains(SKELETON));
		assertTrue(rolled.contains("defeat_a_ghost"));
		assertEquals(2, rolled.size());
		assertEquals("", state.get("unrevealedTasks"));
	}

	@Test
	void completedRetiredLeftAlone() throws Exception
	{
		state.put("regionRolledTasks", MANOR + ":" + SKELETON + ",defeat_a_ghost");
		when(config.completedTasks()).thenReturn(SKELETON);
		replace();
		assertTrue(rolled().contains(SKELETON));
	}

	@Test
	void nothingLeftJustDrops() throws Exception
	{
		manor(SKELETON, "defeat_a_ghost");
		state.put("regionRolledTasks", MANOR + ":" + SKELETON + ",defeat_a_ghost");
		replace();
		assertEquals(new HashSet<>(Arrays.asList("defeat_a_ghost")), rolled());
	}

	@Test
	void replacementNotOutElsewhere() throws Exception
	{
		for (int i = 0; i < 20; i++)
		{
			state.put("regionRolledTasks", "12339:defeat_rats|" + MANOR + ":" + SKELETON + ",defeat_a_ghost");
			replace();
			assertTrue(rolled().contains("defeat_count_draynor"), "only Count Draynor is free: " + rolled());
		}
	}

	private Set<String> rolled()
	{
		String blob = state.get("regionRolledTasks");
		for (String entry : blob.split("\\|"))
		{
			if (entry.startsWith(MANOR + ":"))
			{
				return new HashSet<>(Arrays.asList(entry.substring(entry.indexOf(':') + 1).split(",")));
			}
		}
		return new HashSet<>();
	}

	private void replace() throws Exception
	{
		Method m = ChunkBlazerPlugin.class.getDeclaredMethod("replaceRetiredTasks");
		m.setAccessible(true);
		m.invoke(plugin);
	}

	@SuppressWarnings("unchecked")
	private void manor(String... taskIds) throws Exception
	{
		List<NuzlockeTask> tasks = new ArrayList<>();
		for (String id : taskIds)
		{
			NuzlockeTask t = new NuzlockeTask();
			t.setTaskId(id);
			t.setName(id);
			t.setIsUnlocked(!id.equals(SKELETON));
			set(t, "assignmentWeight", 8);
			tasks.add(t);
		}
		NuzlockeChunk c = new NuzlockeChunk();
		set(c, "tasks", tasks);
		Field f = ChunkBlazerPlugin.class.getDeclaredField("chunksByRegionId");
		f.setAccessible(true);
		((Map<Integer, NuzlockeChunk>) f.get(plugin)).put(MANOR, c);
	}

	private static void set(Object target, String name, Object value) throws Exception
	{
		Field f = target.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}
}
