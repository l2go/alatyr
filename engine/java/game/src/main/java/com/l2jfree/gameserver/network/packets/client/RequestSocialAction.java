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
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;
import com.l2jfree.gameserver.util.Util;

/**
 * This class ...
 * 
 * @version $Revision: 1.6.4.4 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestSocialAction extends L2ClientPacket
{
	private static final String _C__1B_REQUESTSOCIALACTION = "[C] 1B RequestSocialAction";
	
	// format  cd
	private int _actionId;
	
	/**
	 * packet type id 0x1b
	 * format:		cd
	 * @param decrypt
	 */
	@Override
	protected void readImpl()
	{
		_actionId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (!FloodProtector.tryPerformAction(activeChar, Protected.SOCIAL))
			return;
		
		// check if its the actionId is allowed
		else if (_actionId < 2 || _actionId > 14)
		{
			Util.handleIllegalPlayerAction(activeChar, "Warning!! Character " + activeChar.getName() + " of account "
					+ activeChar.getAccountName() + " requested an internal Social Action.", Config.DEFAULT_PUNISH);
			sendAF();
			return;
		}
		
		// You cannot do anything else while fishing
		else if (activeChar.isFishing())
		{
			requestFailed(SystemMessageId.CANNOT_DO_WHILE_FISHING_3);
			return;
		}
		
		else if (activeChar.isSitting() || activeChar.getActiveRequester() != null || activeChar.isAlikeDead()
				|| activeChar.isCastingNow() || activeChar.isCastingSimultaneouslyNow()
				|| activeChar.getAI().getIntention() != CtrlIntention.AI_INTENTION_IDLE
				|| (activeChar.isAllSkillsDisabled() && !activeChar.isInDuel()))
		{
			sendAF();
			return;
		}
		
		activeChar.broadcastPacket(new SocialAction(activeChar.getObjectId(), _actionId));
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__1B_REQUESTSOCIALACTION;
	}
}
