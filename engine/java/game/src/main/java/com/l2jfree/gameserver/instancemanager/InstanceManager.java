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
package com.l2jfree.gameserver.instancemanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.util.WorldFiles;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.model.entity.Instance;
import com.l2jfree.util.L2FastSet;
import com.l2jfree.util.LookupTable;

/**
 * @author evill33t, GodKratos
 * 
 */
public class InstanceManager
{
	private final static Logger _log = LoggerFactory.getLogger(InstanceManager.class);
	
	private final Map<Integer, Instance> _instanceList = new ConcurrentHashMap<Integer, Instance>();
	private final Map<Integer, InstanceWorld> _instanceWorlds = new ConcurrentHashMap<Integer, InstanceWorld>();
	
	private final AtomicInteger _instanceIds = new AtomicInteger(300000);
	
	// InstanceId Names
	private final LookupTable<String> _instanceIdNames = new LookupTable<String>();
	private final Map<Integer, Map<Integer, Long>> _playerInstanceTimes = new ConcurrentHashMap<Integer, Map<Integer, Long>>();
	
	private static final String ADD_INSTANCE_TIME =
			"INSERT INTO player_instance_reentry (player_id, instance_template_id, reenter_at) VALUES (?, ?, ?) "
					+ "ON CONFLICT (player_id, instance_template_id) DO UPDATE SET reenter_at = EXCLUDED.reenter_at";
	private static final String RESTORE_INSTANCE_TIMES =
			"SELECT instance_template_id, reenter_at FROM player_instance_reentry WHERE player_id = ?";
	private static final String DELETE_INSTANCE_TIME =
			"DELETE FROM player_instance_reentry WHERE player_id = ? AND instance_template_id = ?";
	
	public long getInstanceTime(int playerObjId, int id)
	{
		if (!_playerInstanceTimes.containsKey(playerObjId))
			restoreInstanceTimes(playerObjId);
		if (_playerInstanceTimes.get(playerObjId).containsKey(id))
			return _playerInstanceTimes.get(playerObjId).get(id);
		return -1;
	}
	
	public Map<Integer, Long> getAllInstanceTimes(int playerObjId)
	{
		if (!_playerInstanceTimes.containsKey(playerObjId))
			restoreInstanceTimes(playerObjId);
		return _playerInstanceTimes.get(playerObjId);
	}
	
