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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.l2jfree.network.TestProtocol.Peer;
import com.l2jfree.network.TestProtocol.Server;

/**
 * The network core against a plain socket client, on every transport this host offers. The client has its own copy of
 * the rolling keys, so a packet sent or read out of order fails the test.
 */
class NetworkServerTest
{
	private Server _server;
	
	@AfterEach
	void stop() throws InterruptedException
	{
		System.clearProperty(Transport.PROPERTY);
		
		if (_server != null)
			_server.shutdown();
	}
	
	private Server start(Transport.Kind kind, NetworkConfig config) throws IOException
	{
		System.setProperty(Transport.PROPERTY, kind.getName());
		
		try
		{
			Transport.select(kind.getName());
		}
		catch (IllegalStateException e)
		{
			assumeTrue(false, kind.getName() + " is not available here: " + e.getMessage());
		}
		
		_server = new Server(config);
		_server.openServerSocket(InetAddress.getLoopbackAddress(), 0);
		_server.start();
		return _server;
	}
	
	private Client connect(Transport.Kind kind) throws IOException
	{
		start(kind, new NetworkConfig().setIoThreads(2));
		return new Client(port());
	}
	
	private int port()
	{
		return _server.getLocalAddress().getPort();
	}
	
	@ParameterizedTest
	@EnumSource(Transport.Kind.class)
	void packetsGoBothWaysThroughTheCipher(Transport.Kind kind) throws Exception
	{
		try (Client client = connect(kind))
		{
			assertThat(client.readText()).isEqualTo("hello");
			for (int i = 0; i < 50; i++)
			{
				client.send(echo("n" + i, i));
				assertThat(client.readText()).isEqualTo("n" + i + ":" + (i * 2));
			}
		}
	}
	
	@ParameterizedTest
	@EnumSource(Transport.Kind.class)
	void framesSplitAndJoinedInTheStreamAreReadInOrder(Transport.Kind kind) throws Exception
	{
		try (Client client = connect(kind))
		{
			assertThat(client.readText()).isEqualTo("hello");
			final ByteArrayOutputStream stream = new ByteArrayOutputStream();
			for (int i = 0; i < 20; i++)
			{
				stream.write(client.frame(echo("x" + i, i)));
				// frames that carry no body are skipped
				stream.write(new byte[] { 2, 0 });
			}
			final byte[] bytes = stream.toByteArray();
			for (int offset = 0; offset < bytes.length; offset += 7)
			{
				client.writeRaw(bytes, offset, Math.min(7, bytes.length - offset));
				Thread.sleep(1);
			}
			for (int i = 0; i < 20; i++)
				assertThat(client.readText()).isEqualTo("x" + i + ":" + (i * 2));
		}
	}
	
