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
package com.l2jfree.gameserver.datatables;

/**
 * This class has just one simple function to return the item id of a crown regarding to castleid
 * 
 * @author evill33t
 */
public final class CrownTable
{
	private static final int[] CROWN_IDS = { 6841, // Crown of the lord
			6834, // Innadril
			6835, // Dion
			6836, // Goddard
			6837, // Oren
			6838, // Gludio
			6839, // Giran
			6840, // Aden
			8182, // Rune
			8183, // Schuttgart
	};
	
	public static int[] getCrownIds()
	{
		return CROWN_IDS;
	}
	
	public static int getCrownId(int castleId)
	{
		switch (castleId)
		{
			case 1:// Gludio
				return 6838;
				
			case 2: // Dion
				return 6835;
				
			case 3: // Giran
				return 6839;
				
			case 4: // Oren
				return 6837;
				
			case 5: // Aden
				return 6840;
				
			case 6: // Innadril
				return 6834;
				
			case 7: // Goddard
				return 6836;
				
			case 8:// Rune
				return 8182;
				
			case 9: // Schuttgart
				return 8183;
		}
		
		return 0;
	}
}
