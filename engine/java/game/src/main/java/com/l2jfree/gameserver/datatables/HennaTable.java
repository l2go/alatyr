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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.model.items.templates.L2Henna;
import com.l2jfree.gameserver.templates.StatsSet;

public class HennaTable
{
	private static final Logger _log = LoggerFactory.getLogger(HennaTable.class);
	private static final String LOAD_HENNA =
			"SELECT id, name, dye_item_template_id, dye_count, price, intelligence_bonus, strength_bonus, constitution_bonus, mental_bonus, dexterity_bonus, wit_bonus FROM henna";
	
	private final Map<Integer, L2Henna> _henna = new ConcurrentHashMap<Integer, L2Henna>();
	
	public static HennaTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private HennaTable()
	{
		restoreHennaData();
	}
	
	private void restoreHennaData()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement(LOAD_HENNA);
			ResultSet hennadata = statement.executeQuery();
			fillHennaTable(hennadata);
			hennadata.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("error while creating henna table!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void fillHennaTable(ResultSet hennaData) throws Exception
	{
		while (hennaData.next())
		{
			int id = hennaData.getInt("id");
			
			StatsSet hennaDat = new StatsSet();
			hennaDat.set("symbol_id", id);
			hennaDat.set("symbol_name", hennaData.getString("name"));
			hennaDat.set("dye_id", hennaData.getInt("dye_item_template_id"));
			hennaDat.set("price", hennaData.getInt("price"));
			hennaDat.set("dye_amount", hennaData.getInt("dye_count"));
			hennaDat.set("mod_INT", hennaData.getInt("intelligence_bonus"));
			hennaDat.set("mod_STR", hennaData.getInt("strength_bonus"));
			hennaDat.set("mod_CON", hennaData.getInt("constitution_bonus"));
			hennaDat.set("mod_MEN", hennaData.getInt("mental_bonus"));
			hennaDat.set("mod_DEX", hennaData.getInt("dexterity_bonus"));
			hennaDat.set("mod_WIT", hennaData.getInt("wit_bonus"));
			
			_henna.put(id, new L2Henna(hennaDat));
		}
		_log.info("HennaTable: Loaded " + _henna.size() + " Templates.");
	}
	
	public L2Henna getTemplate(int id)
	{
		return _henna.get(id);
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final HennaTable _instance = new HennaTable();
	}
}
