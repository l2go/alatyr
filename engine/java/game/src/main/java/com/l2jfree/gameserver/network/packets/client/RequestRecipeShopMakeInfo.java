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

import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.RecipeShopItemInfo;

/**
 * This class ...
 * cdd
 * @version $Revision: 1.1.2.1.2.2 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestRecipeShopMakeInfo extends L2ClientPacket
{
	private static final String _C__B5_RequestRecipeShopMakeInfo = "[C] b5 RequestRecipeShopMakeInfo";
	
	private int _objectId;
	private int _recipeId;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		_recipeId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2Object obj = null;
		
		// Get object from target
		if (activeChar.getTargetId() == _objectId)
			obj = activeChar.getTarget();
		
		// Get object from world
		if (obj == null)
			obj = L2World.getInstance().getPlayer(_objectId);
		
		if (!(obj instanceof L2Player))
		{
			requestFailed(SystemMessageId.TARGET_IS_INCORRECT);
			return;
		}
		
		sendPacket(new RecipeShopItemInfo((L2Player)obj, _recipeId));
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__B5_RequestRecipeShopMakeInfo;
	}
}
