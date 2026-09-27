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

/**
 * How a server response should be retried.
 */
public enum ApiOutcome
{
	/** 2xx. */
	SUCCESS,
	/** Network error, 408 or 5xx. */
	TRANSIENT,
	/** 429. */
	RATE_LIMITED,
	/** 401: key refused. */
	AUTH_REJECTED,
	/** 403 registration_closed. */
	REGISTRATION_CLOSED,
	/** Any other 403 (Cloudflare). */
	BLOCKED,
	/** Any other 4xx. */
	REJECTED;

	/**
	 * @param status HTTP status, or 0 for a network failure
	 * @param errorCode the {@code error} field of our JSON error body, or null
	 */
	public static ApiOutcome classify(int status, String errorCode)
	{
		if (status >= 200 && status < 300)
		{
			return SUCCESS;
		}
		if (status == 401)
		{
			return AUTH_REJECTED;
		}
		if (status == 403)
		{
			return "registration_closed".equals(errorCode) ? REGISTRATION_CLOSED : BLOCKED;
		}
		if (status == 429)
		{
			return RATE_LIMITED;
		}
		if (status <= 0 || status == 408 || status >= 500)
		{
			return TRANSIENT;
		}
		return REJECTED;
	}

	/** Server unreachable, as opposed to a refusal. */
	public boolean isOutage()
	{
		return this == TRANSIENT || this == RATE_LIMITED || this == BLOCKED;
	}
}
