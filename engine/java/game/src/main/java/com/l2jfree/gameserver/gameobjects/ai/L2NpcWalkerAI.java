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

import java.util.ArrayList;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.NpcWalkerRoutesTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.instance.L2NpcWalkerInstance;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.L2NpcWalkerNode;
import com.l2jfree.gameserver.taskmanager.AbstractIterativePeriodicTaskManager;

public class L2NpcWalkerAI extends L2CreatureAI implements Runnable
{
	private static final class NpcWalkerAiTaskManager extends AbstractIterativePeriodicTaskManager<L2NpcWalkerAI>
	{
		private static final NpcWalkerAiTaskManager _instance = new NpcWalkerAiTaskManager();
		
		private static NpcWalkerAiTaskManager getInstance()
		{
			return _instance;
		}
		
		private NpcWalkerAiTaskManager()
		{
			super(1000);
		}
		
		@Override
		protected void callTask(L2NpcWalkerAI task)
		{
			task.run();
		}
		
		@Override
		protected String getCalledMethodName()
		{
			return "run()";
		}
	}
	
	private static final int DEFAULT_MOVE_DELAY = 0;
	
	private long _nextMoveTime;
	
	private boolean _walkingToNextPoint = false;
	
	/**
	 * home points for xyz
	 */
	//private int _homeX, _homeY, _homeZ;
	
	/**
	 * route of the current npc
	 */
	private final L2NpcWalkerNode[] _route;
	
	/**
	 * current node
	 */
	private int _currentPos;
	
	/**
	 * Constructor of L2CreatureAI.<BR>
	 * <BR>
	 * 
	 * @param accessor The AI accessor of the L2Creature
	 */
	public L2NpcWalkerAI(L2Creature.AIAccessor accessor)
	{
		super(accessor);
		
		if (!Config.ALLOW_NPC_WALKERS)
		{
			_route = null;
			return;
		}
		
		ArrayList<L2NpcWalkerNode> route = NpcWalkerRoutesTable.getInstance().getRouteForNpc(getActor().getNpcId());
		
		_route = route.toArray(new L2NpcWalkerNode[route.size()]);
		
		if (_route.length == 0)
		{
			_log.warn("L2NpcWalker(ID: " + getActor().getNpcId() + ") without defined route!");
			return;
		}
		
		NpcWalkerAiTaskManager.getInstance().startTask(this);
	}
	
	private L2NpcWalkerNode getCurrentNode()
	{
		return _route[_currentPos];
	}
	
	@Override
	public void run()
	{
		notifyEvent(CtrlEvent.EVT_THINK);
	}
	
	@Override
	protected void onEvtThink()
	{
		if (!Config.ALLOW_NPC_WALKERS || _route.length == 0 || getActor().getKnownList().getKnownPlayers().isEmpty())
			return;
		
		if (isWalkingToNextPoint())
		{
			checkArrived();
			return;
		}
		
		if (_nextMoveTime < System.currentTimeMillis())
			walkToLocation();
	}
	
	/**
	 * If npc can't walk to it's target then just teleport to next point
	 * 
	 * @param blocked_at_pos ignoring it
	 */
	@Override
	protected void onEvtArrivedBlocked(L2CharPosition blocked_at_pos)
	{
		_log.warn("NpcWalker ID: " + getActor().getNpcId() + ": Blocked at rote position [" + _currentPos
				+ "], coords: " + blocked_at_pos.x + ", " + blocked_at_pos.y + ", " + blocked_at_pos.z
				+ ". Teleporting to next point");
		
		L2NpcWalkerNode node = getCurrentNode();
		
		int destinationX = node.getMoveX();
		int destinationY = node.getMoveY();
		int destinationZ = node.getMoveZ();
		
		getActor().teleToLocation(destinationX, destinationY, destinationZ, false);
		super.onEvtArrivedBlocked(blocked_at_pos);
	}
	
	private void checkArrived()
	{
		L2NpcWalkerNode node = getCurrentNode();
		
		int destX = node.getMoveX();
		int destY = node.getMoveY();
		int destZ = node.getMoveZ();
		
		if (getActor().getX() == destX && getActor().getY() == destY && getActor().getZ() == destZ)
		{
			String chat = node.getChatText();
			if (chat != null && !chat.isEmpty())
			{
				getActor().broadcastChat(chat);
			}
			
			//time in millis
			long delay = node.getDelay() * 1000;
			
			if (delay < 0)
			{
				_log.warn("L2NpcWalkerAI: negative delay(" + delay + "), using default instead.");
				delay = DEFAULT_MOVE_DELAY;
			}
			
			_nextMoveTime = System.currentTimeMillis() + delay;
			
			setWalkingToNextPoint(false);
		}
	}
	
	private void walkToLocation()
	{
		_currentPos = (_currentPos + 1) % _route.length;
		
		L2NpcWalkerNode node = getCurrentNode();
		
		if (node.getRunning())
			getActor().setRunning();
		else
			getActor().setWalking();
		
		//now we define destination
		int destX = node.getMoveX();
		int destY = node.getMoveY();
		int destZ = node.getMoveZ();
		
		//notify AI of MOVE_TO
		setWalkingToNextPoint(true);
		
		setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, new L2CharPosition(destX, destY, destZ, 0));
	}
	
	@Override
	public L2NpcWalkerInstance getActor()
	{
		return (L2NpcWalkerInstance)_actor;
	}
	
	/*public int getHomeX()
	{
		return _homeX;
	}
	
	public int getHomeY()
	{
		return _homeY;
	}
	
	public int getHomeZ()
	{
		return _homeZ;
	}
	
	public void setHomeX(int homeX)
	{
		_homeX = homeX;
	}
	
	public void setHomeY(int homeY)
	{
		_homeY = homeY;
	}
	
	public void setHomeZ(int homeZ)
	{
		_homeZ = homeZ;
	}*/
	
	public boolean isWalkingToNextPoint()
	{
		return _walkingToNextPoint;
	}
	
	public void setWalkingToNextPoint(boolean value)
	{
		_walkingToNextPoint = value;
	}
}
