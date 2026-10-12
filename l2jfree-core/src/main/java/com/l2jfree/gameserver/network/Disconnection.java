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

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.taskmanager.AttackStanceTaskManager;

/**
 * @author NB4L1
 */
public final class Disconnection
{
	private static final Logger _log = LoggerFactory.getLogger(Disconnection.class);
	
	private static final int STORE_ATTEMPTS = 3;
	
	/** How long the packets of a client may keep its disconnect waiting, in milliseconds. */
	private static final long QUEUE_GRACE = 30000;
	
	public static L2Client getClient(L2Client client, L2Player activeChar)
	{
		if (client != null)
			return client;
		
		if (activeChar != null)
			return activeChar.getClient();
		
		return null;
	}
	
	public static L2Player getActiveChar(L2Client client, L2Player activeChar)
	{
		if (activeChar != null)
			return activeChar;
		
		if (client != null)
			return client.getActiveChar();
		
		return null;
	}
	
	private final L2Client _client;
	private final L2Player _activeChar;
	
	public Disconnection(L2Client client)
	{
		this(client, null);
	}
	
	public Disconnection(L2Player activeChar)
	{
		this(null, activeChar);
	}
	
	public Disconnection(L2Client client, L2Player activeChar)
	{
		_client = getClient(client, activeChar);
		_activeChar = getActiveChar(client, activeChar);
		
		if (_client != null)
			_client.setActiveChar(null);
		
		if (_activeChar != null)
			_activeChar.setClient(null);
	}
	
	public Disconnection store()
	{
		try
		{
			// a rolled-back save loses everything since the last one, and nothing saves this player afterwards
			if (_activeChar != null)
			{
				boolean saved = false;
				for (int attempt = 1; attempt <= STORE_ATTEMPTS && !saved; attempt++)
				{
					saved = _activeChar.store(true, true);
					if (!saved)
						_log.warn("Saving " + _activeChar.getName() + " on disconnect failed, attempt " + attempt + " of "
								+ STORE_ATTEMPTS);
				}
				
				if (!saved)
					_log.error("Could not save " + _activeChar.getName()
							+ " on disconnect: the progress since the last save is lost.");
			}
		}
		catch (RuntimeException e)
		{
			_log.warn("", e);
		}
		
		return this;
	}
	
	public Disconnection deleteMe()
	{
		try
		{
			if (_activeChar != null)
				_activeChar.deleteMe();
		}
		catch (RuntimeException e)
		{
			_log.warn("", e);
		}
		
		return this;
	}
	
	public Disconnection close(boolean toLoginScreen)
	{
		if (_client != null)
			_client.close(toLoginScreen);
		
		return this;
	}
	
	public void defaultSequence(boolean toLoginScreen)
	{
		store();
		deleteMe();
		close(toLoginScreen);
	}
	
	/** Runs the disconnect on the client's packet queue. With no client, there is no queue to wait for. */
	static void offerToPacketQueue(L2Client client, Runnable work)
	{
		if (client == null)
		{
			work.run();
			return;
		}
		
		client.getPacketQueue().execute(work);
	}
	
	public void onDisconnection()
	{
		if (_activeChar != null)
		{
			// The save and the delete run behind the packets that the client sent before it went away, so that they
			// do not meet a packet that is still changing the inventory. A packet that hangs must not keep the
			// player unsaved: after a grace period the same work is offered to the queue again. That wakes a
			// queue that stopped, and it still runs only after the packet that is in progress, and only once.
			final AtomicBoolean done = new AtomicBoolean();
			final Runnable work = new Runnable() {
				@Override
				public void run()
				{
					if (done.compareAndSet(false, true))
					{
						store();
						deleteMe();
					}
				}
			};
			
			ThreadPoolManager.getInstance().schedule(new Runnable() {
				@Override
				public void run()
				{
					offerToPacketQueue(_client, work);
					if (_client == null)
						return;
					
					ThreadPoolManager.getInstance().schedule(new Runnable() {
						@Override
						public void run()
						{
							offerToPacketQueue(_client, work);
						}
					}, QUEUE_GRACE);
				}
			}, _activeChar.canLogout() ? 0 : AttackStanceTaskManager.COMBAT_TIME);
		}
	}
}
