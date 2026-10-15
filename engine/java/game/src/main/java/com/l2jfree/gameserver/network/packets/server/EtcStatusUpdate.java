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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.L2Client;

/**
 * @author Luca Baldi
 */
public final class EtcStatusUpdate extends StaticPacket
{
	private static final String _S__F9_ETCSTATUSUPDATE = "[S] f9 EtcStatusUpdate [dddddddd]";
	
	public static final EtcStatusUpdate STATIC_PACKET = new EtcStatusUpdate();
	
	private EtcStatusUpdate()
	{
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket#writeImpl()
	 */
	@Override
	protected void writeImpl(L2Client client, L2Player activeChar)
	{
		if (activeChar == null)
			return;
		
		writeC(0xF9); //several icons to a separate line (0 = disabled)
		writeD(activeChar.getCharges());
		writeD(activeChar.getWeightPenalty());
		writeD(activeChar.getMessageRefusal() || activeChar.isChatBanned() ? 1 : 0);
		writeD(activeChar.isInsideZone(L2Zone.FLAG_DANGER) ? 1 : 0);
		writeD(activeChar.getExpertisePenalty());
		writeD(activeChar.getCharmOfCourage() ? 1 : 0); // 1 = charm of courage (allows resurrection on the same spot upon death on the siege battlefield)
		writeD(activeChar.getDeathPenaltyBuffLevel());
		writeD(activeChar.getSouls());
	}
	
	/**
	 * @see com.l2jfree.gameserver.network.packets.L2ServerPacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__F9_ETCSTATUSUPDATE;
	}
}
