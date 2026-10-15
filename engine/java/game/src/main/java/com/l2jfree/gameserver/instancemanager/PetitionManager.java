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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.GmListTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * Petition Manager
 * 
 * @author Tempy
 *
 */
public final class PetitionManager
{
	protected static Logger _log = LoggerFactory.getLogger(PetitionManager.class);
	
	private static int _lastUsedId;
	
	private final Map<Integer, Petition> _pendingPetitions;
	private final Map<Integer, Petition> _completedPetitions;
	
	private static enum PetitionState
	{
		Pending,
		Responder_Cancel,
		Responder_Missing,
		Responder_Reject,
		Responder_Complete,
		Petitioner_Cancel,
		Petitioner_Missing,
		In_Process,
		Completed
	}
	
	private static enum PetitionType
	{
		Immobility,
		Recovery_Related,
		Bug_Report,
		Quest_Related,
		Bad_User,
		Suggestions,
		Game_Tip,
		Operation_Related,
		Other
	}
	
	public static PetitionManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private class Petition
	{
		private final long _submitTime = System.currentTimeMillis();
		//		private long					_endTime	= -1;
		
		private final int _id;
		private final PetitionType _type;
		private PetitionState _state = PetitionState.Pending;
		private final String _content;
		
		private final List<CreatureSay> _messageLog = new ArrayList<CreatureSay>();
		
		private final L2Player _petitioner;
		private L2Player _responder;
		
		public Petition(L2Player petitioner, String petitionText, int petitionType)
		{
			petitionType--;
			//_id = IdFactory.getInstance().getNextId();
			_lastUsedId++;
			_id = _lastUsedId;
			if (petitionType >= PetitionType.values().length)
			{
				_log.warn("PetitionManager:Petition : invalid petition type (received type was +1) : " + petitionType);
			}
			_type = PetitionType.values()[petitionType];
			_content = petitionText;
			
			_petitioner = petitioner;
		}
		
		protected boolean addLogMessage(CreatureSay cs)
		{
			return _messageLog.add(cs);
		}
		
		protected List<CreatureSay> getLogMessages()
		{
			return _messageLog;
		}
		
		public boolean endPetitionConsultation(PetitionState endState)
		{
			setState(endState);
			//			_endTime = System.currentTimeMillis();
			
			if (getResponder() != null && getResponder().isOnline() == 1)
			{
				if (endState == PetitionState.Responder_Reject)
				{
					getPetitioner().sendMessage("Your petition was rejected. Please try again later.");
				}
				else
				{
					// Ending petition consultation with <Player>.
					SystemMessage sm = new SystemMessage(SystemMessageId.PETITION_ENDED_WITH_C1);
					sm.addString(getPetitioner().getName());
					getResponder().sendPacket(sm);
					
					if (endState == PetitionState.Petitioner_Cancel)
					{
						// Receipt No. <ID> petition cancelled.
						sm = new SystemMessage(SystemMessageId.RECENT_NO_S1_CANCELED);
						sm.addNumber(getId());
						getResponder().sendPacket(sm);
					}
				}
			}
			
			// End petition consultation and inform them, if they are still online.
			if (getPetitioner() != null && getPetitioner().isOnline() == 1)
				getPetitioner().sendPacket(SystemMessageId.THIS_END_THE_PETITION_PLEASE_PROVIDE_FEEDBACK);
			
			getCompletedPetitions().put(getId(), this);
			return (getPendingPetitions().remove(getId()) != null);
		}
		
		public String getContent()
		{
			return _content;
		}
		
		public int getId()
		{
			return _id;
		}
		
		public L2Player getPetitioner()
		{
			return _petitioner;
		}
		
		public L2Player getResponder()
		{
			return _responder;
		}
		
		//		public long getEndTime()
		//		{
		//			return _endTime;
		//		}
		
		public long getSubmitTime()
		{
			return _submitTime;
		}
		
		public PetitionState getState()
		{
			return _state;
		}
		
		public String getTypeAsString()
		{
			return _type.toString().replace("_", " ");
		}
		
		public void sendPetitionerPacket(L2ServerPacket responsePacket)
		{
			if (getPetitioner() == null || getPetitioner().isOnline() == 0)
			{
				// Allows petitioners to see the results of their petition when they log back into the game.
				//endPetitionConsultation(PetitionState.Petitioner_Missing);
				return;
			}
			
			getPetitioner().sendPacket(responsePacket);
		}
		
		public void sendResponderPacket(L2ServerPacket responsePacket)
		{
			if (getResponder() == null || getResponder().isOnline() == 0)
			{
				endPetitionConsultation(PetitionState.Responder_Missing);
				return;
			}
			
			getResponder().sendPacket(responsePacket);
		}
		
		public void setState(PetitionState state)
		{
			_state = state;
		}
		
		public void setResponder(L2Player respondingAdmin)
		{
			if (getResponder() != null)
				return;
			
			_responder = respondingAdmin;
		}
	}
	
