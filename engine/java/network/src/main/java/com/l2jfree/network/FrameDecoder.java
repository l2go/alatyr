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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Cuts the inbound byte stream into frames. A frame is a 2-byte little-endian length that includes the 2 bytes
 * themselves, followed by the payload. The decoder fires the payload of each complete frame, as a {@code byte[]}
 * of exactly the payload, in the order the frames arrived. The payload is still encrypted.
 * <p>
 * <ul>
 * <li>A frame whose length is only the header (2) has no payload. It is consumed and nothing is fired, as the old
 * transport did for packets without a body.</li>
 * <li>A length below 2 or above the maximum frame size cannot be resynchronized. The decoder drops what it holds,
 * ignores all later input, and fires an {@link InvalidFrameException} through the pipeline. The length is checked as
 * soon as the two header bytes are there, so a client cannot make the decoder hold more than the maximum frame size.</li>
 * </ul>
 * One decoder belongs to one channel.
 */
public final class FrameDecoder extends ChannelInboundHandlerAdapter
{
	private final int _maxFrameSize;
	
	/** The bytes of an incomplete frame, or null. */
	private ByteBuf _cumulation;
	private boolean _failed;
	
	/**
	 * @param maxFrameSize the largest accepted frame, header included
	 */
	public FrameDecoder(int maxFrameSize)
	{
		if (maxFrameSize <= NetworkConfig.HEADER_SIZE)
			throw new IllegalArgumentException("maxFrameSize: " + maxFrameSize);
		
		_maxFrameSize = maxFrameSize;
	}
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
	{
		if (!(msg instanceof ByteBuf))
		{
			ctx.fireChannelRead(msg);
			return;
		}
		
		final ByteBuf in = (ByteBuf)msg;
		
		try
		{
			if (_failed)
				return;
			
			if (_cumulation == null)
			{
				// fast path: cut the frames straight out of the incoming buffer, keep only an incomplete tail
				decode(ctx, in);
				
				if (in.isReadable())
				{
					_cumulation = ctx.alloc().buffer(Math.max(in.readableBytes(), 64));
					_cumulation.writeBytes(in);
				}
			}
			else
			{
				_cumulation.writeBytes(in);
				decode(ctx, _cumulation);
				
				if (_cumulation.isReadable())
					_cumulation.discardReadBytes();
				else
					releaseCumulation();
			}
		}
		catch (InvalidFrameException e)
		{
			_failed = true;
			releaseCumulation();
			ctx.fireExceptionCaught(e);
		}
		finally
		{
			in.release();
		}
	}
	
	private void decode(ChannelHandlerContext ctx, ByteBuf buf)
	{
		while (buf.readableBytes() >= NetworkConfig.HEADER_SIZE)
		{
			final int length = buf.getUnsignedShortLE(buf.readerIndex());
			
			if (length < NetworkConfig.HEADER_SIZE)
				throw new InvalidFrameException("Frame length " + length + " is shorter than the "
						+ NetworkConfig.HEADER_SIZE + "-byte header", length);
			
			if (length > _maxFrameSize)
				throw new InvalidFrameException("Frame length " + length + " is longer than the maximum of " + _maxFrameSize,
						length);
			
			if (buf.readableBytes() < length)
				return; // wait for the rest
			
			buf.skipBytes(NetworkConfig.HEADER_SIZE);
			
			final int payloadSize = length - NetworkConfig.HEADER_SIZE;
			
			if (payloadSize == 0)
				continue; // a packet without a body
			
			final byte[] payload = new byte[payloadSize];
			buf.readBytes(payload);
			
			ctx.fireChannelRead(payload);
		}
	}
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception
	{
		releaseCumulation();
		
		super.channelInactive(ctx);
	}
	
	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception
	{
		releaseCumulation();
	}
	
	private void releaseCumulation()
	{
		if (_cumulation != null)
		{
			_cumulation.release();
			_cumulation = null;
		}
	}
}
