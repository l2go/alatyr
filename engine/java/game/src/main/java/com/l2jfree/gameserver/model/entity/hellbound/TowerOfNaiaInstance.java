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
package com.l2jfree.gameserver.model.entity.hellbound;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.hellbound.TowerOfNaiaManager;
import com.l2jfree.gameserver.model.entity.Instance;
import com.l2jfree.gameserver.model.world.L2World;

/**
 * @author hex1r0
 */
public final class TowerOfNaiaInstance extends Instance
{
	public static final class NaiaFactory implements InstanceFactory
	{
		@Override
		public Instance createInstance(int instanceId, String template)
		{
			return new TowerOfNaiaInstance(instanceId, template);
		}
		
		@Override
		public String[] getInstanceTemplates()
		{
			return new String[] { "TowerOfNaiaRoom.xml" };
		}
	}
	
	private int _currentRoomId = -1;
	private boolean _isInstanceStarted = false;
	
	public TowerOfNaiaInstance(int id, String template)
	{
		super(id, template);
	}
	
	public final int getCurrentRoomId()
	{
		return _currentRoomId;
	}
	
	public void prepareRoom()
	{
		if (_currentRoomId >= TowerOfNaiaManager.ROOMS.size() - 1)
			return;
		
		_currentRoomId++;
		TowerOfNaiaManager.ROOMS.get(_currentRoomId).prepare(getId());
	}
	
	public void startRoomInvasion()
	{
		if (_isInstanceStarted)
			return;
		
		_isInstanceStarted = true;
		for (L2Npc npc : getNpcs())
		{
			if (npc == null)
				continue;
			
			removeNpc(npc);
			npc.deleteMe();
		}
		TowerOfNaiaManager.ROOMS.get(_currentRoomId).start(getId());
		setDuration(Config.ALT_NAIA_ROOM_DURATION * 60000);
	}
	
	public void finishRoomInvasion()
	{
		setDuration(-1);
		TowerOfNaiaManager.ROOMS.get(_currentRoomId).finish(getId());
		
		if (_currentRoomId == TowerOfNaiaManager.ROOMS.size() - 1)
		{
			for (Integer objectId : getPlayers())
			{
				L2Player player = L2World.getInstance().findPlayer(objectId);
				if (player != null)
					player.setInstanceId(0);
			}
			TowerOfNaiaManager.getInstance().finishInstance(getId());
			TowerOfNaiaManager.ROOMS.get(_currentRoomId).finish(0);
		}
		else
			prepareRoom();
		
		_isInstanceStarted = false;
	}
	
	public boolean areAllNpcsKilled()
	{
		for (L2Npc npc : getNpcs())
		{
			if (npc == null || npc.isDead() || npc.isDecayed())
				continue;
			else
				return false;
		}
		
		TowerOfNaiaManager.getInstance().finishRoomInvasion(getId());
		return true;
	}
	
	@Override
	protected void scheduleCheckTimeUp(final int remaining, final int interval)
	{
		if (!areAllNpcsKilled())
		{
			super.scheduleCheckTimeUp(remaining, interval);
		}
	}
}
