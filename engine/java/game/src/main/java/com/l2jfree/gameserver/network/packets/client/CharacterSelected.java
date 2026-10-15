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

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.L2Client.GameClientState;
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
		// should always be null
		// but if not then this is repeated packet and nothing should be done here
		if (getClient().getActiveChar() != null)
			return;
		
		final L2Player cha = getClient().loadCharFromDisk(_charSlot);
		
		if (cha == null)
		{
			_log.error(getClient() + ": character couldn't be loaded (slot:" + _charSlot + ")");
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if ((cha.getAccessLevel() < 0) || (Config.SERVER_GMONLY && cha.getAccessLevel() <= 0))
		{
			new Disconnection(getClient(), cha).defaultSequence(false);
			return;
		}
		
		// preinitialize some values for each login
		cha.setRunning(); // running is default
		cha.standUp(); // standing is default
		
		// the char & skills are fully loaded, so update
		cha.refreshOverloaded();
		// refresh expertise already done when loading character (after loading inv)
		cha.setOnlineStatus(true);
		
		L2World.getInstance().storeObject(cha);
		L2World.getInstance().addOnlinePlayer(cha);
		
		cha.setClient(getClient());
		getClient().setActiveChar(cha);
		
		getClient().setState(GameClientState.IN_GAME);
		sendPacket(new CharSelected(cha, getClient().getSessionId().playOk1()));
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getType()
	{
		return _C__0D_CHARACTERSELECTED;
	}
}
