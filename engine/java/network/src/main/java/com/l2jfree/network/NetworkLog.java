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
package com.l2jfree.network;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A logger that coalesces equal records. A flooding client can make the transport log the same line thousands of times
 * per second, so a record is not written at once: it is counted, and once per flush interval one line "N x message"
 * is written for the records that repeated.
 * <p>
 * Records are equal when they have the same level, the same message, and a throwable of the same class and message.
 */
public final class NetworkLog
{
	private static enum Level
	{
		TRACE,
		DEBUG,
		INFO,
		WARN,
		ERROR;
	}
	
	private static final class Entry
	{
		private final Level _level;
		private final String _message;
		private final Throwable _throwable;
		
		private Entry(Level level, String message, Throwable throwable)
		{
			_level = level;
			_message = message;
			_throwable = throwable;
		}
		
		@Override
		public boolean equals(Object obj)
		{
			if (!(obj instanceof Entry))
				return false;
			
			final Entry entry = (Entry)obj;
			
			return _level == entry._level && _message.equals(entry._message) && sameThrowable(_throwable, entry._throwable);
		}
		
		private static boolean sameThrowable(Throwable t1, Throwable t2)
		{
			if (t1 == t2)
				return true;
			
			if (t1 == null || t2 == null)
				return false;
			
			return t1.getClass() == t2.getClass() && String.valueOf(t1.getMessage()).equals(String.valueOf(t2.getMessage()))
					&& sameThrowable(t1.getCause(), t2.getCause());
		}
		
		@Override
		public int hashCode()
		{
			int hash = _level.hashCode() * 31 + _message.hashCode();
			
			for (Throwable t = _throwable; t != null; t = t.getCause())
				hash = hash * 31 + t.getClass().hashCode() + String.valueOf(t.getMessage()).hashCode();
			
			return hash;
		}
	}
	
	private final Map<Entry, Integer> _entries = new LinkedHashMap<Entry, Integer>();
	private final Logger _logger;
	
	public NetworkLog(Class<?> clazz, int flushInterval)
	{
		_logger = LoggerFactory.getLogger(clazz);
		
		Maintenance.add(new Runnable() {
			@Override
			public void run()
			{
				flush();
			}
		}, flushInterval);
	}
	
	/**
	 * Writes the counted records now.
	 */
	public void flush()
	{
		for (;;)
		{
			final Entry entry;
			final int count;
			
			synchronized (_entries)
			{
				final java.util.Iterator<Map.Entry<Entry, Integer>> it = _entries.entrySet().iterator();
				
				if (!it.hasNext())
					break;
				
				final Map.Entry<Entry, Integer> first = it.next();
				entry = first.getKey();
				count = first.getValue();
				it.remove();
			}
			
			write(entry._level, count == 1 ? entry._message : count + " x " + entry._message, entry._throwable);
		}
	}
	
	private void write(Level level, String message, Throwable throwable)
	{
		switch (level)
		{
			case TRACE:
				_logger.trace(message, throwable);
				break;
			case DEBUG:
				_logger.debug(message, throwable);
				break;
			case INFO:
				_logger.info(message, throwable);
				break;
			case WARN:
				_logger.warn(message, throwable);
				break;
			default:
				_logger.error(message, throwable);
				break;
		}
	}
	
	private boolean isEnabled(Level level)
	{
		switch (level)
		{
			case TRACE:
				return _logger.isTraceEnabled();
			case DEBUG:
				return _logger.isDebugEnabled();
			case INFO:
				return _logger.isInfoEnabled();
			case WARN:
				return _logger.isWarnEnabled();
			default:
				return _logger.isErrorEnabled();
		}
	}
	
	private void log(Level level, String message, Throwable throwable)
	{
		if (!isEnabled(level))
			return;
		
		final Entry entry = new Entry(level, message, throwable);
		
		synchronized (_entries)
		{
			final Integer count = _entries.get(entry);
			
			_entries.put(entry, count == null ? 1 : count + 1);
		}
	}
	
	public void debug(Object message)
	{
		log(Level.DEBUG, String.valueOf(message), null);
	}
	
	public void debug(Object message, Throwable throwable)
	{
		log(Level.DEBUG, String.valueOf(message), throwable);
	}
	
	public void error(Object message)
	{
		log(Level.ERROR, String.valueOf(message), null);
	}
	
	public void error(Object message, Throwable throwable)
	{
		log(Level.ERROR, String.valueOf(message), throwable);
	}
	
	public void info(Object message)
	{
		log(Level.INFO, String.valueOf(message), null);
	}
	
	public void info(Object message, Throwable throwable)
	{
		log(Level.INFO, String.valueOf(message), throwable);
	}
	
	public void trace(Object message)
	{
		log(Level.TRACE, String.valueOf(message), null);
	}
	
	public void trace(Object message, Throwable throwable)
	{
		log(Level.TRACE, String.valueOf(message), throwable);
	}
	
	public void warn(Object message)
	{
		log(Level.WARN, String.valueOf(message), null);
	}
	
	public void warn(Object message, Throwable throwable)
	{
		log(Level.WARN, String.valueOf(message), throwable);
	}
	
	public boolean isDebugEnabled()
	{
		return isEnabled(Level.DEBUG);
	}
	
	public boolean isInfoEnabled()
	{
		return isEnabled(Level.INFO);
	}
	
	public boolean isWarnEnabled()
	{
		return isEnabled(Level.WARN);
	}
}
