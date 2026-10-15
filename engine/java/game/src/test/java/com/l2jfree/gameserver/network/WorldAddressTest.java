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
package com.l2jfree.gameserver.network;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldAddressTest
{
	@Test
	@DisplayName("a client inside a subnet gets the host of that subnet")
	void subnetMatchReturnsItsHost()
	{
		WorldAddress address = new WorldAddress("10.1.1.1,10.0.0.0/255.0.0.0;203.0.113.7,0.0.0.0/0;");

		assertThat(address.addressFor("10.2.3.4")).isEqualTo("10.1.1.1");
		assertThat(address.addressFor("198.51.100.9")).isEqualTo("203.0.113.7");
	}

	@Test
	@DisplayName("a client outside every subnet gets its own address back")
	void noMatchReturnsTheClientAddress()
	{
		WorldAddress address = new WorldAddress("10.1.1.1,10.0.0.0/255.0.0.0");

		assertThat(address.addressFor("198.51.100.9")).isEqualTo("198.51.100.9");
	}

	@Test
	@DisplayName("a host without subnets matches every client")
	void hostWithoutSubnetsMatchesEveryone()
	{
		WorldAddress address = new WorldAddress("203.0.113.7");

		assertThat(address.addressFor("10.2.3.4")).isEqualTo("203.0.113.7");
		assertThat(address.addressFor("198.51.100.9")).isEqualTo("203.0.113.7");
	}

	@Test
	@DisplayName("the first matching host wins")
	void firstMatchWins()
	{
		WorldAddress address = new WorldAddress("10.1.1.1,10.0.0.0/255.0.0.0;203.0.113.7");

		assertThat(address.addressFor("10.9.9.9")).isEqualTo("10.1.1.1");
	}

	@Test
	@DisplayName("an empty configuration announces the client's own address")
	void emptyConfigurationReturnsTheClientAddress()
	{
		assertThat(new WorldAddress("").addressFor("198.51.100.9")).isEqualTo("198.51.100.9");
		assertThat(new WorldAddress(null).addressFor("198.51.100.9")).isEqualTo("198.51.100.9");
	}

	@Test
	@DisplayName("without subnets the internal host serves private networks and the external host the rest")
	void netConfigFromHostnames()
	{
		String netConfig = WorldAddress.netConfig("203.0.113.7", "192.168.1.5", "");
		WorldAddress address = new WorldAddress(netConfig);

		assertThat(address.addressFor("192.168.4.4")).isEqualTo("192.168.1.5");
		assertThat(address.addressFor("10.4.4.4")).isEqualTo("192.168.1.5");
		assertThat(address.addressFor("198.51.100.9")).isEqualTo("203.0.113.7");
	}

	@Test
	@DisplayName("a subnet list replaces both hostnames")
	void subnetListReplacesHostnames()
	{
		String subnets = "10.1.1.1,10.0.0.0/255.0.0.0;203.0.113.7,0.0.0.0/0";

		assertThat(WorldAddress.netConfig("198.51.100.1", "192.168.1.5", subnets)).isEqualTo(subnets);
	}

	@Test
	@DisplayName("with an update interval the host names are resolved again when it has passed")
	void namesAreResolvedAgainAfterTheInterval()
	{
		java.util.Map<String, String> dns = new java.util.HashMap<String, String>();
		dns.put("world.example", "203.0.113.7");
		long[] now = { 1_000 };
		WorldAddress address = new WorldAddress("world.example,0.0.0.0/0", dns::get, 60_000, () -> now[0]);
		assertThat(address.addressFor("198.51.100.9")).isEqualTo("203.0.113.7");

		dns.put("world.example", "203.0.113.8");
		now[0] += 59_000;
		assertThat(address.addressFor("198.51.100.9")).isEqualTo("203.0.113.7"); // not due yet

		now[0] += 2_000;
		assertThat(address.addressFor("198.51.100.9")).isEqualTo("203.0.113.8"); // due
	}

	@Test
	@DisplayName("without an update interval the names are resolved once")
	void namesAreResolvedOnceWithoutAnInterval()
	{
		java.util.Map<String, String> dns = new java.util.HashMap<String, String>();
		dns.put("world.example", "203.0.113.7");
		long[] now = { 1_000 };
		WorldAddress address = new WorldAddress("world.example,0.0.0.0/0", dns::get, 0, () -> now[0]);

		dns.put("world.example", "203.0.113.8");
		now[0] += 10_000_000;

		assertThat(address.addressFor("198.51.100.9")).isEqualTo("203.0.113.7");
	}

	@Test
	@DisplayName("a name that stops resolving keeps the last address")
	void aNameThatStopsResolvingKeepsTheLastAddress()
	{
		java.util.Map<String, String> dns = new java.util.HashMap<String, String>();
		dns.put("world.example", "203.0.113.7");
		long[] now = { 1_000 };
		WorldAddress address = new WorldAddress("world.example,0.0.0.0/0", dns::get, 1_000, () -> now[0]);

		dns.remove("world.example");
		now[0] += 5_000;

		assertThat(address.addressFor("198.51.100.9")).isEqualTo("203.0.113.7");
	}
}
