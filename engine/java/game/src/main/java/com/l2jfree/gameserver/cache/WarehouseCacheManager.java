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
package com.l2jfree.gameserver.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Player;

/**
 * @author -Nemesiss-
 */
public final class WarehouseCacheManager implements Runnable
{
	private static final Logger _log = LoggerFactory.getLogger(WarehouseCacheManager.class);
	
	public static WarehouseCacheManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final Map<L2Player, Long> _cache = new LinkedHashMap<L2Player, Long>();
	
	private WarehouseCacheManager()
	{
		ThreadPoolManager.getInstance().scheduleAtFixedRate(this, 60000, 60000);
		
		_log.info("WarehouseCacheManager: Initialized.");
	}
	
	public synchronized void add(L2Player player)
	{
		_cache.put(player, System.currentTimeMillis());
	}
	
	public synchronized void remove(L2Player player)
	{
		_cache.remove(player);
	}
	
	@Override
	public synchronized void run()
	{
		for (Iterator<Map.Entry<L2Player, Long>> it = _cache.entrySet().iterator(); it.hasNext();)
		{
			final Map.Entry<L2Player, Long> entry = it.next();
			if (System.currentTimeMillis() > entry.getValue() + Config.WAREHOUSE_CACHE_TIME * 60000L)
			{
				final L2Player player = entry.getKey();
				
				player.clearWarehouse();
				
				it.remove();
			}
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final WarehouseCacheManager _instance = new WarehouseCacheManager();
	}
}
