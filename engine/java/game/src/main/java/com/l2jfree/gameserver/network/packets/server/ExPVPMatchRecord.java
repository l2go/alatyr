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
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * This packet represents a dialog shown after the Underground
 * Coliseum's PVP match. The dialog is restricted to 9 players
 * per team AND 9 kills/deaths MAX per player.<BR>
 * You can pass a team of even hundred players, but then only
 * the first nine (of each team) will be shown.<BR>
 * The first record in each team's array will be shown as the
 * team's leader <I>(you can't change that without changing the
 * array)</I>.<BR>
 * Sort by name always works properly.<BR>
 * If a player has more than 9 kills or deaths, it will always
 * be at the end of the list when sorting by kills or deaths.<BR>
 * @author savormix
 */
public class ExPVPMatchRecord extends L2ServerPacket
{
	private static final String _S__FE_7E_EXPVPMATCHRECORD = "[S] FE:7E ExPVPMatchRecord";
	
	private final PlayerRecord[] _t1;
	private final PlayerRecord[] _t2;
	private final boolean _winner;
	
	/**
	 * @param team1 Team 1's player records
	 * @param team2 Team 2's player records
	 * @param t1wins UNK
	 */
	public ExPVPMatchRecord(PlayerRecord[] team1, PlayerRecord[] team2, boolean t1wins)
	{
		_t1 = team1;
		_t2 = team2;
		_winner = t1wins;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xfe);
		writeH(0x7e);
		
		writeD(0x02); // team count?
		writeD(_winner ? 0x01 : 0x02); // winner team no?
		writeD(_winner ? 0x02 : 0x01); // loser team no?
		
		writeD(0x00); // ??
		writeD(0x00); // ??
		
		writeD(_t1.length + _t2.length); // total players
		for (int i = 0; i < _t1.length; i++)
		{
			writeS(_t1[i].getPcName()); // player name
			writeD(_t1[i].getKills()); // kills
			writeD(_t1[i].getDeaths()); // deaths
		}
		for (int i = 0; i < _t2.length; i++)
		{
			writeS(_t2[i].getPcName()); // player name
			writeD(_t2[i].getKills()); // kills
			writeD(_t2[i].getDeaths()); // deaths
		}
	}
	
	@Override
	public String getType()
	{
		return _S__FE_7E_EXPVPMATCHRECORD;
	}
	
	/**
	 * An <U>example</U> class to use with this packet.
	 * @author savormix
	 */
	public class PlayerRecord
	{
		private final String _name;
		private int _kills;
		private int _deaths;
		
		public PlayerRecord(L2Player player)
		{
			this(player.getName());
		}
		
		private PlayerRecord(String name)
		{
			_name = name;
			_kills = 0;
			_deaths = 0;
		}
		
		public final String getPcName()
		{
			return _name;
		}
		
		public final int getKills()
		{
			return _kills;
		}
		
		public final void setKills(int kills)
		{
			_kills = kills;
		}
		
		public final int getDeaths()
		{
			return _deaths;
		}
		
		public final void setDeaths(int deaths)
		{
			_deaths = deaths;
		}
	}
}
