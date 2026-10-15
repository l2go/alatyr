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
package com.l2jfree.util.logging;

import org.apache.commons.lang3.ArrayUtils;

/**
 * @author NB4L1
 */
public final class ListeningLog
{
	private ListeningLog()
	{
	}
	
	public interface LogListener
	{
		public void write(String message);
	}
	
	private static LogListener[] _listeners = new LogListener[0];
	
	public static void addListener(LogListener listener)
	{
		_listeners = ArrayUtils.add(_listeners, listener);
	}
	
	static void writeToListeners(String s)
	{
		for (LogListener listener : _listeners)
			listener.write(s);
	}
	
}
