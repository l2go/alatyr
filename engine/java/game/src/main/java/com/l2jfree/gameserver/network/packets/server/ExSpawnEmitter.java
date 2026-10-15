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

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class ExSpawnEmitter extends L2ServerPacket
{
	public ExSpawnEmitter(int playerObjectId, int npcObjectId)
	{
		_playerObjectId = playerObjectId;
		_npcObjectId = npcObjectId;
	}
	
	public ExSpawnEmitter(L2Player player, L2Npc npc)
	{
		_playerObjectId = player.getObjectId();
		_npcObjectId = npc.getObjectId();
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xfe);
		writeH(0x5d);
		writeD(_npcObjectId);
		writeD(_playerObjectId);
		writeD(0x00);
	}
	
	@Override
	public String getType()
	{
		return "SpawnEmitter";
	}
	
	private final int _npcObjectId;
	private final int _playerObjectId;
}
