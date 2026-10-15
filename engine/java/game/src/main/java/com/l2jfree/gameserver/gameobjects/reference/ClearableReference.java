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
package com.l2jfree.gameserver.gameobjects.reference;

import java.lang.ref.WeakReference;

import com.l2jfree.gameserver.gameobjects.L2Object;

/**
 * @author NB4L1
 */
public class ClearableReference<T> extends WeakReference<T>
{
	private final String _name;
	
	public ClearableReference(T referent)
	{
		super(referent);
		
		StringBuilder sb = new StringBuilder();
		sb.append("[ ");
		sb.append(Integer.toHexString(System.identityHashCode(referent)).toUpperCase());
		sb.append(" | ");
		sb.append(referent);
		if (referent instanceof L2Object)
		{
			L2Object obj = (L2Object)referent;
			sb.append(" | ");
			sb.append(obj.getObjectId()).append(" - ").append(obj.getName());
		}
		sb.append(" ]");
		
		_name = sb.toString();
	}
	
	public String getName()
	{
		return _name;
	}
}
