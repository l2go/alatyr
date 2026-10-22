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
package com.l2jfree.gameserver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.extension.GameServerContext;
import com.l2jfree.gameserver.extension.GameServerExtension;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.L2ClientSelectorThread;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.network.NetworkServer;

/**
 * Loads {@link GameServerExtension} providers once. With none on the classpath, {@link #start()} and {@link #close()}
 * change nothing.
 */
final class GameServerExtensions
{
	private static final Logger _log = LoggerFactory.getLogger(GameServerExtensions.class);
	
	private static final List<GameServerExtension> _started = new ArrayList<GameServerExtension>();
	
	private GameServerExtensions()
	{
	}
	
	static void start()
	{
		GameServerContext context = null;
		GameServerExtension current = null;
		try
		{
			for (GameServerExtension extension : ServiceLoader.load(GameServerExtension.class))
			{
				if (context == null)
					context = new BoundContext();
				
				current = extension;
				extension.start(context);
				_started.add(extension);
				current = null;
			}
		}
		catch (RuntimeException | Error e)
		{
			if (current != null)
			{
				try
				{
					current.close();
				}
				catch (Exception closeError)
				{
					e.addSuppressed(closeError);
				}
			}
			close();
			throw e;
		}
	}
	
	/** Closes what {@link #start()} actually started, last one first. Safe to call again. */
	static void close()
	{
		for (int i = _started.size() - 1; i >= 0; i--)
		{
			try
			{
				_started.get(i).close();
			}
			catch (Exception e)
			{
				_log.warn("An extension failed to close", e);
			}
		}
		_started.clear();
	}
	
	private static final class BoundContext implements GameServerContext
	{
		@Override
		public NetworkServer<L2Client, L2ClientPacket, L2ServerPacket> getNetworkServer()
		{
			return L2ClientSelectorThread.getInstance();
		}
		
		@Override
		public Path getConfigurationDirectory()
		{
			return Path.of("config");
		}
		
		@Override
		public LoginLink getLoginLink()
		{
			return LoginLink.getInstance();
		}
	}
}
