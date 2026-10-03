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

import com.chunkblazer.api.PlayerLoginResponse;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Partial task progress comes back from the server backup after a crash or on a new device. */
@ExtendWith(MockitoExtension.class)
class ProgressBackupTest
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
		set("config", config);
		set("configManager", configManager);
		state = RsProfileTestSupport.install(configManager, config);
	}

	@Test
	void crashLossRestored() throws Exception
	{
		// The power-off lost the last saves: locally the bars task is back at 0.
		state.put("taskProgressData", "smelt_bronze:0:62");
		login("smelt_bronze:30:62");
		assertEquals("smelt_bronze:30:62", state.get("taskProgressData"));
	}

	@Test
	void newDeviceGetsProgress() throws Exception
	{
		login("smelt_bronze:30:62");
		assertEquals("smelt_bronze:30:62", state.get("taskProgressData"));
	}

	@Test
	void localAheadKept()
	{
		assertEquals("smelt_bronze:41:62,cook_food:3:20",
			ChunkBlazerPlugin.mergeProgressBlobs("smelt_bronze:41:62", "smelt_bronze:30:62,cook_food:3:20"));
	}

	private void login(String serverProgress) throws Exception
	{
		PlayerLoginResponse.PlayerData d = new PlayerLoginResponse.PlayerData();
		d.setTaskProgress(serverProgress);
		Method m = ChunkBlazerPlugin.class.getDeclaredMethod("mergeTaskProgressFromServer", PlayerLoginResponse.PlayerData.class);
		m.setAccessible(true);
		m.invoke(plugin, d);
	}

	private void set(String name, Object value) throws Exception
	{
		Field f = ChunkBlazerPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(plugin, value);
	}
}
