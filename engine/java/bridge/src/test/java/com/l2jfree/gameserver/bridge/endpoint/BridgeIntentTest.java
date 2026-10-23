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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.endpoint.BridgeIntents.Decision;
import com.l2jfree.gameserver.bridge.endpoint.BridgeIntents.Kind;

/**
 * Attempt numbers keep the first body. A different body is an error, and the session stays closed to new numbers
 * until it is ready.
 */
class BridgeIntentTest
{
	@Test
	void aCompletedResultIsImmutableAndCannotBeReplaced()
	{
		BridgeIntents intents = ready();
		byte[] body = { 1 };
		byte[] result = { 2, 3 };
		Decision accepted = intents.offer(1, body);
		accepted.stored[0] = 99;
		body[0] = 98;
		intents.complete(1, result);
		result[0] = 97;
		intents.result(1)[0] = 96;
		intents.complete(1, new byte[] { 95 });
		assertThat(intents.offer(1, new byte[] { 1 }).kind).isEqualTo(Kind.REPLAY);
		assertThat(intents.result(1)).containsExactly(2, 3);
	}

	@Test
	void evictingACompletedResultDoesNotEvictAnOlderPendingAttempt()
	{
		BridgeIntents intents = ready();
		intents.offer(1, new byte[] { 1 });
		for (long n = 2; n <= 66; n++)
		{
			intents.offer(n, new byte[] { (byte) n });
			intents.complete(n, new byte[] { (byte) n });
		}
		assertThat(intents.result(2)).isNull();
		assertThat(intents.offer(2, new byte[] { 2 }).kind).isEqualTo(Kind.UNRECOVERABLE);
		assertThat(intents.offer(1, new byte[] { 1 }).kind).isEqualTo(Kind.REPLAY);
		assertThat(intents.result(1)).isNull();
		assertThat(intents.result(66)).containsExactly(66);
	}

	@Test
	void aDifferentBodyDoesNotReplaceThePendingOrCompletedAttempt()
	{
		BridgeIntents intents = ready();
		byte[] first = new byte[] { 1, 2 };
		byte[] other = new byte[] { 9 };
		
		assertThat(intents.offer(1, first).kind).isEqualTo(Kind.ACCEPTED);
		Decision mismatch = intents.offer(1, other);
		assertThat(mismatch.kind).isEqualTo(Kind.MISMATCH);
		assertThat(mismatch.code).isEqualTo(22);
		assertThat(mismatch.stored).containsExactly(1, 2);
		assertThat(intents.offer(1, first).kind).isEqualTo(Kind.REPLAY);
		assertThat(intents.next()).isEqualTo(2);
		
		intents.complete(1);
		Decision completed = intents.offer(1, other);
		assertThat(completed.kind).isEqualTo(Kind.MISMATCH);
		assertThat(completed.stored).containsExactly(1, 2);
		assertThat(intents.offer(1, new byte[] { 1, 2 }).kind).isEqualTo(Kind.REPLAY);
	}
	
	@Test
	void theNinthPendingAttemptDoesNotAdvanceTheCounter()
	{
		BridgeIntents intents = ready();
		for (int n = 1; n <= BridgeIntents.PENDING_CAP; n++)
			assertThat(intents.offer(n, new byte[] { (byte) n }).kind).isEqualTo(Kind.ACCEPTED);
		
		long next = intents.next();
		Decision extra = intents.offer(next, new byte[] { 99 });
		assertThat(extra.kind).isEqualTo(Kind.OVERFLOW);
		assertThat(extra.code).isEqualTo(256);
		assertThat(intents.next()).isEqualTo(next);
	}
	
	@Test
	void aFutureNumberIsAFrameErrorAndAnUnstoredOldNumberIsUnrecoverable()
	{
		BridgeIntents intents = ready();
		Decision future = intents.offer(2, new byte[] { 1 });
		assertThat(future.kind).isEqualTo(Kind.FRAME);
		assertThat(future.code).isEqualTo(2);
		assertThat(intents.next()).isEqualTo(1);
		
		Decision old = intents.offer(0, new byte[] { 1 });
		assertThat(old.kind).isEqualTo(Kind.UNRECOVERABLE);
		assertThat(old.code).isEqualTo(21);
		assertThat(intents.next()).isEqualTo(1);
	}
	
	@Test
	void anIntentBeforeReadyIsRegisteredAndReplaysItsFinalRefusal()
	{
		BridgeIntents intents = new BridgeIntents();
		Decision early = intents.offer(1, new byte[] { 1 });
		assertThat(early.kind).isEqualTo(Kind.NOT_READY);
		assertThat(early.code).isEqualTo(7);
		assertThat(intents.next()).isEqualTo(2);
		byte[] refusal = new byte[] { 3, 1 };
		intents.complete(1, refusal);
		
		intents.markReady();
		assertThat(intents.offer(1, new byte[] { 1 }).kind).isEqualTo(Kind.REPLAY);
		assertThat(intents.result(1)).containsExactly(3, 1);
	}
	
	private static BridgeIntents ready()
	{
		BridgeIntents intents = new BridgeIntents();
		intents.markReady();
		return intents;
	}
}
