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
package com.l2jfree.gameserver.handler.admincommands;

import java.io.File;

import com.l2jfree.Config;
import com.l2jfree.gameserver.cache.CrestCache;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;

/**
 * @author Layanere
 *
 */
public class AdminCache implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_cache_htm_rebuild", "admin_cache_htm_reload",
			"admin_cache_reload_path", "admin_cache_reload_file", "admin_cache_crest_rebuild",
			"admin_cache_crest_reload" };
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.startsWith("admin_cache_htm_rebuild") || command.equals("admin_cache_htm_reload"))
		{
			HtmCache.getInstance().reload(true);
			activeChar.sendMessage(HtmCache.getInstance().toString());
		}
		else if (command.startsWith("admin_cache_reload_path "))
		{
			try
			{
				HtmCache.getInstance().reloadPath(new File(Config.DATAPACK_ROOT, command.split(" ")[1]));
				activeChar.sendMessage(HtmCache.getInstance().toString());
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //cache_reload_path <path>");
			}
		}
		else if (command.startsWith("admin_cache_reload_file "))
		{
			try
			{
				String path = command.split(" ")[1];
				if (HtmCache.getInstance().loadFile(new File(Config.DATAPACK_ROOT, path)) != null)
				{
					activeChar.sendMessage("Cache[HTML]: file was loaded");
				}
				else
				{
					activeChar.sendMessage("Cache[HTML]: file can't be loaded");
				}
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //cache_reload_file <relative_path/file>");
			}
		}
		else if (command.startsWith("admin_cache_crest_rebuild") || command.startsWith("admin_cache_crest_reload"))
		{
			CrestCache.getInstance().reload();
			activeChar.sendMessage(CrestCache.getInstance().toString());
		}
		
		return true;
	}
}