	private PetitionManager()
	{
		_log.info("PetitionManager: initialized.");
		// petitions are filed by players and answered by GMs on different threads; ids grow, so the order stays
		_pendingPetitions = new ConcurrentSkipListMap<Integer, Petition>();
		_completedPetitions = new ConcurrentSkipListMap<Integer, Petition>();
	}
	
	public void clearPendingPetitions()
	{
		int numPetitions = getPendingPetitionCount();
		
		getPendingPetitions().clear();
		_log.info("PetitionManager: Pending petition queue cleared. " + numPetitions + " petition(s) removed.");
	}
	
	public boolean acceptPetition(L2Player respondingAdmin, int petitionId)
	{
		if (!isValidPetition(petitionId))
			return false;
		
		Petition currPetition = getPendingPetitions().get(petitionId);
		
		if (currPetition.getResponder() != null)
			return false;
		
		currPetition.setResponder(respondingAdmin);
		currPetition.setState(PetitionState.In_Process);
		
		// Petition application accepted. (Send to Petitioner)
		currPetition.sendPetitionerPacket(SystemMessageId.PETITION_APP_ACCEPTED.getSystemMessage());
		
		// Petition application accepted. Reciept No. is <ID>
		SystemMessage sm = new SystemMessage(SystemMessageId.PETITION_ACCEPTED_RECENT_NO_S1);
		sm.addNumber(currPetition.getId());
		currPetition.sendResponderPacket(sm);
		
		// Petition consultation with <Player> underway.
		sm = new SystemMessage(SystemMessageId.STARTING_PETITION_WITH_C1);
		sm.addString(currPetition.getPetitioner().getName());
		currPetition.sendResponderPacket(sm);
		return true;
	}
	
	public boolean cancelActivePetition(L2Player player)
	{
		for (Petition currPetition : getPendingPetitions().values())
		{
			if (currPetition.getPetitioner() != null
					&& currPetition.getPetitioner().getObjectId() == player.getObjectId())
				return (currPetition.endPetitionConsultation(PetitionState.Petitioner_Cancel));
			
			if (currPetition.getResponder() != null
					&& currPetition.getResponder().getObjectId() == player.getObjectId())
				return (currPetition.endPetitionConsultation(PetitionState.Responder_Cancel));
		}
		
		return false;
	}
	
	public void checkPetitionMessages(L2Player petitioner)
	{
		if (petitioner != null)
			for (Petition currPetition : getPendingPetitions().values())
			{
				if (currPetition == null)
					continue;
				
				if (currPetition.getPetitioner() != null
						&& currPetition.getPetitioner().getObjectId() == petitioner.getObjectId())
				{
					for (CreatureSay logMessage : currPetition.getLogMessages())
						petitioner.sendPacket(logMessage);
					
					return;
				}
			}
	}
	
	public boolean endActivePetition(L2Player player)
	{
		if (!player.isGM())
			return false;
		
		for (Petition currPetition : getPendingPetitions().values())
		{
			if (currPetition == null)
				continue;
			
			if (currPetition.getResponder() != null
					&& currPetition.getResponder().getObjectId() == player.getObjectId())
				return (currPetition.endPetitionConsultation(PetitionState.Completed));
		}
		
		return false;
	}
	
	protected Map<Integer, Petition> getCompletedPetitions()
	{
		return _completedPetitions;
	}
	
	protected Map<Integer, Petition> getPendingPetitions()
	{
		return _pendingPetitions;
	}
	
	public int getPendingPetitionCount()
	{
		return getPendingPetitions().size();
	}
	
