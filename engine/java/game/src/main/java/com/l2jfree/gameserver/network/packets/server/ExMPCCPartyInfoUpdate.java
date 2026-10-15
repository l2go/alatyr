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

import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 *
 * @author  chris_00
 * 
 * ch Sddd
 */
public class ExMPCCPartyInfoUpdate extends L2ServerPacket
{
	
	private static final String _S__FE_5B_EXMPCCPARTYINFOUPDATE = "[S] FE:5B ExMPCCPartyInfoUpdate";
	private final L2Party _party;
	private final int _mode;
	
	/**
	 * 
	 * @param party
	 * @param mode 0 = Remove, 1 = Add
	 */
	public ExMPCCPartyInfoUpdate(L2Party party, int mode)
	{
		_party = party;
		_mode = mode;
	}
	
	/**
	* @see com.l2jfree.gameserver.network.packets.L2ServerPacket.L2GameServerPacket#writeImpl()
	*/
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x5b);
		
		writeS(_party.getLeader().getName());
		writeD(_party.getPartyLeaderOID());
		writeD(_party.getMemberCount());
		writeD(_mode); //mode 0 = Remove Party, 1 = AddParty, maybe more...
	}
	
	/**
	* @see com.l2jfree.gameserver.network.packets.L2ServerPacket.L2GameServerPacket#getType()
	*/
	@Override
	public String getType()
	{
		return _S__FE_5B_EXMPCCPARTYINFOUPDATE;
	}
}
