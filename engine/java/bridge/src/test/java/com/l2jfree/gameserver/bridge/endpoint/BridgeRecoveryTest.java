/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;

import org.junit.jupiter.api.Test;

class BridgeRecoveryTest
{
	@Test
	void openingCannotDetachAndACompletedTokenCannotPublishTwice()
	{
		Object source = new Object();
		BridgeRecovery recovery = new BridgeRecovery(source);
		assertThat(recovery.detach(source, recovery.fence())).isZero();
		var token = recovery.checkpoint();
		assertThat(recovery.finishCheckpoint(token)).isTrue();
		assertThat(recovery.finishCheckpoint(token)).isFalse();
		assertThat(recovery.checkpoint()).isNull();
	}

	@Test
	void resumeBeforeTimerInvalidatesGraceBeforeCheckpointFinishes()
	{
		Object first = new Object();
		Object replacement = new Object();
		BridgeRecovery recovery = live(first);
		long generation = recovery.detach(first, recovery.fence());
		ArrayDeque<Runnable> fifo = new ArrayDeque<>();
		fifo.add(() -> assertThat(recovery.resume(replacement, 2)).isEqualTo(BridgeRecovery.Resume.ACCEPTED));
		fifo.add(() -> assertThat(recovery.expireGrace(generation)).isFalse());
		while (!fifo.isEmpty())
			fifo.remove().run();
		assertThat(recovery.admits(replacement, recovery.fence())).isFalse();
		assertThat(recovery.finishCheckpoint(recovery.checkpoint())).isTrue();
		assertThat(recovery.admits(replacement, recovery.fence())).isTrue();
	}

	@Test
	void timerBeforeResumeClosesWithoutGrantingTheReplacement()
	{
		Object first = new Object();
		BridgeRecovery recovery = live(first);
		long generation = recovery.detach(first, recovery.fence());
		ArrayDeque<Runnable> fifo = new ArrayDeque<>();
		fifo.add(() -> assertThat(recovery.expireGrace(generation)).isTrue());
		fifo.add(() -> assertThat(recovery.resume(new Object(), 2)).isEqualTo(BridgeRecovery.Resume.CLOSED));
		while (!fifo.isEmpty())
			fifo.remove().run();
		assertThat(recovery.checkpoint()).isNull();
	}

	@Test
	void duplicateResumeKeepsOneTokenAndAnotherTransportCannotStealItsEpoch()
	{
		Object first = new Object();
		Object replacement = new Object();
		BridgeRecovery recovery = live(first);
		long oldFence = recovery.fence();
		recovery.detach(first, oldFence);
		assertThat(recovery.resume(replacement, 2)).isEqualTo(BridgeRecovery.Resume.ACCEPTED);
		var token = recovery.checkpoint();
		assertThat(recovery.resume(replacement, 2)).isEqualTo(BridgeRecovery.Resume.REPLAY);
		assertThat(recovery.checkpoint()).isEqualTo(token);
		assertThat(recovery.resume(new Object(), 2)).isEqualTo(BridgeRecovery.Resume.FRAME);
		assertThat(recovery.detach(first, oldFence)).isZero();
		assertThat(recovery.finishCheckpoint(token)).isTrue();
		assertThat(recovery.admits(first, oldFence)).isFalse();
		assertThat(recovery.admits(replacement, recovery.fence())).isTrue();
	}

	@Test
	void detachAndNewCheckpointCancelEarlierContinuations()
	{
		Object source = new Object();
		BridgeRecovery recovery = live(source);
		var old = recovery.beginCheckpoint();
		var current = recovery.beginCheckpoint();
		assertThat(recovery.finishCheckpoint(old)).isFalse();
		recovery.detach(source, recovery.fence());
		assertThat(recovery.finishCheckpoint(current)).isFalse();
		assertThat(recovery.checkpoint()).isNull();
	}

	@Test
	void aSecondDetachHasItsOwnGraceGeneration()
	{
		Object first = new Object();
		Object second = new Object();
		BridgeRecovery recovery = live(first);
		long firstGeneration = recovery.detach(first, recovery.fence());
		recovery.resume(second, 2);
		long secondGeneration = recovery.detach(second, recovery.fence());
		assertThat(secondGeneration).isNotEqualTo(firstGeneration);
		assertThat(recovery.expireGrace(firstGeneration)).isFalse();
		assertThat(recovery.expireGrace(secondGeneration)).isTrue();
	}

	private static BridgeRecovery live(Object transport)
	{
		BridgeRecovery recovery = new BridgeRecovery(transport);
		assertThat(recovery.admits(transport, recovery.fence())).isFalse();
		assertThat(recovery.finishCheckpoint(recovery.checkpoint())).isTrue();
		return recovery;
	}
}
