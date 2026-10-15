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

import com.l2jfree.gameserver.datatables.HennaTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.stat.PlayerStat;
import com.l2jfree.gameserver.model.items.templates.L2Henna;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class HennaItemDrawInfo extends L2ServerPacket
{
	private static final String _S__E4_HennaItemDrawInfo = "[S] e4 HennaItemDrawInfo";
	
	private L2Player _activeChar;
	private L2Henna _henna;
	
	public HennaItemDrawInfo(L2Henna henna, L2Player player)
	{
		_henna = henna;
		_activeChar = player;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xe4);
		writeD(_henna.getSymbolId()); // symbol ID
		writeD(_henna.getItemId()); // item ID of dye
		writeCompQ(_henna.getAmount()); // total amount of dye required
		writeCompQ(_henna.getPrice()); // total amount of adena required to draw this symbol
		//able to draw or not 0 is false and 1 is true
		writeD(HennaTreeTable.getInstance().isDrawable(_activeChar, _henna.getSymbolId()));
		writeCompQ(_activeChar.getAdena());
		PlayerStat stats = _activeChar.getStat();
		writeD(stats.getINT()); //current INT
		writeC(stats.getINT() + _henna.getStatINT()); //equip INT
		writeD(stats.getSTR()); //current STR
		writeC(stats.getSTR() + _henna.getStatSTR()); //equip STR
		writeD(stats.getCON()); //current CON
		writeC(stats.getCON() + _henna.getStatCON()); //equip CON
		writeD(stats.getMEN()); //current MEM
		writeC(stats.getMEN() + _henna.getStatMEM()); //equip MEM
		writeD(stats.getDEX()); //current DEX
		writeC(stats.getDEX() + _henna.getStatDEX()); //equip DEX
		writeD(stats.getWIT()); //current WIT
		writeC(stats.getWIT() + _henna.getStatWIT()); //equip WIT
	}
	
	@Override
	public String getType()
	{
		return _S__E4_HennaItemDrawInfo;
	}
}
