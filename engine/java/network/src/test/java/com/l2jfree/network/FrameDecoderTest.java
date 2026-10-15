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
package com.l2jfree.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

class FrameDecoderTest
{
	private static final int MAX = 100;
	
	/** A frame as the client sends it: little-endian length that includes itself, then the payload. */
	private static byte[] frame(byte[] payload)
	{
		return ByteBuffer.allocate(2 + payload.length).order(ByteOrder.LITTLE_ENDIAN)
				.putShort((short)(2 + payload.length)).put(payload).array();
	}
	
	private static byte[] payload(int size, int seed)
	{
		final byte[] payload = new byte[size];
		for (int i = 0; i < size; i++)
			payload[i] = (byte)(seed + i * 7);
		return payload;
	}
	
	private static byte[] concat(byte[]... parts)
	{
		final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		for (byte[] part : parts)
			out.write(part, 0, part.length);
		return out.toByteArray();
	}
	
	private static byte[] slice(byte[] bytes, int from, int to)
	{
		return java.util.Arrays.copyOfRange(bytes, from, to);
	}
	
	/** Writes the bytes into the channel; returns what the pipeline raised, or null. */
	private static Throwable feed(EmbeddedChannel channel, byte[] bytes)
	{
		try
		{
			channel.writeInbound(Unpooled.wrappedBuffer(bytes));
			channel.checkException();
		}
		catch (Throwable t)
		{
			return t;
		}
		return null;
	}
	
	private static List<byte[]> drain(EmbeddedChannel channel)
	{
		final List<byte[]> frames = new ArrayList<byte[]>();
		for (Object message; (message = channel.readInbound()) != null;)
			frames.add((byte[])message);
		return frames;
	}
	
	private static void release(EmbeddedChannel channel)
	{
		try
		{
			channel.finishAndReleaseAll();
		}
		catch (Throwable ignored)
		{
			// the test already looked at what the channel raised
		}
	}
	
	@Test
	@DisplayName("one frame yields its payload without the header")
	void oneFrame()
	{
		final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
		final byte[] payload = payload(10, 1);
		
		assertThat(feed(channel, frame(payload))).isNull();
		
		assertThat(drain(channel)).containsExactly(payload);
		release(channel);
	}
	
	@Test
	@DisplayName("a frame split across two writes at every byte boundary yields one payload")
	void frameSplitAtEveryByteBoundary()
	{
		final byte[] payload = payload(9, 3);
		final byte[] bytes = frame(payload);
		
		for (int split = 1; split < bytes.length; split++)
		{
			final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
			
			assertThat(feed(channel, slice(bytes, 0, split))).as("first part at %d", split).isNull();
			assertThat(drain(channel)).as("nothing before the frame is complete at %d", split).isEmpty();
			assertThat(feed(channel, slice(bytes, split, bytes.length))).isNull();
			
			assertThat(drain(channel)).as("split at %d", split).containsExactly(payload);
			release(channel);
		}
	}
	
	@Test
	@DisplayName("a frame written one byte at a time yields one payload, after the last byte")
	void frameWrittenByteByByte()
	{
		final byte[] payload = payload(12, 5);
		final byte[] bytes = frame(payload);
		final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
		
		for (int i = 0; i < bytes.length - 1; i++)
		{
			assertThat(feed(channel, new byte[] { bytes[i] })).isNull();
			assertThat(drain(channel)).isEmpty();
		}
		assertThat(feed(channel, new byte[] { bytes[bytes.length - 1] })).isNull();
		
		assertThat(drain(channel)).containsExactly(payload);
		release(channel);
	}
	
	@Test
	@DisplayName("several frames coalesced in one write yield their payloads in order")
	void coalescedFrames()
	{
		final byte[] first = payload(4, 1);
		final byte[] second = payload(20, 2);
		final byte[] third = payload(1, 3);
		final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
		
		assertThat(feed(channel, concat(frame(first), frame(second), frame(third)))).isNull();
		
		assertThat(drain(channel)).containsExactly(first, second, third);
		release(channel);
	}
	
