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

import com.l2jfree.gameserver.datatables.RecipeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.items.recipe.L2RecipeList;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.RecipeBookItemList;

public class RequestRecipeBookDestroy extends L2ClientPacket
{
	private static final String _C__AC_REQUESTRECIPEBOOKDESTROY = "[C] AD RequestRecipeBookDestroy";
	
	private int _recipeId;
	
	/**
	* Unknown Packet:ad
	* 0000: ad 02 00 00 00
	*/
	@Override
	protected void readImpl()
	{
		_recipeId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		L2RecipeList rp = RecipeTable.getInstance().getRecipeList(_recipeId);
		if (rp == null)
		{
			sendAF();
			return;
		}
		activeChar.unregisterRecipeList(_recipeId);
		RecipeBookItemList response = new RecipeBookItemList(rp.isDwarvenRecipe(), activeChar.getMaxMp());
		if (rp.isDwarvenRecipe())
			response.addRecipes(activeChar.getDwarvenRecipeBook());
		else
			response.addRecipes(activeChar.getCommonRecipeBook());
		
		sendPacket(response);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__AC_REQUESTRECIPEBOOKDESTROY;
	}
}
