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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.bridge.endpoint.BridgeLoopback.Transport;
import com.l2jfree.gameserver.bridge.wire.Codec;
import com.l2jfree.gameserver.bridge.wire.Envelope;
import com.l2jfree.gameserver.bridge.wire.WireException;

/**
 * One read may carry more than one frame. Bytes past a single incomplete frame are a frame error.
 */
class BridgeReadTest
{
	@Test
	void oneBufferYieldsEveryCompleteFrame() throws Exception
	{
		byte[] first = frame(BridgeFrames.pong(1));
		byte[] second = frame(BridgeFrames.pong(2));
		byte[] both = new byte[first.length + second.length];
		System.arraycopy(first, 0, both, 0, first.length);
		System.arraycopy(second, 0, both, first.length, second.length);
		Transport transport = new Transport(new byte[32]);
		transport.pending.write(both);
		
		List<List<Envelope>> frames = new ArrayList<>();
		while (true)
		{
			List<Envelope> records = BridgeLoopback.takeFrame(transport);
			if (records == null)
			{
				if (transport.pending.size() > BridgeLoopback.FRAME_CAP + 10)
					throw WireException.frame();
				break;
			}
			frames.add(records);
		}
		
		assertThat(frames).hasSize(2);
		assertThat(frames.get(0).get(0).field(1).value().u()).isEqualTo(1);
		assertThat(frames.get(1).get(0).field(1).value().u()).isEqualTo(2);
		assertThat(transport.pending.size()).isZero();
	}
	
	@Test
	void pendingBytesPastOneFrameAreAFrameError()
	{
		Transport transport = new Transport(new byte[32]);
		transport.pending.write(new byte[BridgeLoopback.FRAME_CAP + 11]);
		
		assertThatThrownBy(() ->
		{
			List<Envelope> records = BridgeLoopback.takeFrame(transport);
			if (records == null && transport.pending.size() > BridgeLoopback.FRAME_CAP + 10)
				throw WireException.frame();
		}).isInstanceOf(WireException.class);
	}
	
	private static byte[] frame(Envelope record) throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Codec.bridge().write(out, List.of(record));
		return out.toByteArray();
	}
}
