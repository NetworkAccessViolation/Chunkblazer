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

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Backoff for the periodic login retry and save sync. The first failures retry on
 * the next tick so a blip never delays a new player; after that 1m doubling to 5m,
 * +/-20% jitter. No attempt cap: a resent sync is harmless.
 */
public final class ServerBackoff
{
	static final int GRACE_FAILURES = 2;
	static final long BASE_DELAY_MS = 60_000L;
	static final long MAX_DELAY_MS = 5 * 60_000L;
	static final long MAX_RETRY_AFTER_MS = 30 * 60_000L;
	static final int OUTAGE_NOTICE_THRESHOLD = 5;
	private static final double JITTER = 0.2;

	private final DoubleSupplier random;
	private int consecutiveFailures;
	private long nextAttemptAtMs;
	private boolean outageNoticed;

	public ServerBackoff()
	{
		this(() -> ThreadLocalRandom.current().nextDouble());
	}

	ServerBackoff(DoubleSupplier random)
	{
		this.random = random;
	}

	public synchronized boolean isReady(long nowMs)
	{
		return nowMs >= nextAttemptAtMs;
	}

	/** @return true if this ends an outage the player was told about */
	public synchronized boolean recordSuccess()
	{
		boolean wasNoticed = outageNoticed;
		consecutiveFailures = 0;
		nextAttemptAtMs = 0;
		outageNoticed = false;
		return wasNoticed;
	}

	/** @return true once per outage, when the player should be told */
	public synchronized boolean recordFailure(ApiOutcome outcome, long retryAfterMs, long nowMs)
	{
		consecutiveFailures++;
		long delay;
		if (outcome == ApiOutcome.REJECTED || outcome == ApiOutcome.BLOCKED)
		{
			delay = MAX_DELAY_MS;
		}
		else if (consecutiveFailures <= GRACE_FAILURES)
		{
			delay = 0;
		}
		else
		{
			int exp = Math.min(consecutiveFailures - GRACE_FAILURES - 1, 10);
			delay = Math.min(MAX_DELAY_MS, BASE_DELAY_MS << exp);
		}
		if (outcome == ApiOutcome.RATE_LIMITED && retryAfterMs > 0)
		{
			delay = Math.max(delay, Math.min(retryAfterMs, MAX_RETRY_AFTER_MS));
		}
		double factor = 1.0 - JITTER + (2 * JITTER * random.getAsDouble());
		nextAttemptAtMs = nowMs + (long) (delay * factor);

		if (outcome.isOutage() && !outageNoticed && consecutiveFailures >= OUTAGE_NOTICE_THRESHOLD)
		{
			outageNoticed = true;
			return true;
		}
		return false;
	}

	public synchronized void reset()
	{
		consecutiveFailures = 0;
		nextAttemptAtMs = 0;
		outageNoticed = false;
	}

	synchronized int getConsecutiveFailures()
	{
		return consecutiveFailures;
	}

	synchronized long getNextAttemptAtMs()
	{
		return nextAttemptAtMs;
	}
}
