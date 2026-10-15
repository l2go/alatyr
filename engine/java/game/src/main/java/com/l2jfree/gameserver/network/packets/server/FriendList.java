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
package com.l2jfree.gameserver.network.packets.server;

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.gameserver.datatables.CharNameTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * @author Tempy
 */
public final class FriendList extends L2ServerPacket
{
	private static final String _S__FA_FRIENDLIST = "[S] 75 FriendList";
	
	private final List<FriendStatus> _friends = new ArrayList<FriendStatus>();
	
	public FriendList(L2Player owner)
	{
		for (Integer objId : owner.getFriendList().getFriendIds())
			_friends.add(new FriendStatus(objId));
	}
	
	private static final class FriendStatus
	{
		private final int _objId;
		private final String _name;
		private final boolean _online;
		
		private FriendStatus(int objId)
		{
			_objId = objId;
			_name = CharNameTable.getInstance().getByObjectId(objId);
			_online = L2World.getInstance().findPlayer(objId) != null;
		}
		
		private int getObjId()
		{
			return _objId;
		}
		
		private String getName()
		{
			return _name;
		}
		
		private boolean isOnline()
		{
			return _online;
		}
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0x75);
		writeD(_friends.size());
		
		for (FriendStatus fs : _friends)
		{
			writeD(0x00030b7a); // character id
			writeS(fs.getName());
			writeD(fs.isOnline() ? 0x01 : 0x00); // online
			writeD(fs.isOnline() ? fs.getObjId() : 0x00); // object id if online
		}
	}
	
	@Override
	public String getType()
	{
		return _S__FA_FRIENDLIST;
	}
}
