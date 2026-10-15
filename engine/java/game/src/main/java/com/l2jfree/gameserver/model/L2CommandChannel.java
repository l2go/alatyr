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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Boss;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.ExCloseMPCC;
import com.l2jfree.gameserver.network.packets.server.ExMultiPartyCommandChannelInfo;
import com.l2jfree.gameserver.network.packets.server.ExOpenMPCC;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.util.L2FastSet;

/**
 *
 * @author  chris_00
 */
public class L2CommandChannel
{
	private Set<L2Party> _partys = null;
	private L2Player _commandLeader = null;
	private int _channelLvl;
	
	/**
	 * Creates a New Command Channel and Add the Leaders party to the CC
	 * @param leader Command channel leader
	 */
	public L2CommandChannel(L2Player leader)
	{
		_commandLeader = leader;
		_partys = new L2FastSet<L2Party>().setShared(true);
		_partys.add(leader.getParty());
		_channelLvl = leader.getParty().getLevel();
		leader.getParty().setCommandChannel(this);
		leader.getParty().broadcastToPartyMembers(SystemMessageId.COMMAND_CHANNEL_FORMED.getSystemMessage());
		leader.getParty().broadcastToPartyMembers(ExOpenMPCC.STATIC_PACKET);
		leader.getParty().broadcastToPartyMembers(new ExMultiPartyCommandChannelInfo(this));
	}
	
	/**
	 * Adds a Party to the Command Channel
	 * @param party
	 */
	public void addParty(L2Party party)
	{
		if (party == null)
			return;
		_partys.add(party);
		if (party.getLevel() > _channelLvl)
			_channelLvl = party.getLevel();
		party.setCommandChannel(this);
		if (_partys.size() > 2)
			party.broadcastToPartyMembers(SystemMessageId.JOINED_CHANNEL_ALREADY_OPEN.getSystemMessage());
		else
			party.broadcastToPartyMembers(SystemMessageId.JOINED_COMMAND_CHANNEL.getSystemMessage());
		party.broadcastToPartyMembers(ExOpenMPCC.STATIC_PACKET);
		
		if (_partys.size() < 5)
		{
			SystemMessage sm = SystemMessageId.COMMAND_CHANNEL_ONLY_AT_LEAST_5_PARTIES.getSystemMessage();
			broadcastToChannelMembers(sm);
			sm = new SystemMessage(SystemMessageId.S1_PARTIES_REMAINING_UNTIL_CHANNEL);
			sm.addNumber(5 - _partys.size());
			broadcastToChannelMembers(sm);
		}
		broadcastToChannelMembers(new ExMultiPartyCommandChannelInfo(this));
	}
	
	/**
	 * Removes a Party from the Command Channel
	 * @param Party
	 */
	public void removeParty(L2Party party)
	{
		if (party == null)
			return;
		_partys.remove(party);
		_channelLvl = 0;
		for (L2Party pty : _partys)
		{
			if (pty.getLevel() > _channelLvl)
				_channelLvl = pty.getLevel();
		}
		party.setCommandChannel(null);
		party.broadcastToPartyMembers(ExCloseMPCC.STATIC_PACKET);
		
		SystemMessage sm;
		if (_partys.size() < 2)
		{
			broadcastToChannelMembers(SystemMessageId.COMMAND_CHANNEL_DISBANDED.getSystemMessage());
			disbandChannel();
			return;
		}
		else if (_partys.size() < 5)
		{
			sm = SystemMessageId.COMMAND_CHANNEL_ONLY_AT_LEAST_5_PARTIES.getSystemMessage();
			broadcastToChannelMembers(sm);
			sm = new SystemMessage(SystemMessageId.S1_PARTIES_REMAINING_UNTIL_CHANNEL);
			sm.addNumber(5 - _partys.size());
			broadcastToChannelMembers(sm);
		}
		broadcastToChannelMembers(new ExMultiPartyCommandChannelInfo(this));
	}
	
	/** disbands the whole Command Channel */
	public void disbandChannel()
	{
		if (_partys != null)
			for (L2Party party : _partys)
				if (party != null)
					removeParty(party);
		_partys = null;
	}
	
	/** @return overall member count of the Command Channel */
	public int getMemberCount()
	{
		int count = 0;
		if (_partys != null)
			for (L2Party party : _partys)
				if (party != null)
					count += party.getMemberCount();
		return count;
	}
	
	/**
	 * Broadcast packet to every channel member
	 * @param gsp a sendable packet
	 */
	public void broadcastToChannelMembers(L2ServerPacket gsp)
	{
		if (_partys != null)
			for (L2Party party : _partys)
				if (party != null)
					party.broadcastToPartyMembers(gsp);
	}
	
	public void broadcastCSToChannelMembers(CreatureSay gsp, L2Player broadcaster)
	{
		if (_partys != null)
			for (L2Party party : _partys)
				if (party != null)
					party.broadcastCSToPartyMembers(gsp, broadcaster);
	}
	
	public void broadcastToChannelMembers(L2Player exclude, L2ServerPacket gsp)
	{
		if (_partys != null)
			for (L2Party party : _partys)
				party.broadcastToPartyMembers(exclude, gsp);
	}
	
	/** @return list of Parties in Command Channel */
	public Set<L2Party> getPartys()
	{
		return _partys;
	}
	
	/** @return list of all Members in Command Channel */
	public List<L2Player> getMembers()
	{
		List<L2Player> members = new ArrayList<L2Player>();
		for (L2Party party : getPartys())
			members.addAll(party.getPartyMembers());
		return members;
	}
	
	public boolean contains(L2Creature target)
	{
		if (target.getParty() == null)
			return false;
		
		return _partys.contains(target.getParty());
	}
	
	/** @return Level of Command Channel */
	public int getLevel()
	{
		return _channelLvl;
	}
	
	/**
	 * Sets the new leader of the Command Channel
	 * @param leader
	 */
	public void setChannelLeader(L2Player leader)
	{
		_commandLeader = leader;
	}
	
	/** @return the leader of the Command Channel */
	public L2Player getChannelLeader()
	{
		return _commandLeader;
	}
	
	/**
	 * Queen Ant, Core, Orfen, Zaken: MemberCount > 36<br>
	 * Baium: MemberCount > 56<br>
	 * Antharas: MemberCount > 225<br>
	 * Valakas: MemberCount > 99<br>
	 * normal RaidBoss: MemberCount > 18
	 * 
	 * @param obj
	 * @return true if proper condition for RaidWar
	 */
	public boolean meetRaidWarCondition(L2Object obj)
	{
		if (!(obj instanceof L2Boss))
			return false;
		int npcId = ((L2Attackable)obj).getNpcId();
		switch (npcId)
		{
			case 29001: // Queen Ant
			case 29006: // Core
			case 29014: // Orfen
			case 29022: // Zaken
				return (getMemberCount() > 36);
			case 29020: // Baium
				return (getMemberCount() > 56);
			case 29019: // Antharas
				return (getMemberCount() > 225);
			case 29028: // Valakas
				return (getMemberCount() > 99);
			case 29045: // Frintezza
				return (getMemberCount() > 35);
			default: // normal Raidboss
				return (getMemberCount() > 18);
		}
	}
}
