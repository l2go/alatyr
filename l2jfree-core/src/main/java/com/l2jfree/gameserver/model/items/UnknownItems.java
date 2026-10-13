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
package com.l2jfree.gameserver.model.items;

/**
 * Items skipped while an inventory is loaded because this world has no template for them. The stored rows are left as
 * they are: another world may know the templates. One load produces one warning.
 */
public final class UnknownItems
{
	private int _count;
	
	public void miss()
	{
		_count++;
	}
	
	public int count()
	{
		return _count;
	}
	
	/** @return the one warning for this load, or null when every item has a template */
	public String warning()
	{
		if (_count == 0)
			return null;
		return _count + " items have no template in this world";
	}
}
