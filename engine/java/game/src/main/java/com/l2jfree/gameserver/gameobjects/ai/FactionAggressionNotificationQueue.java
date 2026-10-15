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
package com.l2jfree.gameserver.gameobjects.ai;

import java.util.ConcurrentModificationException;
import java.util.HashMap;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.threadmanager.FIFOExecutableQueue;
import com.l2jfree.lang.L2System;
import com.l2jfree.util.L2FastSet;

/**
 * @author NB4L1
 */
public final class FactionAggressionNotificationQueue extends FIFOExecutableQueue
{
	private static final class NotificationInfo
	{
		private final L2Npc _npc;
		private final L2Creature _target;
		
		private volatile long _lastNotificationTime;
		
		private NotificationInfo(L2Npc npc, L2Creature target)
		{
			_npc = npc;
			_target = target;
		}
		
		@Override
		public boolean equals(Object obj)
		{
			if (!(obj instanceof NotificationInfo))
				return false;
			
			final NotificationInfo ni = (NotificationInfo)obj;
			
			return _npc == ni._npc && _target == ni._target;
		}
		
		@Override
		public int hashCode()
		{
			return L2System.hash((_npc.hashCode() << 16) + _target.hashCode());
		}
	}
	
	private final L2FastSet<NotificationInfo> _new = new L2FastSet<NotificationInfo>();
	private final L2FastSet<NotificationInfo> _old = new L2FastSet<NotificationInfo>();
	
	private final Object _lock = new Object();
	
	public void add(L2Npc npc, L2Creature target)
	{
		final NotificationInfo ni = new NotificationInfo(npc, target);
		
		synchronized (_lock)
		{
			purge();
			
			if (_old.contains(ni))
				return;
			
			_new.add(ni);
		}
		
		execute();
	}
	
	@Override
	protected boolean isEmpty()
	{
		synchronized (_lock)
		{
			return _new.isEmpty();
		}
	}
	
	@Override
	protected void removeAndExecuteFirst()
	{
		NotificationInfo ni;
		
		synchronized (_lock)
		{
			ni = _new.getFirst();
		}
		
		if (shouldNotify(ni._npc))
		{
			ni._npc.getAI().notifyEvent(CtrlEvent.EVT_AGGRESSION, ni._target, 1);
		}
		
		ni._lastNotificationTime = System.currentTimeMillis();
		
		synchronized (_lock)
		{
			_new.remove(ni);
			_old.remove(ni);
			_old.add(ni);
		}
	}
	
	private boolean shouldNotify(L2Npc npc)
	{
		switch (npc.getAI().getIntention())
		{
			case AI_INTENTION_IDLE:
			case AI_INTENTION_ACTIVE:
				return true;
		}
		
		return false;
	}
	
	private void purge()
	{
		synchronized (_lock)
		{
			for (NotificationInfo old; (old = _old.getFirst()) != null;)
			{
				if (old._lastNotificationTime + 1000 > System.currentTimeMillis())
					break;
				
				_old.removeFirst();
			}
		}
	}
	
	private static final HashMap<String, FactionAggressionNotificationQueue> _queues =
			new HashMap<String, FactionAggressionNotificationQueue>();
	
	public static void add(String factionId, L2Npc npc, L2Creature target)
	{
		FactionAggressionNotificationQueue queue = _queues.get(factionId);
		
		if (queue == null)
			_queues.put(factionId, queue = new FactionAggressionNotificationQueue());
		
		queue.add(npc, target);
	}
	
	public static void purgeAll()
	{
		try
		{
			for (FactionAggressionNotificationQueue queue : _queues.values())
				if (queue != null)
					queue.purge();
		}
		catch (ConcurrentModificationException e)
		{
			// skip it
		}
	}
	
	static
	{
		ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {
			@Override
			public void run()
			{
				purgeAll();
			}
		}, 60000, 60000);
	}
}
