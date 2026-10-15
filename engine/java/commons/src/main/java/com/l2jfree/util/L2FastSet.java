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

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A set that keeps insertion order and whose first/last accessors return {@code null} when it is empty.
 * <p>
 * A shared set is backed by a concurrent set: it can be changed while other threads iterate it, but it no longer
 * keeps insertion order, so {@link #getFirst()} and {@link #removeFirst()} return any element.
 * 
 * @author NB4L1
 */
public class L2FastSet<E> extends AbstractSet<E>
{
	private volatile Set<E> _set;
	
	public L2FastSet()
	{
		_set = new LinkedHashSet<E>();
	}
	
	public L2FastSet(int capacity)
	{
		_set = LinkedHashSet.newLinkedHashSet(capacity);
	}
	
	public L2FastSet(Collection<? extends E> elements)
	{
		_set = new LinkedHashSet<E>(elements);
	}
	
	public synchronized L2FastSet<E> setShared(boolean isShared)
	{
		if (isShared != isShared())
		{
			final Set<E> set = isShared ? ConcurrentHashMap.<E> newKeySet() : new LinkedHashSet<E>();
			set.addAll(_set);
			_set = set;
		}
		
		return this;
	}
	
	public boolean isShared()
	{
		return !(_set instanceof LinkedHashSet<?>);
	}
	
	public final E getFirst()
	{
		final Iterator<E> it = _set.iterator();
		return it.hasNext() ? it.next() : null;
	}
	
	public final E getLast()
	{
		if (_set instanceof LinkedHashSet<E> ordered)
			return ordered.isEmpty() ? null : ordered.getLast();
		
		E last = null;
		for (E e : _set)
			last = e;
		return last;
	}
	
	public final E removeFirst()
	{
		for (Iterator<E> it = _set.iterator(); it.hasNext();)
		{
			final E value = it.next();
			if (_set.remove(value))
				return value;
		}
		
		return null;
	}
	
	public final E removeLast()
	{
		if (_set instanceof LinkedHashSet<E> ordered)
			return ordered.isEmpty() ? null : ordered.removeLast();
		
		final E value = getLast();
		return value != null && _set.remove(value) ? value : null;
	}
	
	public boolean addAll(E[] c)
	{
		boolean modified = false;
		
		for (E e : c)
			if (add(e))
				modified = true;
		
		return modified;
	}
	
	@Override
	public boolean add(E value)
	{
		return _set.add(value);
	}
	
	@Override
	public void clear()
	{
		_set.clear();
	}
	
	@Override
	public boolean contains(Object o)
	{
		return o != null && _set.contains(o);
	}
	
	@Override
	public boolean isEmpty()
	{
		return _set.isEmpty();
	}
	
	@Override
	public Iterator<E> iterator()
	{
		return _set.iterator();
	}
	
	@Override
	public boolean remove(Object o)
	{
		return o != null && _set.remove(o);
	}
	
	@Override
	public int size()
	{
		return _set.size();
	}
}
