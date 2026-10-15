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
package com.l2jfree.gameserver.instancemanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.party.L2PartyRoom;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ExClosePartyRoom;

/**
 * Party room and party matching waiting list manager.
 * @author Myzreal
 * @author savormix
 */
public class PartyRoomManager
{
	public static final int ENTRIES_PER_PAGE = 64;
	
	// every logout and many packets use these on different threads: concurrent collections, ordered like before
	private final AtomicInteger _nextId = new AtomicInteger(1);
	private final Set<L2Player> _waitingList;
	private final Map<Integer, L2PartyRoom> _rooms;
	
	public PartyRoomManager()
	{
		_waitingList = new CopyOnWriteArraySet<L2Player>();
		_rooms = new ConcurrentSkipListMap<Integer, L2PartyRoom>();
	}
	
	public static final PartyRoomManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final Set<L2Player> getWaitingList()
	{
		return _waitingList;
	}
	
	private final Map<Integer, L2PartyRoom> getPartyRooms()
	{
		return _rooms;
	}
	
	public final L2PartyRoom getPartyRoom(int roomId)
	{
		return getPartyRooms().get(roomId);
	}
	
	public void addToWaitingList(L2Player player)
	{
		if (getWaitingList().add(player))
		{
			player.setLookingForParty(true);
			player.broadcastUserInfo();
		}
	}
	
	public void removeFromWaitingList(L2Player player)
	{
		getWaitingList().remove(player);
		player.setLookingForParty(false);
		player.broadcastUserInfo();
	}
	
	public List<L2Player> getWaitingList(int minLevel, int maxLevel)
	{
		ArrayList<L2Player> list = new ArrayList<L2Player>();
		for (L2Player pc : getWaitingList())
			if (pc.getLevel() >= minLevel && pc.getLevel() <= maxLevel)
				list.add(pc);
		return list;
	}
	
	/**
	 * Creates a party room without checking conditions. Removes leader from waiting list
	 * and adds him to the party room.
	 * @param leader Party room's leader
	 * @param minLevel Minimum level to join
	 * @param maxLevel Maximum level to join
	 * @param maxMembers Maximum member count
	 * @param lootDist Loot distribution type
	 * @param title Party room's title
	 * @return the newly created party room with leader as the only member
	 */
	public void createRoom(L2Player leader, int minLevel, int maxLevel, int maxMembers, int lootDist, String title)
	{
		L2PartyRoom room = new L2PartyRoom(_nextId.getAndIncrement(), minLevel, maxLevel, maxMembers, lootDist, title);
		room.addMember(leader);
		leader.setLookingForParty(true);
		leader.broadcastUserInfo();
		room.setParty(leader.getParty());
		getPartyRooms().put(room.getId(), room);
	}
	
	/**
	 * Disbands the party room with the given ID, if it exists.
	 * @param roomId Party Room's ID
	 */
	public void removeRoom(int roomId)
	{
		L2PartyRoom room = getPartyRooms().remove(roomId);
		if (room == null)
			return;
		
		L2Party party = room.getParty();
		if (party != null)
		{
			room.setParty(null);
			party.setPartyRoom(null);
		}
		
		for (L2Player member : room.getMembers())
		{
			member.setPartyRoom(null);
			member.sendPacket(ExClosePartyRoom.STATIC_PACKET);
			member.sendPacket(SystemMessageId.PARTY_ROOM_DISBANDED);
		}
	}
	
	public List<L2PartyRoom> getRooms(L2Player player)
	{
		return getRooms(player.getPartyMatchingRegion(), MapRegionManager.getInstance().getL2Region(player),
				player.getPartyMatchingLevelRestriction(), player.getLevel());
	}
	
	public List<L2PartyRoom> getRooms(int reqRegion, int curRegion, boolean lvlRestrict, int lvl)
	{
		if (reqRegion == -2) // find rooms near player
			return getRoomsNearby(curRegion, lvlRestrict, lvl);
		
		ArrayList<L2PartyRoom> list = new ArrayList<L2PartyRoom>();
		for (L2PartyRoom room : getPartyRooms().values())
		{
			// find rooms in a specific region
			if (reqRegion > 0 && room.getLocation() != reqRegion)
				continue;
			else if (!room.checkLevel(lvlRestrict, lvl))
				continue;
			else
				list.add(room);
		}
		return list;
	}
	
	public List<L2PartyRoom> getRoomsNearby(int region, boolean lvlRestrict, int lvl)
	{
		ArrayList<L2PartyRoom> list = new ArrayList<L2PartyRoom>();
		for (L2PartyRoom room : getPartyRooms().values())
		{
			if (room.getLocation() != region || !room.checkLevel(lvlRestrict, lvl))
				continue;
			else
				list.add(room);
		}
		return list;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final PartyRoomManager _instance = new PartyRoomManager();
	}
}
