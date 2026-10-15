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

import java.util.StringTokenizer;

import com.l2jfree.Config;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * Dawn/Dusk Seven Signs Priest Instance
 * 
 * @author Tempy
 */
public class L2SignsPriestInstance extends L2Npc
{
	public L2SignsPriestInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (player.getLastFolkNPC() == null || player.getLastFolkNPC().getObjectId() != getObjectId())
			return;
		
		if (command.startsWith("SevenSignsDesc"))
		{
			int val = Integer.parseInt(command.substring(15));
			showChatWindow(player, val, null, true);
		}
		else if (command.startsWith("SevenSigns"))
		{
			SystemMessage sm;
			InventoryUpdate iu;
			StatusUpdate su;
			String path;
			int cabal = SevenSigns.CABAL_NULL;
			int stoneType = 0;
			L2ItemInstance ancientAdena = player.getInventory().getItemByItemId(PlayerInventory.ANCIENT_ADENA_ID);
			long ancientAdenaAmount = ancientAdena == null ? 0 : ancientAdena.getCount();
			int val = Integer.parseInt(command.substring(11, 12).trim());
			
			if (command.length() > 12) // SevenSigns x[x] x [x..x]
				val = Integer.parseInt(command.substring(11, 13).trim());
			
			if (command.length() > 13)
			{
				try
				{
					cabal = Integer.parseInt(command.substring(14, 15).trim());
				}
				catch (Exception e)
				{
					try
					{
						cabal = Integer.parseInt(command.substring(13, 14).trim());
					}
					catch (Exception e2)
					{
						try
						{
							StringTokenizer st = new StringTokenizer(command.trim());
							st.nextToken();
							cabal = Integer.parseInt(st.nextToken());
						}
						catch (Exception e3)
						{
							_log.warn("Failed to retrieve cabal from bypass command. NpcId: " + getNpcId()
									+ "; Command: " + command);
						}
					}
				}
			}
			
			switch (val)
			{
				case 2: // Purchase Record of the Seven Signs
					if (!player.getInventory().validateCapacity(1))
					{
						player.sendPacket(SystemMessageId.SLOTS_FULL);
						break;
					}
					
					if (!player.reduceAdena("SevenSigns", SevenSigns.RECORD_SEVEN_SIGNS_COST, this, true))
					{
						player.sendPacket(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
						break;
					}
					L2ItemInstance recordSevenSigns =
							player.getInventory().addItem("SevenSigns", SevenSigns.RECORD_SEVEN_SIGNS_ID, 1, player,
									this);
					
					// Send inventory update packet
					player.getInventory().updateInventory(recordSevenSigns);
					
					sm = new SystemMessage(SystemMessageId.EARNED_S1);
					sm.addItemName(SevenSigns.RECORD_SEVEN_SIGNS_ID);
					player.sendPacket(sm);
					break;
				case 34: // Pay the participation fee request
					boolean fee = true;
					L2ItemInstance adena = player.getInventory().getItemByItemId(PlayerInventory.ADENA_ID); // Adena
					L2ItemInstance certif =
							player.getInventory().getItemByItemId(SevenSigns.CERTIFICATE_OF_APPROVAL_ID); // Lord of the Manor's Certificate of Approval
					if (player.getClassId().level() < 2
							|| (adena != null && adena.getCount() >= Config.ALT_DAWN_JOIN_COST)
							|| (certif != null && certif.getCount() >= 1))
						fee = false;
					if (fee)
					{
						showChatWindow(player, SevenSigns.SEVEN_SIGNS_HTML_PATH + "signs_33_dawn_no.htm");
						break;
					}
					
					showChatWindow(player, SevenSigns.SEVEN_SIGNS_HTML_PATH + "signs_33_dawn.htm");
					break;
				case 33: // "I want to participate" request
					if (cabal == SevenSigns.CABAL_DUSK && Config.ALT_GAME_CASTLE_DUSK) // Dusk
					{
						// Castle owners cannot participate with dusk side
						if (player.getClan() != null && player.getClan().getHasCastle() > 0)
						{
							showChatWindow(player, SevenSigns.SEVEN_SIGNS_HTML_PATH + "signs_33_dusk_no.htm");
							break;
						}
					}
					else if (player.getClassId().level() >= 2
							&& (player.getClan() == null || (player.getClan() != null && player.getClan()
									.getHasCastle() == 0))) // Dawn
					{
						// Clans without castle need to pay participation fee
						if (player.getClan() == null || player.getClan().getHasCastle() == 0)
						{
							showChatWindow(player, SevenSigns.SEVEN_SIGNS_HTML_PATH + "signs_33_dawn_fee.htm");
							break;
						}
					}
					//  Commented out break; to fix Participation request
					//  To get rid of warnings, just enable both lines below
					/*
					    showChatWindow(player, val, SevenSigns.getCabalShortName(cabal), false);
					    break;
					 */
					//$FALL-THROUGH$
				case 3: // Join Cabal Intro 1
				case 8: // Festival of Darkness Intro - SevenSigns x [0]1
				case 10: // Teleport Locations List
					showChatWindow(player, val, SevenSigns.getCabalShortName(cabal), false);
					break;
				case 4: // Join a Cabal - SevenSigns 4 [0]1 x
					int newSeal = Integer.parseInt(command.substring(15));
					int oldCabal = SevenSigns.getInstance().getPlayerCabal(player);
					
					if (oldCabal != SevenSigns.CABAL_NULL)
					{
						player.sendMessage("You are already a member of the " + SevenSigns.getCabalName(cabal) + ".");
						return;
					}
					
					if (player.getClassId().level() == 0)
					{
						player.sendMessage("You must have already completed your first class transfer.");
						break;
					}
					else if (player.getClassId().level() >= 2)
					{
						if (cabal == SevenSigns.CABAL_DUSK && Config.ALT_GAME_CASTLE_DUSK)
						{
							if (player.getClan() != null && player.getClan().getHasCastle() >= 0)
							{
								showChatWindow(player, SevenSigns.SEVEN_SIGNS_HTML_PATH + "signs_33_dusk_no.htm");
								return;
							}
						}
						
						/*
						 * If the player is trying to join the Lords of Dawn, check if they are
						 * carrying a Lord's certificate.
						 *
						 * If not then try to take the required amount of adena instead.
						 */
						if (Config.ALT_GAME_CASTLE_DAWN && cabal == SevenSigns.CABAL_DAWN)
						{
							if (player.getClan() != null && player.getClan().getHasCastle() >= 0) // Castle owner don't need to pay anything
							{
								// No additional fees
							}
							else if (player.destroyItemByItemId("SevenSigns", SevenSigns.CERTIFICATE_OF_APPROVAL_ID, 1,
									this, false))
							{
								sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
								sm.addItemName(SevenSigns.CERTIFICATE_OF_APPROVAL_ID);
								sm.addItemNumber(1);
								player.sendPacket(sm);
							}
							else if (player.reduceAdena("SevenSigns", Config.ALT_DAWN_JOIN_COST, this, false))
							{
								sm = new SystemMessage(SystemMessageId.S1_ADENA_DISAPPEARED);
								sm.addItemNumber(Config.ALT_DAWN_JOIN_COST);
								player.sendPacket(sm);
							}
							else
							{
								showChatWindow(player, SevenSigns.SEVEN_SIGNS_HTML_PATH + "signs_33_dawn_fee.htm");
								return;
							}
						}
					}
					
					SevenSigns.getInstance().setPlayerInfo(player, cabal, newSeal);
					
					if (cabal == SevenSigns.CABAL_DAWN)
						player.sendPacket(SystemMessageId.SEVENSIGNS_PARTECIPATION_DAWN); // Joined Dawn
					else
						player.sendPacket(SystemMessageId.SEVENSIGNS_PARTECIPATION_DUSK); // Joined Dusk
						
					// Show a confirmation message to the user, indicating which seal they chose.
					switch (newSeal)
					{
						case SevenSigns.SEAL_AVARICE:
							player.sendPacket(SystemMessageId.FIGHT_FOR_AVARICE);
							break;
						case SevenSigns.SEAL_GNOSIS:
							player.sendPacket(SystemMessageId.FIGHT_FOR_GNOSIS);
							break;
						case SevenSigns.SEAL_STRIFE:
							player.sendPacket(SystemMessageId.FIGHT_FOR_STRIFE);
							break;
					}
					
					showChatWindow(player, 4, SevenSigns.getCabalShortName(cabal), false);
					break;
				case 6: // Contribute Seal Stones - SevenSigns 6 x
					stoneType = Integer.parseInt(command.substring(13));
					L2ItemInstance redStones = player.getInventory().getItemByItemId(SevenSigns.SEAL_STONE_RED_ID);
					long redStoneCount = redStones == null ? 0 : redStones.getCount();
					L2ItemInstance greenStones = player.getInventory().getItemByItemId(SevenSigns.SEAL_STONE_GREEN_ID);
					long greenStoneCount = greenStones == null ? 0 : greenStones.getCount();
					L2ItemInstance blueStones = player.getInventory().getItemByItemId(SevenSigns.SEAL_STONE_BLUE_ID);
					long blueStoneCount = blueStones == null ? 0 : blueStones.getCount();
					long contribScore = SevenSigns.getInstance().getPlayerContribScore(player);
					boolean stonesFound = false;
					
					if (contribScore == Config.ALT_MAXIMUM_PLAYER_CONTRIB)
					{
						player.sendPacket(SystemMessageId.CONTRIB_SCORE_EXCEEDED);
						break;
					}
					
					long redContribCount = 0;
					long greenContribCount = 0;
					long blueContribCount = 0;
					
					switch (stoneType)
					{
						case 1:
							blueContribCount =
									(Config.ALT_MAXIMUM_PLAYER_CONTRIB - contribScore) / SevenSigns.BLUE_CONTRIB_POINTS;
							if (blueContribCount > blueStoneCount)
								blueContribCount = blueStoneCount;
							break;
						case 2:
							greenContribCount =
									(Config.ALT_MAXIMUM_PLAYER_CONTRIB - contribScore)
											/ SevenSigns.GREEN_CONTRIB_POINTS;
							if (greenContribCount > greenStoneCount)
								greenContribCount = greenStoneCount;
							break;
						case 3:
							redContribCount =
									(Config.ALT_MAXIMUM_PLAYER_CONTRIB - contribScore) / SevenSigns.RED_CONTRIB_POINTS;
							if (redContribCount > redStoneCount)
								redContribCount = redStoneCount;
							break;
						case 4:
							long tempContribScore = contribScore;
							redContribCount =
									(Config.ALT_MAXIMUM_PLAYER_CONTRIB - tempContribScore)
											/ SevenSigns.RED_CONTRIB_POINTS;
							if (redContribCount > redStoneCount)
								redContribCount = redStoneCount;
							tempContribScore += redContribCount * SevenSigns.RED_CONTRIB_POINTS;
							greenContribCount =
									(Config.ALT_MAXIMUM_PLAYER_CONTRIB - tempContribScore)
											/ SevenSigns.GREEN_CONTRIB_POINTS;
							if (greenContribCount > greenStoneCount)
								greenContribCount = greenStoneCount;
							tempContribScore += greenContribCount * SevenSigns.GREEN_CONTRIB_POINTS;
							blueContribCount =
									(Config.ALT_MAXIMUM_PLAYER_CONTRIB - tempContribScore)
											/ SevenSigns.BLUE_CONTRIB_POINTS;
							if (blueContribCount > blueStoneCount)
								blueContribCount = blueStoneCount;
							break;
					}
					if (redContribCount > 0)
					{
						if (player.destroyItemByItemId("SevenSigns", SevenSigns.SEAL_STONE_RED_ID, redContribCount,
								this, false))
						{
							stonesFound = true;
							SystemMessage msg = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
							msg.addItemName(SevenSigns.SEAL_STONE_RED_ID);
							msg.addItemNumber(redContribCount);
							player.sendPacket(msg);
						}
					}
					if (greenContribCount > 0)
					{
						if (player.destroyItemByItemId("SevenSigns", SevenSigns.SEAL_STONE_GREEN_ID, greenContribCount,
								this, false))
						{
							stonesFound = true;
							SystemMessage msg = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
							msg.addItemName(SevenSigns.SEAL_STONE_GREEN_ID);
							msg.addItemNumber(greenContribCount);
							player.sendPacket(msg);
						}
					}
					if (blueContribCount > 0)
					{
						if (player.destroyItemByItemId("SevenSigns", SevenSigns.SEAL_STONE_BLUE_ID, blueContribCount,
								this, false))
						{
							stonesFound = true;
							SystemMessage msg = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
							msg.addItemName(SevenSigns.SEAL_STONE_BLUE_ID);
							msg.addItemNumber(blueContribCount);
							player.sendPacket(msg);
						}
					}
					
					if (!stonesFound)
					{
						player.sendMessage("You do not have any seal stones of that type.");
						break;
					}
					
					contribScore =
							SevenSigns.getInstance().addPlayerStoneContrib(player, blueContribCount, greenContribCount,
									redContribCount);
					
					sm = new SystemMessage(SystemMessageId.CONTRIB_SCORE_INCREASED_BY_S1);
					sm.addItemNumber(contribScore);
					player.sendPacket(sm);
					
					showChatWindow(player, 6, null, false);
					break;
				case 7: // Exchange Ancient Adena for Adena - SevenSigns 7 xxxxxxx
					long ancientAdenaConvert = 0;
					
					try
					{
						ancientAdenaConvert = Long.parseLong(command.substring(13).trim());
					}
					catch (Exception e)
					{
						showChatWindow(player, SevenSigns.SEVEN_SIGNS_HTML_PATH + "blkmrkt_3.htm");
						break;
					}
					
					if (ancientAdenaConvert < 1)
					{
						showChatWindow(player, SevenSigns.SEVEN_SIGNS_HTML_PATH + "blkmrkt_3.htm");
						break;
					}
					if (ancientAdenaAmount < ancientAdenaConvert)
					{
						showChatWindow(player, SevenSigns.SEVEN_SIGNS_HTML_PATH + "blkmrkt_4.htm");
						break;
					}
					
					player.reduceAncientAdena("SevenSigns", ancientAdenaConvert, this, true);
					player.addAdena("SevenSigns", ancientAdenaConvert, this, true);
					
					iu = new InventoryUpdate();
					iu.addModifiedItem(player.getInventory().getAncientAdenaInstance());
					iu.addModifiedItem(player.getInventory().getAdenaInstance());
					player.sendPacket(iu);
					showChatWindow(player, SevenSigns.SEVEN_SIGNS_HTML_PATH + "blkmrkt_5.htm");
					break;
				case 9: // Receive Contribution Rewards
					int playerCabal = SevenSigns.getInstance().getPlayerCabal(player);
					int winningCabal = SevenSigns.getInstance().getCabalHighestScore();
					
					if (SevenSigns.getInstance().isSealValidationPeriod() && playerCabal == winningCabal)
					{
						long ancientAdenaReward = SevenSigns.getInstance().getAncientAdenaReward(player, true);
						
						if (ancientAdenaReward < 3)
						{
							showChatWindow(player, 9, "b", false);
							break;
						}
						
						player.addAncientAdena("SevenSigns", ancientAdenaReward, this, true);
						
						// Send inventory update packet
						iu = new InventoryUpdate();
						iu.addModifiedItem(player.getInventory().getAncientAdenaInstance());
						player.sendPacket(iu);
						
						// Update current load as well
						su = new StatusUpdate(player.getObjectId());
						su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
						player.sendPacket(su);
						
						showChatWindow(player, 9, "a", false);
					}
					break;
				case 11: // Teleport to Hunting Grounds
					try
					{
						String portInfo = command.substring(14).trim();
						
						StringTokenizer st = new StringTokenizer(portInfo);
						int x = Integer.parseInt(st.nextToken());
						int y = Integer.parseInt(st.nextToken());
						int z = Integer.parseInt(st.nextToken());
						long ancientAdenaCost = Long.parseLong(st.nextToken());
						
						if (ancientAdenaCost > 0)
						{
							if (!player.reduceAncientAdena("SevenSigns", ancientAdenaCost, this, true))
								break;
						}
						
						player.teleToLocation(x, y, z);
					}
					catch (Exception e)
					{
						_log.warn("SevenSigns: Error occurred while teleporting player: ", e);
					}
					break;
				case 17: // Exchange Seal Stones for Ancient Adena (Type Choice) - SevenSigns 17 x
					stoneType = Integer.parseInt(command.substring(14));
					int stoneId = 0;
					long stoneCount = 0;
					long stoneValue = 0;
					String stoneColor = null;
					String content;
					
					switch (stoneType)
					{
						case 1:
							stoneColor = "blue";
							stoneId = SevenSigns.SEAL_STONE_BLUE_ID;
							stoneValue = SevenSigns.SEAL_STONE_BLUE_VALUE;
							break;
						case 2:
							stoneColor = "green";
							stoneId = SevenSigns.SEAL_STONE_GREEN_ID;
							stoneValue = SevenSigns.SEAL_STONE_GREEN_VALUE;
							break;
						case 3:
							stoneColor = "red";
							stoneId = SevenSigns.SEAL_STONE_RED_ID;
							stoneValue = SevenSigns.SEAL_STONE_RED_VALUE;
							break;
					}
					
					L2ItemInstance stoneInstance = player.getInventory().getItemByItemId(stoneId);
					
					if (stoneInstance != null)
						stoneCount = stoneInstance.getCount();
					
					path = SevenSigns.SEVEN_SIGNS_HTML_PATH + "signs_17.htm";
					content = HtmCache.getInstance().getHtm(path);
					
					if (content != null)
					{
						content = content.replaceAll("%stoneColor%", stoneColor);
						content = content.replaceAll("%stoneValue%", String.valueOf(stoneValue));
						content = content.replaceAll("%stoneCount%", String.valueOf(stoneCount));
						content = content.replaceAll("%stoneItemId%", String.valueOf(stoneId));
						content = content.replaceAll("%objectId%", String.valueOf(getObjectId()));
						
						NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
						html.setHtml(content);
						player.sendPacket(html);
					}
					else
					{
						_log.warn("Problem with HTML text " + SevenSigns.SEVEN_SIGNS_HTML_PATH + "signs_17.htm: "
								+ path);
					}
					break;
				case 18: // Exchange Seal Stones for Ancient Adena - SevenSigns 18 xxxx xxxxxx
					int convertStoneId = Integer.parseInt(command.substring(14, 18));
					long convertCount = 0;
					
					try
					{
						convertCount = Long.parseLong(command.substring(19).trim());
					}
					catch (Exception NumberFormatException)
					{
						player.sendMessage("You must enter an integer amount.");
						break;
					}
					
					L2ItemInstance convertItem = player.getInventory().getItemByItemId(convertStoneId);
					
					if (convertItem == null)
					{
						player.sendMessage("You do not have any seal stones of that type.");
						break;
					}
					
					long totalCount = convertItem.getCount();
					long ancientAdenaReward = 0;
					
					if (convertCount <= totalCount && convertCount > 0)
					{
						switch (convertStoneId)
						{
							case SevenSigns.SEAL_STONE_BLUE_ID:
								ancientAdenaReward = SevenSigns.calcAncientAdenaReward(convertCount, 0, 0);
								break;
							case SevenSigns.SEAL_STONE_GREEN_ID:
								ancientAdenaReward = SevenSigns.calcAncientAdenaReward(0, convertCount, 0);
								break;
							case SevenSigns.SEAL_STONE_RED_ID:
								ancientAdenaReward = SevenSigns.calcAncientAdenaReward(0, 0, convertCount);
								break;
						}
						
						if (player.destroyItemByItemId("SevenSigns", convertStoneId, convertCount, this, true))
						{
							player.addAncientAdena("SevenSigns", ancientAdenaReward, this, true);
							
							// Send inventory update packet
							iu = new InventoryUpdate();
							iu.addModifiedItem(player.getInventory().getAncientAdenaInstance());
							iu.addModifiedItem(convertItem);
							player.sendPacket(iu);
							
							// Update current load as well
							su = new StatusUpdate(player.getObjectId());
							su.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
							player.sendPacket(su);
						}
					}
					else
					{
						player.sendMessage("You do not have that many seal stones.");
					}
					break;
				case 19: // Seal Information (for when joining a cabal)
					int chosenSeal = Integer.parseInt(command.substring(16));
					String fileSuffix =
							SevenSigns.getSealName(chosenSeal, true) + "_" + SevenSigns.getCabalShortName(cabal);
					
					showChatWindow(player, val, fileSuffix, false);
					break;
				case 20: // Seal Status (for when joining a cabal)
					StringBuilder contentBuffer =
							new StringBuilder("<html><body><font color=\"LEVEL\">[ Seal Status ]</font><br>");
					
					for (int i = 1; i < 4; i++)
					{
						int sealOwner = SevenSigns.getInstance().getSealOwner(i);
						
						if (sealOwner != SevenSigns.CABAL_NULL)
							contentBuffer.append("[" + SevenSigns.getSealName(i, false) + ": "
									+ SevenSigns.getCabalName(sealOwner) + "]<br>");
						else
							contentBuffer.append("[" + SevenSigns.getSealName(i, false) + ": Nothingness]<br>");
					}
					
					contentBuffer.append("<a action=\"bypass -h npc_" + getObjectId() + "_SevenSigns 3 " + cabal
							+ "\">Go back.</a></body></html>");
					
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					html.setHtml(contentBuffer.toString());
					player.sendPacket(html);
					break;
				default:
					// 1 = Purchase Record Intro
					// 5 = Contrib Seal Stones Intro
					// 16 = Choose Type of Seal Stones to Convert
					
					showChatWindow(player, val, null, false);
					break;
			
			}
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
	
	private void showChatWindow(L2Player player, int val, String suffix, boolean isDescription)
	{
		String filename = SevenSigns.SEVEN_SIGNS_HTML_PATH;
		
		filename += (isDescription) ? "desc_" + val : "signs_" + val;
		filename += (suffix != null) ? "_" + suffix + ".htm" : ".htm";
		
		showChatWindow(player, filename);
	}
}
