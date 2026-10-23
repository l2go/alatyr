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

/**
 * Transport identity and cancellation state for one game session. This monitor is a leaf: methods only inspect
 * or update local state and never call the game, a transport, a scheduler, or a consumer. Detach invalidates the
 * fence on the reader; resume, grace expiry, and readiness run in the client's FIFO.
 */
final class BridgeRecovery
{
	private enum State { OPENING, LIVE, DETACHED, CLOSING, CLOSED }
	private State state = State.OPENING;
	private Object transport;
	private long epoch = 1;
	private long fence = 1;
	private long disconnectGeneration;
	private long checkpointGeneration = 1;
	private boolean checkpointActive = true;
	private boolean ready;
	private boolean resumed;

	record Checkpoint(long generation, long fence) {}
	enum Resume { ACCEPTED, REPLAY, FRAME, CLOSED }

	BridgeRecovery(Object transport)
	{
		this.transport = java.util.Objects.requireNonNull(transport);
	}

	synchronized long fence()
	{
		return fence;
	}

	synchronized long epoch()
	{
		return epoch;
	}

	synchronized boolean admits(Object source, long expectedFence)
	{
		return state == State.LIVE && ready && transport == source && fence == expectedFence;
	}

	synchronized Checkpoint checkpoint()
	{
		if (!checkpointActive || (state != State.OPENING && state != State.LIVE))
			return null;
		return new Checkpoint(checkpointGeneration, fence);
	}

	/** Begins another projection capture; any previous continuation loses the right to publish end/ready. */
	synchronized Checkpoint beginCheckpoint()
	{
		if (state != State.OPENING && state != State.LIVE)
			return null;
		checkpointGeneration = increment(checkpointGeneration);
		checkpointActive = true;
		ready = false;
		return new Checkpoint(checkpointGeneration, fence);
	}

	/** The caller has already verified projection completeness and relocation under the relocation lock. */
	synchronized boolean finishCheckpoint(Checkpoint token)
	{
		if (token == null || !checkpointActive || (state != State.OPENING && state != State.LIVE)
				|| token.generation() != checkpointGeneration || token.fence() != fence)
			return false;
		state = State.LIVE;
		checkpointActive = false;
		ready = true;
		return true;
	}

	/** Returns the generation for the grace task, or zero when this is a stale/unbound transport. */
	synchronized long detach(Object source, long expectedFence)
	{
		if (state != State.LIVE || transport != source || fence != expectedFence)
			return 0;
		long nextFence = increment(fence);
		long nextDisconnect = increment(disconnectGeneration);
		long nextCheckpoint = increment(checkpointGeneration);
		fence = nextFence;
		disconnectGeneration = nextDisconnect;
		checkpointGeneration = nextCheckpoint;
		state = State.DETACHED;
		checkpointActive = false;
		ready = false;
		transport = null;
		return disconnectGeneration;
	}

	/** Called only after authentication and session lookup, in FIFO order with grace expiry. */
	synchronized Resume resume(Object source, long requestedEpoch)
	{
		java.util.Objects.requireNonNull(source);
		if (state == State.CLOSING || state == State.CLOSED || state == State.OPENING)
			return Resume.CLOSED;
		if (state == State.LIVE && resumed && transport == source && requestedEpoch == epoch)
			return Resume.REPLAY;
		// Epochs use the complete unsigned wire range. Exhaustion must not wrap to zero.
		if (epoch == -1L || requestedEpoch == 0 || requestedEpoch != epoch + 1)
			return Resume.FRAME;
		long nextFence = increment(fence);
		long nextCheckpoint = increment(checkpointGeneration);
		transport = source;
		epoch = requestedEpoch;
		fence = nextFence;
		checkpointGeneration = nextCheckpoint;
		state = State.LIVE;
		resumed = true;
		checkpointActive = true;
		ready = false;
		return Resume.ACCEPTED;
	}

	/** A timer queued before a successful Resume becomes inert even while its checkpoint is unfinished. */
	synchronized boolean expireGrace(long generation)
	{
		if (state != State.DETACHED || generation == 0 || disconnectGeneration != generation)
			return false;
		close();
		return true;
	}

	synchronized void close()
	{
		if (state == State.CLOSING || state == State.CLOSED)
			return;
		state = State.CLOSING;
		checkpointActive = false;
		ready = false;
		transport = null;
	}

	synchronized void closed()
	{
		close();
		state = State.CLOSED;
	}

	private static long increment(long value)
	{
		if (value == -1L)
			throw new IllegalStateException("bridge generation exhausted");
		return value + 1;
	}
}
