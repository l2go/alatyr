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
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.util.concurrent.DefaultThreadFactory;

class TransportTest
{
	@Test
	@DisplayName("auto never fails and picks the first available transport")
	void autoNeverFails()
	{
		final Transport transport = Transport.select("auto");
		
		assertThat(transport).isNotNull();
		
		if (IoUring.isAvailable())
			assertThat(transport.getKind()).isEqualTo(Transport.Kind.IO_URING);
		else if (Epoll.isAvailable())
			assertThat(transport.getKind()).isEqualTo(Transport.Kind.EPOLL);
		else
			assertThat(transport.getKind()).isEqualTo(Transport.Kind.NIO);
	}
	
	@Test
	@DisplayName("a missing or empty request means auto")
	void missingMeansAuto()
	{
		final Transport.Kind auto = Transport.select("auto").getKind();
		
		assertThat(Transport.select(null).getKind()).isEqualTo(auto);
		assertThat(Transport.select("").getKind()).isEqualTo(auto);
		assertThat(Transport.select("  ").getKind()).isEqualTo(auto);
	}
	
	@Test
	@DisplayName("forcing nio returns the NIO classes")
	void nioIsAlwaysAvailable()
	{
		final Transport transport = Transport.select("NIO ");
		
		assertThat(transport.getKind()).isEqualTo(Transport.Kind.NIO);
		assertThat(transport.getName()).isEqualTo("nio");
		assertThat(transport.serverChannelClass()).isEqualTo(NioServerSocketChannel.class);
		
		final EventLoopGroup group = transport.newEventLoopGroup(1, new DefaultThreadFactory("transport-test", true));
		try
		{
			assertThat(group.isShuttingDown()).isFalse();
			assertThat(NioIoHandler.newFactory()).isNotNull();
		}
		finally
		{
			group.shutdownGracefully(0, 1, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
		}
	}
	
	@Test
	@DisplayName("an unknown transport fails and names the value")
	void unknownTransport()
	{
		assertThatThrownBy(() -> Transport.select("kqueue")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("kqueue").hasMessageContaining(Transport.PROPERTY);
	}
	
	@Test
	@DisplayName("forcing epoll fails with a message that names it when it is not available")
	void forcedEpoll()
	{
		if (Epoll.isAvailable())
		{
			assertThat(Transport.select("epoll").getKind()).isEqualTo(Transport.Kind.EPOLL);
			return;
		}
		
		assumeFalse(Epoll.isAvailable());
		assertThatThrownBy(() -> Transport.select("epoll")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("epoll").hasMessageContaining("not available");
	}
	
	@Test
	@DisplayName("forcing io_uring fails with a message that names it when it is not available")
	void forcedIoUring()
	{
		if (IoUring.isAvailable())
		{
			assertThat(Transport.select("io_uring").getKind()).isEqualTo(Transport.Kind.IO_URING);
			return;
		}
		
		assumeTrue(!IoUring.isAvailable());
		assertThatThrownBy(() -> Transport.select("io_uring")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("io_uring").hasMessageContaining("not available");
	}
	
	@Test
	@DisplayName("the system property is read by select()")
	void systemProperty()
	{
		final String previous = System.getProperty(Transport.PROPERTY);
		try
		{
			System.setProperty(Transport.PROPERTY, "nio");
			assertThat(Transport.select().getKind()).isEqualTo(Transport.Kind.NIO);
		}
		finally
		{
			if (previous == null)
				System.clearProperty(Transport.PROPERTY);
			else
				System.setProperty(Transport.PROPERTY, previous);
		}
	}
}
