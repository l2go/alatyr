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
package com.l2jfree.gameserver.gameobjects.knownlist;

import com.l2jfree.gameserver.gameobjects.L2Object;

public abstract class ObjectKnownList
{
	private static final ObjectKnownList _instance = new ObjectKnownList() {
		@Override
		public boolean addKnownObject(L2Object object)
		{
			return false;
		}
		
		@Override
		public L2Object getKnownObject(int objectId)
		{
			return null;
		}
		
		@Override
		public void removeAllKnownObjects()
		{
		}
		
		@Override
		public boolean removeKnownObject(L2Object object)
		{
			return false;
		}
		
		@Override
		public void tryAddObjects(L2Object[][] surroundingObjects)
		{
		}
		
		@Override
		public boolean tryRemoveObject(L2Object obj)
		{
			return false;
		}
		
		@Override
		public void tryRemoveObjects()
		{
		}
	};
	
	public static ObjectKnownList getInstance()
	{
		return _instance;
	}
	
	ObjectKnownList()
	{
	}
	
	public abstract boolean addKnownObject(L2Object object);
	
	public abstract L2Object getKnownObject(int objectId);
	
	public abstract void removeAllKnownObjects();
	
	public abstract boolean removeKnownObject(L2Object object);
	
	public abstract void tryAddObjects(L2Object[][] surroundingObjects);
	
	public abstract void tryRemoveObjects();
	
	public abstract boolean tryRemoveObject(L2Object obj);
}
