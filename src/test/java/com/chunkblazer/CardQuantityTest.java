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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The quantity shown on a face-down card is the one the task gets when it's revealed. */
@ExtendWith(MockitoExtension.class)
class CardQuantityTest
{
	@Mock
	private ChunkBlazerConfig config;
	@Mock
	private ConfigManager configManager;

	private ChunkBlazerPlugin plugin;
	private Map<String, NuzlockeTask> tasksById;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() throws Exception
	{
		plugin = new ChunkBlazerPlugin();
		set("config", config);
		set("configManager", configManager);
		RsProfileTestSupport.install(configManager, config);
		Field f = ChunkBlazerPlugin.class.getDeclaredField("tasksById");
		f.setAccessible(true);
		tasksById = (Map<String, NuzlockeTask>) f.get(plugin);
	}

	@Test
	void cardMatchesRevealedTask() throws Exception
	{
		Method initialize = ChunkBlazerPlugin.class.getDeclaredMethod("initializeTask", NuzlockeTask.class);
		initialize.setAccessible(true);
		for (int i = 0; i < 30; i++)
		{
			NuzlockeTask task = rangedTask("cook_" + i);
			tasksById.put(task.getTaskId(), task);

			int shown = plugin.getTaskTargetQuantity(task.getTaskId());
			initialize.invoke(plugin, task);

			assertEquals(shown, task.getTargetQuantity());
			assertEquals(shown, plugin.getTaskTargetQuantity(task.getTaskId()));
		}
	}

	private static NuzlockeTask rangedTask(String id)
	{
		NuzlockeTask task = new NuzlockeTask();
		task.setTaskId(id);
		task.setCompletionType("COOKING");
		RequiredItem food = new RequiredItem();
		food.setItemIds(Arrays.asList(379));
		food.setQuantityRange(Arrays.asList(5, 50));
		task.setRequiredItems(Collections.singletonList(food));
		return task;
	}

	private void set(String name, Object value) throws Exception
	{
		Field f = ChunkBlazerPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(plugin, value);
	}
}
