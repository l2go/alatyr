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
package com.l2jfree.gameserver.gameobjects.view;

import com.l2jfree.gameserver.gameobjects.L2Creature;

/**
 * @author NB4L1
 */
public abstract class CreatureView<T extends L2Creature> implements ICreatureView
{
	protected final T _activeChar;
	
	protected CreatureView(T activeChar)
	{
		_activeChar = activeChar;
	}
	
	//private long _lastRefreshTime;
	
	@Override
	public final void refresh()
	{
		//if (System.currentTimeMillis() - _lastRefreshTime < 100)
		//	return;
		//
		//_lastRefreshTime = System.currentTimeMillis();
		
		refreshImpl();
	}
	
	protected void refreshImpl()
	{
	}
}
