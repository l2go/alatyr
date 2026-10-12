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
package com.l2jfree.gameserver.model.party;


import java.util.ArrayList;
import java.util.List;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.instancemanager.MapRegionManager;
import com.l2jfree.gameserver.instancemanager.PartyRoomManager;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.server.ExClosePartyRoom;
import com.l2jfree.gameserver.network.packets.server.ExManagePartyRoomMember;
import com.l2jfree.gameserver.network.packets.server.ExPartyRoomMember;
import com.l2jfree.gameserver.network.packets.server.PartyRoomInfo;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.lang.L2Math;

/**
 * This class represents a party room, used for party creation and party matching.
 * @author Myzreal
 * @author savormix
 */
public class L2PartyRoom
{
	private final int _id;
	private final List<L2Player> _members;
	private int _minLevel;
	private int _maxLevel;
	private int _lootDist;
	private int _maxMembers;
	private String _title;
	private L2Party _party;
	
	public L2PartyRoom(int id, int minLevel, int maxLevel, int maxMembers, int lootDist, String title)
	{
		_id = id;
		setMinLevel(minLevel);
		setMaxLevel(maxLevel);
		setMaxMembers(maxMembers);
		_lootDist = lootDist;
		_title = title;
		_members = new ArrayList<L2Player>();
		_party = null;
	}
	
	public final List<L2Player> getMembers()
	{
		synchronized (_members)
		{
			return new ArrayList<L2Player>(_members);
		}
	}
	
	public int getMemberCount()
	{
		synchronized (_members)
		{
			return _members.size();
		}
	}
	
	public L2Player getLeader()
	{
		if (_party == null || _party.getLeader() == null)
		{
			synchronized (_members)
			{
				return _members.getFirst();
			}
		}
		else
			return _party.getLeader();
	}
	
	/**
	 * Verifies is player is eligible to join this party room.<BR>
	 * Does not specify a reason, does not send a message.
	 * @param activeChar a player
	 * @return whether the given player can join this room
	 * @see #tryJoin(L2Player, L2PartyRoom, boolean)
	 */
	public final boolean canJoin(L2Player activeChar)
	{
		return (activeChar.getPartyRoom() == null && activeChar.getParty() == null && checkLevel(activeChar.getLevel()) && getMemberCount() < getMaxMembers());
	}
	
	/**
	 * Adds the player when the room has a free seat and the player is not already in it.
	 * The waiting-list removal commits with that addition: a refusal leaves the player looking for a party.
	 * @return false when the player was not added
	 */
	public boolean addMember(L2Player player)
	{
		synchronized (_members)
		{
			if (_members.contains(player) || _members.size() >= _maxMembers)
				return false;
			
			_members.add(player);
			player.setPartyRoom(this);
			PartyRoomManager.getInstance().removeFromWaitingList(player);
		}
		
		broadcastPacket(new ExManagePartyRoomMember(ExManagePartyRoomMember.ADDED, player));
		broadcastPacket(new SystemMessage(SystemMessageId.C1_ENTERED_PARTY_ROOM).addPcName(player));
		updateRoomStatus(false);
		player.sendPacket(new PartyRoomInfo(this));
		player.sendPacket(new ExPartyRoomMember(this, getMemberCount() == 1));
		return true;
	}
	
	public void addMembers(L2Party party)
	{
		synchronized (_members)
		{
			for (L2Player player : party.getPartyMembersWithoutLeader())
			{
				_members.add(player);
				player.setPartyRoom(this);
			}
		}
		updateRoomStatus(true);
	}
	
	public void removeMember(L2Player member, boolean oust)
	{
		synchronized (_members)
		{
			// the last member (leader) cannot be removed
			if (_members.size() == 1 || !_members.remove(member))
				return;
			
			member.setPartyRoom(null);
		}
		
		member.sendPacket(ExClosePartyRoom.STATIC_PACKET);
		if (oust)
			member.sendPacket(SystemMessageId.OUSTED_FROM_PARTY_ROOM);
		else
			member.sendPacket(SystemMessageId.PARTY_ROOM_EXITED);
		
		SystemMessage sm;
		if (oust)
			sm = new SystemMessage(SystemMessageId.C1_KICKED_FROM_PARTY_ROOM);
		else
			sm = new SystemMessage(SystemMessageId.C1_LEFT_PARTY_ROOM);
		sm.addPcName(member);
		broadcastPacket(new ExManagePartyRoomMember(ExManagePartyRoomMember.REMOVED, member));
		broadcastPacket(sm);
		updateRoomStatus(false);
	}
	
	public void broadcastPacket(L2ServerPacket packet)
	{
		final L2Player[] members;
		synchronized (_members)
		{
			members = _members.toArray(new L2Player[_members.size()]);
		}
		for (L2Player player : members)
			player.sendPacket(packet);
	}
	
