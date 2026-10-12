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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.config.Deployment;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Plain HTTP health endpoint for the container runtime: {@code /health/live} answers 200 while the JVM runs,
 * {@code /health/ready} answers 200 once the server accepts players and 503 before that.
 * <p>
 * It is off unless the deployment variable {@code L2JFREE_HEALTH_PORT} is set; {@code L2JFREE_HEALTH_HOST} defaults to
 * the loopback address, so the endpoint is reachable only from inside the container.
 */
public final class HealthServer
{
	private static final Logger _log = LoggerFactory.getLogger(HealthServer.class);
	
	private final HttpServer _server;
	
	private HealthServer(HttpServer server)
	{
		_server = server;
	}
	
	public static HealthServer start(InetSocketAddress address, Readiness readiness) throws IOException
	{
		final HttpServer server = HttpServer.create(address, 0);
		server.createContext("/", exchange -> handle(exchange, readiness));
		server.start();
		return new HealthServer(server);
	}
	
	/** Starts the endpoint when {@code L2JFREE_HEALTH_PORT} is set; returns {@code null} otherwise. */
	public static HealthServer startIfConfigured(Readiness readiness)
	{
		final String port = Deployment.value("HEALTH_PORT", "").trim();
		if (port.isEmpty() || port.equals("0"))
			return null;
		
		final String host = Deployment.value("HEALTH_HOST", "127.0.0.1");
		try
		{
			final HealthServer server = start(new InetSocketAddress(host, Integer.parseInt(port)), readiness);
			_log.info("Health endpoint on " + host + ":" + server.port());
			return server;
		}
		catch (IOException | RuntimeException e)
		{
			throw new IllegalStateException("Cannot start the health endpoint on " + host + ":" + port, e);
		}
	}
	
	public int port()
	{
		return _server.getAddress().getPort();
	}
	
	public void stop()
	{
		_server.stop(0);
	}
	
	private static void handle(HttpExchange exchange, Readiness readiness) throws IOException
	{
		try (exchange)
		{
			final String path = exchange.getRequestURI().getPath();
			if (path.equals("/health/live"))
				respond(exchange, 200, "{\"server\":\"" + readiness.server() + "\",\"live\":true}");
			else if (path.equals("/health/ready"))
			{
				final long startupMillis = readiness.startupMillis();
				if (startupMillis < 0)
					respond(exchange, 503, "{\"server\":\"" + readiness.server() + "\",\"ready\":false}");
				else
					respond(exchange, 200, "{\"server\":\"" + readiness.server() + "\",\"ready\":true,\"startupMillis\":"
							+ startupMillis + "}");
			}
			else
				respond(exchange, 404, "{}");
		}
	}
	
	private static void respond(HttpExchange exchange, int status, String body) throws IOException
	{
		final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody())
		{
			out.write(bytes);
		}
	}
}
