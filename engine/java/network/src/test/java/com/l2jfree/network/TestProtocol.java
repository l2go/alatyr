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

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import io.netty.channel.Channel;

/**
 * A minimal protocol for the tests. Bodies are XOR-ed with a key that changes after every packet in each direction,
 * so a packet encrypted out of order cannot be decrypted. Every server packet gets a 2-byte trailer, as the login
 * cipher appends a checksum.
 */
final class TestProtocol
{
	static final int TRAILER = 2;
	
	static final class Server extends NetworkServer<Peer, Inbound, Outbound>
	{
		final List<Peer> connections = new CopyOnWriteArrayList<Peer>();
		volatile boolean acceptAll = true;
		
		Server(NetworkConfig config)
		{
			super(config, (buf, client, opcode) -> switch (opcode) {
				case 1 -> new Echo();
				case 2 -> new Quit();
				case 3 -> new Short();
				default -> null;
			});
		}
		
		@Override
		protected Peer createClient(Channel channel)
		{
			final Peer peer = new Peer(this, channel);
			connections.add(peer);
			peer.sendPacket(new Text("hello"));
			return peer;
		}
		
		@Override
		protected void executePacket(Inbound packet)
		{
			packet.run();
		}
		
		@Override
		protected boolean acceptConnectionFrom(InetAddress address)
		{
			return acceptAll && super.acceptConnectionFrom(address);
		}
	}
	
	static final class Peer extends Connection<Peer, Inbound, Outbound>
	{
		private int _inKey = 7;
		private int _outKey = 7;
		volatile int disconnections;
		volatile boolean forced;
		final CountDownLatch gone = new CountDownLatch(1);
		
		Peer(Server server, Channel channel)
		{
			super(server, channel);
		}
		
		@Override
		protected boolean decrypt(ByteBuffer buf, int size)
		{
			_inKey = xor(buf, buf.position(), size, _inKey);
			return true;
		}
		
		@Override
		protected boolean encrypt(ByteBuffer buf, int size)
		{
			_outKey = xor(buf, buf.position(), size, _outKey);
			buf.position(buf.position() + size);
			buf.put((byte)0xAB).put((byte)0xCD);
			return true;
		}
		
		@Override
		protected void onDisconnection()
		{
			disconnections++;
			gone.countDown();
		}
		
		@Override
		protected void onForcedDisconnection()
		{
			forced = true;
		}
		
		@Override
		protected Outbound getDefaultClosePacket()
		{
			return new Text("bye");
		}
		
		@Override
		protected String getUID()
		{
			return null;
		}
	}
	
	/** XORs {@code size} bytes from {@code offset} with a key stream starting at {@code key}; returns the next key. */
	static int xor(ByteBuffer buf, int offset, int size, int key)
	{
		for (int i = 0; i < size; i++)
			buf.put(offset + i, (byte)(buf.get(offset + i) ^ (key + i)));
		return key + 1;
	}
	
	abstract static class Inbound extends ReceivablePacket<Peer, Inbound, Outbound>
	{
	}
	
	abstract static class Outbound extends SendablePacket<Peer, Inbound, Outbound>
	{
	}
	
	/** Opcode 1: a string and a number; the answer is the string back with the number doubled. */
	static final class Echo extends Inbound
	{
		private String _text;
		private int _number;
		
		@Override
		protected boolean read()
		{
			_text = readS();
			_number = readD();
			return true;
		}
		
		@Override
		protected void runImpl()
		{
			sendPacket(new Text(_text + ":" + (_number * 2)));
		}
	}
	
	/** Opcode 2: the server closes the connection with a last packet. */
	static final class Quit extends Inbound
	{
		@Override
		protected boolean read()
		{
			return true;
		}
		
		@Override
		protected void runImpl()
		{
			getClient().close(new Text("closing"));
		}
	}
	
	/** Opcode 3: wants more bytes than it gets. */
	static final class Short extends Inbound
	{
		@Override
		protected boolean read()
		{
			readQ();
			return true;
		}
		
		@Override
		protected void runImpl()
		{
		}
	}
	
	/** Opcode 0x10: a string. */
	static final class Text extends Outbound
	{
		private final String _text;
		
		Text(String text)
		{
			_text = text;
		}
		
		@Override
		protected void write(Peer client)
		{
			writeC(0x10);
			writeS(_text);
		}
	}
}
