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
package com.l2jfree.gameserver.model.entity.events;

import java.util.concurrent.ScheduledFuture;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.model.restriction.global.AbstractRestriction;

/**
 * @author NB4L1
 */
public abstract class AbstractFunEvent
{
	public enum FunEventState
	{
		INACTIVE,
		REGISTRATION,
		PREPARATION,
		RUNNING,
		COOLDOWN;
	}
	
	private final AbstractRestriction[] _restrictions = new AbstractRestriction[FunEventState.values().length];
	private final FunEventTask _task;
	
	private FunEventState _state = FunEventState.INACTIVE;
	
	protected AbstractFunEvent()
	{
		for (FunEventState state : FunEventState.values())
			_restrictions[state.ordinal()] = initRestriction(state);
		
		_task = initTask();
	}
	
	/**
	 * Called only from the constructor of {@link AbstractFunEvent} to initialize restrictions<br>
	 * that will be activated/de-actived as state changes.
	 * 
	 * @param state
	 * @return the proper restriction, can be null
	 */
	protected abstract AbstractRestriction initRestriction(FunEventState state);
	
	/**
	 * Called only from the constructor of {@link AbstractFunEvent} to initialize the task<br>
	 * that can be scheduled later..
	 * 
	 * @param state
	 * @return the proper restriction, can be null
	 */
	protected abstract FunEventTask initTask();
	
	/**
	 * Loads the event parameters, if required.
	 */
	public synchronized void load()
	{
	}
	
	/**
	 * @return the current state
	 */
	public FunEventState getState()
	{
		return _state;
	}
	
	/**
	 * Sets the state of the funevent.
	 * 
	 * @param nextState
	 * @return the previous state
	 */
	protected synchronized final FunEventState setState(FunEventState nextState)
	{
		final FunEventState prevState = getState();
		
		setState(prevState, nextState);
		
		return prevState;
	}
	
	/**
	 * Sets the state of the funevent with validation.
	 * 
	 * @param expectedPrevState
	 * @param nextState
	 * @throws IllegalStateException if the previous and the expected previous state is different
	 */
	protected synchronized void setState(FunEventState expectedPrevState, FunEventState nextState)
			throws IllegalStateException
	{
		final FunEventState prevState = getState();
		
		if (expectedPrevState != prevState)
			throw new IllegalStateException();
		
		_state = nextState;
		
		for (AbstractRestriction restriction : _restrictions)
			if (restriction != null)
				restriction.deactivate();
		
		if (_restrictions[nextState.ordinal()] != null)
			_restrictions[nextState.ordinal()].activate();
		
		switch (nextState)
		{
			case INACTIVE:
			{
				break;
			}
			case REGISTRATION:
			{
				break;
			}
			case PREPARATION:
			{
				break;
			}
			case RUNNING:
			{
				break;
			}
			case COOLDOWN:
			{
				break;
			}
		}
	}
	
	protected FunEventTask getTask()
	{
		return _task;
	}
	
	protected abstract class FunEventTask implements Runnable
	{
		/**
		 * The scheduled event task.
		 */
		private ScheduledFuture<?> _future;
		
		protected FunEventTask()
		{
		}
		
		/**
		 * Schedules this event.
		 * 
		 * @param delay the time to schedule the event with
		 */
		protected void schedule(long delay)
		{
			synchronized (AbstractFunEvent.this)
			{
				cancel();
				
				_future = ThreadPoolManager.getInstance().schedule(this, delay);
			}
			
		}
		
		/**
		 * Cancels the currently running task.
		 */
		protected void cancel()
		{
			synchronized (AbstractFunEvent.this)
			{
				if (_future != null)
				{
					_future.cancel(true);
					_future = null;
				}
			}
		}
		
		@Override
		public void run()
		{
			synchronized (AbstractFunEvent.this)
			{
				long duration = onTimeElapsed();
				
				if (duration < 0)
					cancel();
				else
					schedule(duration);
			}
		}
		
		/**
		 * Called by the default Runnable object initialized by calling initRunnable at the constuctor.<br>
		 * Handles the proper scheduled actions.
		 * 
		 * @return the delay with the next task should be scheduled with. -1 if no more scheduling required.
		 */
		protected long onTimeElapsed()
		{
			synchronized (AbstractFunEvent.this)
			{
				switch (getState())
				{
					case INACTIVE:
					{
						return -1;
					}
					case REGISTRATION:
					{
						return -1;
					}
					case PREPARATION:
					{
						return -1;
					}
					case RUNNING:
					{
						return -1;
					}
					case COOLDOWN:
					{
						return -1;
					}
					default:
					{
						throw new InternalError();
					}
				}
			}
		}
	}
}
