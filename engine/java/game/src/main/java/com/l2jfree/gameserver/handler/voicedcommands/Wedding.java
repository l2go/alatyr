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
package com.l2jfree.gameserver.handler.voicedcommands;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Player.TeleportMode;
import com.l2jfree.gameserver.handler.IVoicedCommandHandler;
import com.l2jfree.gameserver.instancemanager.CoupleManager;
import com.l2jfree.gameserver.instancemanager.DimensionalRiftManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.entity.Siege;
import com.l2jfree.gameserver.model.restriction.AvailableRestriction;
import com.l2jfree.gameserver.model.restriction.ObjectRestrictions;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.effects.AbnormalEffect;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.client.ConfirmDlgAnswer.AnswerHandler;
import com.l2jfree.gameserver.network.packets.server.ConfirmDlg;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * @author evill33t
 * 
 */
public class Wedding implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS = { "divorce", "engage", "gotolove" };
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IVoicedCommandHandler#useVoicedCommand(String, com.l2jfree.gameserver.model.L2Player), String)
	 */
	@Override
	public boolean useVoicedCommand(String command, L2Player activeChar, String target)
	{
		if (!Config.ALLOW_WEDDING)
			return false;
		
		if (command.startsWith("engage"))
		{
			engage(activeChar);
			return true;
		}
		else if (command.startsWith("divorce"))
		{
			divorce(activeChar);
			return true;
		}
		else if (command.startsWith("gotolove"))
		{
			goToLove(activeChar);
			return true;
		}
		
		return false;
	}
	
	public boolean divorce(L2Player activeChar)
	{
		if (activeChar.getPartnerId() == 0)
			return false;
		
		int _partnerId = activeChar.getPartnerId();
		int _coupleId = activeChar.getCoupleId();
		long adenaAmount = 0;
		
		if (activeChar.isMaried())
		{
			activeChar.sendMessage("You are now divorced.");
			
			adenaAmount = (activeChar.getAdena() / 100) * Config.WEDDING_DIVORCE_COSTS;
			activeChar.getInventory().reduceAdena("Wedding", adenaAmount, activeChar, null);
			
		}
		else
			activeChar.sendMessage("You have broken up as a couple.");
		
		L2Player partner = L2World.getInstance().getPlayer(_partnerId);
		if (partner != null)
		{
			partner.setPartnerId(0);
			if (partner.isMaried())
				partner.sendMessage("Your fiance has decided to divorce from you.");
			else
				partner.sendMessage("Your fiance has decided to break the engagement with you.");
			
			// give adena
			if (adenaAmount > 0)
				partner.addAdena("WEDDING", adenaAmount, null, false);
		}
		
		CoupleManager.getInstance().deleteCouple(_coupleId);
		return true;
	}
	
	public boolean engage(final L2Player activeChar)
	{
		// Check target
		if (activeChar.getTarget() == null)
		{
			activeChar.sendMessage("You have no one targeted.");
			return false;
		}
		
		// Check if target is a L2Player
		if (!(activeChar.getTarget() instanceof L2Player))
		{
			activeChar.sendMessage("You can only ask another player for engagement");
			
			return false;
		}
		
		// Check if player is already engaged
		if (activeChar.getPartnerId() != 0)
		{
			activeChar.sendMessage("You are already engaged.");
			if (Config.WEDDING_PUNISH_INFIDELITY)
			{
				activeChar.startAbnormalEffect(AbnormalEffect.BIG_HEAD); // give player a Big Head
				// lets recycle the sevensigns debuffs
				int skillId;
				
				int skillLevel = 1;
				
				if (activeChar.getLevel() > 40)
					skillLevel = 2;
				
				if (activeChar.isMageClass())
					skillId = 4361;
				else
					skillId = 4362;
				
				L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLevel);
				
				if (activeChar.getFirstEffect(skill) == null)
				{
					skill.getEffects(activeChar, activeChar);
					SystemMessage sm = new SystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT);
					sm.addSkillName(skill);
					activeChar.sendPacket(sm);
				}
			}
			return false;
		}
		
		final L2Player ptarget = (L2Player)activeChar.getTarget();
		
		// Check if player target himself
		if (ptarget.getObjectId() == activeChar.getObjectId())
		{
			activeChar.sendMessage("Is there something wrong with you, are you trying to go out with yourself?");
			return false;
		}
		
		if (ptarget.isMaried())
		{
			activeChar.sendMessage("Player already married.");
			return false;
		}
		
		if (ptarget.getPartnerId() != 0)
		{
			activeChar.sendMessage("Player already engaged.");
			return false;
		}
		
		if (ptarget.hasActiveConfirmDlg())
		{
			activeChar.sendMessage("Player already asked by someone else.");
			return false;
		}
		
		if (ptarget.getAppearance().getSex() == activeChar.getAppearance().getSex() && !Config.WEDDING_SAMESEX)
		{
			activeChar.sendMessage("You can't ask someone of the same sex for engagement.");
			return false;
		}
		
		if (!activeChar.getFriendList().contains(ptarget))
		{
			activeChar
					.sendMessage("The player you want to ask is not on your friends list, you must first be on each others friends list before you choose to engage.");
			return false;
		}
		
		ConfirmDlg dlg = new ConfirmDlg(SystemMessageId.S1);
		dlg.addAnswerHandler(new AnswerHandler() {
			@Override
			public void handle(boolean answer)
			{
				if (!Config.ALLOW_WEDDING)
					return;
				
				if (answer)
				{
					CoupleManager.getInstance().createCouple(activeChar, ptarget);
					activeChar.sendMessage("Engage accepted.");
				}
				else
					activeChar.sendMessage("Engage declined.");
			}
		});
		ptarget.sendPacket(dlg.addString(activeChar.getName()
				+ " is asking to engage you. Do you want to start a new relationship?"));
		return true;
	}
	
	private static L2Player checkGoToLoveState(L2Player activeChar)
	{
		Siege siege = SiegeManager.getInstance().getSiege(activeChar);
		
		if (!activeChar.isMaried())
		{
			activeChar.sendMessage("You're not married.");
			return null;
		}
		else if (activeChar.getPartnerId() == 0)
		{
			activeChar.sendMessage("Couldn't find your fiance in Database - Inform a Gamemaster.");
			_log.error("Married but couldn't find partner for " + activeChar.getName());
			return null;
		}
		else if (!activeChar.canTeleport(TeleportMode.GOTOLOVE))
		{
			return null;
		}
		// Check to see if the player is in dimensional rift.
		else if (activeChar.isInParty() && activeChar.getParty().isInDimensionalRift())
		{
			activeChar.sendMessage("You are in the dimensional rift.");
			return null;
		}
		// Check if player is in Siege
		else if (siege != null && siege.getIsInProgress())
		{
			activeChar.sendMessage("You are in siege, you can't go to your partner.");
			return null;
		}
		// Check if player is a Cursed Weapon owner
		else if (activeChar.isCursedWeaponEquipped())
		{
			activeChar.sendMessage("You are currently holding a cursed weapon.");
			return null;
		}
		else if (activeChar.isInsideZone(L2Zone.FLAG_SUNLIGHTROOM) || activeChar.isInsideZone(L2Zone.FLAG_NOSUMMON))
		{
			activeChar.sendMessage("You are in a unsuitable area for teleporting.");
			return null;
		}
		else if (ObjectRestrictions.getInstance().checkRestriction(activeChar, AvailableRestriction.PlayerGotoLove))
		{
			activeChar.sendMessage("You cannot find your love due to a restriction.");
			return null;
		}
		
		L2Player partner = L2World.getInstance().getPlayer(activeChar.getPartnerId());
		if (partner != null)
		{
			siege = SiegeManager.getInstance().getSiege(partner);
		}
		else
		{
			activeChar.sendMessage("Your partner is not online.");
			return null;
		}
		
		// Check to see if the player is in a instance.
		if (!activeChar.isSameInstance(partner))
		{
			activeChar.sendMessage("Your partner is in another World!");
			return null;
		}
		else if (!partner.canTeleport(TeleportMode.GOTOLOVE))
		{
			return null;
		}
		else if (DimensionalRiftManager.getInstance().checkIfInRiftZone(partner.getX(), partner.getY(), partner.getZ(),
				false))
		{
			activeChar.sendMessage("Your partner is in dimensional rift.");
			return null;
		}
		else if (siege != null && siege.getIsInProgress())
		{
			if (partner.getAppearance().getSex())
				activeChar.sendMessage("Your partner is in siege, you can't go to her.");
			else
				activeChar.sendMessage("Your partner is in siege, you can't go to him.");
			return null;
		}
		else if (partner.isCursedWeaponEquipped())
		{
			activeChar.sendMessage("Your partner is currently holding a cursed weapon.");
			return null;
		}
		else if (partner.isInsideZone(L2Zone.FLAG_SUNLIGHTROOM) || partner.isInsideZone(L2Zone.FLAG_NOSUMMON))
		{
			activeChar.sendMessage("Your partner is in a unsuitable area for teleporting.");
			return null;
		}
		else if (partner.isIn7sDungeon() && !activeChar.isIn7sDungeon())
		{
			int playerCabal = SevenSigns.getInstance().getPlayerCabal(activeChar);
			boolean isSealValidationPeriod = SevenSigns.getInstance().isSealValidationPeriod();
			int compWinner = SevenSigns.getInstance().getCabalHighestScore();
			
			if (isSealValidationPeriod)
			{
				if (playerCabal != compWinner)
				{
					activeChar
							.sendMessage("Your Partner is in a Seven Signs Dungeon and you are not in the winner Cabal!");
					return null;
				}
			}
			else
			{
				if (playerCabal == SevenSigns.CABAL_NULL)
				{
					activeChar.sendMessage("Your Partner is in a Seven Signs Dungeon and you are not registered!");
					return null;
				}
			}
		}
		
		return partner;
	}
	
	public boolean goToLove(L2Player activeChar)
	{
		L2Player partner = null;
		if ((partner = checkGoToLoveState(activeChar)) == null)
			return false;
		
		int teleportTimer = Config.WEDDING_TELEPORT_INTERVAL * 1000;
		
		activeChar.sendMessage("After " + teleportTimer / 60000 + " min. you will be teleported to your partner.");
		activeChar.getInventory().reduceAdena("Wedding", Config.WEDDING_TELEPORT_PRICE, activeChar, null);
		
		// Continue execution later
		activeChar.setTeleportSkillCast(new EscapeFinalizer(activeChar, partner.getLoc(), partner.isIn7sDungeon()),
				teleportTimer);
		
		return true;
	}
	
	private static class EscapeFinalizer implements Runnable
	{
		private final L2Player _activeChar;
		private final Location _partnerLoc;
		private final boolean _to7sDungeon;
		
		EscapeFinalizer(L2Player activeChar, Location partnerLoc, boolean to7sDungeon)
		{
			_activeChar = activeChar;
			_partnerLoc = partnerLoc;
			_to7sDungeon = to7sDungeon;
		}
		
		@Override
		public void run()
		{
			if (checkGoToLoveState(_activeChar) == null)
				return;
			
			_activeChar.setIsIn7sDungeon(_to7sDungeon);
			_activeChar.teleToLocation(_partnerLoc, true);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IVoicedCommandHandler#getVoicedCommandList()
	 */
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}
