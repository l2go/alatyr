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
package com.l2jfree.gameserver.communitybbs.Manager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.CharNameTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.BlockList;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ExMailArrived;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.WorldTransaction;

/**
 * 
 * @author Vital
 *
 */

public class MailBBSManager extends BaseBBSManager
{
	private final static Logger _log = LoggerFactory.getLogger(MailBBSManager.class);
	
	/**
	 * 
	 * TODO Messages
	 * used
	 * Message: No more messages may be sent at this time. Each account is allowed 10 messages per day.
	 * NO_MORE_MESSAGES_TODAY(1229)
	 * used
	 * Message: You are limited to five recipients at a time.
	 * ONLY_FIVE_RECIPIENTS(1230)
	 * used
	 * Message: Your mailbox is full. There is a 100 message limit.
	 * MAILBOX_FULL(1205)
	 * used
	 * Message: $s1 has blocked you. You cannot send mail to $s1.
	 * S1_BLOCKED_YOU_CANNOT_MAIL(1228)
	 * used
	 * Message: You've sent mail.
	 * SENT_MAIL(1231)
	 * used
	 * Message: The message was not sent.
	 * MESSAGE_NOT_SENT(1232)
	 * used
	 * Message: You've got mail.
	 * NEW_MAIL(1233)
	 * 
	 * Message: The mail has been stored in your temporary mailbox.
	 * MAIL_STORED_IN_MAILBOX(1234)
	 * 
	 * Message: Your temporary mailbox is full. No more mail can be stored; you have reached the 10 message limit.
	 * TEMP_MAILBOX_FULL(1238)
	 * used
	 * Message: Your message to $s1 did not reach its recipient. You cannot send mail
	 * to the GM staff.
	 * CANNOT_MAIL_GM(1370)
	 * 
	 */
	
	private static MailBBSManager _instance = new MailBBSManager();
	
	/**
	 * @return
	 */
	public static MailBBSManager getInstance()
	{
		return _instance;
	}
	
	private class UpdateMail
	{
		private Integer charId;
		private Integer letterId;
		private Integer senderId;
		private String location;
		private String recipientNames;
		private String subject;
		private String message;
		private String sentDateFormated;
		private long sentDate;
		private String deleteDateFormated;
		private long deleteDate;
		private boolean unread;
	}
	
