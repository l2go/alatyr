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
package com.l2jfree.gameserver.taskmanager;

import java.util.ArrayList;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.ai.CtrlEvent;
import com.l2jfree.gameserver.instancemanager.GameTimeManager;
import com.l2jfree.gameserver.threadmanager.FIFOSimpleExecutableQueue;
import com.l2jfree.util.L2Collections;
import com.l2jfree.util.L2FastSet;
import com.l2jfree.util.concurrent.RunnableStatsManager;

/**
 * @author NB4L1
 */
public final class MovementController extends AbstractPeriodicTaskManager
{
	private static final class SingletonHolder
	{
		private static final MovementController INSTANCE = new MovementController();
	}
	
	public static MovementController getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private final L2FastSet<L2Creature> _movingChars = new L2FastSet<L2Creature>().setShared(true);
	
	private final EvtArrivedManager _evtArrivedManager = new EvtArrivedManager();
	private final EvtArrivedRevalidateManager _evtArrivedRevalidateManager = new EvtArrivedRevalidateManager();
	
	private MovementController()
	{
		super(GameTimeManager.MILLIS_IN_TICK);
	}
	
	public void add(L2Creature cha, int ticks)
	{
		_movingChars.add(cha);
	}
	
	public void remove(L2Creature cha)
	{
		_movingChars.remove(cha);
		_evtArrivedManager.remove(cha);
		_evtArrivedRevalidateManager.remove(cha);
	}
	
	@Override
	public void run()
	{
		final ArrayList<L2Creature> arrivedChars = L2Collections.newArrayList();
		final ArrayList<L2Creature> followers = L2Collections.newArrayList();
		
		for (L2Creature cha : _movingChars)
		{
			boolean arrived = cha.updatePosition(GameTimeManager.getGameTicks());
			
			// normal movement to an exact coordinate
			if (cha.getAI().getFollowTarget() == null)
			{
				if (arrived)
					arrivedChars.add(cha);
			}
			// following a target
			else
			{
				followers.add(cha);
			}
		}
		
		// the followed chars must move before checking for acting radius
		for (L2Creature follower : followers)
		{
			// we have reached our target
			if (follower.getAI().isInsideActingRadius())
				arrivedChars.add(follower);
		}
		
		_movingChars.removeAll(arrivedChars);
		followers.removeAll(arrivedChars);
		
		_evtArrivedManager.executeAll(arrivedChars);
		_evtArrivedRevalidateManager.executeAll(followers);
		
		L2Collections.recycle(arrivedChars);
		L2Collections.recycle(followers);
	}
	
	private final class EvtArrivedManager extends FIFOSimpleExecutableQueue<L2Creature>
	{
		@Override
		protected void removeAndExecuteFirst()
		{
			final L2Creature cha = removeFirst();
			final long begin = System.nanoTime();
			
			try
			{
				cha.getKnownList().updateKnownObjects();
				
				if (cha.hasAI())
					cha.getAI().notifyEvent(CtrlEvent.EVT_ARRIVED);
			}
			catch (RuntimeException | Error e)
			{
				_log.warn("", e);
			}
			finally
			{
				RunnableStatsManager.handleStats(cha.getClass(), "notifyEvent(CtrlEvent.EVT_ARRIVED)",
						System.nanoTime() - begin);
			}
		}
	}
	
	private final class EvtArrivedRevalidateManager extends FIFOSimpleExecutableQueue<L2Creature>
	{
		@Override
		protected void removeAndExecuteFirst()
		{
			final L2Creature cha = removeFirst();
			final long begin = System.nanoTime();
			
			try
			{
				if (cha.hasAI())
					cha.getAI().notifyEvent(CtrlEvent.EVT_ARRIVED_REVALIDATE);
			}
			catch (RuntimeException | Error e)
			{
				_log.warn("", e);
			}
			finally
			{
				RunnableStatsManager.handleStats(cha.getClass(), "notifyEvent(CtrlEvent.EVT_ARRIVED_REVALIDATE)",
						System.nanoTime() - begin);
			}
		}
	}
}
