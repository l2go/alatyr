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
package com.l2jfree.gameserver.model.items.templates;

import com.l2jfree.gameserver.templates.StatsSet;

public final class L2Henna
{
	private final int _symbolId;
	private final int _itemId;
	private final long _price;
	private final long _amount;
	private final int _modINT;
	private final int _modSTR;
	private final int _modCON;
	private final int _modMEN;
	private final int _modDEX;
	private final int _modWIT;
	private final String _name;
	
	public L2Henna(StatsSet set)
	{
		_symbolId = set.getInteger("symbol_id");
		_name = set.getString("symbol_name");
		_itemId = set.getInteger("dye_id");
		_price = set.getLong("price");
		_amount = set.getLong("dye_amount");
		_modINT = set.getInteger("mod_INT");
		_modSTR = set.getInteger("mod_STR");
		_modCON = set.getInteger("mod_CON");
		_modMEN = set.getInteger("mod_MEN");
		_modDEX = set.getInteger("mod_DEX");
		_modWIT = set.getInteger("mod_WIT");
	}
	
	public int getSymbolId()
	{
		return _symbolId;
	}
	
	public int getItemId()
	{
		return _itemId;
	}
	
	public long getPrice()
	{
		return _price;
	}
	
	public long getAmount()
	{
		return _amount;
	}
	
	public int getStatINT()
	{
		return _modINT;
	}
	
	public int getStatSTR()
	{
		return _modSTR;
	}
	
	public int getStatCON()
	{
		return _modCON;
	}
	
	public int getStatMEM()
	{
		return _modMEN;
	}
	
	public int getStatDEX()
	{
		return _modDEX;
	}
	
	public int getStatWIT()
	{
		return _modWIT;
	}
	
	public String getName()
	{
		return _name;
	}
}
