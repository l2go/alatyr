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
package com.l2jfree.gameserver.model;

/**
 * The count and price a manor setting packet may store. The band is the one
 * {@code ExShowSeedSetting} and {@code ExShowCropSetting} already send: 60% to 10× the basic price.
 */
public final class ManorOrderLimits
{
	private ManorOrderLimits()
	{
	}
	
	public static boolean allows(long count, long price, int limit, int basicPrice, long maxAdena)
	{
		if (count < 0 || price < 0 || limit < 0 || basicPrice <= 0 || maxAdena <= 0)
			return false;
		if (count > limit)
			return false;
		if (count != 0 && maxAdena / count < price)
			return false;
		long min = basicPrice * 60L / 100L;
		long max = basicPrice * 10L;
		return price >= min && price <= max;
	}
}
