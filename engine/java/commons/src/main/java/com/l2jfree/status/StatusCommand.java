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
package com.l2jfree.status;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.lang.L2Thread;

/**
 * @author NB4L1
 */
public abstract class StatusCommand
{
	protected static final Logger _log = LoggerFactory.getLogger(StatusCommand.class);
	
	protected abstract void useCommand(String command, String params);
	
	protected abstract String[] getCommands();
	
	protected final String listCommands()
	{
		return StringUtils.join(getCommands(), "|");
	}
	
	protected abstract String getDescription();
	
	protected String getParameterUsage()
	{
		return null;
	}
	
	protected final StatusThread getStatusThread()
	{
		return (StatusThread)Thread.currentThread();
	}
	
	protected final StatusCommand print(Object obj)
	{
		getStatusThread().print(obj);
		
		return this;
	}
	
	protected final StatusCommand println(Object obj)
	{
		getStatusThread().println(obj);
		
		return this;
	}
	
	protected final StatusCommand println()
	{
		getStatusThread().println();
		
		return this;
	}
	
	protected final String readLine() throws IOException
	{
		return getStatusThread().readLine();
	}
	
	protected String getHostAddress()
	{
		return getStatusThread().getSocket().getInetAddress().getHostAddress();
	}
	
	protected final void printMemoryStatistics()
	{
		for (String line : L2Thread.getMemoryUsageStatistics())
			println(line);
	}
}