	public void setInstanceTime(int playerObjId, int id, long time)
	{
		if (!_playerInstanceTimes.containsKey(playerObjId))
			restoreInstanceTimes(playerObjId);
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = null;
			statement = con.prepareStatement(ADD_INSTANCE_TIME);
			statement.setInt(1, playerObjId);
			statement.setInt(2, id);
			statement.setTimestamp(3, new Timestamp(time));
			statement.execute();
			statement.close();
			_playerInstanceTimes.get(playerObjId).put(id, time);
		}
		catch (Exception e)
		{
			_log.warn("Could not insert character instance time data: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void deleteInstanceTime(int playerObjId, int id)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = null;
			statement = con.prepareStatement(DELETE_INSTANCE_TIME);
			statement.setInt(1, playerObjId);
			statement.setInt(2, id);
			statement.execute();
			statement.close();
			_playerInstanceTimes.get(playerObjId).remove(id);
		}
		catch (Exception e)
		{
			_log.warn("Could not delete character instance time data: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public void restoreInstanceTimes(int playerObjId)
	{
		if (_playerInstanceTimes.containsKey(playerObjId))
			return; // already restored
		_playerInstanceTimes.put(playerObjId, new LinkedHashMap<Integer, Long>());
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(RESTORE_INSTANCE_TIMES);
			statement.setInt(1, playerObjId);
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				int id = rset.getInt("instance_template_id");
				long time = rset.getTimestamp("reenter_at").getTime();
				if (time < System.currentTimeMillis())
					deleteInstanceTime(playerObjId, id);
				else
					_playerInstanceTimes.get(playerObjId).put(id, time);
			}
			
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Could not delete character instance time data: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public String getInstanceIdName(int id)
	{
		if (_instanceIdNames.containsKey(id))
			return _instanceIdNames.get(id);
		return ("UnknownInstance");
	}
	
	/**
	 * Reads the {@code instance} elements of an instance names document (id and name attributes) into the table.
	 */
	static void parseInstanceNames(InputStream in, LookupTable<String> names) throws XMLStreamException
	{
		XMLInputFactory factory = XMLInputFactory.newFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		XMLStreamReader xpp = factory.createXMLStreamReader(in);
		for (int e = xpp.getEventType(); e != XMLStreamConstants.END_DOCUMENT; e = xpp.next())
		{
			if (e == XMLStreamConstants.START_ELEMENT)
			{
				if (xpp.getLocalName().equals("instance"))
				{
					Integer id = Integer.valueOf(xpp.getAttributeValue(null, "id"));
					String name = xpp.getAttributeValue(null, "name");
					names.put(id, name);
				}
			}
		}
	}
	
	private void loadInstanceNames()
	{
		File file = new File(Config.DATAPACK_ROOT + "/data/instancenames.xml");
		if (WorldFiles.skip(_log, "instance names", file))
			return;
		
		InputStream in = null;
		try
		{
			in = new FileInputStream(file);
			parseInstanceNames(in, _instanceIdNames);
		}
		catch (FileNotFoundException e)
		{
			WorldFiles.unreadable(_log, "instance names", file, e);
		}
		catch (XMLStreamException xppe)
		{
			xppe.printStackTrace();
		}
		finally
		{
			try
			{
				if (in != null)
					in.close();
			}
			catch (Exception e)
			{
			}
		}
	}
	
	public class InstanceWorld
	{
		public int instanceId;
		public int templateId = -1;
		public final L2FastSet<Integer> allowed = new L2FastSet<Integer>().setShared(true);
		public int status;
	}
	
	public void addWorld(InstanceWorld world)
	{
		_instanceWorlds.put(world.instanceId, world);
	}
	
	public InstanceWorld getWorld(int instanceId)
	{
		return _instanceWorlds.get(instanceId);
	}
	
	public InstanceWorld getPlayerWorld(L2Player player)
	{
		for (InstanceWorld iw : _instanceWorlds.values())
		{
			// check if the player have a World Instance where he/she is allowed to enter
			if (iw.allowed.contains(player.getObjectId()))
				return iw;
		}
		return null;
	}
	
	public Instance getDynamicInstance(L2Player player)
	{
		for (Instance i : _instanceList.values())
		{
			// check if the player is in a dynamic instance
			if (i.containsPlayer(player.getObjectId()))
				return i;
		}
		return null;
	}
	
	public static final InstanceManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private InstanceManager()
	{
		_log.info("Initializing InstanceManager");
		loadInstanceNames();
		_log.info("Loaded " + _instanceIdNames.size() + " instance names");
		
		Instance themultiverse = new Instance(-1);
		themultiverse.setName("multiverse");
		_instanceList.put(themultiverse.getId(), themultiverse);
		_log.info("Multiverse Instance created");
		
		Instance universe = new Instance(0);
		universe.setName("universe");
		_instanceList.put(universe.getId(), universe);
		_log.info("Universe Instance created");
	}
	
	public void destroyInstance(int instanceid)
	{
		if (instanceid == 0)
			return;
		Instance temp = _instanceList.get(instanceid);
		if (temp != null)
		{
			temp.removeNpcs();
			temp.removePlayers();
			temp.removeDoors();
			temp.cancelTimer();
			_instanceList.remove(instanceid);
			_instanceWorlds.remove(instanceid);
			GeoData.getInstance().deleteInstanceGeodata(instanceid);
		}
	}
	
	public Instance getInstance(int instanceid)
	{
		return _instanceList.get(instanceid);
	}
	
	public Map<Integer, Instance> getInstances()
	{
		return _instanceList;
	}
	
	@Deprecated
	public boolean createInstance(int id)
	{
		if (getInstance(id) != null)
			return false;
		
		Instance instance = new Instance(id);
		_instanceList.put(instance.getId(), instance);
		return true;
	}
	
	@Deprecated
	public boolean createInstanceFromTemplate(int id, String template)
	{
		if (getInstance(id) != null)
			return false;
		
		Instance instance = Instance.createInstance(id, template);
		_instanceList.put(instance.getId(), instance);
		
		return true;
	}
	
	public int createDynamicInstance(String template)
	{
		Instance instance = Instance.createInstance(_instanceIds.incrementAndGet(), template);
		_instanceList.put(instance.getId(), instance);
		
		return instance.getId();
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final InstanceManager _instance = new InstanceManager();
	}
}
