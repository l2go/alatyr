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

import java.util.StringTokenizer;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.instancemanager.TransformationManager;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.SetupGauge;

/**
 * This class handles following admin commands: - polymorph
 * 
 * @version $Revision: 1.2.2.1.2.4 $ $Date: 2007/07/31 10:05:56 $
 */
public class AdminPolymorph implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_polymorph", "admin_unpolymorph", "admin_polymorph_menu",
			"admin_unpolymorph_menu", "admin_transform", "admin_untransform", "admin_transform_menu",
			"admin_untransform_menu" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (activeChar.isMounted())
		{
			activeChar.sendMessage("You can't transform while mounted, please dismount and try again.");
			return false;
		}
		
		if (command.startsWith("admin_untransform"))
		{
			L2Object obj = activeChar.getTarget();
			if (obj instanceof L2Creature)
			{
				((L2Creature)obj).stopTransformation(true);
			}
			else
			{
				activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			}
		}
		else if (command.startsWith("admin_transform"))
		{
			L2Object obj = activeChar.getTarget();
			
			String[] parts = command.split(" ");
			if (parts.length == 2)
			{
				if (obj instanceof L2Player)
				{
					final L2Player cha = (L2Player)obj;
					try
					{
						final int id = Integer.parseInt(parts[1]);
						if (id == 0)
						{
							cha.untransform();
						}
						else
						{
							if (cha.getTransformation() != null)
								cha.stopTransformation(true);
							
							if (!TransformationManager.getInstance().transformPlayer(id, cha))
							{
								activeChar.sendMessage("Unknown transformation id: " + id);
							}
						}
					}
					catch (NumberFormatException e)
					{
						activeChar.sendMessage("Usage: //transform <id>");
					}
				}
				else
				{
					activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
				}
			}
		}
		else if (command.startsWith("admin_polymorph"))
		{
			StringTokenizer st = new StringTokenizer(command);
			L2Object target = activeChar.getTarget();
			try
			{
				st.nextToken();
				String p1 = st.nextToken();
				if (st.hasMoreTokens())
				{
					String p2 = st.nextToken();
					doPolymorph(activeChar, target, p2, p1);
				}
				else
					doPolymorph(activeChar, target, p1, "npc");
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //polymorph [type] <id>");
			}
		}
		else if (command.equals("admin_unpolymorph"))
		{
			doUnpoly(activeChar, activeChar.getTarget());
		}
		if (command.contains("_menu"))
		{
			showMainPage(activeChar, command);
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	@SuppressWarnings("deprecation")
	private void doPolymorph(L2Player activeChar, L2Object obj, String id, String type)
	{
		if (obj != null)
		{
			if (!obj.getPoly().setPolyInfo(type, id))
			{
				activeChar.sendMessage("Invalid ID specified.");
				return;
			}
			//animation
			if (obj instanceof L2Creature)
			{
				L2Creature Char = (L2Creature)obj;
				MagicSkillUse msk = new MagicSkillUse(Char, 1008, 1, 4000, 0);
				Char.broadcastPacket(msk);
				SetupGauge sg = new SetupGauge(0, 4000);
				Char.sendPacket(sg);
			}
			//end of animation
			obj.decayMe();
			obj.spawnMe(obj.getX(), obj.getY(), obj.getZ());
			activeChar.sendMessage("Polymorph succeed");
		}
		else
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
	}
	
	/**
	 * @param activeChar
	 * @param target
	 */
	private void doUnpoly(L2Player activeChar, L2Object target)
	{
		if (target != null && target.getPoly().isMorphed())
		{
			target.getPoly().setPolyInfo(null, "1");
			target.decayMe();
			target.spawnMe(target.getX(), target.getY(), target.getZ());
			activeChar.sendMessage("Unpolymorph succeed");
		}
		else
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
	}
	
	private void showMainPage(L2Player activeChar, String command)
	{
		if (command.contains("transform"))
			AdminHelpPage.showHelpPage(activeChar, "transform.htm");
		else if (command.contains("abnormal"))
			AdminHelpPage.showHelpPage(activeChar, "abnormal.htm");
		else
			AdminHelpPage.showHelpPage(activeChar, "effects_menu.htm");
	}
}
