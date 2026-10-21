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
 * One decoded value. Presence of the field is carried by the enclosing field list: an empty list and an
 * empty object stay present.
 */
public final class Value
{
	private long unsigned;
	private long signed;
	private byte[] data = new byte[0];
	private List<Field> fields = List.of();
	private List<Value> items = List.of();
	private int x;
	private int y;
	private int z;
	private byte[] fixed = new byte[8];
	
	Value()
	{
	}
	
	/**
	 * @return the unsigned value
	 */
	public long u()
	{
		return unsigned;
	}
	
	/**
	 * @return the signed value
	 */
	public long s()
	{
		return signed;
	}
	
	/**
	 * @return the string, identifier, or byte payload
	 */
	public byte[] data()
	{
		return data;
	}
	
	/**
	 * @return the object fields, empty when this value is not an object
	 */
	public List<Field> fields()
	{
		return fields;
	}
	
	/**
	 * @return the list items, empty when this value is not a list
	 */
	public List<Value> items()
	{
		return items;
	}
	
	/**
	 * @return the position x
	 */
	public int x()
	{
		return x;
	}
	
	/**
	 * @return the position y
	 */
	public int y()
	{
		return y;
	}
	
	/**
	 * @return the position z
	 */
	public int z()
	{
		return z;
	}
	
	/**
	 * @return the eight fixed bytes
	 */
	public byte[] fixed()
	{
		return fixed;
	}
	
	void setU(long unsigned)
	{
		this.unsigned = unsigned;
	}
	
	void setS(long signed)
	{
		this.signed = signed;
	}
	
	void setData(byte[] data)
	{
		this.data = data;
	}
	
	void setFields(List<Field> fields)
	{
		this.fields = fields;
	}
	
	void setItems(List<Value> items)
	{
		this.items = items;
	}
	
	void setXyz(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	void setFixed(byte[] fixed)
	{
		this.fixed = fixed;
	}
	
	static Value ofU(long unsigned)
	{
		Value value = new Value();
		value.unsigned = unsigned;
		return value;
	}
	
	static Value ofS(long signed)
	{
		Value value = new Value();
		value.signed = signed;
		return value;
	}
	
	static Value ofData(byte[] data)
	{
		Value value = new Value();
		value.data = data;
		return value;
	}
	
	static Value ofFields(List<Field> fields)
	{
		Value value = new Value();
		value.fields = fields;
		return value;
	}
	
	static Value ofItems(List<Value> items)
	{
		Value value = new Value();
		value.items = items;
		return value;
	}
	
	static Value at(int x, int y, int z)
	{
		Value value = new Value();
		value.x = x;
		value.y = y;
		value.z = z;
		return value;
	}
}