	@Test
	@DisplayName("coalesced frames followed by the start of another keep the start until the rest arrives")
	void coalescedFramesWithIncompleteTail()
	{
		final byte[] first = payload(4, 1);
		final byte[] second = payload(6, 2);
		final byte[] tail = frame(second);
		final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
		
		assertThat(feed(channel, concat(frame(first), slice(tail, 0, 3)))).isNull();
		assertThat(drain(channel)).containsExactly(first);
		
		assertThat(feed(channel, slice(tail, 3, tail.length))).isNull();
		assertThat(drain(channel)).containsExactly(second);
		release(channel);
	}
	
	@Test
	@DisplayName("a frame of exactly the maximum length is accepted")
	void frameOfExactlyTheMaximumLength()
	{
		final byte[] payload = payload(MAX - 2, 9);
		final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
		
		assertThat(feed(channel, frame(payload))).isNull();
		
		assertThat(drain(channel)).containsExactly(payload);
		release(channel);
	}
	
	@Test
	@DisplayName("a frame longer than the maximum raises an invalid frame as soon as its header is read")
	void frameTooLong()
	{
		final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
		
		// only the header of a frame of MAX + 1 bytes: the decoder must not wait for the rest
		final Throwable raised = feed(channel, new byte[] { (byte)(MAX + 1), 0 });
		
		assertThat(raised).isInstanceOf(InvalidFrameException.class);
		assertThat(((InvalidFrameException)raised).getLength()).isEqualTo(MAX + 1);
		assertThat(drain(channel)).isEmpty();
		release(channel);
	}
	
	@Test
	@DisplayName("a length of the largest the two bytes can hold raises an invalid frame with the default maximum of 100")
	void frameLengthOfSixtyFiveThousand()
	{
		final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
		
		final Throwable raised = feed(channel, new byte[] { (byte)0xFF, (byte)0xFF, 1, 2, 3 });
		
		assertThat(raised).isInstanceOf(InvalidFrameException.class);
		assertThat(((InvalidFrameException)raised).getLength()).isEqualTo(0xFFFF);
		release(channel);
	}
	
	@Test
	@DisplayName("a length shorter than the header raises an invalid frame")
	void lengthShorterThanTheHeader()
	{
		final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
		
		final Throwable raised = feed(channel, new byte[] { 1, 0, 42 });
		
		assertThat(raised).isInstanceOf(InvalidFrameException.class);
		assertThat(((InvalidFrameException)raised).getLength()).isEqualTo(1);
		assertThat(drain(channel)).isEmpty();
		release(channel);
	}
	
	@Test
	@DisplayName("a length of zero raises an invalid frame")
	void lengthZero()
	{
		final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
		
		final Throwable raised = feed(channel, new byte[] { 0, 0, 42, 43 });
		
		assertThat(raised).isInstanceOf(InvalidFrameException.class);
		assertThat(((InvalidFrameException)raised).getLength()).isZero();
		assertThat(drain(channel)).isEmpty();
		release(channel);
	}
	
	@Test
	@DisplayName("after an invalid frame the decoder ignores all later input")
	void inputIsIgnoredAfterAnInvalidFrame()
	{
		final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
		
		assertThat(feed(channel, new byte[] { 0, 0 })).isInstanceOf(InvalidFrameException.class);
		
		assertThat(feed(channel, frame(payload(5, 1)))).isNull();
		assertThat(drain(channel)).isEmpty();
		release(channel);
	}
	
	@Test
	@DisplayName("frames before an invalid frame in the same write are delivered")
	void framesBeforeAnInvalidFrameAreDelivered()
	{
		final byte[] first = payload(3, 1);
		final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
		
		final Throwable raised = feed(channel, concat(frame(first), new byte[] { 0, 0 }));
		
		assertThat(raised).isInstanceOf(InvalidFrameException.class);
		assertThat(drain(channel)).containsExactly(first);
		release(channel);
	}
	
	@Test
	@DisplayName("a frame that has only the header has no payload and is consumed silently")
	void frameWithoutBody()
	{
		final byte[] next = payload(2, 4);
		final EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder(MAX));
		
		assertThat(feed(channel, concat(new byte[] { 2, 0 }, frame(next)))).isNull();
		
		assertThat(drain(channel)).containsExactly(next);
		release(channel);
	}
}