	public List<UpdateMail> getMail(L2Player activeChar)
	{
		List<UpdateMail> _letters = new ArrayList<UpdateMail>();
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT id, player_id, sender_player_id, folder, recipient_names, subject, message, sent_at, delete_at, is_unread FROM player_mail WHERE player_id = ? ORDER BY id DESC");
			statement.setInt(1, activeChar.getObjectId());
			ResultSet result = statement.executeQuery();
			while (result.next())
			{
				UpdateMail letter = new UpdateMail();
				letter.charId = result.getInt("player_id");
				letter.letterId = (int) result.getLong("id");
				// NULL (sender deleted) is read as 0, which getCharName shows as "No Name"
				letter.senderId = result.getInt("sender_player_id");
				letter.location = result.getString("folder");
				letter.recipientNames = result.getString("recipient_names");
				letter.subject = result.getString("subject");
				letter.message = result.getString("message");
				letter.sentDate = result.getTimestamp("sent_at").getTime();
				letter.sentDateFormated = new SimpleDateFormat("yyyy-MM-dd").format(new Date(letter.sentDate));
				letter.deleteDate = result.getTimestamp("delete_at").getTime();
				letter.deleteDateFormated = new SimpleDateFormat("yyyy-MM-dd").format(new Date(letter.deleteDate));
				letter.unread = result.getBoolean("is_unread");
				_letters.add(letter);
			}
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("couldnt load mail for " + activeChar.getName(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return _letters;
	}
	
	private UpdateMail getLetter(L2Player activeChar, int letterId)
	{
		UpdateMail letter = new UpdateMail();
		for (UpdateMail temp : getMail(activeChar))
		{
			letter = temp;
			if (letter.letterId == letterId)
				break;
		}
		return letter;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.communitybbs.Manager.BaseBBSManager#parsecmd(java.lang.String, com.l2jfree.gameserver.gameobjects.instance.L2Player)
	 */
	@Override
	public void parsecmd(String command, L2Player activeChar)
	{
		if (command.equals("_maillist_0_1_0_"))
		{
			showInbox(activeChar, 1);
		}
		else if (command.startsWith("_maillist_0_1_0_ "))
		{
			showInbox(activeChar, Integer.parseInt(command.substring(17)));
		}
		else if (command.equals("_maillist_0_1_0_sentbox"))
		{
			showSentbox(activeChar, 1);
		}
		else if (command.startsWith("_maillist_0_1_0_sentbox "))
		{
			showSentbox(activeChar, Integer.parseInt(command.substring(24)));
		}
		else if (command.equals("_maillist_0_1_0_archive"))
		{
			showMailArchive(activeChar, 1);
		}
		else if (command.startsWith("_maillist_0_1_0_archive "))
		{
			showMailArchive(activeChar, Integer.parseInt(command.substring(24)));
		}
		else if (command.equals("_maillist_0_1_0_temp_archive"))
		{
			showTempMailArchive(activeChar, 1);
		}
		else if (command.startsWith("_maillist_0_1_0_temp_archive "))
		{
			showTempMailArchive(activeChar, Integer.parseInt(command.substring(29)));
		}
		else if (command.equals("_maillist_0_1_0_write"))
		{
			showWriteView(activeChar);
		}
		else if (command.startsWith("_maillist_0_1_0_view "))
		{
			UpdateMail letter = getLetter(activeChar, Integer.parseInt(command.substring(21)));
			showLetterView(activeChar, letter);
			if (letter.unread)
				setLetterToRead(letter.letterId);
		}
		else if (command.startsWith("_maillist_0_1_0_reply "))
		{
			UpdateMail letter = getLetter(activeChar, Integer.parseInt(command.substring(22)));
			showWriteView(activeChar, getCharName(letter.senderId), letter);
		}
		else if (command.startsWith("_maillist_0_1_0_delete "))
		{
			UpdateMail letter = getLetter(activeChar, Integer.parseInt(command.substring(23)));
			deleteLetter(letter.letterId);
			showInbox(activeChar, 1);
		}
		else
		{
			notImplementedYet(activeChar, command);
		}
	}
	
	private String abbreviate(String s, int maxWidth)
	{
		return StringUtils.abbreviate(s, maxWidth);
	}
	
	private void showInbox(L2Player activeChar, int page)
	{
		int index = 0, minIndex = 0, maxIndex = 0;
		maxIndex = (page == 1 ? page * 14 : (page * 15) - 1);
		minIndex = maxIndex - 14;
		
		final StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("<body><br><br>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=810><tr><td width=10></td><td width=800 height=30 align=left>");
		html.append("<a action=\"bypass _bbshome\">HOME</a>&nbsp;&gt;&nbsp;<a action=\"bypass _maillist_0_1_0_\">Inbox</a>");
		html.append("</td></tr>");
		html.append("</table>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=810 bgcolor=808080>");
		html.append("<tr><td height=10></td></tr>");
		html.append("<tr>");
		html.append("<td fixWIDTH=5></td>");
		html.append("<td fixWIDTH=760>");
		html.append("<a action=\"bypass _maillist_0_1_0_\">[Inbox]</a>(").append(countLetters(activeChar, "inbox"))
				.append(")&nbsp;").append("<a action=\"bypass _maillist_0_1_0_sentbox\">[Sent Box]</a>(")
				.append(countLetters(activeChar, "sentbox")).append(")&nbsp;")
				.append("<a action=\"bypass _maillist_0_1_0_archive\">[Mail Archive]</a>(")
				.append(countLetters(activeChar, "archive")).append(")&nbsp;")
				.append("<a action=\"bypass _maillist_0_1_0_temp_archive\">[Temporary Mail Archive]</a>(")
				.append(countLetters(activeChar, "temparchive")).append(")</td>");
		html.append("<td fixWIDTH=5></td>");
		html.append("</tr>");
		html.append("<tr><td height=10></td></tr>");
		html.append("</table>");
		if (countLetters(activeChar, "inbox") == 0)
			html.append("<br><center>Your inbox is empty.</center>");
		else
		{
			html.append("<br>");
			html.append("<table border=0 cellspacing=0 cellpadding=2 bgcolor=808080 width=770>");
			html.append("<tr>");
			html.append("<td FIXWIDTH=5></td>");
			html.append("<td FIXWIDTH=150 align=center>Author</td>");
			html.append("<td FIXWIDTH=460 align=left>Title</td>");
			html.append("<td FIXWIDTH=150 align=center>Authoring Date</td>");
			html.append("<td FIXWIDTH=5></td>");
			html.append("</tr></table>");
			for (UpdateMail letter : getMail(activeChar))
			{
				if (activeChar.getObjectId().equals(letter.charId) && letter.location.equals("inbox"))
				{
					if (index < minIndex)
					{
						index++;
						continue;
					}
					if (index > maxIndex)
						break;
					String tempName = getCharName(letter.senderId);
					html.append("<table border=0 cellspacing=0 cellpadding=2 width=770>");
					html.append("<tr>");
					html.append("<td FIXWIDTH=5></td>");
					html.append("<td FIXWIDTH=150 align=center>").append(abbreviate(tempName, 6)).append("</td>");
					html.append("<td FIXWIDTH=460 align=left><a action=\"bypass _maillist_0_1_0_view ")
							.append(letter.letterId).append("\">").append(abbreviate(letter.subject, 51))
							.append("</a></td>");
					html.append("<td FIXWIDTH=150 align=center>").append(letter.sentDateFormated).append("</td>");
					html.append("<td FIXWIDTH=5></td>");
					html.append("</tr></table>");
					html.append("<img src=\"L2UI.SquareBlank\" width=\"770\" height=\"3\">");
					html.append("<img src=\"L2UI.SquareGrey\" width=\"770\" height=\"1\">");
					index++;
				}
			}
		}
		html.append("<table width=770><tr>");
		html.append("<td align=right><button value=\"Write\" action=\"bypass _maillist_0_1_0_write\" width=60 height=30 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("</tr></table>");
		html.append("<center><table width=770><tr>");
		html.append("<td align=right><button action=\"bypass _maillist_0_1_0_ ")
				.append(page == 1 ? page : page - 1)
				.append("\" width=16 height=16 back=\"L2UI_ct1.button_df_left_down\" fore=\"L2UI_ct1.button_df_left\"></td>");
		for (int i = 1; i <= 7; i++)
			html.append("<td align=center fixedwidth=10><a action=\"bypass _maillist_0_1_0_ ").append(i).append("\">")
					.append(i).append("</a></td>");
		html.append("<td align=left><button action=\"bypass _maillist_0_1_0_ ")
				.append(page + 1)
				.append("\" width=16 height=16 back=\"L2UI_ct1.button_df_right_down\" fore=\"L2UI_ct1.button_df_right\"></td>");
		html.append("</tr></table>");
		html.append("<table><tr>");
		html.append("<td align=right><combobox width=65 var=combo list=\"Writer\"></td>");
		html.append("<td align=center><edit var=\"keyword\" width=130 height=11 length=\"16\"></td>");
		html.append("<td align=left><button value=\"Search\" action=\"bypass _maillist_0_1_0_search $combo $keyword\" width=60 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("</tr></table></center>");
		html.append("</body></html>");
		separateAndSend(html, activeChar);
	}
	
	private void showLetterView(L2Player activeChar, UpdateMail letter)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("<body><br><br>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=770><tr><td width=10></td><td height=30 width=760 align=left>");
		html.append("<a action=\"bypass _bbshome\">HOME</a>&nbsp;&gt;&nbsp;<a action=\"bypass _maillist_0_1_0_\">Inbox</a>");
		html.append("</td></tr>");
		html.append("</table>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=770 bgcolor=808080>");
		html.append("<tr><td height=10></td></tr>");
		html.append("<tr>");
		html.append("<td FIXWIDTH=5 height=20></td>");
		html.append("<td FIXWIDTH=100 height=20 align=right>Sender:&nbsp;</td>");
		html.append("<td FIXWIDTH=360 height=20 align=left>").append(getCharName(letter.senderId)).append("</td>");//
		html.append("<td FIXWIDTH=150 height=20 align=right>Send Time:&nbsp;</td>");
		html.append("<td FIXWIDTH=150 height=20 align=left>").append(letter.sentDateFormated).append("</td>");//
		html.append("<td fixWIDTH=5 height=20></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=5 height=20></td>");
		html.append("<td FIXWIDTH=100 height=20 align=right>Recipient:&nbsp;</td>");
		html.append("<td FIXWIDTH=360 height=20 align=left>").append(letter.recipientNames).append("</td>");//
		html.append("<td FIXWIDTH=150 height=20 align=right>Delete Intended Time:&nbsp;</td>");
		html.append("<td FIXWIDTH=150 height=20 align=left>").append(letter.deleteDateFormated).append("</td>");//
		html.append("<td fixWIDTH=5 height=20></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=5 height=20></td>");
		html.append("<td FIXWIDTH=100 height=20 align=right>Title:&nbsp;</td>");
		html.append("<td FIXWIDTH=360 height=20 align=left>").append(letter.subject).append("</td>");//
		html.append("<td FIXWIDTH=150 height=20></td>");
		html.append("<td FIXWIDTH=150 height=20></td>");
		html.append("<td fixWIDTH=5 height=20></td>");
		html.append("</tr>");
		html.append("<tr><td height=10></td></tr>");
		html.append("</table>");
		html.append("<table width=770><tr>");
		html.append("<td height=10></td>");
		html.append("<td height=10></td>");
		html.append("<td height=10></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=100></td>");
		html.append("<td FIXWIDTH=560>").append(letter.message).append("</td>");
		html.append("<td FIXWIDTH=100></td>");
		html.append("</tr></table>");
		html.append("<img src=\"L2UI.SquareBlank\" width=\"770\" height=\"3\">");
		html.append("<img src=\"L2UI.SquareGrey\" width=\"770\" height=\"1\">");
		html.append("<table width=770><tr>");
		html.append("<td align=left><button value=\"View List\" action=\"bypass _maillist_0_1_0_\" width=70 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td FIXWIDTH=300></td>");
		html.append("<td align=right><button value=\"Reply\" action=\"bypass _maillist_0_1_0_reply ")
				.append(letter.letterId)
				.append("\" width=70 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td align=right><button value=\"Deliver\" action=\"bypass _maillist_0_1_0_deliver\" width=70 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td align=right><button value=\"Delete\" action=\"bypass _maillist_0_1_0_delete ")
				.append(letter.letterId)
				.append("\" width=70 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td align=right><button value=\"Store\" action=\"bypass _maillist_0_1_0_store ")
				.append(letter.letterId)
				.append("\" width=70 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td align=right><button value=\"Mail Writing\" action=\"bypass _maillist_0_1_0_write\" width=70 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("</tr></table>");
		html.append("</body></html>");
		separateAndSend(html, activeChar);
	}
	
	private void showSentbox(L2Player activeChar, int page)
	{
		int index = 0, minIndex = 0, maxIndex = 0;
		maxIndex = (page == 1 ? page * 14 : (page * 15) - 1);
		minIndex = maxIndex - 14;
		
		final StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("<body><br><br>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=810><tr><td width=10></td><td width=800 height=30 align=left>");
		html.append("<a action=\"bypass _bbshome\">HOME</a>&nbsp;&gt;&nbsp;<a action=\"bypass _maillist_0_1_0_sentbox\">Sent Box</a>");
		html.append("</td></tr>");
		html.append("</table>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=810 bgcolor=808080>");
		html.append("<tr><td height=10></td></tr>");
		html.append("<tr>");
		html.append("<td fixWIDTH=5></td>");
		html.append("<td fixWIDTH=760>");
		html.append("<a action=\"bypass _maillist_0_1_0_\">[Inbox]</a>(").append(countLetters(activeChar, "inbox"))
				.append(")&nbsp;").append("<a action=\"bypass _maillist_0_1_0_sentbox\">[Sent Box]</a>(")
				.append(countLetters(activeChar, "sentbox")).append(")&nbsp;")
				.append("<a action=\"bypass _maillist_0_1_0_archive\">[Mail Archive]</a>(")
				.append(countLetters(activeChar, "archive")).append(")&nbsp;")
				.append("<a action=\"bypass _maillist_0_1_0_temp_archive\">[Temporary Mail Archive]</a>(")
				.append(countLetters(activeChar, "temparchive")).append(")</td>");
		html.append("<td fixWIDTH=5></td>");
		html.append("</tr>");
		html.append("<tr><td height=10></td></tr>");
		html.append("</table>");
		if (countLetters(activeChar, "sentbox") == 0)
			html.append("<br><center>Your sent box is empty.</center>");
		else
		{
			html.append("<br>");
			html.append("<table border=0 cellspacing=0 cellpadding=2 bgcolor=808080 width=770>");
			html.append("<tr>");
			html.append("<td FIXWIDTH=5></td>");
			html.append("<td FIXWIDTH=150 align=center>Author</td>");
			html.append("<td FIXWIDTH=460 align=left>Title</td>");
			html.append("<td FIXWIDTH=150 align=center>Authoring Date</td>");
			html.append("<td FIXWIDTH=5></td>");
			html.append("</tr></table>");
			for (UpdateMail letter : getMail(activeChar))
			{
				if (activeChar.getObjectId().equals(letter.charId) && letter.location.equals("sentbox"))
				{
					if (index < minIndex)
					{
						index++;
						continue;
					}
					if (index > maxIndex)
						break;
					String tempName = getCharName(letter.senderId);
					html.append("<table border=0 cellspacing=0 cellpadding=2 width=770>");
					html.append("<tr>");
					html.append("<td FIXWIDTH=5></td>");
					html.append("<td FIXWIDTH=150 align=center>").append(abbreviate(tempName, 6)).append("</td>");
					html.append("<td FIXWIDTH=460 align=left><a action=\"bypass _maillist_0_1_0_view ")
							.append(letter.letterId).append("\">").append(abbreviate(letter.subject, 51))
							.append("</a></td>");
					html.append("<td FIXWIDTH=150 align=center>").append(letter.sentDateFormated).append("</td>");
					html.append("<td FIXWIDTH=5></td>");
					html.append("</tr></table>");
					html.append("<img src=\"L2UI.SquareBlank\" width=\"770\" height=\"3\">");
					html.append("<img src=\"L2UI.SquareGrey\" width=\"770\" height=\"1\">");
					index++;
				}
			}
		}
		html.append("<table width=770><tr>");
		html.append("<td align=right><button value=\"Write\" action=\"bypass _maillist_0_1_0_write\" width=60 height=30 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("</tr></table>");
		html.append("<center><table width=770><tr>");
		html.append("<td align=right><button action=\"bypass _maillist_0_1_0_sentbox ")
				.append(page == 1 ? page : page - 1)
				.append("\" width=16 height=16 back=\"L2UI_ct1.button_df_left_down\" fore=\"L2UI_ct1.button_df_left\"></td>");
		for (int i = 1; i <= 7; i++)
			html.append("<td align=center fixedwidth=10><a action=\"bypass _maillist_0_1_0_sentbox ").append(i)
					.append("\">").append(i).append("</a></td>");
		html.append("<td align=left><button action=\"bypass _maillist_0_1_0_sentbox ")
				.append(page + 1)
				.append("\" width=16 height=16 back=\"L2UI_ct1.button_df_right_down\" fore=\"L2UI_ct1.button_df_right\"></td>");
		html.append("</tr></table>");
		html.append("<table><tr>");
		html.append("<td align=right><combobox width=65 var=combo list=\"Writer\"></td>");
		html.append("<td align=center><edit var=\"keyword\" width=130 height=11 length=\"16\"></td>");
		html.append("<td align=left><button value=\"Search\" action=\"bypass _maillist_0_1_0_search $combo $keyword\" width=60 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("</tr></table></center>");
		html.append("</body></html>");
		separateAndSend(html, activeChar);
	}
	
	private void showMailArchive(L2Player activeChar, int page)
	{
		int index = 0, minIndex = 0, maxIndex = 0;
		maxIndex = (page == 1 ? page * 14 : (page * 15) - 1);
		minIndex = maxIndex - 14;
		
		final StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("<body><br><br>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=810><tr><td width=10></td><td width=800 height=30 align=left>");
		html.append("<a action=\"bypass _bbshome\">HOME</a>&nbsp;&gt;&nbsp;<a action=\"bypass _maillist_0_1_0_archive\">Mail Archive</a>");
		html.append("</td></tr>");
		html.append("</table>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=810 bgcolor=808080>");
		html.append("<tr><td height=10></td></tr>");
		html.append("<tr>");
		html.append("<td fixWIDTH=5></td>");
		html.append("<td fixWIDTH=760>");
		html.append("<a action=\"bypass _maillist_0_1_0_\">[Inbox]</a>(").append(countLetters(activeChar, "inbox"))
				.append(")&nbsp;").append("<a action=\"bypass _maillist_0_1_0_sentbox\">[Sent Box]</a>(")
				.append(countLetters(activeChar, "sentbox")).append(")&nbsp;")
				.append("<a action=\"bypass _maillist_0_1_0_archive\">[Mail Archive]</a>(")
				.append(countLetters(activeChar, "archive")).append(")&nbsp;")
				.append("<a action=\"bypass _maillist_0_1_0_temp_archive\">[Temporary Mail Archive]</a>(")
				.append(countLetters(activeChar, "temparchive")).append(")</td>");
		html.append("<td fixWIDTH=5></td>");
		html.append("</tr>");
		html.append("<tr><td height=10></td></tr>");
		html.append("</table>");
		if (countLetters(activeChar, "archive") == 0)
			html.append("<br><center>Your mail archive is empty.</center>");
		else
		{
			html.append("<br>");
			html.append("<table border=0 cellspacing=0 cellpadding=2 bgcolor=808080 width=770>");
			html.append("<tr>");
			html.append("<td FIXWIDTH=5></td>");
			html.append("<td FIXWIDTH=150 align=center>Author</td>");
			html.append("<td FIXWIDTH=460 align=left>Title</td>");
			html.append("<td FIXWIDTH=150 align=center>Authoring Date</td>");
			html.append("<td FIXWIDTH=5></td>");
			html.append("</tr></table>");
			for (UpdateMail letter : getMail(activeChar))
			{
				if (activeChar.getObjectId().equals(letter.charId) && letter.location.equals("archive"))
				{
					if (index < minIndex)
					{
						index++;
						continue;
					}
					if (index > maxIndex)
						break;
					String tempName = getCharName(letter.senderId);
					html.append("<table border=0 cellspacing=0 cellpadding=2 width=770>");
					html.append("<tr>");
					html.append("<td FIXWIDTH=5></td>");
					html.append("<td FIXWIDTH=150 align=center>").append(abbreviate(tempName, 6)).append("</td>");
					html.append("<td FIXWIDTH=460 align=left><a action=\"bypass _maillist_0_1_0_view ")
							.append(letter.letterId).append("\">").append(abbreviate(letter.subject, 51))
							.append("</a></td>");
					html.append("<td FIXWIDTH=150 align=center>").append(letter.sentDateFormated).append("</td>");
					html.append("<td FIXWIDTH=5></td>");
					html.append("</tr></table>");
					html.append("<img src=\"L2UI.SquareBlank\" width=\"770\" height=\"3\">");
					html.append("<img src=\"L2UI.SquareGrey\" width=\"770\" height=\"1\">");
					index++;
				}
			}
		}
		html.append("<table width=770><tr>");
		html.append("<td align=right><button value=\"Write\" action=\"bypass _maillist_0_1_0_write\" width=60 height=30 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("</tr></table>");
		html.append("<center><table width=770><tr>");
		html.append("<td align=right><button action=\"bypass _maillist_0_1_0_archive ")
				.append(page == 1 ? page : page - 1)
				.append("\" width=16 height=16 back=\"L2UI_ct1.button_df_left_down\" fore=\"L2UI_ct1.button_df_left\"></td>");
		for (int i = 1; i <= 7; i++)
			html.append("<td align=center fixedwidth=10><a action=\"bypass _maillist_0_1_0_archive ").append(i)
					.append("\">").append(i).append("</a></td>");
		html.append("<td align=left><button action=\"bypass _maillist_0_1_0_archive ")
				.append(page + 1)
				.append("\" width=16 height=16 back=\"L2UI_ct1.button_df_right_down\" fore=\"L2UI_ct1.button_df_right\"></td>");
		html.append("</tr></table>");
		html.append("<table><tr>");
		html.append("<td align=right><combobox width=65 var=combo list=\"Writer\"></td>");
		html.append("<td align=center><edit var=\"keyword\" width=130 height=11 length=\"16\"></td>");
		html.append("<td align=left><button value=\"Search\" action=\"bypass _maillist_0_1_0_search $combo $keyword\" width=60 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("</tr></table></center>");
		html.append("</body></html>");
		separateAndSend(html, activeChar);
	}
	
	private void showTempMailArchive(L2Player activeChar, int page)
	{
		int index = 0, minIndex = 0, maxIndex = 0;
		maxIndex = (page == 1 ? page * 14 : (page * 15) - 1);
		minIndex = maxIndex - 14;
		
		final StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("<body><br><br>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=810><tr><td width=10></td><td width=800 height=30 align=left>");
		html.append("<a action=\"bypass _bbshome\">HOME</a>&nbsp;&gt;&nbsp;<a action=\"bypass _maillist_0_1_0_temp_archive\">Temporary Mail Archive</a>");
		html.append("</td></tr>");
		html.append("</table>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=810 bgcolor=808080>");
		html.append("<tr><td height=10></td></tr>");
		html.append("<tr>");
		html.append("<td fixWIDTH=5></td>");
		html.append("<td fixWIDTH=760>");
		html.append("<a action=\"bypass _maillist_0_1_0_\">[Inbox]</a>(").append(countLetters(activeChar, "inbox"))
				.append(")&nbsp;").append("<a action=\"bypass _maillist_0_1_0_sentbox\">[Sent Box]</a>(")
				.append(countLetters(activeChar, "sentbox")).append(")&nbsp;")
				.append("<a action=\"bypass _maillist_0_1_0_archive\">[Mail Archive]</a>(")
				.append(countLetters(activeChar, "archive")).append(")&nbsp;")
				.append("<a action=\"bypass _maillist_0_1_0_temp_archive\">[Temporary Mail Archive]</a>(")
				.append(countLetters(activeChar, "temparchive")).append(")</td>");
		html.append("<td fixWIDTH=5></td>");
		html.append("</tr>");
		html.append("<tr><td height=10></td></tr>");
		html.append("</table>");
		if (countLetters(activeChar, "temparchive") == 0)
			html.append("<br><center>Your temporary mail archive is empty.</center>");
		else
		{
			html.append("<br>");
			html.append("<table border=0 cellspacing=0 cellpadding=2 bgcolor=808080 width=770>");
			html.append("<tr>");
			html.append("<td FIXWIDTH=5></td>");
			html.append("<td FIXWIDTH=150 align=center>Author</td>");
			html.append("<td FIXWIDTH=460 align=left>Title</td>");
			html.append("<td FIXWIDTH=150 align=center>Authoring Date</td>");
			html.append("<td FIXWIDTH=5></td>");
			html.append("</tr></table>");
			for (UpdateMail letter : getMail(activeChar))
			{
				if (activeChar.getObjectId().equals(letter.charId) && letter.location.equals("temparchive"))
				{
					if (index < minIndex)
					{
						index++;
						continue;
					}
					if (index > maxIndex)
						break;
					String tempName = getCharName(letter.senderId);
					html.append("<table border=0 cellspacing=0 cellpadding=2 width=770>");
					html.append("<tr>");
					html.append("<td FIXWIDTH=5></td>");
					html.append("<td FIXWIDTH=150 align=center>").append(abbreviate(tempName, 6)).append("</td>");
					html.append("<td FIXWIDTH=460 align=left><a action=\"bypass _maillist_0_1_0_view ")
							.append(letter.letterId).append("\">").append(abbreviate(letter.subject, 51))
							.append("</a></td>");
					html.append("<td FIXWIDTH=150 align=center>").append(letter.sentDateFormated).append("</td>");
					html.append("<td FIXWIDTH=5></td>");
					html.append("</tr></table>");
					html.append("<img src=\"L2UI.SquareBlank\" width=\"770\" height=\"3\">");
					html.append("<img src=\"L2UI.SquareGrey\" width=\"770\" height=\"1\">");
					index++;
				}
			}
		}
		html.append("<table width=770><tr>");
		html.append("<td align=right><button value=\"Write\" action=\"bypass _maillist_0_1_0_write\" width=60 height=30 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("</tr></table>");
		html.append("<center><table width=770><tr>");
		html.append("<td align=right><button action=\"bypass _maillist_0_1_0_temp_archive ")
				.append(page == 1 ? page : page - 1)
				.append("\" width=16 height=16 back=\"L2UI_ct1.button_df_left_down\" fore=\"L2UI_ct1.button_df_left\"></td>");
		for (int i = 1; i <= 7; i++)
			html.append("<td align=center fixedwidth=10><a action=\"bypass _maillist_0_1_0_temp_archive ").append(i)
					.append("\">").append(i).append("</a></td>");
		html.append("<td align=left><button action=\"bypass _maillist_0_1_0_temp_archive ")
				.append(page + 1)
				.append("\" width=16 height=16 back=\"L2UI_ct1.button_df_right_down\" fore=\"L2UI_ct1.button_df_right\"></td>");
		html.append("</tr></table>");
		html.append("<table><tr>");
		html.append("<td align=right><combobox width=65 var=combo list=\"Writer\"></td>");
		html.append("<td align=center><edit var=\"keyword\" width=130 height=11 length=\"16\"></td>");
		html.append("<td align=left><button value=\"Search\" action=\"bypass _maillist_0_1_0_search $combo $keyword\" width=60 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("</tr></table></center>");
		html.append("</body></html>");
		separateAndSend(html, activeChar);
	}
	
	private void showWriteView(L2Player activeChar)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("<body><br><br>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=770><tr><td width=10></td><td height=30 width=760 align=left>");
		html.append("<a action=\"bypass _bbshome\">HOME</a>&nbsp;&gt;&nbsp;<a action=\"bypass _maillist_0_1_0_\">Inbox</a>");
		html.append("</td></tr>");
		html.append("</table>");
		html.append("<img src=\"L2UI.SquareBlank\" width=\"770\" height=\"3\">");
		html.append("<img src=\"L2UI.SquareGrey\" width=\"770\" height=\"1\">");
		html.append("<table width=770><tr>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("<td FIXWIDTH=60 align=center>Recipient</td>");
		html.append("<td FIXWIDTH=690 align=left><edit var=\"Recipients\" width=700 height=11 length=\"128\"></td>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("<td FIXWIDTH=60 align=center>Title</td>");
		html.append("<td FIXWIDTH=690 align=left><edit var=\"Title\" width=700 height=11 length=\"128\"></td>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("<td FIXWIDTH=60 align=center>Body</td>");
		html.append("<td FIXWIDTH=690 align=left><MultiEdit var=\"Message\" width=700 height=200></td>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("</tr></table>");
		html.append("<table width=770><tr>");
		html.append("<td align=left></td>");
		html.append("<td FIXWIDTH=60></td>");
		html.append("<td align=left><button value=\"Send\" action=\"Write Mail Send _ Recipients Title Message\" width=70 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td align=left><button value=\"Cancel\" action=\"bypass _maillist_0_1_0_\" width=70 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td align=left><button value=\"Delete\" action=\"bypass _maillist_0_1_0_delete 0\" width=70 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td FIXWIDTH=400></td>");
		html.append("</tr></table>");
		html.append("</body></html>");
		separateAndSend(html, activeChar);
	}
	
	private void showWriteView(L2Player activeChar, String parcipientName, UpdateMail letter)
	{
		final StringBuilder html = new StringBuilder();
		html.append("<html>");
		html.append("<body><br><br>");
		html.append("<table border=0 cellspacing=0 cellpadding=0 width=770><tr><td width=10></td><td height=30 width=760 align=left>");
		html.append("<a action=\"bypass _bbshome\">HOME</a>&nbsp;&gt;&nbsp;<a action=\"bypass _maillist_0_1_0_\">Inbox</a>");
		html.append("</td></tr>");
		html.append("</table>");
		html.append("<img src=\"L2UI.SquareBlank\" width=\"770\" height=\"3\">");
		html.append("<img src=\"L2UI.SquareGrey\" width=\"770\" height=\"1\">");
		html.append("<table width=770><tr>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("<td FIXWIDTH=60 align=center>Recipient</td>");
		html.append("<td FIXWIDTH=690 align=left><combobox width=684 var=\"Recipient\" list=\"").append(parcipientName)
				.append("\"></td>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("<td FIXWIDTH=60 align=center>Title</td>");
		html.append("<td FIXWIDTH=690 align=left><edit var=\"Title\" width=684 height=11 length=\"128\"></td>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("</tr><tr>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("<td FIXWIDTH=60 align=center valign=top>Body</td>");
		html.append("<td FIXWIDTH=690 align=left><multiedit var=\"Message\" width=684 height=300 length=\"2000\"></td>");
		html.append("<td FIXWIDTH=5></td>");
		html.append("</tr></table>");
		html.append("<table width=770><tr>");
		html.append("<td align=left></td>");
		html.append("<td FIXWIDTH=60></td>");
		html.append("<td align=left><button value=\"Send\" action=\"Write Mail Send _ Recipient Title Message\" width=70 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td align=left><button value=\"Cancel\" action=\"bypass _maillist_0_1_0_\" width=70 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td align=left><button value=\"Delete\" action=\"bypass _maillist_0_1_0_delete ")
				.append(letter.letterId)
				.append("\" width=70 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		html.append("<td FIXWIDTH=400></td>");
		html.append("</tr></table>");
		html.append("</body></html>");
		
		send1001(html.toString(), activeChar);
		send1002(activeChar, " ", "Re: " + letter.subject, "0");
	}
	
	private void sendLetter(String recipients, String subject, String message, L2Player activeChar)
	{
		// The copies in the recipient mailboxes and the copy in the sender's sentbox belong together.
		WorldTransaction.run("send community board mail", () -> sendLetterInTransaction(recipients, subject, message, activeChar));
	}
	
	private void sendLetterInTransaction(String recipients, String subject, String message, L2Player activeChar)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			String[] recipAr = recipients.split(";");
			message = message.replaceAll("\n", "<br1>");
			boolean sent = false;
			long date = System.currentTimeMillis();
			int countRecips = 0;
			int countTodaysLetters = 0;
			
			if (subject.isEmpty())
				subject = "(no subject)";
			
			// The columns have a length limit: subject 12, recipient names 200, message 3000
			subject = StringUtils.left(subject, 12);
			message = StringUtils.left(message, 3000);
			final String storedRecipients = StringUtils.left(recipients, 200);
			
			for (UpdateMail letter : getMail(activeChar))
				if (date < letter.sentDate + Long.valueOf("86400000") && letter.location.equals("sentbox"))
					countTodaysLetters++;
			
			if (countTodaysLetters >= 10)
			{
				activeChar.sendPacket(SystemMessageId.NO_MORE_MESSAGES_TODAY);
				return;
			}
			
			for (String recipient : recipAr)
			{
				int recipId = getCharId(recipient.trim());
				if (recipId == 0)
					activeChar.sendMessage("Could not find " + recipient.trim() + ", Therefor will not get mail.");
				else if (isGM(recipId) && !activeChar.isGM())
					activeChar.sendPacket(new SystemMessage(SystemMessageId.CANNOT_MAIL_GM_C1).add("a GM"));
				else if (isBlocked(activeChar, recipId) && !activeChar.isGM())
				{
					SystemMessage sm = new SystemMessage(SystemMessageId.C1_BLOCKED_YOU_CANNOT_MAIL);
					for (L2Player player : L2World.getInstance().getAllPlayers())
						if (player.getObjectId().equals(recipId) && player.isOnline() == 1)
							sm.addPcName(player);
					activeChar.sendPacket(sm);
				}
				else if (isRecipInboxFull(recipId) && !activeChar.isGM())
				{
					activeChar.sendMessage(recipient.trim() + "'s inbox is full.");
					activeChar.sendPacket(SystemMessageId.MESSAGE_NOT_SENT);
					for (L2Player player : L2World.getInstance().getAllPlayers())
						if (player.getObjectId().equals(recipId) && player.isOnline() == 1)
							player.sendPacket(SystemMessageId.MAILBOX_FULL);
				}
				else if (countRecips < 5 && !activeChar.isGM() || activeChar.isGM())
				{
					PreparedStatement statement =
							con.prepareStatement("INSERT INTO player_mail (player_id, sender_player_id, folder, recipient_names, subject, message, sent_at, delete_at, is_unread) VALUES (?,?,?,?,?,?,?,?,?)");
					statement.setInt(1, recipId);
					statement.setInt(2, activeChar.getObjectId());
					statement.setString(3, "inbox");
					statement.setString(4, storedRecipients);
					statement.setString(5, subject);
					statement.setString(6, message);
					statement.setTimestamp(7, new Timestamp(date));
					statement.setTimestamp(8, new Timestamp(date + Long.valueOf("7948804000")));
					statement.setBoolean(9, true);
					statement.execute();
					statement.close();
					sent = true;
					countRecips++;
					
					for (L2Player player : L2World.getInstance().getAllPlayers())
						if (player.getObjectId().equals(recipId) && player.isOnline() == 1)
						{
							player.sendPacket(SystemMessageId.NEW_MAIL);
							player.sendPacket(ExMailArrived.STATIC_PACKET);
						}
					
				}
			}
			// Create a copy into activeChar's sent box
			PreparedStatement statement =
					con.prepareStatement("INSERT INTO player_mail (player_id, sender_player_id, folder, recipient_names, subject, message, sent_at, delete_at, is_unread) VALUES (?,?,?,?,?,?,?,?,?)");
			statement.setInt(1, activeChar.getObjectId());
			statement.setInt(2, activeChar.getObjectId());
			statement.setString(3, "sentbox");
			statement.setString(4, storedRecipients);
			statement.setString(5, subject);
			statement.setString(6, message);
			statement.setTimestamp(7, new Timestamp(date));
			statement.setTimestamp(8, new Timestamp(date + Long.valueOf("7948804000")));
			statement.setBoolean(9, false);
			statement.execute();
			statement.close();
			
			if (countRecips > 5 && !activeChar.isGM())
				activeChar.sendPacket(SystemMessageId.ONLY_FIVE_RECIPIENTS);
			
			if (sent)
				activeChar.sendPacket(SystemMessageId.SENT_MAIL);
		}
		catch (Exception e)
		{
			_log.warn("couldnt send letter for " + activeChar.getName(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private int countLetters(L2Player activeChar, String location)
	{
		int count = 0;
		for (UpdateMail letter : getMail(activeChar))
			if (activeChar.getObjectId().equals(letter.charId) && letter.location.equals(location))
				count++;
		return count;
	}
	
	private boolean isBlocked(L2Player activeChar, int recipId)
	{
		for (L2Player player : L2World.getInstance().getAllPlayers())
			if (player.getObjectId().equals(recipId))
				if (BlockList.isBlocked(player, activeChar))
					return true;
		return false;
	}
	
	public void deleteLetter(int letterId)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("DELETE FROM player_mail WHERE id = ?");
			statement.setInt(1, letterId);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("couldnt delete letter " + letterId, e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private void setLetterToRead(int letterId)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("UPDATE player_mail SET is_unread = ? WHERE id = ?");
			statement.setBoolean(1, false);
			statement.setInt(2, letterId);
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("couldnt set unread to false for " + letterId, e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private String getCharName(int charId)
	{
		if (charId == 100100)
			return "Auction";
		
		String name = CharNameTable.getInstance().getByObjectId(charId);
		
		return name == null ? "No Name" : name;
	}
	
	private int getCharId(String charName)
	{
		Integer objId = CharNameTable.getInstance().getByName(charName);
		
		return objId == null ? 0 : objId;
	}
	
	private boolean isGM(int charId)
	{
		if (charId == 100100)
			return false;
		
		boolean isGM = false;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement("SELECT access_level FROM player WHERE id = ?");
			statement.setInt(1, charId);
			ResultSet result = statement.executeQuery();
			result.next();
			isGM = result.getInt(1) > 0;
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return isGM;
	}
	
	private boolean isRecipInboxFull(int charId)
	{
		boolean isFull = false;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT COUNT(*) FROM player_mail WHERE player_id = ? AND folder = ?");
			statement.setInt(1, charId);
			statement.setString(2, "inbox");
			ResultSet result = statement.executeQuery();
			result.next();
			isFull = result.getLong(1) >= 100;
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return isFull;
	}
	
	/** FIXME is there a better way? */
	public boolean hasUnreadMail(L2Player activeChar)
	{
		boolean hasUnreadMail = false;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT COUNT(*) FROM player_mail WHERE player_id = ? AND folder = ? AND is_unread = ?");
			statement.setInt(1, activeChar.getObjectId());
			statement.setString(2, "inbox");
			statement.setBoolean(3, true);
			ResultSet result = statement.executeQuery();
			result.next();
			hasUnreadMail = result.getLong(1) > 0;
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return hasUnreadMail;
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.communitybbs.Manager.BaseBBSManager#parsewrite(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, com.l2jfree.gameserver.gameobjects.instance.L2Player)
	 */
	@Override
	public void parsewrite(String ar1, String ar2, String ar3, String ar4, String ar5, L2Player activeChar)
	{
		if (ar1.equals("Send"))
		{
			sendLetter(ar3, ar4, ar5, activeChar);
			showSentbox(activeChar, 0);
		}
	}
}
