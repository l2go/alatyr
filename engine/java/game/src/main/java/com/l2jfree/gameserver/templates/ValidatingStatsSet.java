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

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author NB4L1
 */
public class ValidatingStatsSet extends StatsSet
{
	private static final Logger _log = LoggerFactory.getLogger(ValidatingStatsSet.class);
	
	private final Map<String, Object> _nonRequestedStats = new LinkedHashMap<String, Object>();
	
	private String _description;
	private boolean _validating = true;
	
	public ValidatingStatsSet setDescription(String description)
	{
		_description = description;
		return this;
	}
	
	public ValidatingStatsSet setValidating(boolean validating)
	{
		_validating = validating;
		return this;
	}
	
	@Override
	protected Object get(String key)
	{
		_nonRequestedStats.remove(key);
		
		return super.get(key);
	}
	
	@Override
	protected Object put(String key, Object value)
	{
		_nonRequestedStats.put(key, value);
		
		final Object previousValue = super.put(key, value);
		
		if (previousValue != null)
			if (_validating)
				_log.info(_description + ": '" + key + "' has been replaced {" + previousValue + " -> " + value + "}!");
		
		return previousValue;
	}
	
	@Override
	public void clear()
	{
		for (Map.Entry<String, Object> entry : _nonRequestedStats.entrySet())
		{
			_log.info(_description + ": non-requested stat {" + entry.getKey() + " => " + entry.getValue() + "}!");
		}
		
		_nonRequestedStats.clear();
		
		super.clear();
	}
	
	@Override
	protected void finalize()
	{
		clear();
	}
}
