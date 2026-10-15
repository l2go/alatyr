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
package com.l2jfree.gameserver.network.packets.server;

import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class ExBrBuyProduct extends L2ServerPacket
{
	private static final String _S__EXBRBUYPRODUCT = "[S] FE:A9 ExBR_BuyProduct ch[d]";
	
	public static final int RESULT_NOT_ENOUGH_POINTS = -1;
	public static final int RESULT_WRONG_PRODUCT = -2; // also -5
	public static final int RESULT_INVENTORY_FULL = -4;
	public static final int RESULT_SALE_PERIOD_ENDED = -7; // also -8
	public static final int RESULT_WRONG_USER_STATE = -9; // also -11
	public static final int RESULT_WRONG_PRODUCT_ITEM = -10;
	
	private final int _result;
	
	public ExBrBuyProduct(int result)
	{
		_result = result;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0xA9);
		
		writeD(_result);
	}
	
	@Override
	public String getType()
	{
		return _S__EXBRBUYPRODUCT;
	}
}
