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

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import com.l2jfree.network.FloodManager.ErrorMode;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

/**
 * The last handler of the pipeline of a connection. It receives the payloads the {@link FrameDecoder} cuts out, decrypts
 * and reads them on the I/O thread, hands the packets to {@link NetworkServer#executePacket(ReceivablePacket)}, and
 * maps the events of the channel to the hooks of the {@link Connection}.
 * <p>
 * The frames of one connection are handled one after the other on one I/O thread, so the packets are read in the order
 * they arrived and a packet sees the state its predecessors left, as before.
 */
final class ConnectionHandler<T extends Connection<T, RP, SP>, RP extends ReceivablePacket<T, RP, SP>, SP extends SendablePacket<T, RP, SP>>
		extends ChannelInboundHandlerAdapter
{
	private final NetworkServer<T, RP, SP> _server;
	private final PacketHandler<T, RP, SP> _packetHandler;
	private final T _client;
	
	ConnectionHandler(NetworkServer<T, RP, SP> server, PacketHandler<T, RP, SP> packetHandler, T client)
	{
		_server = server;
		_packetHandler = packetHandler;
		_client = client;
	}
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
	{
		if (!(msg instanceof byte[]))
		{
			ReferenceCountUtil.release(msg);
			return;
		}
		
		// the connection ended or is closing while the frames of one read were still being cut: no more input is run
		if (_client.isClosed())
			return;
		
		parseClientPacket((byte[])msg);
	}
	
	/**
	 * Decrypts the payload of one frame and reads the packet in it.
	 */
	private void parseClientPacket(byte[] payload)
	{
		final T client = _client;
		final int dataSize = payload.length;
		final ByteBuffer buf = ByteBuffer.wrap(payload).order(_server.getByteOrder());
		
		if (client.decrypt(buf, dataSize) && buf.hasRemaining())
		{
			final int opcode = buf.get() & 0xFF;
			
			if (_server.canReceivePacketFrom(client, opcode))
			{
				final RP cp = _packetHandler.handlePacket(buf, client, opcode);
				
				if (cp != null)
				{
					cp.setByteBuffer(buf);
					cp.setClient(client);
					
					try
					{
						if (cp.getAvaliableBytes() < cp.getMinimumLength())
						{
							_server.report(ErrorMode.BUFFER_UNDER_FLOW, client, cp, null);
						}
						else if (cp.read())
						{
							_server.executePacket(cp);
							
							if (buf.hasRemaining())
							{
								// disabled until packet structures updated properly
								//report(ErrorMode.BUFFER_OVER_FLOW, client, cp, null);
								
								NetworkServer._log.info("Invalid packet format (buf: " + buf + ", dataSize: " + dataSize
										+ ", opcode: " + opcode + ") used for reading - " + client + " - " + cp.getType()
										+ " - " + _server.getVersionInfo());
							}
						}
					}
					catch (BufferUnderflowException e)
					{
						_server.report(ErrorMode.BUFFER_UNDER_FLOW, client, cp, e);
					}
					catch (RuntimeException e)
					{
						_server.report(ErrorMode.FAILED_READING, client, cp, e);
					}
					
					cp.setByteBuffer(null);
				}
			}
		}
	}
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception
	{
		// the client closed the connection, or the channel was closed after the connection ended
		_client.terminate(false);
		
		super.channelInactive(ctx);
	}
	
	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception
	{
		_client.checkWritable();
		
		super.channelWritabilityChanged(ctx);
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
	{
		if (cause instanceof InvalidFrameException)
		{
			_server.report(ErrorMode.INVALID_FRAME, _client, null, cause);
		}
		else if (!(cause instanceof IOException))
		{
			// a failure in the decryption or in a hook: the stream cannot be trusted any more
			_server.report(ErrorMode.FAILED_READING, _client, null, cause);
		}
		
		// an I/O error or a broken stream is a forced disconnection
		_client.terminate(true);
	}
}
