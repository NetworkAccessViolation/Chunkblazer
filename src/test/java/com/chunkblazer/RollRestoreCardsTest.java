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

/** Login restore of the server's roll must also drop face-down cards the roll no longer has. */
@ExtendWith(MockitoExtension.class)
class RollRestoreCardsTest
{
	// ChunkDragoon's real rolls: the server's original, and what his new device rolled with sync off.
	private static final String SERVER_ROLL = "12850:chop_tree,pickpocket_man,burn_log,equip_steel_battleaxe";
	private static final String DEVICE_CARDS = "equip_steel_battleaxe,obtain_goblin_mail,Spin_Wool,talk_to_bob_lumbridge";

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
	void newDeviceCardsDropped() throws Exception
	{
		state.put("regionRolledTasks", "12850:" + DEVICE_CARDS);
		state.put("unrevealedTasks", DEVICE_CARDS);

		restore(SERVER_ROLL, "");

		assertEquals(SERVER_ROLL, state.get("regionRolledTasks"));
		assertEquals("equip_steel_battleaxe", state.get("unrevealedTasks"));
	}

	@Test
	void offlineUnlockCardsKept() throws Exception
	{
		state.put("regionRolledTasks", "12851:cow_hide,kill_cow");
		state.put("unrevealedTasks", "cow_hide,kill_cow");

		restore(SERVER_ROLL, "");

		assertEquals("cow_hide,kill_cow", state.get("unrevealedTasks"));
	}

	@Test
	void serverCardsPrunedOnFreshLoad() throws Exception
	{
		restore(SERVER_ROLL, "equip_steel_battleaxe,obtain_goblin_mail");

		assertEquals("equip_steel_battleaxe", state.get("unrevealedTasks"));
	}

	private void restore(String roll, String cards) throws Exception
	{
		PlayerLoginResponse.PlayerData d = new PlayerLoginResponse.PlayerData();
		d.setRegionRolledTasks(roll);
		d.setUnrevealedTasks(cards);
		Method m = ChunkBlazerPlugin.class.getDeclaredMethod("restoreRollStateFromServer", PlayerLoginResponse.PlayerData.class);
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
