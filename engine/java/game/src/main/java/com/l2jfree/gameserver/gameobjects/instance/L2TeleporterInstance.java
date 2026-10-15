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

import java.util.Calendar;
import java.util.StringTokenizer;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.TeleportLocationTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.instancemanager.TownManager;
import com.l2jfree.gameserver.model.L2TeleportLocation;
import com.l2jfree.gameserver.model.restriction.AvailableRestriction;
import com.l2jfree.gameserver.model.restriction.ObjectRestrictions;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

/**
 * @author NightMarez
 * @version $Revision: 1.3.2.2.2.5 $ $Date: 2005/03/27 15:29:32 $
 *
 */
public final class L2TeleporterInstance extends L2Npc
{
	private static final int COND_ALL_FALSE = 0;
	private static final int COND_BUSY_BECAUSE_OF_SIEGE = 1;
	private static final int COND_OWNER = 2;
	private static final int COND_REGULAR = 3;
	
	private static final int BIRTHDAY_HELPER = 32600;
	
	public L2TeleporterInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (ObjectRestrictions.getInstance().checkRestriction(player, AvailableRestriction.PlayerTeleport))
		{
			player.sendMessage("You cannot teleport due to a restriction.");
			return;
		}
		
		int condition = validateCondition(player);
		
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken(); // Get actual command
		
		if (actualCommand.equalsIgnoreCase("goto"))
		{
			int npcId = getTemplate().getNpcId();
			
			switch (npcId)
			{
				case 31095: //
				case 31096: //
				case 31097: //
				case 31098: // Enter Necropolises
				case 31099: //
				case 31100: //
				case 31101: //
				case 31102: //
					
				case 31114: //
				case 31115: //
				case 31116: // Enter Catacombs
				case 31117: //
				case 31118: //
				case 31119: //
					player.setIsIn7sDungeon(true);
					break;
				case 31103: //
				case 31104: //
				case 31105: //
				case 31106: // Exit Necropolises
				case 31107: //
				case 31108: //
				case 31109: //
				case 31110: //
					
				case 31120: //
				case 31121: //
				case 31122: // Exit Catacombs
				case 31123: //
				case 31124: //
				case 31125: //
					player.setIsIn7sDungeon(false);
					break;
			}
			
			if (st.countTokens() <= 0)
				return;
			
			int whereTo = Integer.parseInt(st.nextToken());
			if (condition == COND_REGULAR)
			{
				if (player != null)
					doTeleport(player, whereTo);
				return;
			}
			else if (condition == COND_OWNER)
			{
				int minPrivilegeLevel = 0; // NOTE: Replace 0 with highest level when privilege level is implemented
				if (st.countTokens() >= 1)
				{
					minPrivilegeLevel = Integer.parseInt(st.nextToken());
				}
				if (10 >= minPrivilegeLevel) // NOTE: Replace 10 with privilege level of player
					doTeleport(player, whereTo);
				else
					player.sendMessage("You don't have the sufficient access level to teleport there.");
				return;
			}
		}
		else if (actualCommand.equals("birthday"))
		{
			if (player.canReceiveAnnualPresent() == 0 || player.isGM())
			{
				L2NpcTemplate template = NpcTable.getInstance().getTemplate(BIRTHDAY_HELPER);
				if (template == null)
				{
					if (player.isGM())
						player.sendMessage("Missing NPC " + BIRTHDAY_HELPER);
					else
						player.sendPacket(SystemMessageId.TRY_AGAIN_LATER);
					return;
				}
				else if (!player.claimCreationPrize())
				{
					player.sendPacket(SystemMessageId.TRY_AGAIN_LATER);
					return;
				}
				L2Spawn spawn = new L2Spawn(template);
				spawn.setLocx(player.getX());
				spawn.setLocy(player.getY());
				spawn.setLocz(player.getZ() + 20);
				spawn.setAmount(1);
				spawn.setHeading(65535 - player.getHeading());
				spawn.setInstanceId(player.getInstanceId());
				L2BirthdayHelperInstance helper = (L2BirthdayHelperInstance)spawn.spawnOne(false);
				spawn.stopRespawn();
				helper.setOwner(player);
			}
			else
			{
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/teleporter/notbirthday.htm");
				player.sendPacket(html);
			}
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String pom = "";
		if (val == 0)
			pom = String.valueOf(npcId);
		else
			pom = npcId + "-" + val;
		
		return "data/html/teleporter/" + pom + ".htm";
	}
	
