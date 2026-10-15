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
package com.l2jfree.gameserver.gameobjects.appearance;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;

public final class PlayerAppearance
{
	/** The default hexadecimal color of players' name (white is 0xFFFFFF) */
	public static final int DEFAULT_NAME_COLOR = 0xFFFFFF;
	/** The default hexadecimal color of players' title (light blue is 0xFFFF77) */
	public static final int DEFAULT_TITLE_COLOR = 0xFFFF77;
	
	// =========================================================
	// Data Field
	private L2Player _owner;
	private byte _face;
	private byte _hairColor;
	private byte _hairStyle;
	private boolean _sex; // Female true(1)
	
	/** true if the player is invisible */
	private boolean _invisible = false;
	
	/** The current visisble name of this palyer, not necessarily the real one */
	private String _visibleName;
	/** The current visisble title of this palyer, not necessarily the real one */
	private String _visibleTitle;
	
	/** The hexadecimal Color of players name (white is 0xFFFFFF) */
	private int _nameColor = DEFAULT_NAME_COLOR;
	// No idea if this should be stored between sessions
	private int _nickColor = -1;
	/** The hexadecimal Color of players title (light blue is 0xFFFF77) */
	private int _titleColor = DEFAULT_TITLE_COLOR;
	
	// =========================================================
	// Constructor
	public PlayerAppearance(byte face, byte hColor, byte hStyle, boolean sex)
	{
		_face = face;
		_hairColor = hColor;
		_hairStyle = hStyle;
		_sex = sex;
	}
	
	public void setVisibleName(String visibleName)
	{
		_visibleName = visibleName;
	}
	
	public String getVisibleName()
	{
		if (_visibleName != null)
			return _visibleName;
		
		return _owner.getName();
	}
	
	public void setVisibleTitle(String visibleTitle)
	{
		_visibleTitle = visibleTitle;
	}
	
	public String getVisibleTitle()
	{
		if (_visibleTitle != null)
			return _visibleTitle;
		
		return _owner.getTitle();
	}
	
	public byte getFace()
	{
		return _face;
	}
	
	public void setFace(int value)
	{
		_face = (byte)value;
	}
	
	public byte getHairColor()
	{
		return _hairColor;
	}
	
	public void setHairColor(int value)
	{
		_hairColor = (byte)value;
	}
	
	public byte getHairStyle()
	{
		return _hairStyle;
	}
	
	public void setHairStyle(int value)
	{
		_hairStyle = (byte)value;
	}
	
	public boolean getSex()
	{
		return _sex;
	}
	
	public void setSex(boolean isfemale)
	{
		_sex = isfemale;
	}
	
	public void setInvisible()
	{
		_invisible = true;
	}
	
	public void setVisible()
	{
		_invisible = false;
	}
	
	public boolean isInvisible()
	{
		return _invisible;
	}
	
	public int getNameColor()
	{
		final int value = GlobalRestrictions.getNameColor(_owner);
		
		if (value != -1)
			return value;
		
		if (_nickColor != -1)
			return _nickColor;
		
		return _nameColor;
	}
	
	public void setNameColor(int nameColor)
	{
		_nameColor = nameColor;
	}
	
	public void setNameColor(int red, int green, int blue)
	{
		setNameColor((red & 0xFF) + ((green & 0xFF) << 8) + ((blue & 0xFF) << 16));
	}
	
	public int getTitleColor()
	{
		final int value = GlobalRestrictions.getTitleColor(_owner);
		
		if (value != -1)
			return value;
		
		return _titleColor;
	}
	
	public void setTitleColor(int titleColor)
	{
		_titleColor = titleColor;
	}
	
	public void setTitleColor(int red, int green, int blue)
	{
		setTitleColor((red & 0xFF) + ((green & 0xFF) << 8) + ((blue & 0xFF) << 16));
	}
	
	public void setOwner(L2Player owner)
	{
		_owner = owner;
	}
	
	public int getNickColor()
	{
		return _nickColor;
	}
	
	public void setNickColor(int color)
	{
		_nickColor = color;
	}
}
