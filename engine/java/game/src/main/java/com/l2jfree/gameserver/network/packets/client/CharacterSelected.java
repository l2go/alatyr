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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.bridge.CharacterSelection;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.CharSelected;

public final class CharacterSelected extends L2ClientPacket
{
	private static final String _C__0D_CHARACTERSELECTED = "[C] 0D CharacterSelected";
	
	private int _charSlot;
	
	//private int _unk1; // new in C4
	//private int _unk2; // new in C4
	//private int _unk3; // new in C4
	//private int _unk4; // new in C4
	
	@Override
	protected void readImpl()
	{
		_charSlot = readD();
		/*_unk1 = */readH();
		/*_unk2 = */readD();
		/*_unk3 = */readD();
		/*_unk4 = */readD();
	}
	
	@Override
	protected void runImpl()
	{
		if (!CharacterSelection.enter(getClient(), _charSlot))
			return;
		
		// A bridge opening does not mint a login session key, so it does not send this packet's tail.
		if (getClient().isBridge())
			return;
		
		L2Player cha = getClient().getActiveChar();
		sendPacket(new CharSelected(cha, getClient().getSessionId().playOk1()));
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getType()
	{
		return _C__0D_CHARACTERSELECTED;
	}
}
