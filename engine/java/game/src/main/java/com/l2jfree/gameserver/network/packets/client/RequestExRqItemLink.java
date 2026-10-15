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
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.ExRpItemLink;
import com.l2jfree.gameserver.util.IllegalPlayerAction;
import com.l2jfree.gameserver.util.Util;

/**
 *
 * @author KenM
 */
public class RequestExRqItemLink extends L2ClientPacket
{
	private static final String _C__D0_1E_REQUESTEXRQITEMLINK = "[C] DO:1E RequestExRqItemLink";
	
	private int _objectId;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		L2Object object = L2World.getInstance().findObject(_objectId);
		if (object instanceof L2ItemInstance)
			sendPacket(new ExRpItemLink((L2ItemInstance)object));
		else if (object != null && Config.BAN_CLIENT_EMULATORS)
			Util.handleIllegalPlayerAction(player, "Fake item link packet! " + player,
					IllegalPlayerAction.PUNISH_KICKBAN);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_1E_REQUESTEXRQITEMLINK;
	}
}
