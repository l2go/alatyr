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
package com.l2jfree.gameserver.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.lang.L2Integer;
import com.l2jfree.util.L2Collections;

public final class CharNameTable
{
	private static final Logger _log = LoggerFactory.getLogger(CharNameTable.class);
	
	public static CharNameTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final Map<Integer, CharacterInfo> _mapByObjectId = new ConcurrentHashMap<Integer, CharacterInfo>();
	private final Map<String, CharacterInfo> _mapByName = new ConcurrentHashMap<String, CharacterInfo>();
	
	private CharNameTable()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			
			PreparedStatement statement =
					con.prepareStatement("SELECT id, account_name, name FROM player");
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
				update(rset.getInt("id"), rset.getString("account_name"), rset.getString("name"));
			
			rset.close();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		_log.info("CharNameTable: Loaded " + _mapByObjectId.size() + " character names.");
	}
	
	public String getByObjectId(Integer objectId)
	{
		CharacterInfo characterInfo = _mapByObjectId.get(objectId);
		
		return characterInfo == null ? null : characterInfo._name;
	}
	
	public Integer getByName(String name)
	{
		CharacterInfo characterInfo = _mapByName.get(name.toLowerCase());
		
		return characterInfo == null ? null : characterInfo._objectId;
	}
	
	public void update(int objectId, String accountName, String name)
	{
		CharacterInfo characterInfo = _mapByObjectId.get(objectId);
		if (characterInfo == null)
			characterInfo = new CharacterInfo(objectId);
		
		characterInfo.updateNames(accountName, name);
	}
	
	/**
	 * Forgets a deleted character, so that its name and its place on the account are free again.
	 */
	public void remove(int objectId)
	{
		CharacterInfo characterInfo = _mapByObjectId.remove(objectId);
		if (characterInfo != null && characterInfo._name != null)
			_mapByName.remove(characterInfo._name.toLowerCase());
	}
	
	private class CharacterInfo
	{
		private final Integer _objectId;
		
		private String _accountName;
		private String _name;
		
		private CharacterInfo(int objectId)
		{
			_objectId = L2Integer.valueOf(objectId);
			
			CharacterInfo characterInfo = _mapByObjectId.put(_objectId, this);
			if (characterInfo != null)
				_log.warn("CharNameTable: Duplicated objectId: [" + this + "] - [" + characterInfo + "]");
		}
		
		private void updateNames(String accountName, String name)
		{
			_accountName = accountName;
			
			if (_name != null)
				_mapByName.remove(_name.toLowerCase());
			
			_name = name.intern();
			
			CharacterInfo characterInfo = _mapByName.put(_name.toLowerCase(), this);
			if (characterInfo != null)
				_log.warn("CharNameTable: Duplicated hashName: [" + this + "] - [" + characterInfo + "]");
		}
		
		@Override
		public String toString()
		{
			return "objectId: " + _objectId + ", accountName: " + _accountName + ", name: " + _name;
		}
	}
	
	public boolean doesCharNameExist(String name)
	{
		return getByName(name) != null;
	}
	
	public int accountCharNumber(String account)
	{
		int count = 0;
		
		for (CharacterInfo characterInfo : _mapByObjectId.values())
			if (characterInfo._accountName.equalsIgnoreCase(account))
				count++;
		
		return count;
	}
	
	public Iterable<Integer> getObjectIdsForAccount(final String account)
	{
		return L2Collections.convertingIterable(_mapByObjectId.values(),
				new L2Collections.Converter<CharacterInfo, Integer>() {
					@Override
					public Integer convert(CharacterInfo characterInfo)
					{
						if (characterInfo._accountName.equalsIgnoreCase(account))
							return characterInfo._objectId;
						
						return null;
					}
				});
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final CharNameTable _instance = new CharNameTable();
	}
}
