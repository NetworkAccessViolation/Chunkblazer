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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class ServerRetryPolicyTest
{
	// Jitter pinned to the midpoint.
	private static ServerBackoff backoff()
	{
		return new ServerBackoff(() -> 0.5);
	}

	@Test
	void classify()
	{
		assertEquals(ApiOutcome.SUCCESS, ApiOutcome.classify(200, null));
		assertEquals(ApiOutcome.SUCCESS, ApiOutcome.classify(201, null));
		assertEquals(ApiOutcome.TRANSIENT, ApiOutcome.classify(0, null));
		assertEquals(ApiOutcome.TRANSIENT, ApiOutcome.classify(408, null));
		assertEquals(ApiOutcome.TRANSIENT, ApiOutcome.classify(500, "internal"));
		assertEquals(ApiOutcome.TRANSIENT, ApiOutcome.classify(503, "guard_unavailable"));
		assertEquals(ApiOutcome.RATE_LIMITED, ApiOutcome.classify(429, null));
		assertEquals(ApiOutcome.AUTH_REJECTED, ApiOutcome.classify(401, "unknown_api_key"));
		assertEquals(ApiOutcome.REGISTRATION_CLOSED, ApiOutcome.classify(403, "registration_closed"));
		assertEquals(ApiOutcome.BLOCKED, ApiOutcome.classify(403, null));
		assertEquals(ApiOutcome.REJECTED, ApiOutcome.classify(400, "bad_request"));
		assertEquals(ApiOutcome.REJECTED, ApiOutcome.classify(409, "destructive_sync_rejected"));
		assertEquals(ApiOutcome.REJECTED, ApiOutcome.classify(302, null));
	}

	@Test
	void isOutage()
	{
		assertTrue(ApiOutcome.TRANSIENT.isOutage());
		assertTrue(ApiOutcome.RATE_LIMITED.isOutage());
		assertTrue(ApiOutcome.BLOCKED.isOutage());
		assertFalse(ApiOutcome.REJECTED.isOutage());
		assertFalse(ApiOutcome.AUTH_REJECTED.isOutage());
		assertFalse(ApiOutcome.REGISTRATION_CLOSED.isOutage());
	}

	@Test
	void doublesToCap()
	{
		ServerBackoff b = backoff();
		long[] expected = {0, 0, 60_000, 120_000, 240_000, 300_000, 300_000};
		for (long delay : expected)
		{
			b.recordFailure(ApiOutcome.TRANSIENT, 0, 1_000);
			assertEquals(1_000 + delay, b.getNextAttemptAtMs());
		}
		for (int i = 0; i < 100; i++)
		{
			b.recordFailure(ApiOutcome.TRANSIENT, 0, 0);
		}
		assertEquals(ServerBackoff.MAX_DELAY_MS, b.getNextAttemptAtMs());
	}

	@Test
	void isReady()
	{
		ServerBackoff b = backoff();
		assertTrue(b.isReady(0));
		for (int i = 0; i < ServerBackoff.GRACE_FAILURES; i++)
		{
			b.recordFailure(ApiOutcome.TRANSIENT, 0, 0);
			assertTrue(b.isReady(0));
		}
		b.recordFailure(ApiOutcome.TRANSIENT, 0, 0);
		assertFalse(b.isReady(59_999));
		assertTrue(b.isReady(60_000));
	}

	@Test
	void successResets()
	{
		ServerBackoff b = backoff();
		for (int i = 0; i < 4; i++)
		{
			b.recordFailure(ApiOutcome.TRANSIENT, 0, 0);
		}
		b.recordSuccess();
		assertEquals(0, b.getConsecutiveFailures());
		assertTrue(b.isReady(0));
		b.recordFailure(ApiOutcome.TRANSIENT, 0, 0);
		assertEquals(0, b.getNextAttemptAtMs());
	}

	@Test
	void refusalWaitsCap()
	{
		ServerBackoff b = backoff();
		b.recordFailure(ApiOutcome.REJECTED, 0, 0);
		assertEquals(ServerBackoff.MAX_DELAY_MS, b.getNextAttemptAtMs());
		ServerBackoff c = backoff();
		c.recordFailure(ApiOutcome.BLOCKED, 0, 0);
		assertEquals(ServerBackoff.MAX_DELAY_MS, c.getNextAttemptAtMs());
	}

	@Test
	void retryAfter()
	{
		ServerBackoff b = backoff();
		b.recordFailure(ApiOutcome.RATE_LIMITED, 120_000, 0);
		assertEquals(120_000, b.getNextAttemptAtMs());

		ServerBackoff c = backoff();
		c.recordFailure(ApiOutcome.RATE_LIMITED, 5_000, 0);
		assertEquals(5_000, c.getNextAttemptAtMs());

		ServerBackoff d = backoff();
		d.recordFailure(ApiOutcome.RATE_LIMITED, 24 * 3_600_000L, 0);
		assertEquals(ServerBackoff.MAX_RETRY_AFTER_MS, d.getNextAttemptAtMs());
	}

	@Test
	void jitter()
	{
		ServerBackoff low = new ServerBackoff(() -> 0.0);
		ServerBackoff high = new ServerBackoff(() -> 1.0);
		for (int i = 0; i <= ServerBackoff.GRACE_FAILURES; i++)
		{
			low.recordFailure(ApiOutcome.TRANSIENT, 0, 0);
			high.recordFailure(ApiOutcome.TRANSIENT, 0, 0);
		}
		assertEquals(48_000, low.getNextAttemptAtMs());
		assertEquals(72_000, high.getNextAttemptAtMs());
	}

	@Test
	void outageNotice()
	{
		ServerBackoff b = backoff();
		for (int i = 1; i < ServerBackoff.OUTAGE_NOTICE_THRESHOLD; i++)
		{
			assertFalse(b.recordFailure(ApiOutcome.TRANSIENT, 0, 0));
		}
		assertTrue(b.recordFailure(ApiOutcome.TRANSIENT, 0, 0));
		assertFalse(b.recordFailure(ApiOutcome.TRANSIENT, 0, 0));
		assertTrue(b.recordSuccess());
		assertFalse(b.recordSuccess());
	}

	@Test
	void refusalNoNotice()
	{
		ServerBackoff b = backoff();
		for (int i = 0; i < 20; i++)
		{
			assertFalse(b.recordFailure(ApiOutcome.REJECTED, 0, 0));
		}
		assertFalse(b.recordSuccess());
	}

	@Test
	void errorCode()
	{
		Gson gson = new Gson();
		assertEquals("registration_closed", ChunkBlazerApiClient.errorCode(gson,
			"{\"status\":\"error\",\"error\":\"registration_closed\",\"message\":\"closed\"}"));
		assertNull(ChunkBlazerApiClient.errorCode(gson, "<html>Attention Required! | Cloudflare</html>"));
		assertNull(ChunkBlazerApiClient.errorCode(gson, ""));
		assertNull(ChunkBlazerApiClient.errorCode(gson, "{\"status\":\"error\"}"));
		assertNull(ChunkBlazerApiClient.errorCode(gson, "{\"error\":{\"nested\":true}}"));
	}

	@Test
	void parseRetryAfter()
	{
		assertEquals(120_000, ChunkBlazerApiClient.parseRetryAfterMs("120"));
		assertEquals(5_000, ChunkBlazerApiClient.parseRetryAfterMs(" 5 "));
		assertEquals(0, ChunkBlazerApiClient.parseRetryAfterMs(null));
		assertEquals(0, ChunkBlazerApiClient.parseRetryAfterMs("0"));
		assertEquals(0, ChunkBlazerApiClient.parseRetryAfterMs("-3"));
		assertEquals(0, ChunkBlazerApiClient.parseRetryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"));
	}
}
