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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.LoginLink;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.bridge.BridgeFailure;

/**
 * The one full disconnect of a bridge client. It runs on that client's queue, saves once, and does not call back into
 * {@link Disconnection}. Ordinary clients never reach it.
 */
final class DisconnectionExecutor
{
	private static final Logger _log = LoggerFactory.getLogger(DisconnectionExecutor.class);
	
	private DisconnectionExecutor()
	{
	}
	
	static void request(L2Client client)
	{
		if (client == null || !client.beginBridgeDisconnect())
			return;
		
		client.submit(new Runnable() {
			@Override
			public void run()
			{
				full(client);
			}
		});
	}
	
	static void full(L2Client client)
	{
		final L2Player player = client.capturedBridgePlayer();
		final String account = client.capturedBridgeAccount();
		boolean saved = player == null;
		
		if (player != null)
		{
			try
			{
				player.detachBridge(client);
			}
			catch (RuntimeException e)
			{
				_log.warn("A bridge disconnect step failed", e);
			}
			
			try
			{
				saved = player.store(true, true);
			}
			catch (RuntimeException e)
			{
				saved = false;
				_log.warn("Saving a bridge character failed", e);
			}
		}
		
		final BridgeFailure failure = saved && !client.bridgeInternalFailure()
				? BridgeFailure.normal() : BridgeFailure.internal();
		
		if (player != null)
		{
			try
			{
				player.deleteMe();
			}
			catch (RuntimeException e)
			{
				_log.warn("A bridge disconnect step failed", e);
			}
		}
		
		// The sink hears the outcome while the channel can still carry it.
		try
		{
			client.sink().disconnect(failure);
		}
		catch (RuntimeException e)
		{
			_log.warn("A bridge disconnect step failed", e);
		}
		
		try
		{
			client.closeBridgeTransport();
		}
		catch (RuntimeException e)
		{
			_log.warn("A bridge disconnect step failed", e);
		}
		
		try
		{
			if (account != null && !account.isEmpty())
				LoginLink.getInstance().leaveTrusted(account, client);
		}
		catch (RuntimeException e)
		{
			_log.warn("A bridge disconnect step failed", e);
		}
		
		client.markBridgeDisconnected();
	}
}
