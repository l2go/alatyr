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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * Puts a payload into a frame: a 2-byte little-endian length that includes the 2 bytes themselves, then the payload
 * unchanged.
 */
public final class FrameEncoder
{
	/** The longest payload a frame can carry. */
	public static final int MAX_PAYLOAD_SIZE = NetworkConfig.MAX_FRAME_SIZE_LIMIT - NetworkConfig.HEADER_SIZE;
	
	private FrameEncoder()
	{
	}
	
	/**
	 * @param allocator allocates the frame
	 * @param payload the array that holds the payload
	 * @param offset the index of the first payload byte
	 * @param length the number of payload bytes, 0 to {@link #MAX_PAYLOAD_SIZE}
	 * @return a new buffer with the frame, owned by the caller
	 * @throws IllegalArgumentException when the payload does not fit into one frame
	 */
	public static ByteBuf encode(ByteBufAllocator allocator, byte[] payload, int offset, int length)
	{
		if (length < 0 || length > MAX_PAYLOAD_SIZE)
			throw new IllegalArgumentException("A payload of " + length + " bytes does not fit into a frame (maximum "
					+ MAX_PAYLOAD_SIZE + ")");
		
		final int frameLength = NetworkConfig.HEADER_SIZE + length;
		final ByteBuf frame = allocator.buffer(frameLength, frameLength);
		
		frame.writeShortLE(frameLength);
		frame.writeBytes(payload, offset, length);
		
		return frame;
	}
}
