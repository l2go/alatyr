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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.util.WorldFiles;
import com.l2jfree.gameserver.gameobjects.instance.L2AirShipControllerInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2AirShipInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2CreatureTemplate;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.templates.StatsSet;

public class AirShipManager
{
	private final static Logger _log = LoggerFactory.getLogger(AirShipManager.class);
	
	private L2AirShipInstance _airShip = null;
	
	private final ArrayList<L2AirShipControllerInstance> _atcs = new ArrayList<L2AirShipControllerInstance>(2);
	
	public static AirShipManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private AirShipManager()
	{
		load();
	}
	
	private final void load()
	{
		BufferedReader lnr = null;
		try
		{
			File doorData = new File(Config.DATAPACK_ROOT, "data/airship.csv");
			if (WorldFiles.skip(_log, "airships", doorData))
				return;
			lnr = new BufferedReader(new FileReader(doorData));
			
			String line = null;
			while ((line = lnr.readLine()) != null)
			{
				if (line.trim().length() == 0 || line.startsWith("#"))
					continue;
				L2AirShipInstance airShip = parseLine(line);
				airShip.spawn();
				_airShip = airShip;
				if (_log.isDebugEnabled())
					_log.info("AirShip ID : " + airShip.getObjectId());
			}
		}
		catch (FileNotFoundException e)
		{
			WorldFiles.unreadable(_log, "airships", new File(Config.DATAPACK_ROOT, "data/airship.csv"), e);
		}
		catch (Exception e)
		{
			_log.warn("error while creating AirShip table ", e);
		}
		finally
		{
			try
			{
				if (lnr != null)
					lnr.close();
			}
			catch (Exception e1)
			{ /* ignore problems */
			}
		}
	}
	
	/**
	 * @param line
	 * @return
	 */
	private L2AirShipInstance parseLine(String line)
	{
		L2AirShipInstance airShip;
		StringTokenizer st = new StringTokenizer(line, ";");
		
		int xspawn = Integer.parseInt(st.nextToken());
		int yspawn = Integer.parseInt(st.nextToken());
		int zspawn = Integer.parseInt(st.nextToken());
		int heading = Integer.parseInt(st.nextToken());
		
		StatsSet npcDat = new StatsSet();
		npcDat.set("npcId", 9);
		npcDat.set("level", 0);
		npcDat.set("jClass", "boat");
		
		npcDat.set("baseSTR", 0);
		npcDat.set("baseCON", 0);
		npcDat.set("baseDEX", 0);
		npcDat.set("baseINT", 0);
		npcDat.set("baseWIT", 0);
		npcDat.set("baseMEN", 0);
		
		npcDat.set("baseShldDef", 0);
		npcDat.set("baseShldRate", 0);
		npcDat.set("baseAccCombat", 38);
		npcDat.set("baseEvasRate", 38);
		npcDat.set("baseCritRate", 38);
		
		// npcDat.set("name", "");
		npcDat.set("collision_radius", 0);
		npcDat.set("collision_height", 0);
		npcDat.set("fcollision_radius", 0);
		npcDat.set("fcollision_height", 0);
		
		npcDat.set("sex", "male");
		npcDat.set("type", "");
		npcDat.set("baseAtkRange", 0);
		npcDat.set("baseMpMax", 0);
		npcDat.set("baseCpMax", 0);
		npcDat.set("rewardExp", 0);
		npcDat.set("rewardSp", 0);
		npcDat.set("basePAtk", 0);
		npcDat.set("baseMAtk", 0);
		npcDat.set("basePAtkSpd", 0);
		npcDat.set("aggroRange", 0);
		npcDat.set("baseMAtkSpd", 0);
		npcDat.set("rhand", 0);
		npcDat.set("lhand", 0);
		npcDat.set("armor", 0);
		npcDat.set("baseWalkSpd", 0);
		npcDat.set("baseRunSpd", 0);
		npcDat.set("name", "AirShip");
		npcDat.set("baseHpMax", 50000);
		npcDat.set("baseHpReg", 3.e-3f);
		npcDat.set("baseMpReg", 3.e-3f);
		npcDat.set("basePDef", 100);
		npcDat.set("baseMDef", 100);
		L2CreatureTemplate template = new L2CreatureTemplate(npcDat);
		airShip = new L2AirShipInstance(IdFactory.getInstance().getNextId(), template);
		airShip.setIsFlying(true);
		airShip.getPosition().setHeading(heading);
		airShip.getPosition().setXYZ(xspawn, yspawn, zspawn);
		airShip.setTrajet1(1);
		airShip.setTrajet2(2);
		airShip.setTrajet3(3);
		airShip.setTrajet4(4);
		return airShip;
	}
	
	public L2AirShipInstance getAirShip()
	{
		return _airShip;
	}
	
	public void registerATC(L2AirShipControllerInstance atc)
	{
		_atcs.add(atc);
	}
	
	public ArrayList<L2AirShipControllerInstance> getATCs()
	{
		return _atcs;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final AirShipManager _instance = new AirShipManager();
	}
}