	public int getPlayerTotalPetitionCount(L2Player player)
	{
		if (player == null)
			return 0;
		
		int petitionCount = 0;
		
		for (Petition currPetition : getPendingPetitions().values())
		{
			if (currPetition == null)
				continue;
			
			if (currPetition.getPetitioner() != null
					&& currPetition.getPetitioner().getObjectId() == player.getObjectId())
				petitionCount++;
		}
		
		for (Petition currPetition : getCompletedPetitions().values())
		{
			if (currPetition == null)
				continue;
			
			if (currPetition.getPetitioner() != null
					&& currPetition.getPetitioner().getObjectId() == player.getObjectId())
				petitionCount++;
		}
		
		return petitionCount;
	}
	
	public boolean isPetitionInProcess()
	{
		for (Petition currPetition : getPendingPetitions().values())
		{
			if (currPetition == null)
				continue;
			
			if (currPetition.getState() == PetitionState.In_Process)
				return true;
		}
		
		return false;
	}
	
	public boolean isPetitionInProcess(int petitionId)
	{
		if (!isValidPetition(petitionId))
			return false;
		
		Petition currPetition = getPendingPetitions().get(petitionId);
		return (currPetition.getState() == PetitionState.In_Process);
	}
	
	public boolean isPlayerInConsultation(L2Player player)
	{
		if (player != null)
			for (Petition currPetition : getPendingPetitions().values())
			{
				if (currPetition == null)
					continue;
				
				if (currPetition.getState() != PetitionState.In_Process)
					continue;
				
				if ((currPetition.getPetitioner() != null && currPetition.getPetitioner().getObjectId() == player
						.getObjectId())
						|| (currPetition.getResponder() != null && currPetition.getResponder().getObjectId() == player
								.getObjectId()))
					return true;
			}
		
		return false;
	}
	
	public boolean isPetitioningAllowed()
	{
		return Config.PETITIONING_ALLOWED;
	}
	
	public boolean isPlayerPetitionPending(L2Player petitioner)
	{
		if (petitioner != null)
			for (Petition currPetition : getPendingPetitions().values())
			{
				if (currPetition == null)
					continue;
				
				if (currPetition.getPetitioner() != null
						&& currPetition.getPetitioner().getObjectId() == petitioner.getObjectId())
					return true;
			}
		
		return false;
	}
	
	private boolean isValidPetition(int petitionId)
	{
		return getPendingPetitions().containsKey(petitionId);
	}
	
	public boolean rejectPetition(L2Player respondingAdmin, int petitionId)
	{
		if (!isValidPetition(petitionId))
			return false;
		
		Petition currPetition = getPendingPetitions().get(petitionId);
		
		if (currPetition.getResponder() != null)
			return false;
		
		currPetition.setResponder(respondingAdmin);
		return (currPetition.endPetitionConsultation(PetitionState.Responder_Reject));
	}
	
	public boolean sendActivePetitionMessage(L2Player player, String messageText)
	{
		//if (!isPlayerInConsultation(player))
		//return false;
		
		CreatureSay cs;
		
		for (Petition currPetition : getPendingPetitions().values())
		{
			if (currPetition == null)
				continue;
			
			if (currPetition.getPetitioner() != null
					&& currPetition.getPetitioner().getObjectId() == player.getObjectId())
			{
				cs =
						new CreatureSay(player.getObjectId(), SystemChatChannelId.Chat_User_Pet, player.getName(),
								messageText);
				currPetition.addLogMessage(cs);
				
				currPetition.sendResponderPacket(cs);
				currPetition.sendPetitionerPacket(cs);
				return true;
			}
			
			if (currPetition.getResponder() != null
					&& currPetition.getResponder().getObjectId() == player.getObjectId())
			{
				cs =
						new CreatureSay(player.getObjectId(), SystemChatChannelId.Chat_GM_Pet, player.getName(),
								messageText);
				currPetition.addLogMessage(cs);
				
				currPetition.sendResponderPacket(cs);
				currPetition.sendPetitionerPacket(cs);
				return true;
			}
		}
		
		return false;
	}
	