	public void broadcastPacket(L2ServerPacket toLeader, L2ServerPacket toMember)
	{
		final L2Player[] members;
		final L2Player leader;
		synchronized (_members)
		{
			members = _members.toArray(new L2Player[_members.size()]);
			if (_party == null || _party.getLeader() == null)
				leader = members.length == 0 ? null : members[0];
			else
				leader = _party.getLeader();
		}
		for (L2Player player : members)
			if (player == leader)
				player.sendPacket(toLeader);
			else
				player.sendPacket(toMember);
	}
	
	/** Broadcasts PartyRoomInfo and ExPartyRoomMember packets */
	public void updateRoomStatus(boolean playerList)
	{
		broadcastPacket(new PartyRoomInfo(this));
		if (playerList)
			broadcastPacket(new ExPartyRoomMember(this, true), new ExPartyRoomMember(this));
	}
	
	public int getId()
	{
		return _id;
	}
	
	public int getMinLevel()
	{
		return _minLevel;
	}
	
	public int getMaxLevel()
	{
		return _maxLevel;
	}
	
	public int getMaxMembers()
	{
		return _maxMembers;
	}
	
	public int getLootDist()
	{
		return _lootDist;
	}
	
	public String getTitle()
	{
		return _title;
	}
	
	public L2Party getParty()
	{
		return _party;
	}
	
	public void setParty(L2Party party)
	{
		_party = party;
		if (party == null)
			return;
		_party.setPartyRoom(this);
		_party.setLootDistribution(getLootDist());
		// room is created while in party
		if (getMemberCount() == 1)
			addMembers(party);
		// otherwise party members already in room
	}
	
	public void setMinLevel(int minLevel)
	{
		_minLevel = L2Math.limit(1, minLevel, 85);
	}
	
	public void setMaxLevel(int maxLevel)
	{
		_maxLevel = L2Math.limit(1, maxLevel, 85);
	}
	
	public void setMaxMembers(int maxMembers)
	{
		_maxMembers = L2Math.limit(2, maxMembers, 12);
	}
	
	public void setLootDist(int lootDist)
	{
		_lootDist = lootDist;
		if (getParty() != null)
			getParty().setLootDistribution(lootDist);
	}
	
	public void setTitle(String title)
	{
		_title = title;
	}
	
	/** @return L2 region ID (1-15) */
	public int getLocation()
	{
		return MapRegionManager.getInstance().getL2Region(getLeader());
	}
	
	/**
	 * @param restrict whether player enabled level restriction
	 * @param level player's level
	 * @return whether a player should see this room
	 */
	public boolean checkLevel(boolean restrict, int level)
	{
		if (restrict)
			return checkLevel(level);
		else
			return true;
	}
	
	/**
	 * @param level player's level
	 * @return whether a player is able to join
	 */
	public boolean checkLevel(int level)
	{
		return (level >= getMinLevel() && level <= getMaxLevel());
	}
	
	/**
	 * Verifies if player can join the given party room and either adds the player to the
	 * party room or sends a message why the player could not join the room.<BR>
	 * <I>Parameters may be <CODE>null</CODE>, which guarantees <CODE>false</CODE></I>
	 * @param activeChar a player
	 * @param room a party room
	 * @param checkForParty whether check if the player is in party/[room]
	 * @return true if player joined the given room, false otherwise
	 */
	public static final boolean tryJoin(L2Player activeChar, L2PartyRoom room, boolean checkForParty)
	{
		if (activeChar == null)
			return false;
		
		if (checkForParty)
		{
			if (activeChar.getPartyRoom() != null || activeChar.getParty() != null)
			{
				activeChar.sendPacket(SystemMessageId.PARTY_ROOM_FORBIDDEN);
				return false;
			}
		}
		
		if (room == null)
		{
			activeChar.sendPacket(SystemMessageId.PARTY_ROOM_FORBIDDEN);
			return false;
		}
		else if (!room.checkLevel(activeChar.getLevel()))
		{
			activeChar.sendPacket(SystemMessageId.CANT_ENTER_PARTY_ROOM);
			return false;
		}
		else if (room.getMemberCount() >= room.getMaxMembers())
		{
			activeChar.sendPacket(SystemMessageId.PARTY_ROOM_FULL);
			return false;
		}
		else if (!room.addMember(activeChar))
		{
			activeChar.sendPacket(SystemMessageId.PARTY_ROOM_FULL);
			return false;
		}
		return true;
	}
	
	public static final int getPartyRoomState(L2Player player)
	{
		L2PartyRoom room = player.getPartyRoom();
		if (room == null)
			return 0;
		if (room.getLeader() == player)
			return 1;
		L2Party party = room.getParty();
		if (party != null && party == player.getParty())
			return 2;
		return 0;
	}
}
