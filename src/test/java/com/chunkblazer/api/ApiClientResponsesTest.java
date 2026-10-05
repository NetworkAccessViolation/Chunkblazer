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

package com.chunkblazer.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

import com.chunkblazer.ChunkBlazerConfig;
import com.chunkblazer.GameMode;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * What each API call returns for a success, an HTTP error and a dropped connection,
 * through the real OkHttp request path (an interceptor answers instead of the network).
 */
@ExtendWith(MockitoExtension.class)
class ApiClientResponsesTest
{
	private static final String KEY = "11111111-2222-3333-4444-555555555555";

	@Mock
	private ChunkBlazerConfig config;

	private final List<Request> sent = new ArrayList<>();
	private int code;
	private String body;
	private boolean dropConnection;
	private ChunkBlazerApiClient api;

	@BeforeEach
	void setUp()
	{
		lenient().when(config.apiEnabled()).thenReturn(true);
		lenient().when(config.apiBaseUrl()).thenReturn("http://api.test");
		lenient().when(config.apiKey()).thenReturn("");
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			sent.add(chain.request());
			if (dropConnection)
			{
				throw new IOException("connection dropped");
			}
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
				.code(code).message("x").body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
		}).build();
		api = new ChunkBlazerApiClient(config, new Gson(), http);
		api.setPlayerApiKey(KEY);
	}

	private void answer(int code, String body)
	{
		this.code = code;
		this.body = body;
	}

	private static <T> T get(java.util.concurrent.CompletableFuture<T> f) throws Exception
	{
		return f.get(5, TimeUnit.SECONDS);
	}

	@Test
	void requestsCarryKeyAndJson() throws Exception
	{
		answer(200, "{\"eligible\":true}");
		get(api.checkNuzlockeEligibility(EligibilitySnapshot.builder().build()));
		Request r = sent.get(0);
		assertEquals("http://api.test/api/player/nuzlocke/eligibility", r.url().toString());
		assertEquals("POST", r.method());
		assertEquals(KEY, r.header("X-API-Key"));
		assertTrue(r.body().contentType().toString().startsWith("application/json"));
	}

	@Test
	void verifyStart() throws Exception
	{
		answer(200, "{\"nonce\":\"ABC123\",\"alreadyVerified\":false}");
		assertEquals("ABC123", get(api.verifyStart()).getNonce());
		answer(500, "oops");
		VerifyStartResponse failed = get(api.verifyStart());
		assertFalse(failed.isAlreadyVerified());
		assertEquals(null, failed.getNonce());
	}

	@Test
	void verifyRejectedSaysWhy() throws Exception
	{
		answer(403, "{}");
		VerifyResponse r = get(api.verify("ABC123"));
		assertFalse(r.isVerified());
		assertEquals("server rejected: 403", r.getMessage());
		dropConnection = true;
		assertFalse(get(api.verify("ABC123")).isVerified());
	}

	@Test
	void eligibility() throws Exception
	{
		answer(200, "{\"eligible\":true}");
		assertTrue(get(api.checkNuzlockeEligibility(EligibilitySnapshot.builder().build())).isEligible());
		answer(502, "");
		NuzlockeEligibilityResponse down = get(api.checkNuzlockeEligibility(EligibilitySnapshot.builder().build()));
		assertFalse(down.isEligible());
		assertEquals("Could not reach the ChunkBlazer server to check eligibility.", down.getReason());
	}

	@Test
	void lockModeUsesTheServersErrorBody() throws Exception
	{
		answer(409, "{\"status\":\"error\",\"error\":\"NOT_ELIGIBLE\",\"message\":\"too far along\"}");
		LockModeResponse r = get(api.lockGameMode(GameMode.NUZLOCKE, null));
		assertEquals("NOT_ELIGIBLE", r.getError());
		assertEquals("too far along", r.getMessage());
		dropConnection = true;
		assertEquals("offline", get(api.lockGameMode(GameMode.CASUAL, null)).getStatus());
	}

	@Test
	void taskReports() throws Exception
	{
		answer(200, "{\"success\":true,\"pointsAwarded\":3}");
		assertEquals(3, get(api.reportNpcKill(NpcKillReport.builder().taskId("t").build())).getPointsAwarded());
		assertEquals("http://api.test/api/v1/events/npc-kill", sent.get(0).url().toString());
		answer(500, "");
		assertEquals("Server error: 500", get(api.reportSkillChange(SkillChangeReport.builder().taskId("t").build())).getErrorMessage());
		assertEquals("http://api.test/api/v1/events/skill-change", sent.get(1).url().toString());
		dropConnection = true;
		assertEquals("Network error", get(api.reportItemEquipped(ItemEquippedReport.builder().taskId("t").build())).getErrorMessage());
		assertEquals("http://api.test/api/v1/events/item-equipped", sent.get(2).url().toString());
	}

	@Test
	void hcimDeath() throws Exception
	{
		answer(200, "{}");
		assertTrue(get(api.reportHcimDeath(HcimDeathReport.builder().regionId(1).build())));
		answer(500, "");
		assertFalse(get(api.reportHcimDeath(HcimDeathReport.builder().regionId(1).build())));
		assertEquals(KEY, sent.get(0).header("X-API-Key"));
	}

	/** Login and sync carry the account hash, so the server can tell a rename from another account. */
	@Test
	void loginAndSyncSendAccountHash() throws Exception
	{
		answer(200, "{\"status\":\"ok\"}");
		get(api.login("Chunky Bran", "name-hash", "acct-hash"));
		okio.Buffer login = new okio.Buffer();
		sent.get(0).body().writeTo(login);
		assertTrue(login.readUtf8().contains("\"account_hash\":\"acct-hash\""));

		get(api.syncPlayerState(PlayerSyncRequest.builder().accountHash("acct-hash").build()));
		okio.Buffer sync = new okio.Buffer();
		sent.get(1).body().writeTo(sync);
		assertTrue(sync.readUtf8().contains("\"accountHash\":\"acct-hash\""));

		// Before login the client has no account id; the field is left out, so the server skips the check.
		get(api.login("Chunky Bran", "name-hash", null));
		okio.Buffer none = new okio.Buffer();
		sent.get(2).body().writeTo(none);
		assertFalse(none.readUtf8().contains("account_hash"));
	}
}