	@ParameterizedTest
	@EnumSource(Transport.Kind.class)
	void closingSendsTheLastPacketAndDisconnectsOnce(Transport.Kind kind) throws Exception
	{
		try (Client client = connect(kind))
		{
			assertThat(client.readText()).isEqualTo("hello");
			client.send(new byte[] { 2 });
			assertThat(client.readText()).isEqualTo("closing");
			assertThat(client.endOfStream()).isTrue();
			
			final Peer peer = _server.connections.get(0);
			assertThat(peer.gone.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(peer.disconnections).isEqualTo(1);
			assertThat(peer.forced).isFalse();
		}
	}
	
	@ParameterizedTest
	@EnumSource(Transport.Kind.class)
	void aClientThatHangsUpIsDisconnected(Transport.Kind kind) throws Exception
	{
		final Client client = connect(kind);
		assertThat(client.readText()).isEqualTo("hello");
		client.close();
		
		final Peer peer = _server.connections.get(0);
		assertThat(peer.gone.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(peer.disconnections).isEqualTo(1);
	}
	
	@Test
	void unknownAndShortPacketsAreDroppedButTheConnectionStays() throws Exception
	{
		try (Client client = connect(Transport.Kind.NIO))
		{
			assertThat(client.readText()).isEqualTo("hello");
			client.send(new byte[] { 99, 1, 2, 3 });
			client.send(new byte[] { 3, 1, 2 });
			client.send(echo("still", 21));
			assertThat(client.readText()).isEqualTo("still:42");
		}
	}
	
	@Test
	void aRefusedAddressIsClosedWithoutAConnection() throws Exception
	{
		try (Client client = connect(Transport.Kind.NIO))
		{
			assertThat(client.readText()).isEqualTo("hello");
		}
		_server.acceptAll = false;
		try (Client client = new Client(port()))
		{
			assertThat(client.endOfStream()).isTrue();
		}
		assertThat(_server.connections).hasSize(1);
	}
	
	@Test
	void aServerThatStopsAcceptingKeepsItsConnections() throws Exception
	{
		try (Client client = connect(Transport.Kind.NIO))
		{
			assertThat(client.readText()).isEqualTo("hello");
			final int port = port();
			
			_server.stopAccepting();
			
			// On loopback a connect to a closed port in the ephemeral range can connect the socket to itself, so the
			// test does not expect an error: it expects that the server never sees the connection.
			try (Client late = new Client(port))
			{
				Thread.sleep(300);
			}
			catch (java.io.IOException refused)
			{
				// the usual case
			}
			assertThat(_server.connections).hasSize(1);
			client.send(echo("alive", 4));
			assertThat(client.readText()).isEqualTo("alive:8");
		}
	}
	
	@Test
	void anAddressThatOpensTooManyConnectionsIsRefused() throws Exception
	{
		start(Transport.Kind.NIO, new NetworkConfig().setIoThreads(2).setAcceptLimits(1, 2, 60, 100, 200, 600));
		
		try (Client first = new Client(port()); Client second = new Client(port()))
		{
			assertThat(first.readText()).isEqualTo("hello");
			assertThat(second.readText()).isEqualTo("hello");
			try (Client third = new Client(port()))
			{
				assertThat(third.endOfStream()).isTrue();
			}
		}
		assertThat(_server.connections).hasSize(2);
	}
	
	@Test
	void higherLimitsAdmitTheConnectionsOfPlayersBehindOneAddress() throws Exception
	{
		start(Transport.Kind.NIO, new NetworkConfig().setIoThreads(2).setAcceptLimits(100, 200, 10, 300, 600, 60));
		
		List<Client> clients = new ArrayList<Client>();
		try
		{
			for (int i = 0; i < 30; i++)
			{
				Client client = new Client(port());
				clients.add(client);
				assertThat(client.readText()).isEqualTo("hello");
			}
		}
		finally
		{
			for (Client client : clients)
				client.close();
		}
		assertThat(_server.connections).hasSize(30);
	}
	
	@Test
	void aServerAcceptsOnlyAfterStart() throws Exception
	{
		System.setProperty(Transport.PROPERTY, "nio");
		_server = new Server(new NetworkConfig().setIoThreads(1));
		_server.openServerSocket(InetAddress.getLoopbackAddress(), 0);
		
		// the socket is bound, but nothing is accepted yet: the first packet of the server does not arrive
		try (Socket early = new Socket(InetAddress.getLoopbackAddress(), port()))
		{
			early.setSoTimeout(300);
			Thread.sleep(200);
			assertThat(_server.connections).isEmpty();
			
			_server.start();
			final Client client = new Client(early, 5000);
			assertThat(client.readText()).isEqualTo("hello");
		}
	}
	
	@Test
	void aClientThatStopsReadingIsDisconnected() throws Exception
	{
		start(Transport.Kind.NIO, new NetworkConfig().setIoThreads(1).setWriteHighWaterMark(64 * 1024));
		try (Socket silent = new Socket(InetAddress.getLoopbackAddress(), port()))
		{
			silent.setReceiveBufferSize(4096);
			while (_server.connections.isEmpty())
				Thread.sleep(10);
			final Peer peer = _server.connections.get(0);
			final String filler = "x".repeat(1000);
			for (int i = 0; i < 20_000 && peer.gone.getCount() > 0; i++)
				peer.sendPacket(new TestProtocol.Text(filler));
			
			assertThat(peer.gone.await(10, TimeUnit.SECONDS)).isTrue();
			assertThat(peer.forced).isTrue();
		}
	}
	
	@Test
	void aBurstToAClientThatReadsIsNotClosed() throws Exception
	{
		start(Transport.Kind.NIO, new NetworkConfig().setIoThreads(1));
		try (Client client = new Client(port()))
		{
			assertThat(client.readText()).isEqualTo("hello");
			while (_server.connections.isEmpty())
				Thread.sleep(10);
			final Peer peer = _server.connections.get(0);
			
			final String filler = "y".repeat(1000);
			// about 1.5 MB: over the default high-water mark of 1 MiB, but small enough for the socket buffers. Written
			// without flushing in between, it would stand in the outbound buffer and close a healthy client.
			final int count = 1500;
			final Thread sender = new Thread(() -> {
				for (int i = 0; i < count; i++)
					peer.sendPacket(new TestProtocol.Text(filler));
			});
			sender.start();
			
			for (int i = 0; i < count; i++)
				assertThat(client.readText()).hasSize(1000);
			
			sender.join();
			assertThat(peer.forced).isFalse();
			assertThat(peer.gone.getCount()).isEqualTo(1);
		}
	}
	
	@ParameterizedTest
	@EnumSource(value = Transport.Kind.class, names = { "NIO" })
	void aFrameThatIsTooShortEndsTheConnectionAsForced(Transport.Kind kind) throws Exception
	{
		for (byte[] header : new byte[][] { { 0, 0 }, { 1, 0 } })
		{
			try (Client client = connect(kind))
			{
				assertThat(client.readText()).isEqualTo("hello");
				client.writeRaw(header, 0, 2);
				assertThat(client.endOfStream()).isTrue();
				
				final Peer peer = _server.connections.get(0);
				assertThat(peer.gone.await(5, TimeUnit.SECONDS)).isTrue();
				assertThat(peer.forced).isTrue();
			}
			stop();
			_server = null;
		}
	}
	
	@Test
	void aFrameOverTheMaximumEndsTheConnectionAsForced() throws Exception
	{
		start(Transport.Kind.NIO, new NetworkConfig().setIoThreads(1).setMaxFrameSize(1024));
		try (Client client = new Client(port()))
		{
			assertThat(client.readText()).isEqualTo("hello");
			client.writeRaw(new byte[] { (byte)0xD0, 0x07 }, 0, 2); // 2000 bytes
			assertThat(client.endOfStream()).isTrue();
			
			final Peer peer = _server.connections.get(0);
			assertThat(peer.gone.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(peer.forced).isTrue();
		}
	}
	
	@Test
	void aSharedPacketCanGoToTwoConnectionsAtTheSameTime() throws Exception
	{
		start(Transport.Kind.NIO, new NetworkConfig().setIoThreads(2));
		final int count = 300;
		final TestProtocol.Text shared = new TestProtocol.Text("shared");
		
		try (Client first = new Client(port()); Client second = new Client(port()))
		{
			assertThat(first.readText()).isEqualTo("hello");
			assertThat(second.readText()).isEqualTo("hello");
			while (_server.connections.size() < 2)
				Thread.sleep(10);
			
			final Thread[] senders = new Thread[2];
			for (int t = 0; t < 2; t++)
			{
				final Peer peer = _server.connections.get(t);
				senders[t] = new Thread(() -> {
					for (int i = 0; i < count; i++)
						peer.sendPacket(shared);
				});
				senders[t].start();
			}
			
			for (int i = 0; i < count; i++)
			{
				assertThat(first.readText()).isEqualTo("shared");
				assertThat(second.readText()).isEqualTo("shared");
			}
			
			for (Thread sender : senders)
				sender.join();
		}
	}
	
	@Test
	void packetsOfSeveralSendersKeepTheirOrderThroughTheCipher() throws Exception
	{
		start(Transport.Kind.NIO, new NetworkConfig().setIoThreads(1));
		final int count = 500;
		
		try (Client client = new Client(port()))
		{
			assertThat(client.readText()).isEqualTo("hello");
			while (_server.connections.isEmpty())
				Thread.sleep(10);
			final Peer peer = _server.connections.get(0);
			
			final Thread[] senders = new Thread[2];
			final String[] prefixes = { "a", "b" };
			for (int t = 0; t < 2; t++)
			{
				final String prefix = prefixes[t];
				senders[t] = new Thread(() -> {
					for (int i = 0; i < count; i++)
						peer.sendPacket(new TestProtocol.Text(prefix + i));
				});
				senders[t].start();
			}
			
			final int[] next = new int[2];
			final List<String> seen = new ArrayList<String>();
			for (int i = 0; i < 2 * count; i++)
			{
				final String text = client.readText(); // the rolling key fails here if the order of writing and encrypting differs
				final int sender = text.charAt(0) == 'a' ? 0 : 1;
				assertThat(Integer.parseInt(text.substring(1))).isEqualTo(next[sender]++);
				seen.add(text);
			}
			
			for (Thread sender : senders)
				sender.join();
			assertThat(seen).hasSize(2 * count);
		}
	}
	
	private static byte[] echo(String text, int number)
	{
		final ByteBuffer body = ByteBuffer.allocate(1 + 2 * (text.length() + 1) + 4).order(ByteOrder.LITTLE_ENDIAN);
		body.put((byte)1);
		for (char c : text.toCharArray())
			body.putChar(c);
		body.putChar('\0');
		body.putInt(number);
		return body.array();
	}
	
	/** The client side of the test protocol, with its own copies of the rolling keys. */
	private static final class Client implements AutoCloseable
	{
		private final Socket _socket;
		private final DataInputStream _in;
		private final OutputStream _out;
		private int _outKey = 7;
		private int _inKey = 7;
		
		Client(int port) throws IOException
		{
			this(new Socket(InetAddress.getLoopbackAddress(), port), 5000);
		}
		
		Client(Socket socket, int timeoutMillis) throws IOException
		{
			_socket = socket;
			_socket.setSoTimeout(timeoutMillis);
			_in = new DataInputStream(_socket.getInputStream());
			_out = _socket.getOutputStream();
		}
		
		byte[] frame(byte[] body)
		{
			final ByteBuffer frame = ByteBuffer.allocate(2 + body.length).order(ByteOrder.LITTLE_ENDIAN);
			frame.putShort((short)(2 + body.length)).put(body);
			_outKey = TestProtocol.xor(frame, 2, body.length, _outKey);
			return frame.array();
		}
		
		void send(byte[] body) throws IOException
		{
			_out.write(frame(body));
			_out.flush();
		}
		
		void writeRaw(byte[] bytes, int offset, int length) throws IOException
		{
			_out.write(bytes, offset, length);
			_out.flush();
		}
		
		String readText() throws IOException
		{
			final int length = Short.reverseBytes(_in.readShort()) & 0xFFFF;
			final byte[] body = new byte[length - 2];
			_in.readFully(body);
			final ByteBuffer buf = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
			assertThat(buf.get(body.length - 2)).isEqualTo((byte)0xAB);
			assertThat(buf.get(body.length - 1)).isEqualTo((byte)0xCD);
			_inKey = TestProtocol.xor(buf, 0, body.length - TestProtocol.TRAILER, _inKey);
			assertThat(buf.get() & 0xFF).isEqualTo(0x10);
			final StringBuilder sb = new StringBuilder();
			for (char c; (c = buf.getChar()) != 0;)
				sb.append(c);
			return sb.toString();
		}
		
		boolean endOfStream() throws IOException
		{
			try
			{
				return _in.read() == -1;
			}
			catch (EOFException e)
			{
				return true;
			}
		}
		
		@Override
		public void close() throws IOException
		{
			_socket.close();
		}
	}
}
