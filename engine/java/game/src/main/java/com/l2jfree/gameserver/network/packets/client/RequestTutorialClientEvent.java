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
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/*
 * events:
 * 00 none
 * 01 Move Char
 * 02 Move Point of View
 * 03 ??
 * 04 ??
 * 05 ??
 * 06 ??
 * 07 ??
 * 08 Talk to Newbie Helper
 */

/**
 * 7E 01 00 00 00
 * 
 * Format: (c) cccc
 * 
 * @author  DaDummy
 */
public class RequestTutorialClientEvent extends L2ClientPacket
{
	private static final String _C__7E_REQUESTTUTORIALCLIENTEVENT = "[C] 7E RequestTutorialClientEvent";
	
	private int _event;
	
	@Override
	protected void readImpl()
	{
		_event = readD(); // event
	}
	
	@Override
	protected void runImpl()
	{
		L2Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		QuestState qs = player.getQuestState("255_Tutorial");
		if (qs != null)
			qs.getQuest().notifyEvent("CE" + _event + "", null, player);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__7E_REQUESTTUTORIALCLIENTEVENT;
	}
}
