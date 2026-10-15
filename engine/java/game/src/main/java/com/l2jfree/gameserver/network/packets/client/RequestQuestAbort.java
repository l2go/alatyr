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
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.QuestList;

public class RequestQuestAbort extends L2ClientPacket
{
	private static final String _C__64_REQUESTQUESTABORT = "[C] 64 RequestQuestAbort";
	
	private int _questId;
	
	/**
	 * packet type id 0x64<p>
	 */
	@Override
	protected void readImpl()
	{
		_questId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		Quest qe = QuestManager.getInstance().getQuest(_questId);
		if (qe != null)
		{
			QuestState qs = activeChar.getQuestState(qe.getName());
			if (qs != null)
			{
				qs.exitQuest(true);
				// Is it still necessary?
				if (qe.getName() == "605_AllianceWithKetraOrcs" || qe.getName() == "611_AllianceWithVarkaSilenos")
					activeChar.setAllianceWithVarkaKetra(0);
				sendPacket(new QuestList(activeChar));
			}
			else
			{
				if (_log.isDebugEnabled())
					_log.debug("Player '" + activeChar.getName() + "' try to abort quest " + qe.getName()
							+ " but he didn't have it started.");
			}
		}
		else
		{
			if (_log.isDebugEnabled())
				_log.warn("Quest (id='" + _questId + "') not found.");
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__64_REQUESTQUESTABORT;
	}
}
