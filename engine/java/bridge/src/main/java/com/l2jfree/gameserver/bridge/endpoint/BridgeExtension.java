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

import com.l2jfree.gameserver.extension.GameServerContext;
import com.l2jfree.gameserver.extension.GameServerExtension;

/**
 * The bridge listener. Disabled settings do not bind a port. {@link #close()} may run more than once.
 */
public final class BridgeExtension implements GameServerExtension
{
	private BridgeLoopback loopback;
	
	@Override
	public void start(GameServerContext context)
	{
		if (context == null)
			throw new IllegalArgumentException("context");
		
		BridgeSettings settings = BridgeSettings.load(context.getConfigurationDirectory());
		if (!settings.enabled())
			return;
		
		loopback = BridgeLoopback.open(settings);
	}
	
	@Override
	public void close()
	{
		BridgeLoopback current = loopback;
		loopback = null;
		if (current != null)
			current.close();
	}
}
