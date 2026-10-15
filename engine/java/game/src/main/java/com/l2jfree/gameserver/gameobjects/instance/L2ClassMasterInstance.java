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
package com.l2jfree.gameserver.gameobjects.instance;

import com.l2jfree.Config;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.datatables.CharTemplateTable;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.TutorialCloseHtml;
import com.l2jfree.gameserver.network.packets.server.TutorialShowHtml;
import com.l2jfree.gameserver.network.packets.server.TutorialShowQuestionMark;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;

public final class L2ClassMasterInstance extends L2NpcInstance
{
	/**
	 * @param template
	 */
	public L2ClassMasterInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String pom = "";
		
		if (val == 0)
			pom = "" + npcId;
		else
			pom = npcId + "-" + val;
		
		return "data/html/classmaster/" + pom + ".htm";
	}
	
	@Override
	public void onAction(L2Player player)
	{
		if (!canTarget(player))
			return;
		
		if (Config.ALT_L2J_CLASS_MASTER)
		{
			super.onAction(player);
			return;
		}
		
		// Check if the L2Player already target the L2NpcInstance
		if (getObjectId() != player.getTargetId())
		{
			// Set the target of the L2Player player
			player.setTarget(this);
		}
		else
		{
			if (!canInteract(player))
			{
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
				return;
			}
			
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			StringBuilder sb = new StringBuilder();
			sb.append("<html><body>");
			sb.append(getName() + ":<br>");
			sb.append("<br>");
			
			ClassId classId = player.getClassId();
			int level = player.getLevel();
			int jobLevel = classId.level();
			
			int newJobLevel = jobLevel + 1;
			
			if ((((level >= 20 && jobLevel == 0) || (level >= 40 && jobLevel == 1) || (level >= 76 && jobLevel == 2)) && Config.ALT_CLASS_MASTER_SETTINGS
					.isAllowed(newJobLevel)) || Config.ALT_CLASS_MASTER_STRIDER_UPDATE)
			{
				if (((level >= 20 && jobLevel == 0) || (level >= 40 && jobLevel == 1) || (level >= 76 && jobLevel == 2))
						&& Config.ALT_CLASS_MASTER_SETTINGS.isAllowed(newJobLevel))
				{
					sb.append("You can change your occupation to following:<br>");
					
					for (ClassId child : ClassId.values())
						if (child.childOf(classId) && child.level() == newJobLevel)
							sb.append("<br><a action=\"bypass -h npc_" + getObjectId() + "_change_class "
									+ (child.getId()) + "\"> " + CharTemplateTable.getClassNameById(child.getId())
									+ "</a>");
					
					if (Config.ALT_CLASS_MASTER_SETTINGS.getRequireItems(newJobLevel) != null
							&& !Config.ALT_CLASS_MASTER_SETTINGS.getRequireItems(newJobLevel).isEmpty())
					{
						sb.append("<br><br>Item(s) required for class change:");
						sb.append("<table width=270>");
						for (Integer _itemId : Config.ALT_CLASS_MASTER_SETTINGS.getRequireItems(newJobLevel).keySet())
						{
							int _count = Config.ALT_CLASS_MASTER_SETTINGS.getRequireItems(newJobLevel).get(_itemId);
							sb.append("<tr><td><font color=\"LEVEL\">" + _count + "</font></td><td>"
									+ ItemTable.getInstance().getTemplate(_itemId).getName() + "</td></tr>");
						}
						sb.append("</table>");
					}
				}
				
				if (Config.ALT_CLASS_MASTER_STRIDER_UPDATE)
				{
					sb.append("<table width=270>");
					sb.append("<tr><td><br></td></tr>");
					sb.append("<tr><td><a action=\"bypass -h npc_" + getObjectId()
							+ "_upgrade_hatchling\">Upgrade Hatchling to Strider</a></td></tr>");
					sb.append("</table>");
				}
				sb.append("<br>");
			}
			else
			{
				switch (jobLevel)
				{
					case 0:
						if (Config.ALT_CLASS_MASTER_SETTINGS.isAllowed(1))
							sb.append("Come back here when you reached level 20 to change your class.<br>");
						else if (Config.ALT_CLASS_MASTER_SETTINGS.isAllowed(2))
							sb.append("Come back after your first occupation change.<br>");
						else if (Config.ALT_CLASS_MASTER_SETTINGS.isAllowed(3))
							sb.append("Come back after your second occupation change.<br>");
						else
							sb.append("I can't change your occupation.<br>");
						break;
					case 1:
						if (Config.ALT_CLASS_MASTER_SETTINGS.isAllowed(2))
							sb.append("Come back here when you reached level 40 to change your class.<br>");
						else if (Config.ALT_CLASS_MASTER_SETTINGS.isAllowed(3))
							sb.append("Come back after your second occupation change.<br>");
						else
							sb.append("I can't change your occupation.<br>");
						break;
					case 2:
						if (Config.ALT_CLASS_MASTER_SETTINGS.isAllowed(3))
							sb.append("Come back here when you reached level 76 to change your class.<br>");
						else
							sb.append("I can't change your occupation.<br>");
						break;
					case 3:
						sb.append("There is no class change available for you anymore.<br>");
						break;
				}
				//If the player hasn't available class , he can change pet too...
				if (Config.ALT_CLASS_MASTER_STRIDER_UPDATE)
				{
					sb.append("<table width=270>");
					sb.append("<tr><td><br></td></tr>");
					sb.append("<tr><td><a action=\"bypass -h npc_" + getObjectId()
							+ "_upgrade_hatchling\">Upgrade Hatchling to Strider</a></td></tr>");
					sb.append("</table>");
				}
				sb.append("<br>");
			}
			
			for (Quest q : Quest.findAllEvents())
				sb.append("Event: <a action=\"bypass -h Quest " + q.getName() + "\">" + q.getDescr() + "</a><br>");
			sb.append("</body></html>");
			html.setHtml(sb.toString());
			player.sendPacket(html);
			
		}
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (Config.ALT_L2J_CLASS_MASTER)
		{
			if (command.startsWith("1stClass"))
			{
				showHtmlMenu(player, getObjectId(), 1);
			}
			else if (command.startsWith("2ndClass"))
			{
				showHtmlMenu(player, getObjectId(), 2);
			}
			else if (command.startsWith("3rdClass"))
			{
				showHtmlMenu(player, getObjectId(), 3);
			}
			else if (command.startsWith("change_class"))
			{
				int val = Integer.parseInt(command.substring(13));
				
				if (checkAndChangeClass(player, val))
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					html.setFile("data/html/classmaster/ok.htm");
					html.replace("%name%", CharTemplateTable.getClassNameById(val));
					player.sendPacket(html);
				}
			}
			else
			{
				super.onBypassFeedback(player, command);
			}
			return;
		}
		
		if (command.startsWith("change_class"))
		{
			int val = Integer.parseInt(command.substring(13));
			
			ClassId classId = player.getClassId();
			ClassId newClassId = ClassId.values()[val];
			
			int level = player.getLevel();
			int jobLevel = classId.level();
			int newJobLevel = newClassId.level();
			
			// -- Exploit prevention
			// Prevents changing if config option disabled
			if (!Config.ALT_CLASS_MASTER_SETTINGS.isAllowed(newJobLevel))
				return;
			
			// Prevents changing to class not in same class tree
			if (!newClassId.childOf(classId))
				return;
			
			// Prevents changing between same level jobs
			if (newJobLevel != jobLevel + 1)
				return;
			
			// Check for player level
			if (level < 20 && newJobLevel > 1)
				return;
			if (level < 40 && newJobLevel > 2)
				return;
			if (level < 76 && newJobLevel > 3)
				return;
			// -- Prevention ends
			
			if (!checkDestroyAndRewardItems(player, newJobLevel))
				return;
			
			changeClass(player, val);
			
			player.rewardSkills();
			
			if (newJobLevel == 3)
				// System sound 3rd occupation
				player.sendPacket(SystemMessageId.THIRD_CLASS_TRANSFER);
			else
				// System sound for 1st and 2nd occupation
				player.sendPacket(SystemMessageId.CLASS_TRANSFER);
			
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			StringBuilder sb = new StringBuilder();
			sb.append("<html><body>");
			sb.append(getName() + ":<br>");
			sb.append("<br>");
			sb.append("You have now become a <font color=\"LEVEL\">"
					+ CharTemplateTable.getClassNameById(player.getClassId().getId()) + "</font>.");
			sb.append("</body></html>");
			html.setHtml(sb.toString());
			player.sendPacket(html);
			
			// Update the overloaded status of the L2Player
			player.refreshOverloaded();
			// Update the expertise status of the L2Player
			player.refreshExpertisePenalty();
		}
		else if (command.startsWith("upgrade_hatchling") && Config.ALT_CLASS_MASTER_STRIDER_UPDATE)
		{
			boolean canUpgrade = false;
			if (player.getPet() != null)
			{
				if (player.getPet().getNpcId() == 12311 || player.getPet().getNpcId() == 12312
						|| player.getPet().getNpcId() == 12313)
				{
					if (player.getPet().getLevel() >= 55)
						canUpgrade = true;
					else
						player.sendMessage("The level of your hatchling is too low to be upgraded.");
				}
				else
					player.sendMessage("You have to summon your hatchling.");
			}
			else
				player.sendMessage("You have to summon your hatchling if you want to upgrade him.");
			
			if (!canUpgrade)
				return;
			
			int[] hatchCollar = { 3500, 3501, 3502 };
			int[] striderCollar = { 4422, 4423, 4424 };
			
			//TODO: Maybe show a complete list of all hatchlings instead of using first one
			for (int i = 0; i < 3; i++)
			{
				L2ItemInstance collar = player.getInventory().getItemByItemId(hatchCollar[i]);
				
				if (collar != null)
				{
					// Unsummon the hatchling
					player.getPet().unSummon(player);
					player.destroyItem("ClassMaster", collar, player, true);
					player.addItem("ClassMaster", striderCollar[i], 1, player, true, true);
					
					return;
				}
			}
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
	
	private void changeClass(L2Player player, int val)
	{
		if (_log.isDebugEnabled())
			_log.debug("Changing class to ClassId:" + val);
		player.setClassId(val);
		
		if (player.isSubClassActive())
			player.getSubClasses().get(player.getClassIndex()).setClassId(player.getActiveClass());
		else
			player.setBaseClass(player.getActiveClass());
		
		player.broadcastUserInfo();
		player.broadcastClassIcon();
	}
	
	private static boolean checkDestroyAndRewardItems(L2Player player, int newJobLevel)
	{
		// Check if player have all required items for class transfer
		for (Integer _itemId : Config.ALT_CLASS_MASTER_SETTINGS.getRequireItems(newJobLevel).keySet())
		{
			int _count = Config.ALT_CLASS_MASTER_SETTINGS.getRequireItems(newJobLevel).get(_itemId);
			if (player.getInventory().getInventoryItemCount(_itemId, -1) < _count)
			{
				player.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
				return false;
			}
		}
		
		// Get all required items for class transfer
		for (Integer _itemId : Config.ALT_CLASS_MASTER_SETTINGS.getRequireItems(newJobLevel).keySet())
		{
			int _count = Config.ALT_CLASS_MASTER_SETTINGS.getRequireItems(newJobLevel).get(_itemId);
			player.destroyItemByItemId("ClassMaster", _itemId, _count, player, true);
		}
		
		// Reward player with items
		for (Integer _itemId : Config.ALT_CLASS_MASTER_SETTINGS.getRewardItems(newJobLevel).keySet())
		{
			int _count = Config.ALT_CLASS_MASTER_SETTINGS.getRewardItems(newJobLevel).get(_itemId);
			player.addItem("ClassMaster", _itemId, _count, player, true);
		}
		
		return true;
	}
	
	// L2JServer CM methods below
	
	public static final void onTutorialLink(L2Player player, String request)
	{
		if (!Config.ALT_CLASS_MASTER_TUTORIAL || request == null || !request.startsWith("CO"))
			return;
		
		if (!FloodProtector.tryPerformAction(player, Protected.SUBCLASS))
			return;
		
		try
		{
			int val = Integer.parseInt(request.substring(2));
			checkAndChangeClass(player, val);
		}
		catch (Exception e)
		{
		}
		player.sendPacket(new TutorialCloseHtml());
	}
	
	public static final void onTutorialQuestionMark(L2Player player, int number)
	{
		if (!Config.ALT_CLASS_MASTER_TUTORIAL || number != 1001)
			return;
		
		showTutorialHtml(player);
	}
	
	public static final void showQuestionMark(L2Player player)
	{
		if (!Config.ALT_CLASS_MASTER_TUTORIAL)
			return;
		
		final ClassId classId = player.getClassId();
		if (getMinLevel(classId.level()) > player.getLevel())
			return;
		
		player.sendPacket(new TutorialShowQuestionMark(1001));
	}
	
	private static final void showHtmlMenu(L2Player player, int objectId, int level)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(objectId);
		
		if (!Config.ALT_L2J_CLASS_MASTER)
		{
			html.setFile("data/html/classmaster/disabled.htm");
		}
		else
		{
			final ClassId currentClassId = player.getClassId();
			if (currentClassId.level() >= level)
			{
				html.setFile("data/html/classmaster/nomore.htm");
			}
			else
			{
				final int minLevel = getMinLevel(currentClassId.level());
				if (player.getLevel() >= minLevel || Config.ALT_CLASS_MASTER_ENTIRE_TREE)
				{
					final StringBuilder menu = new StringBuilder(100);
					for (ClassId cid : ClassId.values())
					{
						if (validateClassId(currentClassId, cid) && cid.level() == level)
						{
							menu.append("<a action=\"bypass -h npc_%objectId%_change_class ");
							menu.append(cid.getId());
							menu.append("\">");
							menu.append(CharTemplateTable.getClassNameById(cid.getId()));
							menu.append("</a><br>");
						}
					}
					
					if (menu.length() > 0)
					{
						html.setFile("data/html/classmaster/template.htm");
						html.replace("%name%", CharTemplateTable.getClassNameById(currentClassId.getId()));
						html.replace("%menu%", menu.toString());
					}
					else
					{
						html.setFile("data/html/classmaster/comebacklater.htm");
						html.replace("%level%", String.valueOf(getMinLevel(level - 1)));
					}
				}
				else
				{
					if (minLevel < Integer.MAX_VALUE)
					{
						html.setFile("data/html/classmaster/comebacklater.htm");
						html.replace("%level%", String.valueOf(minLevel));
					}
					else
						html.setFile("data/html/classmaster/nomore.htm");
				}
			}
		}
		
		html.replace("%objectId%", String.valueOf(objectId));
		player.sendPacket(html);
	}
	
	private static final void showTutorialHtml(L2Player player)
	{
		final ClassId currentClassId = player.getClassId();
		int newJobLevel = currentClassId.level() + 1;
		if (getMinLevel(currentClassId.level()) > player.getLevel() && !Config.ALT_CLASS_MASTER_ENTIRE_TREE)
			return;
		
		String msg = HtmCache.getInstance().getHtm("data/html/classmaster/tutorialtemplate.htm");
		
		msg = msg.replaceAll("%name%", CharTemplateTable.getClassNameById(currentClassId.getId()));
		
		final StringBuilder menu = new StringBuilder(100);
		for (ClassId cid : ClassId.values())
		{
			if (validateClassId(currentClassId, cid))
			{
				menu.append("<a action=\"link CO");
				menu.append(cid.getId());
				menu.append("\">");
				menu.append(CharTemplateTable.getClassNameById(cid.getId()));
				menu.append("</a><br>");
			}
		}
		
		if (Config.ALT_CLASS_MASTER_SETTINGS.getRequireItems(newJobLevel) != null
				&& !Config.ALT_CLASS_MASTER_SETTINGS.getRequireItems(newJobLevel).isEmpty())
		{
			menu.append("<br><br>Item(s) required for class change:");
			menu.append("<table width=270>");
			for (Integer _itemId : Config.ALT_CLASS_MASTER_SETTINGS.getRequireItems(newJobLevel).keySet())
			{
				int _count = Config.ALT_CLASS_MASTER_SETTINGS.getRequireItems(newJobLevel).get(_itemId);
				menu.append("<tr><td><font color=\"LEVEL\">" + _count + "</font></td><td>"
						+ ItemTable.getInstance().getTemplate(_itemId).getName() + "</td></tr>");
			}
			menu.append("</table><br><br>");
		}
		
		msg = msg.replaceAll("%menu%", menu.toString());
		player.sendPacket(new TutorialShowHtml(msg));
	}
	
	private static final boolean checkAndChangeClass(L2Player player, int val)
	{
		final ClassId currentClassId = player.getClassId();
		int newJobLevel = currentClassId.level() + 1;
		if (getMinLevel(currentClassId.level()) > player.getLevel() && !Config.ALT_CLASS_MASTER_ENTIRE_TREE)
			return false;
		
		if (!validateClassId(currentClassId, val))
			return false;
		
		if (!checkDestroyAndRewardItems(player, newJobLevel))
			return false;
		
		player.setClassId(val);
		
		if (player.isSubClassActive())
			player.getSubClasses().get(player.getClassIndex()).setClassId(player.getActiveClass());
		else
			player.setBaseClass(player.getActiveClass());
		
		player.broadcastUserInfo();
		return true;
	}
	
	/**
	 * Returns minimum player level required for next class transfer
	 * @param level - current skillId level (0 - start, 1 - first, etc)
	 */
	private static final int getMinLevel(int level)
	{
		switch (level)
		{
			case 0:
				return 20;
			case 1:
				return 40;
			case 2:
				return 76;
			default:
				return Integer.MAX_VALUE;
		}
	}
	
	/**
	 * Returns true if class change is possible
	 * @param oldCID current player ClassId
	 * @param val new class index
	 * @return
	 */
	private static final boolean validateClassId(ClassId oldCID, int val)
	{
		try
		{
			return validateClassId(oldCID, ClassId.values()[val]);
		}
		catch (Exception e)
		{
			// possible ArrayOutOfBoundsException
		}
		return false;
	}
	
	/**
	 * Returns true if class change is possible
	 * @param oldCID current player ClassId
	 * @param newCID new ClassId
	 * @return true if class change is possible
	 */
	private static final boolean validateClassId(ClassId oldCID, ClassId newCID)
	{
		if (newCID == null || newCID.getRace() == null)
			return false;
		
		if (oldCID.equals(newCID.getParent()))
			return true;
		
		if (Config.ALT_CLASS_MASTER_ENTIRE_TREE && newCID.childOf(oldCID))
			return true;
		
		return false;
	}
}
