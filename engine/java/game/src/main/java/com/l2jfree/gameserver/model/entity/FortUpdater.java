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
package com.l2jfree.gameserver.model.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.model.clan.L2Clan;

/**
 * Vice - 2008 Class managing periodical events with castle
 */
public class FortUpdater implements Runnable
{
	protected static Logger _log = LoggerFactory.getLogger(FortUpdater.class);
	
	private final L2Clan _clan;
	private final Fort _fort;
	private int _runCount;
	
	public FortUpdater(Fort fort, L2Clan clan, int runCount)
	{
		_fort = fort;
		_clan = clan;
		_runCount = runCount;
	}
	
	@Override
	public void run()
	{
		_runCount++;
		if (_fort.getOwnerClan() == null || _fort.getOwnerClan() != _clan)
			return;
		
		_fort.setBloodOathReward(_fort.getBloodOathReward() + Config.FS_BLOOD_OATH_COUNT);
	}
}
