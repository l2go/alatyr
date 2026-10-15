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
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.GMHennaInfo;
import com.l2jfree.gameserver.network.packets.server.GMViewCharacterInfo;
import com.l2jfree.gameserver.network.packets.server.GMViewItemList;
import com.l2jfree.gameserver.network.packets.server.GMViewPledgeInfo;
import com.l2jfree.gameserver.network.packets.server.GMViewQuestInfo;
import com.l2jfree.gameserver.network.packets.server.GMViewSkillInfo;
import com.l2jfree.gameserver.network.packets.server.GMViewWarehouseWithdrawList;

/**
 * This class represents a packet that is sent whenever a GM clicks something
 * player-related in the ALT+G menu.
 */
public class RequestGMCommand extends L2ClientPacket
{
	private static final String _C__REQUESTGMCOMMAND = "[C] 7E RequestGMCommand c[sdd]";
	
	private String _targetName;
	private int _command;
	
	//private final int _unknown;
	
	@Override
	protected void readImpl()
	{
		_targetName = readS();
		_command = readD();
		/*_unknown  = */readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		if (activeChar.getAccessLevel() < Config.GM_ALTG_MIN_LEVEL)
		{
			sendAF();
			return;
		}
		
		L2Player player = L2World.getInstance().getPlayer(_targetName);
		if (player == null)
		{
			requestFailed(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
			return;
		}
		
		switch (_command)
		{
			case 1: // player status
			{
				if (activeChar.getAccessLevel() >= Config.GM_CHAR_VIEW_INFO)
				{
					sendPacket(new GMViewCharacterInfo(player));
					sendPacket(new GMHennaInfo(player));
				}
				break;
			}
			case 2: // player clan
			{
				if (activeChar.getAccessLevel() >= Config.GM_CHAR_CLAN_VIEW && player.getClan() != null)
					sendPacket(new GMViewPledgeInfo(player.getClan(), player));
				break;
			}
			case 3: // player skills
			{
				if (activeChar.getAccessLevel() >= Config.GM_CHAR_VIEW_SKILL)
					sendPacket(new GMViewSkillInfo(player));
				break;
			}
			case 4: // player quests
			{
				if (activeChar.getAccessLevel() >= Config.GM_CHAR_VIEW_QUEST)
					sendPacket(new GMViewQuestInfo(player));
				break;
			}
			case 5: // player inventory
			{
				if (activeChar.getAccessLevel() >= Config.GM_CHAR_INVENTORY)
				{
					sendPacket(new GMViewItemList(player));
					sendPacket(new GMHennaInfo(player));
				}
				break;
			}
			case 6: // player warehouse
			{
				if (activeChar.getAccessLevel() >= Config.GM_CHAR_VIEW_WAREHOUSE)
					sendPacket(new GMViewWarehouseWithdrawList(player));
				break;
			}
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTGMCOMMAND;
	}
}
