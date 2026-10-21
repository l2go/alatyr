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
package com.l2jfree.gameserver.bridge.wire;

import java.util.List;

/**
 * One numbered field. Absence is not the same as a present zero, false, or empty value.
 */
public final class Field
{
	private final int number;
	private final boolean essential;
	private final WireType type;
	private final Value value;
	
	Field(int number, boolean essential, WireType type, Value value)
	{
		this.number = number;
		this.essential = essential;
		this.type = type;
		this.value = value;
	}
	
	/**
	 * @return the field number, from 1 through 2^28-1
	 */
	public int number()
	{
		return number;
	}
	
	/**
	 * @return whether the field is essential
	 */
	public boolean essential()
	{
		return essential;
	}
	
	/**
	 * @return the wire type
	 */
	public WireType type()
	{
		return type;
	}
	
	/**
	 * @return the value
	 */
	public Value value()
	{
		return value;
	}
	
	/**
	 * @param number the field number
	 * @param essential whether the field is essential
	 * @param unsigned the unsigned value
	 * @return a field
	 */
	public static Field u(int number, boolean essential, long unsigned)
	{
		return new Field(number, essential, WireType.UNSIGNED, Value.ofU(unsigned));
	}
	
	/**
	 * @param number the field number
	 * @param essential whether the field is essential
	 * @param signed the signed value
	 * @return a field
	 */
	public static Field s(int number, boolean essential, long signed)
	{
		return new Field(number, essential, WireType.SIGNED, Value.ofS(signed));
	}
	
	/**
	 * @param number the field number
	 * @param essential whether the field is essential
	 * @param data the raw bytes
	 * @return a field
	 */
	public static Field data(int number, boolean essential, byte[] data)
	{
		return new Field(number, essential, WireType.LENGTH_DELIMITED, Value.ofData(data));
	}
	
	/**
	 * @param number the field number
	 * @param essential whether the field is essential
	 * @param fields the object fields
	 * @return a field
	 */
	public static Field obj(int number, boolean essential, List<Field> fields)
	{
		return new Field(number, essential, WireType.LENGTH_DELIMITED, Value.ofFields(fields));
	}
	
	/**
	 * @param number the field number
	 * @param essential whether the field is essential
	 * @param items the list items
	 * @return a field
	 */
	public static Field items(int number, boolean essential, List<Value> items)
	{
		return new Field(number, essential, WireType.LENGTH_DELIMITED, Value.ofItems(items));
	}
	
	/**
	 * @param number the field number
	 * @param essential whether the field is essential
	 * @param x the position x
	 * @param y the position y
	 * @param z the position z
	 * @return a field
	 */
	public static Field at(int number, boolean essential, int x, int y, int z)
	{
		return new Field(number, essential, WireType.LENGTH_DELIMITED, Value.at(x, y, z));
	}
	
	static Field find(List<Field> fields, int number)
	{
		if (fields == null)
			return null;
		
		for (Field field : fields)
		{
			if (field.number == number)
				return field;
		}
		return null;
	}
}