	public void sendPendingPetitionList(L2Player activeChar)
	{
		StringBuilder htmlContent =
				new StringBuilder("<html><body>"
						+ "<center><font color=\"LEVEL\">Current Petitions</font><br><table width=\"300\">");
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM HH:mm z");
		
		if (getPendingPetitionCount() == 0)
			htmlContent.append("<tr><td colspan=\"4\">There are no currently pending petitions.</td></tr>");
		else
			htmlContent
					.append("<tr><td></td><td><font color=\"999999\">Petitioner</font></td>"
							+ "<td><font color=\"999999\">Petition Type</font></td><td><font color=\"999999\">Submitted</font></td></tr>");
		
		for (Petition currPetition : getPendingPetitions().values())
		{
			if (currPetition == null)
				continue;
			
			htmlContent.append("<tr><td>");
			
			if (currPetition.getState() != PetitionState.In_Process)
				htmlContent.append("<button value=\"View\" action=\"bypass -h admin_view_petition "
						+ currPetition.getId() + "\" "
						+ "width=\"40\" height=\"15\" back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
			else
				htmlContent.append("<font color=\"999999\">In Process</font>");
			
			htmlContent.append("</td><td>" + currPetition.getPetitioner().getName() + "</td><td>"
					+ currPetition.getTypeAsString() + "</td><td>"
					+ dateFormat.format(new Date(currPetition.getSubmitTime())) + "</td></tr>");
		}
		
		htmlContent
				.append("</table><br><button value=\"Refresh\" action=\"bypass -h admin_view_petitions\" width=\"50\" "
						+ "height=\"15\" back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"><br><button value=\"Back\" action=\"bypass -h admin_admin\" "
						+ "width=\"40\" height=\"15\" back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></center></body></html>");
		
		NpcHtmlMessage htmlMsg = new NpcHtmlMessage(0);
		htmlMsg.setHtml(htmlContent.toString());
		activeChar.sendPacket(htmlMsg);
	}
	
	public int submitPetition(L2Player petitioner, String petitionText, int petitionType)
	{
		// Create a new petition instance and add it to the list of pending petitions.
		Petition newPetition = new Petition(petitioner, petitionText, petitionType);
		int newPetitionId = newPetition.getId();
		getPendingPetitions().put(newPetitionId, newPetition);
		
		// Notify all GMs that a new petition has been submitted.
		String msgContent = petitioner.getName() + " has submitted a new petition."; //(ID: " + newPetitionId + ").";
		GmListTable.broadcastToGMs(new CreatureSay(petitioner.getObjectId(), SystemChatChannelId.Chat_Hero,
				"Petition System", msgContent));
		
		return newPetitionId;
	}
	
	public void viewPetition(L2Player activeChar, int petitionId)
	{
		if (!activeChar.isGM())
			return;
		
		if (!isValidPetition(petitionId))
			return;
		
		Petition currPetition = getPendingPetitions().get(petitionId);
		StringBuilder htmlContent = new StringBuilder();
		SimpleDateFormat dateFormat = new SimpleDateFormat("EEE dd MMM HH:mm z");
		
		htmlContent.append("<html><body><center><br><font color=\"LEVEL\">Petition #");
		htmlContent.append(currPetition.getId());
		htmlContent.append("</font><br1><img src=\"L2UI.SquareGray\" width=\"200\" height=\"1\"></center><br>");
		htmlContent.append("Submit Time: ");
		htmlContent.append(dateFormat.format(new Date(currPetition.getSubmitTime())));
		htmlContent.append("<br1>Petitioner: ");
		htmlContent.append(currPetition.getPetitioner().getName());
		htmlContent.append("<br1>Petition Type: ");
		htmlContent.append(currPetition.getTypeAsString());
		htmlContent.append("<br>");
		htmlContent.append(currPetition.getContent());
		htmlContent.append("<br><center><button value=\"Accept\" action=\"bypass -h admin_accept_petition ");
		htmlContent.append(currPetition.getId());
		htmlContent
				.append("\"width=\"50\" height=\"15\" back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"><br1>");
		htmlContent.append("<button value=\"Reject\" action=\"bypass -h admin_reject_petition ");
		htmlContent.append(currPetition.getId());
		htmlContent
				.append("\" width=\"50\" height=\"15\" back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"><br>");
		htmlContent
				.append("<button value=\"Back\" action=\"bypass -h admin_view_petitions\" width=\"40\" height=\"15\" back=\"L2UI_ct1.button_df\" ");
		htmlContent.append("fore=\"L2UI_ct1.button_df\"></center></body></html>");
		
		NpcHtmlMessage htmlMsg = new NpcHtmlMessage(petitionId);
		htmlMsg.setHtml(htmlContent.toString());
		activeChar.sendPacket(htmlMsg);
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final PetitionManager _instance = new PetitionManager();
	}
}
