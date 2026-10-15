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
package com.l2jfree.gameserver.templates;

import java.security.InvalidParameterException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author mkizub <BR>
 *         This class is used in order to have a set of couples (key,value).<BR>
 *         Methods deployed are accessors to the set (add/get value from its key) and addition of a whole set in the current one.
 */
public class StatsSet
{
	private static final Logger _log = LoggerFactory.getLogger(StatsSet.class);
	
	private final Map<String, Object> _set = new LinkedHashMap<String, Object>();
	
	protected Object get(String key)
	{
		return _set.get(key);
	}
	
	protected Object put(String key, Object value)
	{
		return _set.put(key, value);
	}
	
	public void clear()
	{
		_set.clear();
	}
	
	/**
	 * Returns the set of values
	 * 
	 * @return HashMap
	 */
	public final Map<String, Object> getSet()
	{
		return _set;
	}
	
	public final boolean contains(String name)
	{
		return _set.containsKey(name);
	}
	
	/**
	 * Add a set of couple values in the current set
	 * 
	 * @param newSet : StatsSet pointing out the list of couples to add in the current set
	 */
	public final void add(StatsSet newSet)
	{
		for (Map.Entry<String, Object> entry : newSet._set.entrySet())
		{
			put(entry.getKey(), entry.getValue());
		}
	}
	
