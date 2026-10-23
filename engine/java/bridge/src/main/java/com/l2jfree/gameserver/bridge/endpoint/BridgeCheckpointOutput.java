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

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.List;

import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.Schemas;

/**
 * One encoded checkpoint copy, drained a whole frame at a time with transport backpressure. The owner holds the
 * transport monitor. No producer callback, game access, or sink call occurs here. Cancel drops the unqueued tail.
 */
final class BridgeCheckpointOutput
{
	private final ArrayDeque<byte[]> frames = new ArrayDeque<>();
	private int remaining;
	private final byte[] end;
	private boolean endAllowed;

	BridgeCheckpointOutput(List<Envelope> snapshot, long epoch, List<byte[]> results, Envelope opened)
	{
		if (results.size() > BridgeIntents.COMPLETED_CAP)
			throw new IllegalArgumentException("checkpoint result capacity");
		append(BridgeFrames.checkpoint(Schemas.BEGIN, epoch, false));
		long retained = 0;
		for (Envelope fact : snapshot)
		{
			if (fact.type() != Schemas.FACT)
				throw new IllegalArgumentException("checkpoint record");
			byte[] frame = encode(fact);
			retained += frame.length;
			if (retained > BridgeProjection.RETAINED_BYTES)
				throw new IllegalArgumentException("checkpoint byte capacity");
			add(frame);
		}
		end = encode(BridgeFrames.checkpoint(Schemas.END, epoch, true));
		add(end);
		for (byte[] result : results)
		{
			List<Envelope> decoded = Codec.bridge().decode(result);
			if (decoded.size() != 1 || decoded.get(0).type() != Schemas.RESULT)
				throw new IllegalArgumentException("checkpoint outcome");
			append(decoded.get(0));
		}
		if (opened != null)
			append(opened);
	}

	/** Transfers ownership of one frame only if the bounded queue has room for all of it. */
	byte[] next(int available)
	{
		byte[] frame = frames.peek();
		if (frame == null || frame.length > available || frame == end && !endAllowed)
			return null;
		frames.remove();
		remaining -= frame.length;
		return frame;
	}

	boolean finished()
	{
		return frames.isEmpty();
	}

	boolean waitingForEnd()
	{
		return frames.peek() == end && !endAllowed;
	}

	/** Only a fresh FIFO relocation check may grant the terminal ready tail. */
	void allowEnd()
	{
		endAllowed = true;
	}

	int remainingBytes()
	{
		return remaining;
	}

	void cancel()
	{
		frames.clear();
		remaining = 0;
	}

	private void append(Envelope record)
	{
		add(encode(record));
	}

	private void add(byte[] frame)
	{
		frames.add(frame);
		remaining = Math.addExact(remaining, frame.length);
	}

	private static byte[] encode(Envelope record)
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		Codec.bridge().write(output, List.of(record));
		return output.toByteArray();
	}
}
