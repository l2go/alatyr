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

import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.datatables.RecipeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * @author Administrator
 */
public class RequestRecipeItemMakeSelf extends L2ClientPacket
{
	private static final String _C__AF_REQUESTRECIPEITEMMAKESELF = "[C] AF RequestRecipeItemMakeSelf";
	
	private int _id;
	
	/**
	 * packet type id 0xac
	 * format:		cd
	 * @param decrypt
	 */
	@Override
	protected void readImpl()
	{
		_id = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (Shutdown.isActionDisabled(DisableType.CREATEITEM))
		{
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		else if (activeChar.getPrivateStoreType() != 0)
		{
			requestFailed(SystemMessageId.PRIVATE_STORE_UNDER_WAY);
			return;
		}
		else if (activeChar.isInCraftMode())
		{
			//activeChar.sendMessage("Currently in Craft Mode");
			sendAF();
			return;
		}
		
		RecipeTable.getInstance().requestMakeItem(activeChar, _id);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__AF_REQUESTRECIPEITEMMAKESELF;
	}
}
