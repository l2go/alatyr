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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.TeleportLocationTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.CastleManorManager;
import com.l2jfree.gameserver.model.L2TeleportLocation;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ExShowCropInfo;
import com.l2jfree.gameserver.network.packets.server.ExShowCropSetting;
import com.l2jfree.gameserver.network.packets.server.ExShowManorDefaultInfo;
import com.l2jfree.gameserver.network.packets.server.ExShowSeedInfo;
import com.l2jfree.gameserver.network.packets.server.ExShowSeedSetting;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.util.IllegalPlayerAction;
import com.l2jfree.gameserver.util.Util;

/**
 * Castle Chamberlains implementation used for: - tax rate control - regional
 * manor system control - castle treasure control - ...
 */
public class L2CastleChamberlainInstance extends L2MerchantInstance
{
	protected static final int COND_ALL_FALSE = 0;
	protected static final int COND_BUSY_BECAUSE_OF_SIEGE = 1;
	protected static final int COND_OWNER = 2;
	
	private static final String[] SET_TIME = { "<a action=\"bypass -h npc_%objectId%_siege_time_set 3 ", "\">", ":00 ",
			"</a><br>" };
	
	private int _preDay;
	private int _preHour;
	
	private final NpcHtmlMessage NO_AUTH;
	
	public L2CastleChamberlainInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
		NO_AUTH = new NpcHtmlMessage(getObjectId());
		NO_AUTH.setFile("data/html/chamberlain/chamberlain-noprivs.htm");
	}
	
	private void sendHtmlMessage(L2Player player, NpcHtmlMessage html)
	{
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%npcId%", String.valueOf(getNpcId()));
		player.sendPacket(html);
	}
	
	@Override
	public void onAction(L2Player player)
	{
		if (!canTarget(player))
			return;
		
		player.setLastFolkNPC(this);
		
		// Check if the L2Player already target the L2NpcInstance
		if (this != player.getTarget())
		{
			// Set the target of the L2Player player
			player.setTarget(this);
		}
		else
		{
			// Calculate the distance between the L2Player and the L2NpcInstance
			if (!canInteract(player))
			{
				// Notify the L2Player AI with AI_INTENTION_INTERACT
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
			}
			else
			{
				showMessageWindow(player);
			}
		}
		// Send a Server->Client ActionFailed to the L2Player in order to
		// avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		// BypassValidation Exploit plug.
		if (player.getLastFolkNPC().getObjectId() != getObjectId())
			return;
		
		if (player.getActiveEnchantItem() != null)
		{
			Util.handleIllegalPlayerAction(player, "Player " + player.getName()
					+ " trying to use enchant exploit, ban this player!", IllegalPlayerAction.PUNISH_KICK);
			return;
		}
		
		SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm");
		int condition = validateCondition(player);
		if (condition <= COND_ALL_FALSE)
			return;
		else if (condition == COND_OWNER)
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			String actualCommand = st.nextToken(); // Get actual command
			
			String val = "";
			if (st.countTokens() >= 1)
			{
				val = st.nextToken();
			}
			
			//Take note: it is better to check privileges in each command
			//because that way it is easier for developers to understand the purpose
			//of various CP_CS values
			if (actualCommand.equals("banish_foreigner"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_DISMISS))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				getCastle().banishForeigners(); // Move non-clan members off castle area
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/chamberlain/chamberlain-banishafter.htm");
				html.replace("%objectId%", String.valueOf(getObjectId()));
				player.sendPacket(html);
				return;
			}
			else if (actualCommand.equals("banish_foreigner_show"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_DISMISS))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/chamberlain/chamberlain-banishfore.htm");
				html.replace("%objectId%", String.valueOf(getObjectId()));
				player.sendPacket(html);
				return;
			}
			else if (actualCommand.equals("list_siege_clans"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_MANAGE_SIEGE))
					return;
				
				if (getCastle().getSiege().getIsInProgress())
				{
					if (player.isClanLeader())
					{
						if (player.isNoble())
							getCastle().getSiege().listRegisterClan(player);
						else
							player.sendPacket(SystemMessageId.ONLY_NOBLESSE_LEADER_CAN_VIEW_SIEGE_STATUS_WINDOW);
					}
					else
					{
						NpcHtmlMessage siege = new NpcHtmlMessage(getObjectId());
						siege.setFile("data/html/chamberlain/chamberlain-siege.htm");
						siege.replace("%objectId%", String.valueOf(getObjectId()));
						player.sendPacket(siege);
					}
				}
				else
					getCastle().getSiege().listRegisterClan(player);
			}
			else if (actualCommand.equals("receive_report"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_USE_FUNCTIONS))
					return;
				
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				if (!getCastle().getSiege().getIsInProgress())
				{
					html.setFile("data/html/chamberlain/chamberlain-report.htm");
					html.replace("%castlename%", getCastle().getName());
					L2Clan clan = ClanTable.getInstance().getClan(getCastle().getOwnerId());
					if (clan != null)
					{
						html.replace("%clanname%", clan.getName());
						html.replace("%clanleadername%", clan.getLeaderName());
					}
					else
					// avoid NPE in GM view when castle belongs to NPCs!
					{
						html.replace("%clanname%", "NPC");
						html.replace("%clanleadername%", "");
					}
					html.replace("%ss_event%", SevenSigns.getInstance().getCurrentPeriodName());
					html.replace("%ss_avarice%",
							SevenSigns.getCabalName(SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_AVARICE)));
					html.replace("%ss_gnosis%",
							SevenSigns.getCabalName(SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_GNOSIS)));
					html.replace("%ss_strife%",
							SevenSigns.getCabalName(SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE)));
				}
				else
					html.setFile("data/html/chamberlain/chamberlain-report-siege.htm");
				html.replace("%objectId%", String.valueOf(getObjectId()));
				player.sendPacket(html);
			}
			else if (actualCommand.equals("items"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_USE_FUNCTIONS))
					return;
				if (siegeBlocksFunction(player))
					return;
				if (val.isEmpty())
					return;
				
				player.tempInventoryDisable();
				if (_log.isDebugEnabled())
					_log.debug("Showing chamberlain buylist");
				showBuyWindow(player, Integer.parseInt(val + "1"));
			}
			else if (actualCommand.equals("manage_siege_defender"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_MANAGE_SIEGE))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/chamberlain/chamberlain-defender.htm");
				html.replace("%objectId%", String.valueOf(getObjectId()));
				player.sendPacket(html);
			}
			else if (actualCommand.equals("manage_vault"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_TAXES))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				String filename = "data/html/chamberlain/chamberlain-vault.htm";
				long amount = 0;
				if (val.equals("deposit"))
				{
					try
					{
						amount = Long.parseLong(st.nextToken());
					}
					catch (NoSuchElementException e)
					{
					}
					if (amount > 0 && getCastle().getTreasury() + amount < PlayerInventory.MAX_ADENA)
					{
						if (player.reduceAdena("Castle", amount, this, true))
							getCastle().addToTreasuryNoTax(amount);
						else
							sendPacket(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
					}
				}
				else if (val.equals("withdraw"))
				{
					try
					{
						amount = Long.parseLong(st.nextToken());
					}
					catch (NoSuchElementException e)
					{
					}
					if (amount > 0)
					{
						if (getCastle().getTreasury() < amount)
							filename = "data/html/chamberlain/chamberlain-vault-no.htm";
						else
						{
							if (getCastle().addToTreasuryNoTax((-1) * amount))
								player.addAdena("Castle", amount, this, true);
						}
					}
				}
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile(filename);
				html.replace("%objectId%", String.valueOf(getObjectId()));
				html.replace("%npcname%", getName());
				html.replace("%tax_income%", Util.formatNumber(getCastle().getTreasury()));
				html.replace("%withdraw_amount%", Util.formatNumber(amount));
				player.sendPacket(html);
			}
			else if (actualCommand.equals("manor"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_MANOR_ADMIN))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				String filename = "";
				if (CastleManorManager.getInstance().isDisabled())
					filename = "data/html/npcdefault.htm";
				else
				{
					int cmd = Integer.parseInt(val);
					switch (cmd)
					{
						case 0:
							filename = "data/html/chamberlain/manor/manor.htm";
							break;
						// TODO: correct in html's to 1
						case 4:
							filename = "data/html/chamberlain/manor/manor_help00" + st.nextToken() + ".htm";
							break;
						default:
							filename = "data/html/chamberlain/chamberlain-no.htm";
							break;
					}
				}
				if (filename.length() != 0)
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					html.setFile(filename);
					html.replace("%objectId%", String.valueOf(getObjectId()));
					player.sendPacket(html);
				}
			}
			else if (command.startsWith("manor_menu_select"))
			{// input string format:
				// manor_menu_select?ask=X&state=Y&time=X
				if (CastleManorManager.getInstance().isUnderMaintenance())
				{
					player.sendPacket(SystemMessageId.THE_MANOR_SYSTEM_IS_CURRENTLY_UNDER_MAINTENANCE);
					return;
				}
				
				String params = command.substring(command.indexOf("?") + 1);
				StringTokenizer str = new StringTokenizer(params, "&");
				int ask = Integer.parseInt(str.nextToken().split("=")[1]);
				int state = Integer.parseInt(str.nextToken().split("=")[1]);
				int time = Integer.parseInt(str.nextToken().split("=")[1]);
				
				int castleId;
				if (state == -1) // info for current manor
					castleId = getCastle().getCastleId();
				else
					// info for requested manor
					castleId = state;
				
				switch (ask)
				{ // Main action
					case 3: // Current seeds (Manor info)
						if (time == 1 && !CastleManager.getInstance().getCastleById(castleId).isNextPeriodApproved())
							player.sendPacket(new ExShowSeedInfo(castleId, null));
						else
							player.sendPacket(new ExShowSeedInfo(castleId, CastleManager.getInstance()
									.getCastleById(castleId).getSeedProduction(time)));
						break;
					case 4: // Current crops (Manor info)
						if (time == 1 && !CastleManager.getInstance().getCastleById(castleId).isNextPeriodApproved())
							player.sendPacket(new ExShowCropInfo(castleId, null));
						else
							player.sendPacket(new ExShowCropInfo(castleId, CastleManager.getInstance()
									.getCastleById(castleId).getCropProcure(time)));
						break;
					case 5: // Basic info (Manor info)
						player.sendPacket(new ExShowManorDefaultInfo());
						break;
					case 7: // Edit seed setup
						if (getCastle().isNextPeriodApproved())
							player.sendPacket(SystemMessageId.A_MANOR_CANNOT_BE_SET_UP_BETWEEN_6_AM_AND_8_PM);
						else
							player.sendPacket(new ExShowSeedSetting(getCastle().getCastleId()));
						break;
					case 8: // Edit crop setup
						if (getCastle().isNextPeriodApproved())
							player.sendPacket(SystemMessageId.A_MANOR_CANNOT_BE_SET_UP_BETWEEN_6_AM_AND_8_PM);
						else
							player.sendPacket(new ExShowCropSetting(getCastle().getCastleId()));
						break;
				}
			}
			else if (actualCommand.equals("operate_door")) // door
			// control
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_OPEN_DOOR))
					return;
				
				if (!Config.SIEGE_GATE_CONTROL && getCastle().getSiege().getIsInProgress())
				{
					player.sendPacket(SystemMessageId.GATES_NOT_OPENED_CLOSED_DURING_SIEGE);
					return;
				}
				
				if (!val.isEmpty())
				{
					boolean open = (Integer.parseInt(val) == 1);
					while (st.hasMoreTokens())
					{
						getCastle().openCloseDoor(player, Integer.parseInt(st.nextToken()), open);
					}
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					String file = "data/html/chamberlain/doors-close.htm";
					if (open)
						file = "data/html/chamberlain/doors-open.htm";
					html.setFile(file);
					html.replace("%objectId%", String.valueOf(getObjectId()));
					player.sendPacket(html);
					return;
				}
				else
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					html.setFile("data/html/chamberlain/" + getTemplate().getNpcId() + "-d.htm");
					html.replace("%objectId%", String.valueOf(getObjectId()));
					html.replace("%npcname%", getName());
					player.sendPacket(html);
				}
			}
			else if (actualCommand.equalsIgnoreCase("tax_setup"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_TAXES))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				Castle c = getCastle();
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/chamberlain/chamberlain-settaxrate.htm");
				html.replace("%objectId%", String.valueOf(getObjectId()));
				html.replace("%currTax%", String.valueOf(c.getTaxPercent()));
				html.replace("%nextTax%", String.valueOf(c.getTaxPercentNew()));
				html.replace("%maxTax%", String.valueOf(Castle.getMaxTax()));
				player.sendPacket(html);
			}
			else if (actualCommand.equals("tax_set")) // tax rate control
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_TAXES))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				if (!val.isEmpty())
				{
					if (!getCastle().setTaxPercent(Integer.parseInt(val), true, true))
					{
						NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
						html.setFile("data/html/chamberlain/chamberlain-invalidtaxrate.htm");
						html.replace("%objectId%", String.valueOf(getObjectId()));
						html.replace("%maxTax%", String.valueOf(Castle.getMaxTax()));
						player.sendPacket(html);
						html = null;
						return;
					}
				}
				else
					//player deliberately didn't press "cancel"
					getCastle().setTaxPercent(0, false, true);
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/chamberlain/chamberlain-aftersettaxrate.htm");
				html.replace("%objectId%", String.valueOf(getObjectId()));
				html.replace("%nextTax%", String.valueOf(getCastle().getTaxPercentNew()));
				player.sendPacket(html);
				return;
			}
			else if (actualCommand.equals("manage_functions"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_SET_FUNCTIONS))
					return;
				
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/chamberlain/chamberlain-manage.htm");
				html.replace("%objectId%", String.valueOf(getObjectId()));
				player.sendPacket(html);
				return;
			}
			else if (actualCommand.equals("products"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_USE_FUNCTIONS))
					return;
				
				if (siegeBlocksFunction(player))
					return;
				
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/chamberlain/chamberlain-products.htm");
				html.replace("%objectId%", String.valueOf(getObjectId()));
				html.replace("%npcId%", String.valueOf(getNpcId()));
				player.sendPacket(html);
				return;
			}
			else if (actualCommand.equals("functions"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_USE_FUNCTIONS))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				if (val.equals("tele"))
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					if (getCastle().getFunction(Castle.FUNC_TELEPORT) == null)
						html.setFile("data/html/chamberlain/chamberlain-nac.htm");
					else
						html.setFile("data/html/chamberlain/" + getNpcId() + "-t"
								+ getCastle().getFunction(Castle.FUNC_TELEPORT).getLvl() + ".htm");
					sendHtmlMessage(player, html);
				}
				else if (val.equals("support"))
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					if (getCastle().getFunction(Castle.FUNC_SUPPORT) == null)
						html.setFile("data/html/chamberlain/chamberlain-nac.htm");
					else
					{
						html.setFile("data/html/chamberlain/support"
								+ getCastle().getFunction(Castle.FUNC_SUPPORT).getLvl() + ".htm");
						html.replace("%mp%", String.valueOf((int)getStatus().getCurrentMp()));
					}
					sendHtmlMessage(player, html);
				}
				else if (val.equals("back"))
				{
					showMessageWindow(player);
				}
				else
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					html.setFile("data/html/chamberlain/chamberlain-functions.htm");
					if (getCastle().getFunction(Castle.FUNC_RESTORE_EXP) != null)
						html.replace("%xp_regen%",
								String.valueOf(getCastle().getFunction(Castle.FUNC_RESTORE_EXP).getLvl()));
					else
						html.replace("%xp_regen%", "0");
					if (getCastle().getFunction(Castle.FUNC_RESTORE_HP) != null)
						html.replace("%hp_regen%",
								String.valueOf(getCastle().getFunction(Castle.FUNC_RESTORE_HP).getLvl()));
					else
						html.replace("%hp_regen%", "0");
					if (getCastle().getFunction(Castle.FUNC_RESTORE_MP) != null)
						html.replace("%mp_regen%",
								String.valueOf(getCastle().getFunction(Castle.FUNC_RESTORE_MP).getLvl()));
					else
						html.replace("%mp_regen%", "0");
					sendHtmlMessage(player, html);
				}
			}
			else if (actualCommand.equals("manage"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_SET_FUNCTIONS))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				if (val.equals("recovery"))
				{
					if (st.countTokens() >= 1)
					{
						if (getCastle().getOwnerId() == 0)
						{
							player.sendMessage("This castle have no owner, you cannot change configuration");
							return;
						}
						
						val = st.nextToken();
						if (val.equals("hp_cancel"))
						{
							NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/functions-cancel.htm");
							html.replace("%apply%", "recovery hp 0");
							sendHtmlMessage(player, html);
						}
						else if (val.equals("mp_cancel"))
						{
							NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/functions-cancel.htm");
							html.replace("%apply%", "recovery mp 0");
							sendHtmlMessage(player, html);
						}
						else if (val.equals("exp_cancel"))
						{
							NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/functions-cancel.htm");
							html.replace("%apply%", "recovery exp 0");
							sendHtmlMessage(player, html);
						}
						else if (val.equals("edit_hp"))
						{
							val = st.nextToken();
							NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/functions-apply.htm");
							html.replace("%name%", "Fireplace (HP Recovery Device)");
							int percent = Integer.valueOf(val);
							int cost;
							switch (percent)
							{
								case 80:
									cost = Config.CS_HPREG1_FEE;
									break;
								case 120:
									cost = Config.CS_HPREG2_FEE;
									break;
								case 180:
									cost = Config.CS_HPREG3_FEE;
									break;
								case 240:
									cost = Config.CS_HPREG4_FEE;
									break;
								default: // 300
									cost = Config.CS_HPREG5_FEE;
									break;
							}
							html.replace(
									"%cost%",
									String.valueOf(cost) + "</font>Adena /"
											+ String.valueOf(Config.CS_HPREG_FEE_RATIO / 1000 / 60 / 60 / 24)
											+ " Day</font>)");
							html.replace("%use%",
									"Provides additional HP recovery for clan members in the castle.<font color=\"00FFFF\">"
											+ String.valueOf(percent) + "%</font>");
							html.replace("%apply%", "recovery hp " + String.valueOf(percent));
							sendHtmlMessage(player, html);
						}
						else if (val.equals("edit_mp"))
						{
							val = st.nextToken();
							NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/functions-apply.htm");
							html.replace("%name%", "Carpet (MP Recovery)");
							int percent = Integer.valueOf(val);
							int cost;
							switch (percent)
							{
								case 5:
									cost = Config.CS_MPREG1_FEE;
									break;
								case 15:
									cost = Config.CS_MPREG2_FEE;
									break;
								case 30:
									cost = Config.CS_MPREG3_FEE;
									break;
								default: // 40
									cost = Config.CS_MPREG4_FEE;
									break;
							}
							html.replace(
									"%cost%",
									String.valueOf(cost) + "</font>Adena /"
											+ String.valueOf(Config.CS_MPREG_FEE_RATIO / 1000 / 60 / 60 / 24)
											+ " Day</font>)");
							html.replace("%use%",
									"Provides additional MP recovery for clan members in the castle.<font color=\"00FFFF\">"
											+ String.valueOf(percent) + "%</font>");
							html.replace("%apply%", "recovery mp " + String.valueOf(percent));
							sendHtmlMessage(player, html);
						}
						else if (val.equals("edit_exp"))
						{
							val = st.nextToken();
							NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/functions-apply.htm");
							html.replace("%name%", "Chandelier (EXP Recovery Device)");
							int percent = Integer.valueOf(val);
							int cost;
							switch (percent)
							{
								case 15:
									cost = Config.CS_EXPREG1_FEE;
									break;
								case 25:
									cost = Config.CS_EXPREG2_FEE;
									break;
								case 35:
									cost = Config.CS_EXPREG3_FEE;
									break;
								default: // 50
									cost = Config.CS_EXPREG4_FEE;
									break;
							}
							html.replace(
									"%cost%",
									String.valueOf(cost) + "</font>Adena /"
											+ String.valueOf(Config.CS_EXPREG_FEE_RATIO / 1000 / 60 / 60 / 24)
											+ " Day</font>)");
							html.replace("%use%",
									"Restores the Exp of any clan member who is resurrected in the castle.<font color=\"00FFFF\">"
											+ String.valueOf(percent) + "%</font>");
							html.replace("%apply%", "recovery exp " + String.valueOf(percent));
							sendHtmlMessage(player, html);
						}
						else if (val.equals("hp"))
						{
							if (st.countTokens() >= 1)
							{
								int fee;
								if (_log.isDebugEnabled())
									_log.warn("Hp editing invoked");
								val = st.nextToken();
								NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
								html.setFile("data/html/chamberlain/functions-apply_confirmed.htm");
								if (getCastle().getFunction(Castle.FUNC_RESTORE_HP) != null)
								{
									if (getCastle().getFunction(Castle.FUNC_RESTORE_HP).getLvl() == Integer
											.valueOf(val))
									{
										html.setFile("data/html/chamberlain/functions-used.htm");
										html.replace("%val%", String.valueOf(val) + "%");
										sendHtmlMessage(player, html);
										return;
									}
								}
								int percent = Integer.valueOf(val);
								switch (percent)
								{
									case 0:
										fee = 0;
										html.setFile("data/html/chamberlain/functions-cancel_confirmed.htm");
										break;
									case 80:
										fee = Config.CS_HPREG1_FEE;
										break;
									case 120:
										fee = Config.CS_HPREG2_FEE;
										break;
									case 180:
										fee = Config.CS_HPREG3_FEE;
										break;
									case 240:
										fee = Config.CS_HPREG4_FEE;
										break;
									default: // 300
										fee = Config.CS_HPREG5_FEE;
										break;
								}
								if (!getCastle().updateFunctions(player, Castle.FUNC_RESTORE_HP, percent, fee,
										Config.CS_HPREG_FEE_RATIO,
										(getCastle().getFunction(Castle.FUNC_RESTORE_HP) == null)))
								{
									html.setFile("data/html/chamberlain/chamberlain-noadena.htm");
									sendHtmlMessage(player, html);
								}
								sendHtmlMessage(player, html);
							}
						}
						else if (val.equals("mp"))
						{
							if (st.countTokens() >= 1)
							{
								int fee;
								if (_log.isDebugEnabled())
									_log.warn("Mp editing invoked");
								val = st.nextToken();
								NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
								html.setFile("data/html/chamberlain/functions-apply_confirmed.htm");
								if (getCastle().getFunction(Castle.FUNC_RESTORE_MP) != null)
								{
									if (getCastle().getFunction(Castle.FUNC_RESTORE_MP).getLvl() == Integer
											.valueOf(val))
									{
										html.setFile("data/html/chamberlain/functions-used.htm");
										html.replace("%val%", String.valueOf(val) + "%");
										sendHtmlMessage(player, html);
										return;
									}
								}
								int percent = Integer.valueOf(val);
								switch (percent)
								{
									case 0:
										fee = 0;
										html.setFile("data/html/chamberlain/functions-cancel_confirmed.htm");
										break;
									case 5:
										fee = Config.CS_MPREG1_FEE;
										break;
									case 15:
										fee = Config.CS_MPREG2_FEE;
										break;
									case 30:
										fee = Config.CS_MPREG3_FEE;
										break;
									default: // 40
										fee = Config.CS_MPREG4_FEE;
										break;
								}
								if (!getCastle().updateFunctions(player, Castle.FUNC_RESTORE_MP, percent, fee,
										Config.CS_MPREG_FEE_RATIO,
										(getCastle().getFunction(Castle.FUNC_RESTORE_MP) == null)))
								{
									html.setFile("data/html/chamberlain/chamberlain-noadena.htm");
									sendHtmlMessage(player, html);
								}
								sendHtmlMessage(player, html);
							}
						}
						else if (val.equals("exp"))
						{
							if (st.countTokens() >= 1)
							{
								int fee;
								if (_log.isDebugEnabled())
									_log.warn("Exp editing invoked");
								val = st.nextToken();
								NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
								html.setFile("data/html/chamberlain/functions-apply_confirmed.htm");
								if (getCastle().getFunction(Castle.FUNC_RESTORE_EXP) != null)
								{
									if (getCastle().getFunction(Castle.FUNC_RESTORE_EXP).getLvl() == Integer
											.valueOf(val))
									{
										html.setFile("data/html/chamberlain/functions-used.htm");
										html.replace("%val%", String.valueOf(val) + "%");
										sendHtmlMessage(player, html);
										return;
									}
								}
								int percent = Integer.valueOf(val);
								switch (percent)
								{
									case 0:
										fee = 0;
										html.setFile("data/html/chamberlain/functions-cancel_confirmed.htm");
										break;
									case 15:
										fee = Config.CS_EXPREG1_FEE;
										break;
									case 25:
										fee = Config.CS_EXPREG2_FEE;
										break;
									case 35:
										fee = Config.CS_EXPREG3_FEE;
										break;
									default: // 50
										fee = Config.CS_EXPREG4_FEE;
										break;
								}
								if (!getCastle().updateFunctions(player, Castle.FUNC_RESTORE_EXP, percent, fee,
										Config.CS_EXPREG_FEE_RATIO,
										(getCastle().getFunction(Castle.FUNC_RESTORE_EXP) == null)))
								{
									html.setFile("data/html/chamberlain/chamberlain-noadena.htm");
									sendHtmlMessage(player, html);
								}
								sendHtmlMessage(player, html);
							}
						}
					}
					else
					{
						NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
						html.setFile("data/html/chamberlain/edit_recovery.htm");
						String hp =
								"[<a action=\"bypass -h npc_%objectId%_manage recovery edit_hp 80\">80%</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage recovery edit_hp 120\">120%</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage recovery edit_hp 180\">180%</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage recovery edit_hp 240\">240%</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage recovery edit_hp 300\">300%</a>]";
						String exp =
								"[<a action=\"bypass -h npc_%objectId%_manage recovery edit_exp 15\">15%</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage recovery edit_exp 25\">25%</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage recovery edit_exp 35\">35%</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage recovery edit_exp 50\">50%</a>]";
						String mp =
								"[<a action=\"bypass -h npc_%objectId%_manage recovery edit_mp 5\">5%</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage recovery edit_mp 15\">15%</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage recovery edit_mp 30\">30%</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage recovery edit_mp 40\">40%</a>]";
						
						if (getCastle().getFunction(Castle.FUNC_RESTORE_HP) != null)
						{
							html.replace(
									"%hp_recovery%",
									String.valueOf(getCastle().getFunction(Castle.FUNC_RESTORE_HP).getLvl())
											+ "%</font> (<font color=\"FFAABB\">"
											+ String.valueOf(getCastle().getFunction(Castle.FUNC_RESTORE_HP).getLease())
											+ "</font>Adena /"
											+ String.valueOf(Config.CS_HPREG_FEE_RATIO / 1000 / 60 / 60 / 24) + " Day)");
							html.replace(
									"%hp_period%",
									"Withdraw the fee for the next time at "
											+ format.format(getCastle().getFunction(Castle.FUNC_RESTORE_HP)
													.getEndTime()));
							html.replace("%change_hp%",
									"[<a action=\"bypass -h npc_%objectId%_manage recovery hp_cancel\">Deactivate</a>]"
											+ hp);
						}
						else
						{
							html.replace("%hp_recovery%", "none");
							html.replace("%hp_period%", "none");
							html.replace("%change_hp%", hp);
						}
						
						if (getCastle().getFunction(Castle.FUNC_RESTORE_EXP) != null)
						{
							html.replace(
									"%exp_recovery%",
									String.valueOf(getCastle().getFunction(Castle.FUNC_RESTORE_EXP).getLvl())
											+ "%</font> (<font color=\"FFAABB\">"
											+ String.valueOf(getCastle().getFunction(Castle.FUNC_RESTORE_EXP)
													.getLease()) + "</font>Adena /"
											+ String.valueOf(Config.CS_EXPREG_FEE_RATIO / 1000 / 60 / 60 / 24)
											+ " Day)");
							html.replace(
									"%exp_period%",
									"Withdraw the fee for the next time at "
											+ format.format(getCastle().getFunction(Castle.FUNC_RESTORE_EXP)
													.getEndTime()));
							html.replace("%change_exp%",
									"[<a action=\"bypass -h npc_%objectId%_manage recovery exp_cancel\">Deactivate</a>]"
											+ exp);
						}
						else
						{
							html.replace("%exp_recovery%", "none");
							html.replace("%exp_period%", "none");
							html.replace("%change_exp%", exp);
						}
						
						if (getCastle().getFunction(Castle.FUNC_RESTORE_MP) != null)
						{
							html.replace(
									"%mp_recovery%",
									String.valueOf(getCastle().getFunction(Castle.FUNC_RESTORE_MP).getLvl())
											+ "%</font> (<font color=\"FFAABB\">"
											+ String.valueOf(getCastle().getFunction(Castle.FUNC_RESTORE_MP).getLease())
											+ "</font>Adena /"
											+ String.valueOf(Config.CS_MPREG_FEE_RATIO / 1000 / 60 / 60 / 24) + " Day)");
							html.replace(
									"%mp_period%",
									"Withdraw the fee for the next time at "
											+ format.format(getCastle().getFunction(Castle.FUNC_RESTORE_MP)
													.getEndTime()));
							html.replace("%change_mp%",
									"[<a action=\"bypass -h npc_%objectId%_manage recovery mp_cancel\">Deactivate</a>]"
											+ mp);
						}
						else
						{
							html.replace("%mp_recovery%", "none");
							html.replace("%mp_period%", "none");
							html.replace("%change_mp%", mp);
						}
						sendHtmlMessage(player, html);
					}
				}
				else if (val.equals("other"))
				{
					if (st.countTokens() >= 1)
					{
						if (getCastle().getOwnerId() == 0)
						{
							player.sendMessage("This castle has no owner, you cannot change configuration");
							return;
						}
						val = st.nextToken();
						if (val.equals("tele_cancel"))
						{
							NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/functions-cancel.htm");
							html.replace("%apply%", "other tele 0");
							sendHtmlMessage(player, html);
						}
						else if (val.equals("support_cancel"))
						{
							NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/functions-cancel.htm");
							html.replace("%apply%", "other support 0");
							sendHtmlMessage(player, html);
						}
						else if (val.equalsIgnoreCase("security_cancel"))
						{
							NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/functions-cancel.htm");
							html.replace("%apply%", "other security 0");
							sendHtmlMessage(player, html);
						}
						else if (val.equals("edit_support"))
						{
							val = st.nextToken();
							NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/functions-apply.htm");
							html.replace("%name%", "Insignia (Supplementary Magic)");
							int stage = Integer.valueOf(val);
							int cost;
							switch (stage)
							{
								case 1:
									cost = Config.CS_SUPPORT1_FEE;
									break;
								case 2:
									cost = Config.CS_SUPPORT2_FEE;
									break;
								case 3:
									cost = Config.CS_SUPPORT3_FEE;
									break;
								default:
									cost = Config.CS_SUPPORT4_FEE;
									break;
							}
							html.replace(
									"%cost%",
									String.valueOf(cost) + "</font>Adena /"
											+ String.valueOf(Config.CS_SUPPORT_FEE_RATIO / 1000 / 60 / 60 / 24)
											+ " Day</font>)");
							html.replace("%use%", "Enables the use of supplementary magic.");
							html.replace("%apply%", "other support " + String.valueOf(stage));
							sendHtmlMessage(player, html);
						}
						else if (val.equals("edit_tele"))
						{
							val = st.nextToken();
							NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/functions-apply.htm");
							html.replace("%name%", "Mirror (Teleportation Device)");
							int stage = Integer.valueOf(val);
							int cost;
							switch (stage)
							{
								case 1:
									cost = Config.CS_TELE1_FEE;
									break;
								default:
									cost = Config.CS_TELE2_FEE;
									break;
							}
							html.replace(
									"%cost%",
									String.valueOf(cost) + "</font>Adena /"
											+ String.valueOf(Config.CS_TELE_FEE_RATIO / 1000 / 60 / 60 / 24)
											+ " Day</font>)");
							html.replace("%use%",
									"Teleports clan members in a castle to the target <font color=\"00FFFF\">Stage "
											+ String.valueOf(stage) + "</font> staging area");
							html.replace("%apply%", "other tele " + String.valueOf(stage));
							sendHtmlMessage(player, html);
						}
						else if (val.equalsIgnoreCase("edit_security"))
						{
							val = st.nextToken();
							NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/functions-apply.htm");
							html.replace("%name%", "Pulsar (Security System)");
							int stage = Integer.valueOf(val);
							int cost;
							switch (stage)
							{
								case 1:
									cost = Config.CS_SECURITY1_FEE;
									break;
								case 2:
									cost = Config.CS_SECURITY2_FEE;
									break;
								default:
									cost = Config.CS_SECURITY3_FEE;
									break;
							}
							html.replace(
									"%cost%",
									String.valueOf(cost) + "</font> Adena /"
											+ String.valueOf(Config.CS_SECURITY_FEE_RATIO / 1000 / 60 / 60 / 24)
											+ " Day</font>)");
							html.replace("%use%", "Enables the use of security system.");
							html.replace("%apply%", "other security " + String.valueOf(stage));
							sendHtmlMessage(player, html);
						}
						else if (val.equals("tele"))
						{
							if (st.countTokens() >= 1)
							{
								int fee;
								if (_log.isDebugEnabled())
									_log.warn("Tele editing invoked");
								val = st.nextToken();
								NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
								html.setFile("data/html/chamberlain/functions-apply_confirmed.htm");
								if (getCastle().getFunction(Castle.FUNC_TELEPORT) != null)
								{
									if (getCastle().getFunction(Castle.FUNC_TELEPORT).getLvl() == Integer.valueOf(val))
									{
										html.setFile("data/html/chamberlain/functions-used.htm");
										html.replace("%val%", "Stage " + String.valueOf(val));
										sendHtmlMessage(player, html);
										return;
									}
								}
								int lvl = Integer.valueOf(val);
								switch (lvl)
								{
									case 0:
										fee = 0;
										html.setFile("data/html/chamberlain/functions-cancel_confirmed.htm");
										break;
									case 1:
										fee = Config.CS_TELE1_FEE;
										break;
									default:
										fee = Config.CS_TELE2_FEE;
										break;
								}
								if (!getCastle().updateFunctions(player, Castle.FUNC_TELEPORT, lvl, fee,
										Config.CS_TELE_FEE_RATIO,
										(getCastle().getFunction(Castle.FUNC_TELEPORT) == null)))
								{
									html.setFile("data/html/chamberlain/chamberlain-noadena.htm");
									sendHtmlMessage(player, html);
								}
								sendHtmlMessage(player, html);
							}
						}
						else if (val.equals("support"))
						{
							if (st.countTokens() >= 1)
							{
								int fee;
								if (_log.isDebugEnabled())
									_log.warn("Support editing invoked");
								val = st.nextToken();
								NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
								html.setFile("data/html/chamberlain/functions-apply_confirmed.htm");
								if (getCastle().getFunction(Castle.FUNC_SUPPORT) != null)
								{
									if (getCastle().getFunction(Castle.FUNC_SUPPORT).getLvl() == Integer.valueOf(val))
									{
										html.setFile("data/html/chamberlain/functions-used.htm");
										html.replace("%val%", "Stage " + String.valueOf(val));
										sendHtmlMessage(player, html);
										return;
									}
								}
								int lvl = Integer.valueOf(val);
								switch (lvl)
								{
									case 0:
										fee = 0;
										html.setFile("data/html/chamberlain/functions-cancel_confirmed.htm");
										break;
									case 1:
										fee = Config.CS_SUPPORT1_FEE;
										break;
									case 2:
										fee = Config.CS_SUPPORT2_FEE;
										break;
									case 3:
										fee = Config.CS_SUPPORT3_FEE;
										break;
									default:
										fee = Config.CS_SUPPORT4_FEE;
										break;
								}
								if (!getCastle().updateFunctions(player, Castle.FUNC_SUPPORT, lvl, fee,
										Config.CS_SUPPORT_FEE_RATIO,
										(getCastle().getFunction(Castle.FUNC_SUPPORT) == null)))
								{
									html.setFile("data/html/chamberlain/chamberlain-noadena.htm");
									sendHtmlMessage(player, html);
								}
								else
									sendHtmlMessage(player, html);
							}
						}
						else if (val.equalsIgnoreCase("security"))
						{
							if (st.countTokens() >= 1)
							{
								int fee;
								if (_log.isDebugEnabled())
									_log.warn("Security editing invoked");
								val = st.nextToken();
								NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
								html.setFile("data/html/chamberlain/functions-apply_confirmed.htm");
								if (getCastle().getFunction(Castle.FUNC_SECURITY) != null)
								{
									if (getCastle().getFunction(Castle.FUNC_SECURITY).getLvl() == Integer.valueOf(val))
									{
										html.setFile("data/html/chamberlain/functions-used.htm");
										html.replace("%val%", "Stage " + String.valueOf(val));
										sendHtmlMessage(player, html);
										return;
									}
								}
								int lvl = Integer.valueOf(val);
								switch (lvl)
								{
									case 0:
										fee = 0;
										html.setFile("data/html/chamberlain/functions-cancel_confirmed.htm");
										break;
									case 1:
										fee = Config.CS_SECURITY1_FEE;
										break;
									case 2:
										fee = Config.CS_SECURITY2_FEE;
										break;
									default:
										fee = Config.CS_SECURITY3_FEE;
										break;
								}
								if (!getCastle().updateFunctions(player, Castle.FUNC_SECURITY, lvl, fee,
										Config.CS_SECURITY_FEE_RATIO,
										(getCastle().getFunction(Castle.FUNC_SECURITY) == null)))
								{
									html.setFile("data/html/chamberlain/low_adena.htm");
									sendHtmlMessage(player, html);
								}
								else
									sendHtmlMessage(player, html);
							}
						}
					}
					else
					{
						NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
						html.setFile("data/html/chamberlain/edit_other.htm");
						String tele =
								"[<a action=\"bypass -h npc_%objectId%_manage other edit_tele 1\">Level 1</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage other edit_tele 2\">Level 2</a>]";
						String support =
								"[<a action=\"bypass -h npc_%objectId%_manage other edit_support 1\">Level 1</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage other edit_support 2\">Level 2</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage other edit_support 3\">Level 3</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage other edit_support 4\">Level 4</a>]";
						String security =
								"[<a action=\"bypass -h npc_%objectId%_manage other edit_security 1\">Low</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage other edit_security 2\">Medium</a>]"
										+ "[<a action=\"bypass -h npc_%objectId%_manage other edit_security 3\">High</a>]";
						if (getCastle().getFunction(Castle.FUNC_TELEPORT) != null)
						{
							html.replace(
									"%tele%",
									"Stage " + String.valueOf(getCastle().getFunction(Castle.FUNC_TELEPORT).getLvl())
											+ "</font> (<font color=\"FFAABB\">"
											+ String.valueOf(getCastle().getFunction(Castle.FUNC_TELEPORT).getLease())
											+ "</font>Adena /"
											+ String.valueOf(Config.CS_TELE_FEE_RATIO / 1000 / 60 / 60 / 24) + " Day)");
							html.replace(
									"%tele_period%",
									"Withdraw the fee for the next time at "
											+ format.format(getCastle().getFunction(Castle.FUNC_TELEPORT).getEndTime()));
							html.replace("%change_tele%",
									"[<a action=\"bypass -h npc_%objectId%_manage other tele_cancel\">Deactivate</a>]"
											+ tele);
						}
						else
						{
							html.replace("%tele%", "none");
							html.replace("%tele_period%", "none");
							html.replace("%change_tele%", tele);
						}
						
						if (getCastle().getFunction(Castle.FUNC_SUPPORT) != null)
						{
							html.replace(
									"%support%",
									"Stage " + String.valueOf(getCastle().getFunction(Castle.FUNC_SUPPORT).getLvl())
											+ "</font> (<font color=\"FFAABB\">"
											+ String.valueOf(getCastle().getFunction(Castle.FUNC_SUPPORT).getLease())
											+ "</font>Adena /"
											+ String.valueOf(Config.CS_SUPPORT_FEE_RATIO / 1000 / 60 / 60 / 24)
											+ " Day)");
							html.replace(
									"%support_period%",
									"Withdraw the fee for the next time at "
											+ format.format(getCastle().getFunction(Castle.FUNC_SUPPORT).getEndTime()));
							html.replace("%change_support%",
									"[<a action=\"bypass -h npc_%objectId%_manage other support_cancel\">Deactivate</a>]"
											+ support);
						}
						else
						{
							html.replace("%support%", "none");
							html.replace("%support_period%", "none");
							html.replace("%change_support%", support);
						}
						
						if (getCastle().getFunction(Castle.FUNC_SECURITY) != null)
						{
							html.replace(
									"%security%",
									"Stage " + String.valueOf(getCastle().getFunction(Castle.FUNC_SECURITY).getLvl())
											+ "</font> (<font color=\"FFAABB\">"
											+ String.valueOf(getCastle().getFunction(Castle.FUNC_SECURITY).getLease())
											+ "</font>Adena /"
											+ String.valueOf(Config.CS_SECURITY_FEE_RATIO / 1000 / 60 / 60 / 24)
											+ " Day)");
							html.replace(
									"%security_period%",
									"Withdraw the fee for the next time at "
											+ format.format(getCastle().getFunction(Castle.FUNC_SECURITY).getEndTime()));
							html.replace("%change_security%",
									"[<a action=\"bypass -h npc_%objectId%_manage other security_cancel\">Deactivate</a>]"
											+ security);
						}
						else
						{
							html.replace("%security%", "none");
							html.replace("%security_period%", "none");
							html.replace("%change_security%", security);
						}
						sendHtmlMessage(player, html);
					}
				}
				else if (val.equals("back"))
				{
					showMessageWindow(player);
				}
				else
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					html.setFile("data/html/chamberlain/manage.htm");
					sendHtmlMessage(player, html);
				}
			}
			else if (actualCommand.equals("support"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_USE_FUNCTIONS))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				setTarget(player);
				L2Skill skill;
				if (val.isEmpty())
					return;
				
				try
				{
					int skill_id = Integer.parseInt(val);
					try
					{
						if (getCastle().getFunction(Castle.FUNC_SUPPORT) == null)
							return;
						if (getCastle().getFunction(Castle.FUNC_SUPPORT).getLvl() == 0)
							return;
						
						NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
						int skill_lvl = 0;
						if (st.countTokens() >= 1)
							skill_lvl = Integer.parseInt(st.nextToken());
						skill = SkillTable.getInstance().getInfo(skill_id, skill_lvl);
						if (skill.getSkillType() == L2SkillType.SUMMON)
						{
							player.doSimultaneousCast(skill);
						}
						else
						{
							if (!((skill.getMpConsume() + skill.getMpInitialConsume()) > getStatus().getCurrentMp()))
							{
								doCast(skill);
							}
							else
							{
								html.setFile("data/html/chamberlain/support-no_mana.htm");
								html.replace("%mp%", String.valueOf((int)getStatus().getCurrentMp()));
								sendHtmlMessage(player, html);
								return;
							}
						}
						html.setFile("data/html/chamberlain/support-done.htm");
						html.replace("%mp%", String.valueOf((int)getStatus().getCurrentMp()));
						sendHtmlMessage(player, html);
					}
					catch (Exception e)
					{
						player.sendMessage("Invalid skill level, contact your admin!");
					}
				}
				catch (Exception e)
				{
					player.sendMessage("Invalid skill level, contact your admin!");
				}
			}
			else if (actualCommand.equals("support_back"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_USE_FUNCTIONS))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				if (getCastle().getFunction(Castle.FUNC_SUPPORT).getLvl() == 0)
					return;
				html.setFile("data/html/chamberlain/support" + getCastle().getFunction(Castle.FUNC_SUPPORT).getLvl()
						+ ".htm");
				html.replace("%mp%", String.valueOf((int)getStatus().getCurrentMp()));
				sendHtmlMessage(player, html);
			}
			else if (actualCommand.equals("goto"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_USE_FUNCTIONS))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				int whereTo = Integer.parseInt(val);
				doTeleport(player, whereTo);
			}
			else if (actualCommand.equals("siege_change")) // siege day set
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_MANAGE_SIEGE))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				if (Config.CL_SET_SIEGE_TIME_LIST.isEmpty())
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					html.setFile("data/html/chamberlain/chamberlain-disabled.htm");
					//nothing needs to be replaced
					player.sendPacket(html);
				}
				else if (getCastle().getSiege().getTimeRegistrationOverDate().getTimeInMillis() < System
						.currentTimeMillis())
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					html.setFile("data/html/chamberlain/siegetime1.htm");
					sendHtmlMessage(player, html);
				}
				else if (getCastle().getSiege().getIsTimeRegistrationOver())
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					html.setFile("data/html/chamberlain/siegetime2.htm");
					sendHtmlMessage(player, html);
				}
				else
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					html.setFile("data/html/chamberlain/siegetime3.htm");
					html.replace("%time%", String.valueOf(getCastle().getSiegeDate().getTime()));
					sendHtmlMessage(player, html);
				}
			}
			else if (actualCommand.equals("siege_time_set")) // set preDay
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_MANAGE_SIEGE))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				boolean isAfternoon = Config.SIEGE_HOUR_LIST_MORNING.isEmpty();
				switch (Integer.parseInt(val))
				{
					case 0:
					case 4:
						break;
					case 1:
						_preDay = Integer.parseInt(st.nextToken());
						break;
					case 2:
						isAfternoon = Boolean.parseBoolean(st.nextToken());
						break;
					case 3:
						_preHour = Integer.parseInt(st.nextToken());
						break;
					default:
						break;
				}
				NpcHtmlMessage html = getNextSiegeTimePage(Integer.parseInt(val), isAfternoon);
				
				if (html == null)
				{
					if (Config.CL_SET_SIEGE_TIME_LIST.contains("day"))
						getCastle().getSiegeDate().set(Calendar.DAY_OF_WEEK, _preDay);
					else
						getCastle().getSiegeDate().set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
					if (Config.CL_SET_SIEGE_TIME_LIST.contains("hour"))
						getCastle().getSiegeDate().set(Calendar.HOUR_OF_DAY, _preHour);
					if (Config.CL_SET_SIEGE_TIME_LIST.contains("minute"))
						getCastle().getSiegeDate().set(Calendar.MINUTE, Integer.parseInt(st.nextToken()));
					// Now store the changed time and finished next Siege Time registration
					getCastle().getSiege().endTimeRegistration(false);
					
					html = new NpcHtmlMessage(getObjectId());
					html.setFile("data/html/chamberlain/siegetime8.htm");
					html.replace("%time%", String.valueOf(getCastle().getSiegeDate().getTime()));
				}
				sendHtmlMessage(player, html);
			}
			else if (actualCommand.equals("give_crown"))
			{
				if (siegeBlocksFunction(player))
					return;
				
				if (player.isClanLeader())
				{
					if (player.getInventory().getItemByItemId(6841) == null)
					{
						player.addItem("Chamberlain - Crown", 6841, 1, this, true, true);
						NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
						html.setFile("data/html/chamberlain/chamberlain-gavecrown.htm");
						html.replace("%CharName%", String.valueOf(player.getName()));
						html.replace("%FeudName%", String.valueOf(getCastle().getName()));
						player.sendPacket(html);
					}
					else
					{
						NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
						html.setFile("data/html/chamberlain/chamberlain-hascrown.htm");
						player.sendPacket(html);
					}
				}
				else
					player.sendPacket(NO_AUTH);
			}
			else if (actualCommand.equals("give_LotMCoA"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_USE_FUNCTIONS))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				int valbuy = 63880 + getCastle().getCastleId();
				showBuyWindow(player, valbuy);
			}
			else if (actualCommand.equals("trap"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_MANAGE_SIEGE))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				try
				{
					int[] request = new int[st.countTokens() + 1];
					request[0] = Integer.parseInt(val);
					for (int i = 1; i < request.length; i++)
						request[i] = Integer.parseInt(st.nextToken());
					NpcHtmlMessage html;
					switch (request[0])
					{
						case 0: //show trap location selection
							String file = "data/html/chamberlain/chamberlain-trap-select";
							switch (getCastle().getCastleId())
							{
								case 1:
								case 2:
								case 3:
								case 4:
								case 6:
									file += "-inout";
									break;
								case 5:
									file += "-eastwest";
									break;
								case 7:
									file += "-2nd";
									break;
								default:
									file += "-eastwest";
									break;
							}
							html = new NpcHtmlMessage(getObjectId());
							html.setFile(file + ".htm");
							html.replace("%objectId%", String.valueOf(getObjectId()));
							player.sendPacket(html);
							break;
						case 1: //show trap level selection
							if (request[1] > 2 || request[1] < 1)
							{
								Util.handleIllegalPlayerAction(player, "Tried to exploit the castle trap setup!",
										IllegalPlayerAction.PUNISH_KICKBAN);
								return;
							}
							html = new NpcHtmlMessage(getObjectId());
							if (getCastle().getCastleId() == 5)
								html.setFile("data/html/chamberlain/chamberlain-trap-level.htm");
							else
								html.setFile("data/html/chamberlain/chamberlain-trap-activation.htm");
							html.replace("%objectId%", String.valueOf(getObjectId()));
							html.replace("%trapId%", String.valueOf(request[1]));
							player.sendPacket(html);
							break;
						case 2: //if the level is valid, confirm deploy
							Castle c = getCastle();
							if (request[1] > 2 || request[1] < 1 || request[2] > 4 || request[2] < 0
									|| (c.getCastleId() != 5 && request[2] > 1))
							{
								Util.handleIllegalPlayerAction(player, "Tried to exploit the castle trap setup!",
										IllegalPlayerAction.PUNISH_KICKBAN);
								return;
							}
							
							boolean side = request[1] == 1;
							int trapLevel = c.getSiege().getZoneAreaLevel(side);
							html = new NpcHtmlMessage(getObjectId());
							if (request[2] <= trapLevel) //a [better] trap already deployed
							{
								html.setFile("data/html/chamberlain/chamberlain-trap-already.htm");
								html.replace("%objectId%", String.valueOf(getObjectId()));
								html.replace("%dmglevel%", String.valueOf(trapLevel));
							}
							else
							//show confirmation
							{
								html.setFile("data/html/chamberlain/chamberlain-trap-deploy.htm");
								html.replace("%objectId%", String.valueOf(getObjectId()));
								html.replace("%trapId%", String.valueOf(request[1]));
								html.replace("%trapLvl%", String.valueOf(request[2]));
								int price = 0;
								switch (request[2])
								{
									case 1:
										price = Config.CS_TRAP1_FEE;
										break;
									case 2:
										price = Config.CS_TRAP2_FEE;
										break;
									case 3:
										price = Config.CS_TRAP3_FEE;
										break;
									case 4:
										price = Config.CS_TRAP4_FEE;
										break;
								}
								html.replace("%dmgzone_price%", String.valueOf(price));
							}
							player.sendPacket(html);
							break;
						case 3: //if the level is valid, deploy trap
							boolean east = request[1] == 1;
							c = getCastle();
							if (c.getSiege().getDangerZones(east) == null)
							{
								//missing zone(s), let's blame the big bad admin
								html = new NpcHtmlMessage(getObjectId());
								html.setFile("data/html/chamberlain/chamberlain-disabled.htm");
								player.sendPacket(html);
								return;
							}
							int dmglevel = c.getSiege().getZoneAreaLevel(east);
							if (request[1] > 2 || request[1] < 1 || request[2] > 4 || request[2] < 0
									|| request[2] <= dmglevel || (c.getCastleId() != 5 && request[2] > 1))
							{
								Util.handleIllegalPlayerAction(player, "Tried to exploit the castle trap setup!",
										IllegalPlayerAction.PUNISH_KICKBAN);
								return;
							}
							int price = 0;
							switch (request[2])
							{
								case 1:
									price = Config.CS_TRAP1_FEE;
									break;
								case 2:
									price = Config.CS_TRAP2_FEE;
									break;
								case 3:
									price = Config.CS_TRAP3_FEE;
									break;
								case 4:
									price = Config.CS_TRAP4_FEE;
									break;
							}
							if (!player.reduceAdena("Castle - Trap Deployment", price, this, false))
							{
								html = new NpcHtmlMessage(getObjectId());
								html.setFile("data/html/chamberlain/chamberlain-noadena.htm");
								html.replace("%objectId%", String.valueOf(getObjectId()));
								player.sendPacket(html);
								html = null;
								return;
							}
							c.upgradeDangerZones(east, dmglevel, request[2]);
							html = new NpcHtmlMessage(getObjectId());
							html.setFile("data/html/chamberlain/chamberlain-trap-deployed.htm");
							html.replace("%objectId%", String.valueOf(getObjectId()));
							player.sendPacket(html);
							break;
					}
				}
				catch (NumberFormatException nfe)
				{
					_log.warn("Error while parsing castle trap setup bypass!", nfe);
					Util.handleIllegalPlayerAction(player, "Messing with castle trap setup bypass!",
							Config.DEFAULT_PUNISH);
				}
				catch (ArrayIndexOutOfBoundsException aob)
				{
					_log.warn("Missing castle trap setup bypass parameters (" + getCastle().getName() + ")!");
					Util.handleIllegalPlayerAction(player, "Messing with castle trap setup bypass!",
							Config.DEFAULT_PUNISH);
				}
			}
			else if (actualCommand.equals("reinforce"))
			{
				if (!validatePrivileges(player, L2Clan.CP_CS_MANAGE_SIEGE))
					return;
				if (siegeBlocksFunction(player))
					return;
				
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/chamberlain/chamberlain-disabled.htm");
				player.sendPacket(html);
			}
			else if (actualCommand.equals("terra_war"))
			{
				if (siegeBlocksFunction(player))
					return;
				
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/chamberlain/chamberlain-disabled.htm");
				player.sendPacket(html);
			}
			else
				super.onBypassFeedback(player, command);
		}
	}
	
	private void showMessageWindow(L2Player player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		String filename = "data/html/chamberlain/chamberlain-no.htm";
		
		int condition = validateCondition(player);
		if (condition == COND_OWNER) // Clan owns castle
		{
			// Owner message window
			//if (getCastle().getCastleId() == 8)
			//	filename = "data/html/chamberlain/chamberlain-rune.htm";
			//else
			filename = "data/html/chamberlain/chamberlain.htm";
		}
		
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
		html = null;
	}
	
	private NpcHtmlMessage getNextSiegeTimePage(int now, boolean isAfternoon)
	{
		NpcHtmlMessage ret = new NpcHtmlMessage(getObjectId());
		if (now == 0 && Config.CL_SET_SIEGE_TIME_LIST.contains("day"))
		{
			ret.setFile("data/html/chamberlain/siegetime4.htm");
			return ret;
		}
		if (now < 3 && Config.CL_SET_SIEGE_TIME_LIST.contains("hour"))
		{
			switch (now)
			{
				case 0:
				case 1:
					if (!Config.SIEGE_HOUR_LIST_MORNING.isEmpty() && !Config.SIEGE_HOUR_LIST_AFTERNOON.isEmpty())
					{
						ret.setFile("data/html/chamberlain/siegetime5.htm");
						return ret;
					}
					break;
				case 2:
					ret.setFile("data/html/chamberlain/siegetime6.htm");
					Set<Integer> list;
					int inc = 0;
					String ampm = "";
					
					if (!isAfternoon)
					{
						if (Config.SIEGE_HOUR_LIST_AFTERNOON.isEmpty())
							ampm = "AM";
						list = Config.SIEGE_HOUR_LIST_MORNING;
					}
					else
					{
						if (Config.SIEGE_HOUR_LIST_MORNING.isEmpty())
							ampm = "PM";
						inc = 12;
						list = Config.SIEGE_HOUR_LIST_AFTERNOON;
					}
					/* l2jserver style
					final StringBuilder tList = new StringBuilder(list.size() * 50);
					for (Integer hour : list)
					{
						if (hour == 0)
						{
							StringUtil.append(tList,
									"<a action=\"bypass -h npc_%objectId%_siege_time_set 3 ",
									String.valueOf(hour + inc),
									"\">",
									String.valueOf(hour + 12),
									":00 ",
									ampm,
									"</a><br>"
							);
						}
						else
						{
							StringUtil.append(tList,
									"<a action=\"bypass -h npc_%objectId%_siege_time_set 3 ",
									String.valueOf(hour + inc),
									"\">",
									String.valueOf(hour),
									":00 ",
									ampm,
									"</a><br>"
							);
						}
					}
					*/
					StringBuilder tList = new StringBuilder(list.size() * 50);
					for (Integer hour : list)
					{
						tList.append(SET_TIME[0]);
						tList.append(hour + inc);
						tList.append(SET_TIME[1]);
						tList.append(hour == 0 ? hour + 12 : hour);
						tList.append(SET_TIME[2]);
						tList.append(ampm);
						tList.append(SET_TIME[3]);
					}
					ret.replace("%links%", tList.toString());
			}
			return ret;
		}
		if (now < 4 && Config.CL_SET_SIEGE_TIME_LIST.contains("minute"))
		{
			ret.setFile("data/html/chamberlain/siegetime7.htm");
			return ret;
		}
		
		return null;
	}
	
	private void doTeleport(L2Player player, int val)
	{
		if (_log.isDebugEnabled())
			_log.warn("doTeleport(L2Player player, int val) is called");
		L2TeleportLocation list = TeleportLocationTable.getInstance().getTemplate(val);
		if (list != null)
		{
			if (player.reduceAdena("Teleport", list.getPrice(), this, true))
			{
				if (_log.isDebugEnabled())
					_log.warn("Teleporting player " + player.getName() + " for Castle to new location: "
							+ list.getLocX() + ":" + list.getLocY() + ":" + list.getLocZ());
				player.teleToLocation(list.getLocX(), list.getLocY(), list.getLocZ());
			}
		}
		else
			_log.warn("No teleport destination with id:" + val);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private boolean siegeBlocksFunction(L2Player player)
	{
		if (getCastle().getSiege().getIsInProgress())
		{
			NpcHtmlMessage siege = new NpcHtmlMessage(getObjectId());
			siege.setFile("data/html/chamberlain/chamberlain-siege.htm");
			siege.replace("%objectId%", String.valueOf(getObjectId()));
			player.sendPacket(siege);
			siege = null;
			return true;
		}
		return false;
	}
	
	private boolean validatePrivileges(L2Player player, int privilege)
	{
		if (!L2Clan.checkPrivileges(player, L2Clan.CP_CS_DISMISS))
		{
			player.sendPacket(NO_AUTH);
			return false;
		}
		return true;
	}
	
	protected int validateCondition(L2Player player)
	{
		if (getCastle() != null && getCastle().getCastleId() > 0 && player.getClan() != null
				&& getCastle().getOwnerId() == player.getClanId())
			//&& validatePrivileges(player, L2Clan.CP_CS_USE_FUNCTIONS))
			return COND_OWNER; // Owner clan member
		return COND_ALL_FALSE;
	}
}
