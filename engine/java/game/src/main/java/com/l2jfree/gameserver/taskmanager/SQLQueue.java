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
package com.l2jfree.gameserver.taskmanager;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.sql.SQLQueryQueue;

/**
 * @author NB4L1
 */
public final class SQLQueue extends SQLQueryQueue
{
	private static final Logger _log = LoggerFactory.getLogger(SQLQueue.class);
	
	private static final class SingletonHolder
	{
		private static final SQLQueue INSTANCE = new SQLQueue();
	}
	
	public static SQLQueue getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private SQLQueue()
	{
		ThreadPoolManager.getInstance().scheduleAtFixedRate(this, 60000, 60000);
		
		_log.info("SQLQueryQueue: Initialized.");
	}
	
	@Override
	protected Connection getConnection() throws SQLException
	{
		return L2DatabaseFactory.getInstance().getConnection();
	}
}
