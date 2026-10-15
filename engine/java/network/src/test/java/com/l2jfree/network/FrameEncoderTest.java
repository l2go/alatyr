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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

class FrameEncoderTest
{
	private static byte[] bytes(ByteBuf buf)
	{
		final byte[] bytes = new byte[buf.readableBytes()];
		buf.getBytes(buf.readerIndex(), bytes);
		return bytes;
	}
	
	@Test
	@DisplayName("the header is little-endian and counts itself, the payload follows unchanged")
	void headerIsLittleEndianAndIncludesItself()
	{
		final byte[] payload = { 10, 20, 30, 40, 50 };
		final ByteBuf frame = FrameEncoder.encode(ByteBufAllocator.DEFAULT, payload, 0, payload.length);
		try
		{
			assertThat(bytes(frame)).containsExactly(7, 0, 10, 20, 30, 40, 50);
		}
		finally
		{
			frame.release();
		}
	}
	
	@Test
	@DisplayName("a length that needs both header bytes is split low byte first")
	void lengthOfTwoBytes()
	{
		final byte[] payload = new byte[0x1234 - 2];
		final ByteBuf frame = FrameEncoder.encode(ByteBufAllocator.DEFAULT, payload, 0, payload.length);
		try
		{
			assertThat(frame.readableBytes()).isEqualTo(0x1234);
			assertThat(frame.getByte(0)).isEqualTo((byte)0x34);
			assertThat(frame.getByte(1)).isEqualTo((byte)0x12);
			assertThat(frame.getUnsignedShortLE(0)).isEqualTo(0x1234);
		}
		finally
		{
			frame.release();
		}
	}
	
	@Test
	@DisplayName("an offset and a length select the payload inside a larger array")
	void offsetAndLength()
	{
		final byte[] array = { 9, 9, 1, 2, 3, 9 };
		final ByteBuf frame = FrameEncoder.encode(ByteBufAllocator.DEFAULT, array, 2, 3);
		try
		{
			assertThat(bytes(frame)).containsExactly(5, 0, 1, 2, 3);
		}
		finally
		{
			frame.release();
		}
	}
	
	@Test
	@DisplayName("an empty payload is a frame of the header only")
	void emptyPayload()
	{
		final ByteBuf frame = FrameEncoder.encode(ByteBufAllocator.DEFAULT, new byte[0], 0, 0);
		try
		{
			assertThat(bytes(frame)).containsExactly(2, 0);
		}
		finally
		{
			frame.release();
		}
	}
	
	@Test
	@DisplayName("the longest payload fits, one byte more does not")
	void longestPayload()
	{
		final byte[] payload = new byte[FrameEncoder.MAX_PAYLOAD_SIZE + 1];
		
		final ByteBuf frame = FrameEncoder.encode(ByteBufAllocator.DEFAULT, payload, 0, FrameEncoder.MAX_PAYLOAD_SIZE);
		try
		{
			assertThat(frame.readableBytes()).isEqualTo(0xFFFF);
			assertThat(frame.getUnsignedShortLE(0)).isEqualTo(0xFFFF);
		}
		finally
		{
			frame.release();
		}
		
		assertThatThrownBy(() -> FrameEncoder.encode(ByteBufAllocator.DEFAULT, payload, 0, payload.length))
				.isInstanceOf(IllegalArgumentException.class);
	}
	
	@Test
	@DisplayName("what the encoder writes the decoder reads back")
	void roundTrip()
	{
		final byte[] payload = { 1, 2, 3, 4, 5, 6, 7, 8 };
		final ByteBuf frame = FrameEncoder.encode(ByteBufAllocator.DEFAULT, payload, 0, payload.length);
		
		final io.netty.channel.embedded.EmbeddedChannel channel =
				new io.netty.channel.embedded.EmbeddedChannel(new FrameDecoder(100));
		channel.writeInbound(frame);
		
		assertThat((byte[])channel.readInbound()).containsExactly(payload);
		channel.finishAndReleaseAll();
	}
}
