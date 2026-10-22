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
package com.l2jfree.gameserver.bridge.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.endpoint.BridgeLoopback.Transport;
import com.l2jfree.gameserver.bridge.wire.Codec;

/**
 * A short write keeps the rest of the frame. Past the outbound cap the listener closes for a slow consumer and does
 * not drop a queued frame in the middle.
 */
class BridgeWriteTest
{
	private static final byte[] SECRET = new byte[32];
	
	@Test
	void aShortWriteKeepsTheRestOfTheFrame() throws Exception
	{
		Transport transport = new Transport(SECRET);
		byte[] frame = new byte[] { 1, 2, 3, 4, 5 };
		BridgeLoopback.offer(transport, frame);
		java.nio.channels.SocketChannel socket = mock(java.nio.channels.SocketChannel.class);
		when(socket.write(any(ByteBuffer.class))).thenReturn(0);
		
		assertThat(BridgeLoopback.flush(transport, socket)).isZero();
		assertThat(transport.held).isEqualTo(frame.length);
		
		when(socket.write(any(ByteBuffer.class))).thenAnswer(invocation ->
		{
			ByteBuffer buffer = invocation.getArgument(0);
			int n = Math.min(2, buffer.remaining());
			buffer.position(buffer.position() + n);
			return n;
		});
		assertThat(BridgeLoopback.flush(transport, socket)).isEqualTo(2);
		assertThat(transport.held).isEqualTo(3);
		assertThat(transport.writingAt).isEqualTo(2);
		assertThat(Arrays.copyOfRange(transport.writing, 2, 5)).containsExactly(3, 4, 5);
	}
	
	@Test
	void aFramePastTheOutboundCapClosesForASlowConsumer()
	{
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		Codec.bridge().write(expected, List.of(BridgeFrames.bye(BridgeFrames.BYE_SLOW)));
		byte[] bye = expected.toByteArray();
		Transport transport = new Transport(SECRET);
		BridgeLoopback.offer(transport, new byte[BridgeLoopback.OUT_QUEUE_BYTES - bye.length - 1]);
		BridgeLoopback.offer(transport, new byte[8]);
		
		assertThat(transport.closing).isTrue();
		assertThat(transport.queued).hasSize(2);
		assertThat(transport.queued.peekLast()).isEqualTo(bye);
		assertThat(transport.held).isEqualTo(BridgeLoopback.OUT_QUEUE_BYTES - 1);
	}
}