	@Override
	public void showChatWindow(L2Player player)
	{
		String filename = "data/html/teleporter/castleteleporter-no.htm";
		
		int condition = validateCondition(player);
		if (condition == COND_REGULAR)
		{
			super.showChatWindow(player);
			return;
		}
		else if (condition > COND_ALL_FALSE)
		{
			if (condition == COND_BUSY_BECAUSE_OF_SIEGE)
				filename = "data/html/teleporter/castleteleporter-busy.htm"; // Busy because of siege
			else if (condition == COND_OWNER) // Clan owns castle
				filename = getHtmlPath(getNpcId(), 0); // Owner message window
		}
		
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%npcname%", getName());
		player.sendPacket(html);
	}
	
	private void doTeleport(L2Player player, int val)
	{
		L2TeleportLocation list = TeleportLocationTable.getInstance().getTemplate(val);
		if (list != null)
		{
			if (isInsideZone(L2Zone.FLAG_TOWN))
			{
				// You cannot teleport to village that is in siege
				if (SiegeManager.getInstance().checkIfInZone(list.getLocX(), list.getLocY(), list.getLocZ()))
				{
					player.sendPacket(SystemMessageId.NO_PORT_THAT_IS_IN_SIGE);
					return;
				}
				if (TownManager.getInstance().townHasCastleInSiege(list.getLocX(), list.getLocY(), list.getLocZ()))
				{
					player.sendPacket(SystemMessageId.NO_PORT_THAT_IS_IN_SIGE);
					return;
				}
			}
			if (list.isForNoble() && !player.isNoble())
			{
				String filename = "data/html/teleporter/nobleteleporter-no.htm";
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile(filename);
				html.replace("%objectId%", String.valueOf(getObjectId()));
				html.replace("%npcname%", getName());
				player.sendPacket(html);
				return;
			}
			if (player.isAlikeDead())
				return;
			
			Calendar cal = Calendar.getInstance();
			int price = list.getPrice();
			// From CT2 all players lvl 40 or below (quote) have all ports for free
			if (player.getLevel() <= 40 && !getTemplate().isCustom())
				price = 0;
			
			// At weekend evening hours, teleport costs are / 2
			// But only adena teleports
			else if (!list.isForNoble())
			{
				if (cal.get(Calendar.HOUR_OF_DAY) >= 20 && cal.get(Calendar.HOUR_OF_DAY) <= 23
						&& (cal.get(Calendar.DAY_OF_WEEK) == 1 || cal.get(Calendar.DAY_OF_WEEK) == 7))
					price /= 2;
			}
			
			if (!list.isForNoble()
					&& (Config.ALT_GAME_FREE_TELEPORT || player.reduceAdena("Teleport", price, this, true)))
			{
				if (_log.isDebugEnabled())
					_log.debug("Teleporting player " + player.getName() + " to new location: " + list.getLocX() + ":"
							+ list.getLocY() + ":" + list.getLocZ());
				player.teleToLocation(list.getLocX(), list.getLocY(), list.getLocZ(), true);
			}
			else if (list.isForNoble()
					&& (Config.ALT_GAME_FREE_TELEPORT || player.destroyItemByItemId("Noble Teleport", 13722,
							list.getPrice(), this, true)))
			{
				if (_log.isDebugEnabled())
					_log.debug("Teleporting player " + player.getName() + " to new location: " + list.getLocX() + ":"
							+ list.getLocY() + ":" + list.getLocZ());
				player.teleToLocation(list.getLocX(), list.getLocY(), list.getLocZ());
			}
		}
		else
		{
			_log.warn("No teleport destination with id:" + val);
		}
	}
	
	private int validateCondition(L2Player player)
	{
		if (CastleManager.getInstance().getCastle(this) == null) // Teleporter isn't on castle ground
			return COND_REGULAR; // Regular access
		else if (getCastle().getSiege().getIsInProgress()) // Teleporter is on castle ground and siege is in progress
			return COND_BUSY_BECAUSE_OF_SIEGE; // Busy because of siege
		else if (player.getClan() != null) // Teleporter is on castle ground and player is in a clan
		{
			if (getCastle().getOwnerId() == player.getClanId()) // Clan owns castle
				return COND_OWNER; // Owner
		}
		
		return COND_ALL_FALSE;
	}
}
