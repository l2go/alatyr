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
package com.l2jfree.gameserver.gameobjects.poly;

import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;

public class ObjectPoly
{
	// =========================================================
	// Data Field
	private int _polyId;
	private String _polyType;
	private int _baseId;
	private boolean _firstMorph;
	private L2NpcTemplate _npcTemplate;
	
	// =========================================================
	// Constructor
	public ObjectPoly()
	{
	}
	
	// =========================================================
	// Method - Public
	public boolean setPolyInfo(String polyType, String polyId)
	{
		int id = Integer.parseInt(polyId);
		if ("npc".equals(polyType))
		{
			// May not be null
			L2NpcTemplate template = NpcTable.getInstance().getTemplate(id);
			if (template == null)
				return false;
			_npcTemplate = template;
		}
		setPolyId(id);
		setPolyType(polyType);
		return true;
	}
	
	public boolean setPolyInfo(String polyType, String polyId, String baseId)
	{
		if (setPolyInfo(polyType, polyId))
		{
			setBaseId(Integer.parseInt(baseId));
			setFirstMorph(true);
			return true;
		}
		return false;
	}
	
	// =========================================================
	// Method - Private
	
	// =========================================================
	// Property - Public
	public final boolean isMorphed()
	{
		return getPolyType() != null;
	}
	
	public final int getPolyId()
	{
		return _polyId;
	}
	
	public final void setPolyId(int value)
	{
		_polyId = value;
	}
	
	public final String getPolyType()
	{
		return _polyType;
	}
	
	public final void setPolyType(String value)
	{
		_polyType = value;
	}
	
	public final void setNotMorphed()
	{
		_polyType = null;
	}
	
	public final boolean isFirstMorph()
	{
		return getFirstMorph();
	}
	
	public final int getBaseId()
	{
		return _baseId;
	}
	
	public final void setBaseId(int value)
	{
		_baseId = value;
	}
	
	public final boolean getFirstMorph()
	{
		return _firstMorph;
	}
	
	public final void setFirstMorph(boolean value)
	{
		_firstMorph = value;
	}
	
	public final L2NpcTemplate getNpcTemplate()
	{
		return _npcTemplate;
	}
}
