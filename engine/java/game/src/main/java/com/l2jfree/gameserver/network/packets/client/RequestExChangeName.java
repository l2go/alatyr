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
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * This packet is sent by the client once the player confirms
 * a new clan name in a dialog opened by ExNeedToChangeName.
 * @author savormix
 */
@SuppressWarnings("unused")
public final class RequestExChangeName extends L2ClientPacket
{
	private int _unk1, _unk2;
	private String _name;
	
	@Override
	protected void readImpl()
	{
		_unk1 = readD(); // always 1?
		_name = readS(); // new name
		_unk2 = readD(); // always 0?
	}
	
	@Override
	protected void runImpl()
	{
		//_log.info("RequestExChangeName, unk=" + _unk1 + ", name=" + _name + ", unk=" + _unk2 + ", sent by " + getActiveChar());
		requestFailed(SystemMessageId.NOT_WORKING_PLEASE_TRY_AGAIN_LATER);
	}
}
