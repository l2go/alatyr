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
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2ClanMember;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * This class represents a packet sent by the client when a player/clan leader requests a
 * new title.
 * 
 * @version $Revision: 1.3.2.1.2.4 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestGiveNickName extends L2ClientPacket
{
	private static final String _C__55_REQUESTGIVENICKNAME = "[C] 55 RequestGiveNickName";
	
	private String _target;
	private String _title;
	
	@Override
	protected void readImpl()
	{
		_target = readS();
		_title = readS();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (activeChar.getName().equals(_target) && (activeChar.isGM() || activeChar.isNoble()))
		{
			setTitle(activeChar);
			sendAF();
			return;
		}
		else if (!L2Clan.checkPrivileges(activeChar, L2Clan.CP_CL_GIVE_TITLE))
		{
			requestFailed(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		else if (activeChar.getClan().getLevel() < 3)
		{
			requestFailed(SystemMessageId.CLAN_LVL_3_NEEDED_TO_ENDOWE_TITLE);
			return;
		}
		else if (!Config.TITLE_PATTERN.matcher(_title).matches() || _title.length() > 128)
		{
			requestFailed(SystemMessageId.PLEASE_INPUT_TITLE_LESS_128_CHARACTERS);
			return;
		}
		
		L2ClanMember targetMember = activeChar.getClan().getClanMember(_target);
		if (targetMember == null)
		{
			requestFailed(SystemMessageId.TARGET_MUST_BE_IN_CLAN);
			return;
		}
		L2Player target = targetMember.getPlayerInstance();
		if (target == null)
		{
			requestFailed(SystemMessageId.TARGET_IS_NOT_FOUND_IN_THE_GAME);
			return;
		}
		
		setTitle(target);
		
		sendAF();
	}
	
	private final void setTitle(L2Player target)
	{
		target.setTitle(_title);
		target.sendPacket(SystemMessageId.TITLE_CHANGED);
		target.broadcastTitleInfo();
		if (target != getActiveChar())
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.CLAN_MEMBER_C1_TITLE_CHANGED_TO_S2);
			sm.addString(target.getName());
			sm.addString(target.getTitle());
			sendPacket(sm);
		}
	}
	
	@Override
	public String getType()
	{
		return _C__55_REQUESTGIVENICKNAME;
	}
}