	/**
	 * Return the boolean associated to the key put in parameter ("name")
	 * 
	 * @param name : String designating the key in the set
	 * @return boolean : value associated to the key
	 */
	public final boolean getBool(String name)
	{
		Object val = get(name);
		if (val == null)
			throw new IllegalArgumentException("Boolean value required, but not specified");
		if (val instanceof Boolean)
			return (Boolean)val;
		try
		{
			return Boolean.parseBoolean((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Boolean value required, but found: " + val);
		}
	}
	
	/**
	 * Return the boolean associated to the key put in parameter ("name"). If the value associated to the key is null, this method returns the value of the parameter deflt.
	 * 
	 * @param name : String designating the key in the set
	 * @param deflt : boolean designating the default value if value associated with the key is null
	 * @return boolean : value of the key
	 */
	public final boolean getBool(String name, boolean deflt)
	{
		Object val = get(name);
		if (val == null)
			return deflt;
		if (val instanceof Boolean)
			return (Boolean)val;
		try
		{
			return Boolean.parseBoolean((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Boolean value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the int associated to the key put in parameter ("name"). If the value associated to the key is null, this method returns the value of the parameter deflt.
	 * 
	 * @param name : String designating the key in the set
	 * @param deflt : byte designating the default value if value associated with the key is null
	 * @return byte : value associated to the key
	 */
	public final byte getByte(String name, byte deflt)
	{
		Object val = get(name);
		if (val == null)
			return deflt;
		if (val instanceof Number)
			return ((Number)val).byteValue();
		try
		{
			return Byte.decode((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Byte value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the byte associated to the key put in parameter ("name").
	 * 
	 * @param name : String designating the key in the set
	 * @return byte : value associated to the key
	 */
	public final byte getByte(String name)
	{
		Object val = get(name);
		if (val == null)
			throw new IllegalArgumentException("Byte value required, but not specified");
		if (val instanceof Number)
			return ((Number)val).byteValue();
		try
		{
			return Byte.decode((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Byte value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the short associated to the key put in parameter ("name"). If the value associated to the key is null, this method returns the value of the parameter deflt.
	 * 
	 * @param name : String designating the key in the set
	 * @param deflt : short designating the default value if value associated with the key is null
	 * @return short : value associated to the key
	 */
	public final short getShort(String name, short deflt)
	{
		Object val = get(name);
		if (val == null)
			return deflt;
		if (val instanceof Number)
			return ((Number)val).shortValue();
		try
		{
			return Short.decode((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Short value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the short associated to the key put in parameter ("name").
	 * 
	 * @param name : String designating the key in the set
	 * @return short : value associated to the key
	 */
	public final short getShort(String name)
	{
		Object val = get(name);
		if (val == null)
			throw new IllegalArgumentException("Short value required, but not specified");
		if (val instanceof Number)
			return ((Number)val).shortValue();
		try
		{
			return Short.decode((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Short value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the int associated to the key put in parameter ("name").
	 * 
	 * @param name : String designating the key in the set
	 * @return int : value associated to the key
	 */
	public final int getInteger(String name)
	{
		Object val = get(name);
		if (val == null)
			throw new IllegalArgumentException("Integer value required, but not specified");
		if (val instanceof Number)
			return ((Number)val).intValue();
		try
		{
			return Integer.decode((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Integer value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the int associated to the key put in parameter ("name"). If the value associated to the key is null, this method returns the value of the parameter deflt.
	 * 
	 * @param name : String designating the key in the set
	 * @param deflt : int designating the default value if value associated with the key is null
	 * @return int : value associated to the key
	 */
	public final int getInteger(String name, int deflt)
	{
		Object val = get(name);
		if (val == null)
			return deflt;
		if (val instanceof Number)
			return ((Number)val).intValue();
		try
		{
			return Integer.decode((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Integer value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the int[] associated to the key put in parameter ("name"). If the value associated to the key is null, this method returns the value of the parameter deflt.
	 * 
	 * @param name : String designating the key in the set
	 * @return int[] : value associated to the key
	 */
	public final int[] getIntegerArray(String name)
	{
		Object val = get(name);
		if (val == null)
			throw new IllegalArgumentException("Integer value required, but not specified");
		if (val instanceof Number)
		{
			int[] result = { ((Number)val).intValue() };
			return result;
		}
		int c = 0;
		String[] vals = ((String)val).split(";");
		int[] result = new int[vals.length];
		for (String v : vals)
		{
			try
			{
				result[c++] = Integer.decode(v);
			}
			catch (Exception e)
			{
				throw new IllegalArgumentException("Integer value required, but found: " + val);
			}
		}
		return result;
	}
	
	/**
	 * Returns the long associated to the key put in parameter ("name").
	 * 
	 * @param name : String designating the key in the set
	 * @return long : value associated to the key
	 */
	public final long getLong(String name)
	{
		Object val = get(name);
		if (val == null)
			throw new IllegalArgumentException("Integer value required, but not specified");
		if (val instanceof Number)
			return ((Number)val).longValue();
		try
		{
			return Long.decode((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Integer value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the long associated to the key put in parameter ("name"). If the value associated to the key is null, this method returns the value of the parameter deflt.
	 * 
	 * @param name : String designating the key in the set
	 * @param deflt : long designating the default value if value associated with the key is null
	 * @return long : value associated to the key
	 */
	public final long getLong(String name, long deflt)
	{
		Object val = get(name);
		if (val == null)
			return deflt;
		if (val instanceof Number)
			return ((Number)val).longValue();
		try
		{
			return Long.decode((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Integer value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the float associated to the key put in parameter ("name").
	 * 
	 * @param name : String designating the key in the set
	 * @return float : value associated to the key
	 */
	public final float getFloat(String name)
	{
		Object val = get(name);
		if (val == null)
			throw new IllegalArgumentException("Float value required, but not specified");
		if (val instanceof Number)
			return ((Number)val).floatValue();
		try
		{
			return Float.parseFloat((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Float value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the float associated to the key put in parameter ("name"). If the value associated to the key is null, this method returns the value of the parameter deflt.
	 * 
	 * @param name : String designating the key in the set
	 * @param deflt : float designating the default value if value associated with the key is null
	 * @return float : value associated to the key
	 */
	public final float getFloat(String name, float deflt)
	{
		Object val = get(name);
		if (val == null)
			return deflt;
		if (val instanceof Number)
			return ((Number)val).floatValue();
		try
		{
			return Float.parseFloat((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Float value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the double associated to the key put in parameter ("name").
	 * 
	 * @param name : String designating the key in the set
	 * @return double : value associated to the key
	 */
	public final double getDouble(String name)
	{
		Object val = get(name);
		if (val == null)
			throw new IllegalArgumentException("Float value required, but not specified");
		if (val instanceof Number)
			return ((Number)val).doubleValue();
		try
		{
			return Double.parseDouble((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Float value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the double associated to the key put in parameter ("name"). If the value associated to the key is null, this method returns the value of the parameter deflt.
	 * 
	 * @param name : String designating the key in the set
	 * @param deflt : float designating the default value if value associated with the key is null
	 * @return double : value associated to the key
	 */
	public final double getDouble(String name, double deflt)
	{
		Object val = get(name);
		if (val == null)
			return deflt;
		if (val instanceof Number)
			return ((Number)val).doubleValue();
		try
		{
			return Double.parseDouble((String)val);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Float value required, but found: " + val);
		}
	}
	
	/**
	 * Returns the String associated to the key put in parameter ("name").
	 * 
	 * @param name : String designating the key in the set
	 * @return String : value associated to the key
	 */
	public final String getString(String name)
	{
		Object val = get(name);
		if (val == null)
			throw new IllegalArgumentException("String value required, but not specified");
		return String.valueOf(val);
	}
	
	/**
	 * Returns the String associated to the key put in parameter ("name"). If the value associated to the key is null, this method returns the value of the parameter deflt.
	 * 
	 * @param name : String designating the key in the set
	 * @param deflt : String designating the default value if value associated with the key is null
	 * @return String : value associated to the key
	 */
	public final String getString(String name, String deflt)
	{
		Object val = get(name);
		if (val == null)
			return deflt;
		return String.valueOf(val);
	}
	
	/**
	 * Returns the String[] associated to the key put in parameter ("name").
	 * 
	 * @param name : String designating the key in the set
	 * @return String[] : value associated to the key
	 */
	public final String[] getStringArray(String name)
	{
		Object val = get(name);
		if (val == null)
			throw new IllegalArgumentException("String[] value required, but not specified");
		try
		{
			return (String[])val;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("String[] value required, but found: " + val);
		}
	}
	
	/**
	 * Returns an enumeration of &lt;T&gt; from the set
	 * 
	 * @param <T> : Class of the enumeration returned
	 * @param name : String designating the key in the set
	 * @param enumClass : Class designating the class of the value associated with the key in the set
	 * @return Enum<T>
	 */
	@SuppressWarnings("unchecked")
	public final <T extends Enum<T>> T getEnum(String name, Class<T> enumClass)
	{
		Object val = get(name);
		if (val == null)
			throw new IllegalArgumentException("Enum value of type " + enumClass.getName()
					+ " required, but not specified");
		if (enumClass.isInstance(val))
			return (T)val;
		try
		{
			return Enum.valueOf(enumClass, String.valueOf(val));
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Enum value of type " + enumClass.getName() + "required, but found: "
					+ val);
		}
	}
	
	/**
	 * Returns an enumeration of &lt;T&gt; from the set. If the enumeration is empty, the method returns the value of the parameter "deflt".
	 * 
	 * @param <T> : Class of the enumeration returned
	 * @param name : String designating the key in the set
	 * @param enumClass : Class designating the class of the value associated with the key in the set
	 * @param deflt : <T> designating the value by default
	 * @return Enum<T>
	 */
	@SuppressWarnings("unchecked")
	public final <T extends Enum<T>> T getEnum(String name, Class<T> enumClass, T deflt)
	{
		Object val = get(name);
		if (val == null)
			return deflt;
		if (enumClass.isInstance(val))
			return (T)val;
		try
		{
			return Enum.valueOf(enumClass, String.valueOf(val));
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Enum value of type " + enumClass.getName() + "required, but found: "
					+ val);
		}
	}
	
	/**
	 * Add the String hold in param "value" for the key "name"
	 * 
	 * @param name : String designating the key in the set
	 * @param value : String corresponding to the value associated with the key
	 */
	public final void set(String name, String value)
	{
		put(name, value);
	}
	
	/**
	 * Add the String[] hold in param "value" for the key "name"
	 * 
	 * @param name : String designating the key in the set
	 * @param value : String[] corresponding to the value associated with the key
	 */
	public final void set(String name, String[] value)
	{
		put(name, value);
	}
	
	/**
	 * Add the boolean hold in param "value" for the key "name"
	 * 
	 * @param name : String designating the key in the set
	 * @param value : boolean corresponding to the value associated with the key
	 */
	public final void set(String name, boolean value)
	{
		put(name, value);
	}
	
	/**
	 * Add the int hold in param "value" for the key "name"
	 * 
	 * @param name : String designating the key in the set
	 * @param value : int corresponding to the value associated with the key
	 */
	public final void set(String name, int value)
	{
		put(name, value);
	}
	
	/**
	 * Safe version of "set". Expected values are within [min, max]<br>
	 * Add the int hold in param "value" for the key "name"
	 * 
	 * @param name String designating the key in the set
	 * @param value int corresponding to the value associated with the key
	 */
	public void safeSet(String name, int value, int min, int max, String reference)
	{
		if (min > max)
			throw new InvalidParameterException("Illegal method call: minimum value > maximum value!");
		
		if (value < min || value >= max)
		{
			_log.warn("Incorrect value (" + value + ") while adding " + name + " to StatsSet. Action: " + reference);
			// Don't add the incorrect value, add an allowed one!
			if (Math.abs(value - min) < Math.abs(value - max))
				set(name, min);
			else
				set(name, max);
		}
		else
			set(name, value);
	}
	
	/**
	 * Add the double hold in param "value" for the key "name"
	 * 
	 * @param name : String designating the key in the set
	 * @param value : double corresponding to the value associated with the key
	 */
	public final void set(String name, double value)
	{
		put(name, value);
	}
	
	/**
	 * Add the long hold in param "value" for the key "name"
	 * 
	 * @param name : String designating the key in the set
	 * @param value : double corresponding to the value associated with the key
	 */
	public final void set(String name, long value)
	{
		put(name, value);
	}
	
	/**
	 * Add the Enum hold in param "value" for the key "name"
	 * 
	 * @param name : String designating the key in the set
	 * @param value : Enum corresponding to the value associated with the key
	 */
	public final void set(String name, Enum<?> value)
	{
		put(name, value);
	}
}
