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

//import java.util.Calendar; //signed time related
//import java.util.logging.Logger;

import java.util.Set;

import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2SiegeClan;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.entity.ClanHall;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * Populates the Siege Defender List in the SiegeInfo Window<BR>
 * <BR>
 * packet type id 0xcb<BR>
 * format: cddddddd + dSSdddSSd<BR>
 * <BR>
 * c = 0xcb<BR>
 * d = CastleID<BR>
 * d = unknow (0x00)<BR>
 * d = unknow (0x01)<BR>
 * d = unknow (0x00)<BR>
 * d = Number of Defending Clans?<BR>
 * d = Number of Defending Clans<BR>
 * { //repeats<BR>
 * d = ClanID<BR>
 * S = ClanName<BR>
 * S = ClanLeaderName<BR>
 * d = ClanCrestID<BR>
 * d = signed time (seconds)<BR>
 * d = Type -> Owner = 0x01 || Waiting = 0x02 || Accepted = 0x03<BR> 
 * d = AllyID<BR>
 * S = AllyName<BR>
 * S = AllyLeaderName<BR>
 * d = AllyCrestID<BR>
 * 
 * @author KenM
 */
public class SiegeDefenderList extends L2ServerPacket
{
	private static final String _S__CA_SiegeDefenderList = "[S] cb SiegeDefenderList";
	//private static Logger _log = Logger.getLogger(SiegeDefenderList.class.getName());
	private final int _siegeableID;
	private final Set<L2SiegeClan> _defenders;
	private final Set<L2SiegeClan> _waiting;
	
	public SiegeDefenderList(Castle castle)
	{
		_siegeableID = castle.getCastleId();
		_defenders = castle.getSiege().getDefenderClans();
		_waiting = castle.getSiege().getDefenderWaitingClans();
	}
	
	public SiegeDefenderList(ClanHall hideout)
	{
		_siegeableID = hideout.getId();
		_defenders = null;
		_waiting = null;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xcb);
		writeD(_siegeableID);
		writeD(0x00); //0
		writeD(0x01); //1
		writeD(0x00); //0
		int size = 0;
		if (_defenders != null)
			size += _defenders.size();
		if (_waiting != null)
			size += _waiting.size();
		writeD(size);
		writeD(size);
		if (size > 0)
		{
			L2Clan clan;
			
			// Listing the Lord and the approved clans
			if (_defenders != null)
			{
				for (L2SiegeClan siegeclan : _defenders)
				{
					clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
					if (clan == null)
						continue;
					
					writeD(clan.getClanId());
					writeS(clan.getName());
					writeS(clan.getLeaderName());
					writeD(clan.getCrestId());
					writeD(0x00); //signed time (seconds) (not storated by L2J)
					switch (siegeclan.getType())
					{
						case OWNER:
							writeD(0x01); //owner
							break;
						case DEFENDER_PENDING:
							writeD(0x02);
							_log.warn("A clan contained in approved defender list is NOT approved!");
							break;
						case DEFENDER:
							writeD(0x03);
							break;
						default:
							writeD(0x00);
							break;
					}
					writeD(clan.getAllyId());
					writeS(clan.getAllyName());
					writeS(""); //AllyLeaderName
					writeD(clan.getAllyCrestId());
				}
			}
			if (_waiting != null)
			{
				for (L2SiegeClan siegeclan : _waiting)
				{
					clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
					writeD(clan.getClanId());
					writeS(clan.getName());
					writeS(clan.getLeaderName());
					writeD(clan.getCrestId());
					writeD(0x00); //signed time (seconds) (not storated by L2J)
					writeD(0x02); //waiting approval
					writeD(clan.getAllyId());
					writeS(clan.getAllyName());
					writeS(""); //AllyLeaderName
					writeD(clan.getAllyCrestId());
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__CA_SiegeDefenderList;
	}
}
