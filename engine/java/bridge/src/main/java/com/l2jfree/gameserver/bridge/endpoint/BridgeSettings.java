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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.l2jfree.config.L2Properties;

/**
 * Bridge listener settings. A missing file is the disabled default. Values are never logged.
 */
public final class BridgeSettings
{
	public static final int DEFAULT_PORT = 9016;
	public static final int DEFAULT_GRACE_MS = 30000;
	
	private final boolean enabled;
	private final int port;
	private final byte[] secret;
	private final int graceMs;
	
	private BridgeSettings(boolean enabled, int port, byte[] secret, int graceMs)
	{
		this.enabled = enabled;
		this.port = port;
		this.secret = secret;
		this.graceMs = graceMs;
	}
	
	/**
	 * @param directory the server configuration directory
	 * @return the settings
	 */
	public static BridgeSettings load(Path directory)
	{
		if (directory == null)
			throw new IllegalArgumentException("directory");
		
		Path file = directory.resolve("bridge.properties");
		if (!Files.isRegularFile(L2Properties.resolveFile(file.toString()).toPath()))
			return disabled();
		
		try
		{
			return parse(new L2Properties(file.toFile()));
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Bridge configuration could not be read", e);
		}
	}
	
	/**
	 * @param properties the file contents
	 * @return the settings
	 */
	public static BridgeSettings parse(Properties properties)
	{
		if (properties == null)
			throw new IllegalArgumentException("properties");
		
		boolean enabled = Boolean.parseBoolean(properties.getProperty("bridge.enabled", "false"));
		int port = parseInt(properties.getProperty("bridge.port", Integer.toString(DEFAULT_PORT)), "port");
		int grace = parseInt(properties.getProperty("bridge.grace", Integer.toString(DEFAULT_GRACE_MS)), "grace");
		String secretText = properties.getProperty("bridge.secret", "");
		if (port < 1 || port > 65535)
			throw new IllegalStateException("Bridge port is outside 1..65535");
		if (grace < 1 || grace > 300000)
			throw new IllegalStateException("Bridge grace is outside 1..300000");
		if (!enabled)
			return new BridgeSettings(false, port, new byte[0], grace);
		
		byte[] secret = decodeSecret(secretText);
		if (secret == null)
			throw new IllegalStateException("Bridge secret is not 64 hexadecimal characters");
		
		return new BridgeSettings(true, port, secret, grace);
	}
	
	/**
	 * @return settings that do not bind
	 */
	public static BridgeSettings disabled()
	{
		return new BridgeSettings(false, DEFAULT_PORT, new byte[0], DEFAULT_GRACE_MS);
	}
	
	public boolean enabled()
	{
		return enabled;
	}
	
	public int port()
	{
		return port;
	}
	
	public int graceMs()
	{
		return graceMs;
	}
	
	/**
	 * @return a copy of the 32-byte secret, or an empty array when the listener is disabled
	 */
	public byte[] secret()
	{
		return secret.clone();
	}
	
	private static int parseInt(String text, String name)
	{
		try
		{
			return Integer.parseInt(text.trim());
		}
		catch (NumberFormatException e)
		{
			throw new IllegalStateException("Bridge " + name + " is not an integer");
		}
	}
	
	private static byte[] decodeSecret(String text)
	{
		if (text == null)
			return null;
		
		String hex = text.trim();
		if (hex.length() != 64)
			return null;
		
		byte[] out = new byte[32];
		for (int i = 0; i < 32; i++)
		{
			int hi = Character.digit(hex.charAt(i * 2), 16);
			int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
			if (hi < 0 || lo < 0)
				return null;
			
			out[i] = (byte) ((hi << 4) + lo);
		}
		return out;
	}
}
