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
package com.l2jfree.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

class HealthServerTest
{
	@Test
	void readinessTurnsGreenOnceTheServerAcceptsPlayers() throws Exception
	{
		Readiness readiness = new Readiness("test", 1_000L);
		HealthServer server = HealthServer.start(new InetSocketAddress("127.0.0.1", 0), readiness);
		try
		{
			HttpClient client = HttpClient.newHttpClient();
			URI base = URI.create("http://127.0.0.1:" + server.port());
			
			assertThat(get(client, base.resolve("/health/live")).statusCode()).isEqualTo(200);
			HttpResponse<String> notReady = get(client, base.resolve("/health/ready"));
			assertThat(notReady.statusCode()).isEqualTo(503);
			assertThat(notReady.body()).isEqualTo("{\"server\":\"test\",\"ready\":false}");
			
			assertThat(readiness.markReady(4_250L)).isEqualTo(3_250L);
			
			HttpResponse<String> ready = get(client, base.resolve("/health/ready"));
			assertThat(ready.statusCode()).isEqualTo(200);
			assertThat(ready.body()).isEqualTo("{\"server\":\"test\",\"ready\":true,\"startupMillis\":3250}");
			assertThat(get(client, base.resolve("/other")).statusCode()).isEqualTo(404);
		}
		finally
		{
			server.stop();
		}
	}
	
	@Test
	void secondMarkKeepsTheFirstStartupTime()
	{
		Readiness readiness = new Readiness("test", 0L);
		readiness.markReady(10L);
		readiness.markReady(99L);
		
		assertThat(readiness.startupMillis()).isEqualTo(10L);
	}
	
	private static HttpResponse<String> get(HttpClient client, URI uri) throws Exception
	{
		return client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
	}
}
