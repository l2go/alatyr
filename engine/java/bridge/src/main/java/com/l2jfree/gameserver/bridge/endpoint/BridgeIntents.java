/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * Attempt numbers for one session. A repeated body is the stored attempt. A different body under the same number is
 * an error and does not replace it. Nothing is stored until the session is ready, and a number above the next one
 * closes the frame rules without moving the counter.
 */
final class BridgeIntents
{
	static final int PENDING_CAP = 8;
	static final int COMPLETED_CAP = 64;
	
	private final LinkedHashMap<Long, byte[]> pending = new LinkedHashMap<>();
	private final LinkedHashMap<Long, byte[]> completed = new LinkedHashMap<>();
	private final LinkedHashMap<Long, byte[]> results = new LinkedHashMap<>();
	private long next = 1;
	private long floor = 1;
	private boolean ready;
	
	synchronized void markReady()
	{
		ready = true;
	}
	
	synchronized long next()
	{
		return next;
	}
	
	synchronized Decision offer(long n, byte[] content)
	{
		if (content == null)
			throw new IllegalArgumentException("content");
		
		byte[] stored = pending.get(n);
		if (stored == null)
			stored = completed.get(n);
		if (stored != null)
		{
			if (Arrays.equals(stored, content))
				return Decision.replay(stored);
			return Decision.mismatch(stored);
		}
		if (n > next)
			return Decision.frame();
		if (n != next || n < floor)
			return Decision.unrecoverable();
		if (pending.size() >= PENDING_CAP)
			return Decision.overflow();
		
		byte[] copy = content.clone();
		pending.put(n, copy);
		next++;
		if (!ready)
			return Decision.notReady();
		return Decision.accepted(copy);
	}
	
	/** Moves a pending attempt into the completed ring. The stored body stays the one that was accepted. */
	synchronized void complete(long n)
	{
		complete(n, null);
	}

	/** Retains an encoded result once; callers cannot mutate the replay through their byte arrays. */
	synchronized void complete(long n, byte[] result)
	{
		byte[] body = pending.remove(n);
		if (body == null)
			return;
		
		completed.put(n, body);
		if (result != null)
			results.put(n, result.clone());
		while (completed.size() > COMPLETED_CAP)
		{
			Long oldest = completed.keySet().iterator().next();
			completed.remove(oldest);
			results.remove(oldest);
			floor = Math.max(floor, oldest + 1);
		}
	}

	synchronized byte[] result(long n)
	{
		byte[] stored = results.get(n);
		return stored == null ? null : stored.clone();
	}
	
	enum Kind
	{
		ACCEPTED,
		REPLAY,
		NOT_READY,
		MISMATCH,
		OVERFLOW,
		FRAME,
		UNRECOVERABLE
	}
	
	static final class Decision
	{
		final Kind kind;
		final long code;
		final byte[] stored;
		
		private Decision(Kind kind, long code, byte[] stored)
		{
			this.kind = kind;
			this.code = code;
			this.stored = stored == null ? null : stored.clone();
		}
		
		private static Decision accepted(byte[] stored)
		{
			return new Decision(Kind.ACCEPTED, 0, stored);
		}
		
		private static Decision replay(byte[] stored)
		{
			return new Decision(Kind.REPLAY, 0, stored);
		}
		
		private static Decision mismatch(byte[] stored)
		{
			return new Decision(Kind.MISMATCH, 22, stored);
		}
		
		private static Decision notReady()
		{
			return new Decision(Kind.NOT_READY, 7, null);
		}
		
		private static Decision overflow()
		{
			return new Decision(Kind.OVERFLOW, 256, null);
		}
		
		private static Decision frame()
		{
			return new Decision(Kind.FRAME, 2, null);
		}
		
		private static Decision unrecoverable()
		{
			return new Decision(Kind.UNRECOVERABLE, 21, null);
		}
	}
}
