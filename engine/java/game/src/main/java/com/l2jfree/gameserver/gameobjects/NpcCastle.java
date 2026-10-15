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
package com.l2jfree.gameserver.gameobjects;

import com.l2jfree.gameserver.model.entity.Castle;

/**
 * The castle an NPC belongs to, remembered after the first lookup.
 * <p>
 * Castle ids start at 1. {@link #NONE} means this NPC has no castle, and {@code getCastleById(0)} is null. A shop price
 * uses a multiplier ({@code 1.0} is the catalog price). A multisell uses a share ({@code 0.0} adds nothing). The two
 * stay separate.
 */
public final class NpcCastle
{
	public static final int NONE = 0;
	
	private int _index = -2;
	private boolean _inTown;
	
	public boolean resolved()
	{
		return _index >= 0;
	}
	
	public int index()
	{
		return _index;
	}
	
	public boolean inTown()
	{
		return _inTown;
	}
	
	/**
	 * Remembers the castle id. A later call keeps the first answer.
	 */
	public int resolve(boolean inTown, Castle townCastle, Castle closest)
	{
		if (resolved())
			return _index;
		_inTown = inTown;
		_index = indexOf(inTown, townCastle, closest);
		return _index;
	}
	
	static int indexOf(boolean inTown, Castle townCastle, Castle closest)
	{
		if (inTown && townCastle != null)
			return townCastle.getCastleId();
		if (closest == null)
			return NONE;
		return closest.getCastleId();
	}
	
	/** Shop price multiplier. Without a castle the catalog price is unchanged. */
	public static double shopRate(Castle castle)
	{
		return castle == null ? 1.0 : castle.getTaxRate();
	}
	
	/** Multisell tax share. Without a castle nothing is added. */
	public static double multisellRate(Castle castle)
	{
		return castle == null ? 0.0 : castle.getTaxRate();
	}
	
	/** Sends the purchase tax to the castle owner. A missing castle or an unowned castle receives nothing. */
	public static void addPurchaseTax(Castle castle, long tax)
	{
		if (castle != null && castle.getOwnerId() > 0)
			castle.addToTreasury(tax);
	}
}
