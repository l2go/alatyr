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

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.persistence.clan.ClanRepository;
import com.l2jfree.gameserver.persistence.clan.ClanRepository.CrestRecord;

/**
 * The crest images of clans and alliances. They are stored in the database (table crest) and kept in memory.
 * 
 * @author Layane
 */
public class CrestCache
{
	private static final Logger _log = LoggerFactory.getLogger(CrestCache.class);
	
	public static CrestCache getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final Map<Integer, byte[]> _cachePledge = new ConcurrentHashMap<Integer, byte[]>();
	private final Map<Integer, byte[]> _cachePledgeLarge = new ConcurrentHashMap<Integer, byte[]>();
	private final Map<Integer, byte[]> _cacheAlly = new ConcurrentHashMap<Integer, byte[]>();
	
	private int _loadedFiles;
	private long _bytesBuffLen;
	
	private CrestCache()
	{
		reload();
	}
	
	public synchronized void reload()
	{
		_loadedFiles = 0;
		_bytesBuffLen = 0;
		
		_cachePledge.clear();
		_cachePledgeLarge.clear();
		_cacheAlly.clear();
		
		try
		{
			List<CrestRecord> crests = ClanRepository.getInstance().loadCrests();
			for (CrestRecord crest : crests)
			{
				if (ClanRepository.KIND_CLAN_LARGE.equals(crest.kind()))
					_cachePledgeLarge.put(crest.id(), crest.image());
				else if (ClanRepository.KIND_CLAN.equals(crest.kind()))
					_cachePledge.put(crest.id(), crest.image());
				else if (ClanRepository.KIND_ALLIANCE.equals(crest.kind()))
					_cacheAlly.put(crest.id(), crest.image());
				else
					continue;
				_loadedFiles++;
				_bytesBuffLen += crest.image().length;
			}
		}
		catch (SQLException e)
		{
			_log.warn("Problem with loading the crests from the database", e);
		}
		
		_log.info(String.valueOf(this));
	}
	
	@Override
	public String toString()
	{
		return "Cache[Crest]: " + String.format("%.3f", (float)_bytesBuffLen / 1048576) + " megabytes on "
				+ _loadedFiles + " crest(s) loaded.";
	}
	
	public int getLoadedFiles()
	{
		return _loadedFiles;
	}
	
	public byte[] getPledgeCrest(int id)
	{
		return _cachePledge.get(id);
	}
	
	public byte[] getPledgeCrestLarge(int id)
	{
		return _cachePledgeLarge.get(id);
	}
	
	public byte[] getAllyCrest(int id)
	{
		return _cacheAlly.get(id);
	}
	
	public void removePledgeCrest(int id)
	{
		_cachePledge.remove(id);
		try
		{
			ClanRepository.getInstance().deleteCrest(id, ClanRepository.KIND_CLAN);
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
	}
	
	public boolean removePledgeCrestLarge(int id)
	{
		boolean deleted;
		try
		{
			deleted = ClanRepository.getInstance().deleteCrest(id, ClanRepository.KIND_CLAN_LARGE);
		}
		catch (Exception e)
		{
			_log.warn("", e);
			return false;
		}
		return (_cachePledgeLarge.remove(id) != null) || deleted;
	}
	
	public void removeAllyCrest(int id)
	{
		_cacheAlly.remove(id);
		try
		{
			ClanRepository.getInstance().deleteCrest(id, ClanRepository.KIND_ALLIANCE);
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
	}
	
	public boolean savePledgeCrest(int newId, byte[] data)
	{
		try
		{
			ClanRepository.getInstance().saveCrest(newId, ClanRepository.KIND_CLAN, data);
			_cachePledge.put(newId, data);
			return true;
		}
		catch (SQLException e)
		{
			_log.info("Error saving pledge crest " + newId + ":", e);
			return false;
		}
	}
	
	public boolean savePledgeCrestLarge(int newId, byte[] data)
	{
		try
		{
			ClanRepository.getInstance().saveCrest(newId, ClanRepository.KIND_CLAN_LARGE, data);
			_cachePledgeLarge.put(newId, data);
			return true;
		}
		catch (SQLException e)
		{
			_log.info("Error saving Large pledge crest " + newId + ":", e);
			return false;
		}
	}
	
	public boolean saveAllyCrest(int newId, byte[] data)
	{
		try
		{
			ClanRepository.getInstance().saveCrest(newId, ClanRepository.KIND_ALLIANCE, data);
			_cacheAlly.put(newId, data);
			return true;
		}
		catch (SQLException e)
		{
			_log.info("Error saving ally crest " + newId + ":", e);
			return false;
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final CrestCache _instance = new CrestCache();
	}
}
