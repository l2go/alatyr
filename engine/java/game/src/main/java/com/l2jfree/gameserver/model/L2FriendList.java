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
package com.l2jfree.gameserver.model;

import java.util.Set;

import com.l2jfree.gameserver.datatables.CharNameTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.FriendListManager;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.FriendList;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * @author G1ta0
 */
public final class L2FriendList
{
	private final L2Player _owner;
	private final Set<Integer> _set;
	
	public L2FriendList(L2Player owner)
	{
		_owner = owner;
		_set = FriendListManager.getInstance().getFriendList(owner.getObjectId());
	}
	
	public boolean contains(L2Player player)
	{
		return player != null && _set.contains(player.getObjectId());
	}
	
	public Iterable<Integer> getFriendIds()
	{
		return _set;
	}
	
	public void add(L2Player friend)
	{
		if (_owner == friend)
		{
			_owner.sendPacket(SystemMessageId.YOU_CANNOT_ADD_YOURSELF_TO_YOUR_OWN_FRIENDS_LIST);
			return;
		}
		
		if (FriendListManager.getInstance().insert(_owner.getObjectId(), friend.getObjectId()))
		{
			_owner.sendPacket(SystemMessageId.YOU_HAVE_SUCCEEDED_INVITING_FRIEND);
			
			_owner.sendPacket(new SystemMessage(SystemMessageId.C1_ADDED_TO_FRIENDS).addPcName(friend));
			_owner.sendPacket(new FriendList(_owner));
			
			friend.sendPacket(new SystemMessage(SystemMessageId.C1_JOINED_AS_FRIEND).addPcName(_owner));
			friend.sendPacket(new FriendList(friend));
		}
		else
			_owner.sendPacket(new SystemMessage(SystemMessageId.C1_ALREADY_ON_LIST).addPcName(friend));
	}
	
	public void remove(String name)
	{
		Integer objId = CharNameTable.getInstance().getByName(name);
		
		if (objId != null && FriendListManager.getInstance().remove(_owner.getObjectId(), objId))
		{
			name = CharNameTable.getInstance().getByObjectId(objId);
			
			_owner.sendPacket(new SystemMessage(SystemMessageId.C1_HAS_BEEN_DELETED_FROM_YOUR_FRIENDS_LIST)
					.addString(name));
			_owner.sendPacket(new FriendList(_owner));
			
			L2Player friend = L2World.getInstance().findPlayer(objId);
			if (friend != null)
			{
				friend.sendPacket(new SystemMessage(SystemMessageId.C1_HAS_BEEN_DELETED_FROM_YOUR_FRIENDS_LIST)
						.addPcName(_owner));
				friend.sendPacket(new FriendList(friend));
			}
		}
		else
			_owner.sendPacket(new SystemMessage(SystemMessageId.C1_NOT_ON_YOUR_FRIENDS_LIST).addString(name));
	}
}
