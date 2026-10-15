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
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class CreatureSay extends L2ServerPacket
{
	private static final String _S__4A_CREATURESAY = "[S] 4A CreatureSay [ddss]";
	
	private final int _objectId;
	private final SystemChatChannelId _channel;
	private final String _charName;
	private final String _text;
	
	/**
	 * @param _characters
	 */
	public CreatureSay(int objectId, SystemChatChannelId channel, String charName, String text)
	{
		_objectId = objectId;
		_channel = channel;
		_charName = charName;
		_text = text;
	}
	
	@Override
	public void packetSent(L2Client client, L2Player activeChar)
	{
		if (activeChar != null)
			activeChar.broadcastSnoop(_channel, _charName, _text);
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x4a);
		
		writeD(_objectId);
		writeD(_channel.getId());
		writeS(_charName);
		writeS(_text);
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__4A_CREATURESAY;
	}
}
