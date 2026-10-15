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
package com.l2jfree.gameserver.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2EtcItemType;
import com.l2jfree.gameserver.network.packets.server.ExAutoSoulShot;
import com.l2jfree.gameserver.network.packets.server.ShortCutInit;
import com.l2jfree.gameserver.persistence.player.PlayerRepository;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.ShortcutRow;

public final class ShortCuts
{
	private static final Logger _log = LoggerFactory.getLogger(ShortCuts.class);
	
	private final Map<Integer, L2ShortCut> _shortCuts = new ConcurrentHashMap<Integer, L2ShortCut>();
	private final L2Player _owner;
	
	public ShortCuts(L2Player owner)
	{
		_owner = owner;
	}
	
	public L2ShortCut[] getAllShortCuts()
	{
		return _shortCuts.values().toArray(new L2ShortCut[_shortCuts.size()]);
	}
	
	public synchronized void registerShortCut(L2ShortCut shortcut)
	{
		_shortCuts.put(shortcut.getSlot() + 12 * shortcut.getPage(), shortcut);
		
		try
		{
			PlayerRepository.getInstance().saveShortcut(_owner.getObjectId(), _owner.getClassIndex(),
					new ShortcutRow(shortcut.getSlot(), shortcut.getPage(), shortcut.getType(), shortcut.getId(),
							shortcut.getLevel()));
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
	}
	
	public synchronized void deleteShortCut(int slot, int page)
	{
		L2ShortCut old = _shortCuts.remove(slot + page * 12);
		if (old == null)
			return;
		
		try
		{
			PlayerRepository.getInstance().deleteShortcut(_owner.getObjectId(), _owner.getClassIndex(), old.getPage(),
					old.getSlot());
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}

		if (old.getType() == L2ShortCut.TYPE_ITEM)
		{
			L2ItemInstance item = _owner.getInventory().getItemByObjectId(old.getId());
			
			if (item != null && item.getItemType() == L2EtcItemType.SHOT)
				_owner.getShots().removeAutoSoulShot(item.getItemId());
		}
		
		_owner.sendPacket(new ShortCutInit(_owner));
		
		for (int shotId : _owner.getShots().getAutoSoulShots())
			_owner.sendPacket(new ExAutoSoulShot(shotId, 1));
	}
	
	public synchronized void deleteShortCutByObjectId(int objectId)
	{
		for (L2ShortCut sc : _shortCuts.values())
			if (sc.getType() == L2ShortCut.TYPE_ITEM)
				if (sc.getId() == objectId)
					deleteShortCut(sc.getSlot(), sc.getPage());
	}
	
	public synchronized void restore()
	{
		_shortCuts.clear();
		
		try
		{
			for (ShortcutRow row : PlayerRepository.getInstance().loadShortcuts(_owner.getObjectId(),
					_owner.getClassIndex()))
			{
				_shortCuts.put(row.slot() + row.page() * 12, new L2ShortCut(row.slot(), row.page(), row.type(),
						row.targetId(), row.level(), 1));
			}
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		
		for (L2ShortCut sc : _shortCuts.values())
			if (sc.getType() == L2ShortCut.TYPE_ITEM)
				if (_owner.getInventory().getItemByObjectId(sc.getId()) == null)
					deleteShortCut(sc.getSlot(), sc.getPage());
	}
}
