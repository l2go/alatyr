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
package com.l2jfree.util.concurrent;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.l2jfree.lang.L2Entity;
import com.l2jfree.util.L2Collections;

/**
 * @author NB4L1
 */
public abstract class L2EntityMap<T extends L2Entity<Integer>>
{
	private final int _initialSize;
	
	private volatile boolean _initialized = false;
	private volatile Map<Integer, T> _map = L2Collections.emptyMap();
	
	private void init()
	{
		if (!_initialized)
		{
			synchronized (this)
			{
				if (!_initialized)
				{
					final int initialSize = Math.max(_initialSize, 16);
					
					// Only the shared map is read and iterated without the subclass lock.
					if (this instanceof L2SharedEntityMap<?>)
						_map = new ConcurrentHashMap<Integer, T>(initialSize);
					else
						_map = HashMap.newHashMap(initialSize);
					
					_initialized = true;
				}
			}
		}
	}
	
	protected L2EntityMap()
	{
		this(-1);
	}
	
	protected L2EntityMap(int initialSize)
	{
		_initialSize = initialSize;
	}
	
	public int size()
	{
		return _map.size();
	}
	
	public boolean isEmpty()
	{
		return _map.isEmpty();
	}
	
	public boolean contains(T obj)
	{
		return _map.containsKey(obj.getPrimaryKey());
	}
	
	public T get(Integer id)
	{
		return _map.get(id);
	}
	
	public void add(T obj)
	{
		init();
		
		synchronized (_map)
		{
			_map.put(obj.getPrimaryKey(), obj);
		}
	}
	
	public void remove(T obj)
	{
		if (obj == null || !_initialized)
			return;
		
		Integer id = obj.getPrimaryKey();
		synchronized (_map)
		{
			// A newer instance may already own this id. Removing by id alone would detach it.
			if (_map.get(id) == obj)
				_map.remove(id);
		}
	}
	
	public void clear()
	{
		_map.clear();
	}
	
	@SuppressWarnings("unchecked")
	public T[] toArray(T[] array)
	{
		if (array.length != _map.size())
			array = (T[])Array.newInstance(array.getClass().getComponentType(), _map.size());
		
		if (_map.isEmpty() && array.length == 0)
			return array;
		else
			return _map.values().toArray(array);
	}
	
	@SuppressWarnings("unchecked")
	public T[] toArray(Class<T> clazz)
	{
		T[] array = (T[])Array.newInstance(clazz, _map.size());
		
		if (_map.isEmpty() && array.length == 0)
			return array;
		else
			return _map.values().toArray(array);
	}
	
	protected Iterator<T> iterator()
	{
		return _map.values().iterator();
	}
	
	protected void executeForEach(ForEachExecutable<T> executable)
	{
		if (_map.isEmpty())
			return;
		
		for (T value : _map.values())
			executable.execute(value);
	}
	
	@Override
	public final String toString()
	{
		return super.toString() + "-" + _map.toString();
	}
}
