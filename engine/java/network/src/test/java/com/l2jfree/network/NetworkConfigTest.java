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

import java.nio.ByteOrder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NetworkConfigTest
{
	@Test
	@DisplayName("the defaults are the documented ones")
	void defaults()
	{
		final NetworkConfig config = new NetworkConfig();
		
		assertThat(config.getMaxFrameSize()).isEqualTo(0xFFFF);
		assertThat(config.getWriteHighWaterMark()).isEqualTo(1024 * 1024);
		assertThat(config.getByteOrder()).isEqualTo(ByteOrder.LITTLE_ENDIAN);
		assertThat(config.getIoThreads()).isEqualTo(NetworkConfig.defaultIoThreads());
	}
	
	@Test
	@DisplayName("the automatic number of I/O threads is half of the processors, between 1 and 4")
	void automaticIoThreads()
	{
		final int expected = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
		
		assertThat(NetworkConfig.defaultIoThreads()).isEqualTo(expected).isBetween(1, 4);
	}
	
	@Test
	@DisplayName("a number of I/O threads set explicitly is used, 0 means automatic")
	void explicitIoThreads()
	{
		assertThat(new NetworkConfig().setIoThreads(3).getIoThreads()).isEqualTo(3);
		assertThat(new NetworkConfig().setIoThreads(0).getIoThreads()).isEqualTo(NetworkConfig.defaultIoThreads());
	}
	
	@Test
	@DisplayName("accept limits that cannot work are refused, and good ones are taken")
	void acceptLimits()
	{
		assertThat(new NetworkConfig().setAcceptLimits(5, 5, 1, 100, 1000, 3600)).isNotNull();
		
		assertThatThrownBy(() -> new NetworkConfig().setAcceptLimits(0, 20, 10, 30, 60, 60))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new NetworkConfig().setAcceptLimits(10, 5, 10, 30, 60, 60))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new NetworkConfig().setAcceptLimits(10, 20, 0, 30, 60, 60))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new NetworkConfig().setAcceptLimits(10, 20, 10, 30, 60, 3601))
				.isInstanceOf(IllegalArgumentException.class);
	}
	
	@Test
	@DisplayName("values that cannot work are refused")
	void validation()
	{
		assertThatThrownBy(() -> new NetworkConfig().setIoThreads(-1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new NetworkConfig().setMaxFrameSize(2)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new NetworkConfig().setMaxFrameSize(0x10000)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new NetworkConfig().setWriteHighWaterMark(10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new NetworkConfig().setByteOrder(null)).isInstanceOf(IllegalArgumentException.class);
	}
}
