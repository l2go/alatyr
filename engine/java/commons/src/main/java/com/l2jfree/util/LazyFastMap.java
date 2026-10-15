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
package com.l2jfree.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A map that allocates its backing map on the first write. A shared map is backed by a {@link ConcurrentHashMap},
 * so it does not keep insertion order; lookups with a {@code null} key find nothing instead of failing.
 * 
 * @author NB4L1
 */
public final class LazyFastMap<K, V> implements Map<K, V>
{
	private volatile boolean _initialized = false;
	private volatile Map<K, V> _map = L2Collections.emptyMap();
	
	private boolean _shared = false;
	
	private void init()
	{
		if (!_initialized)
		{
			synchronized (this)
			{
				if (!_initialized)
				{
					_map = _shared ? new ConcurrentHashMap<K, V>() : new LinkedHashMap<K, V>();
					_initialized = true;
				}
			}
		}
	}
	
	public LazyFastMap<K, V> setShared()
	{
		_shared = true;
		
		synchronized (this)
		{
			if (_initialized)
			{
				_map = new ConcurrentHashMap<K, V>(_map);
			}
		}
		
		return this;
	}
	
	@Override
	public void clear()
	{
		_map.clear();
	}
	
	@Override
	public boolean containsKey(Object key)
	{
		return key != null && _map.containsKey(key);
	}
	
	@Override
	public boolean containsValue(Object value)
	{
		return value != null && _map.containsValue(value);
	}
	
	@Override
	public Set<Entry<K, V>> entrySet()
	{
		return _map.entrySet();
	}
	
	@Override
	public V get(Object key)
	{
		return key == null ? null : _map.get(key);
	}
	
	@Override
	public boolean isEmpty()
	{
		return _map.isEmpty();
	}
	
	@Override
	public Set<K> keySet()
	{
		return _map.keySet();
	}
	
	@Override
	public V put(K key, V value)
	{
		init();
		
		return _map.put(key, value);
	}
	
	@Override
	public void putAll(Map<? extends K, ? extends V> m)
	{
		init();
		
		_map.putAll(m);
	}
	
	@Override
	public V remove(Object key)
	{
		return key == null ? null : _map.remove(key);
	}
	
	@Override
	public int size()
	{
		return _map.size();
	}
	
	@Override
	public Collection<V> values()
	{
		return _map.values();
	}
	
	@Override
	public String toString()
	{
		return super.toString() + "-" + _map.toString();
	}
}
