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

import java.net.InetAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.l2jfree.network.TestProtocol.Broken;
import com.l2jfree.network.TestProtocol.Idle;
import com.l2jfree.network.TestProtocol.Peer;
import com.l2jfree.network.TestProtocol.Server;

import io.netty.channel.embedded.EmbeddedChannel;

/**
 * A packet built outside the read path binds once and runs once. A failure is recorded and still reported.
 */
class DirectBindTest
{
	private Server _server;
	private EmbeddedChannel _channel;
	
	@AfterEach
	void stop() throws InterruptedException
	{
		if (_channel != null)
			_channel.close();
		
		if (_server != null)
			_server.shutdown();
	}
	
	private Peer peer() throws Exception
	{
		_server = new Server(new NetworkConfig());
		_channel = new EmbeddedChannel();
		InetAddress address = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
		Peer peer = new Peer(_server, _channel, address);
		assertThat(peer.getInetAddress()).isEqualTo(address);
		return peer;
	}
	
	@Test
	void anOrdinaryPacketMayRunTwice()
	{
		Idle idle = new Idle();
		
		idle.run();
		idle.run();
		
		assertThat(idle.failed()).isFalse();
	}
	
	@Test
	void bindDirectRejectsNullAndASecondBind() throws Exception
	{
		Peer peer = peer();
		Idle idle = new Idle();
		
		assertThatThrownBy(() -> idle.bindDirect(null)).isInstanceOf(IllegalArgumentException.class);
		idle.bindDirect(peer);
		assertThatThrownBy(() -> idle.bindDirect(peer)).isInstanceOf(IllegalStateException.class);
	}
	
	@Test
	void aDirectPacketRunsOnce() throws Exception
	{
		Peer peer = peer();
		Idle idle = new Idle();
		idle.bindDirect(peer);
		
		idle.run();
		
		assertThat(idle.failed()).isFalse();
		assertThatThrownBy(idle::run).isInstanceOf(IllegalStateException.class);
	}
	
	@Test
	void aFailureIsRecordedAndReported() throws Exception
	{
		Peer peer = peer();
		Broken broken = new Broken();
		broken.bindDirect(peer);
		
		broken.run();
		
		assertThat(broken.failed()).isTrue();
	}
}
