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
package com.l2jfree.loginserver.network.packets.server;

import com.l2jfree.loginserver.network.L2Client;
import com.l2jfree.loginserver.network.packets.L2ServerPacket;

/** Format: (c) c */
public final class LoginFail extends L2ServerPacket
{
	/** There is a system error. Please log in again later. */
	public static final int REASON_THERE_IS_A_SYSTEM_ERROR = 1;
	/** The password you have entered is incorrect. Confirm your account information and log in again later. */
	public static final int REASON_PASSWORD_INCORRECT = 2; // also 3
	/** Access failed. Please try again later. . */
	public static final int REASON_ACCESS_FAILED_TRY_AGAIN = 4; // also 6,8,9,10,11,13,14
	/** Your account information is incorrect. For more details, please contact our customer service center at http://support.plaync.com. */
	public static final int REASON_ACCOUNT_INFO_INCORRECT = 5;
	/** Account is already in use. Unable to log in. */
	public static final int REASON_ALREADY_IN_USE = 7;
	/**
	 * Lineage II game services may be used by individuals 15 years of age or older except for PvP servers,
	 * which may only be used by adults 18 years of age and older. (Korea Only)
	 */
	public static final int REASON_AGE_LIMITATION = 12;
	/** Due to high server traffic, your login attempt has failed. Please try again soon. */
	public static final int REASON_TOO_HIGH_TRAFFIC = 15;
	/** Currently undergoing game server maintenance. Please log in again later. */
	public static final int REASON_MAINTENANCE_UNDERGOING = 16;
	/** Please login after changing your temporary password. */
	public static final int REASON_CHANGE_TEMP_PASSWORD = 17;
	/**
	 * Your game time has expired. To continue playing, please purchase Lineage II either
	 * directly from the PlayNC Store or from any leading games retailer.
	 */
	public static final int REASON_GAME_TIME_EXPIRED = 18;
	/** There is no time left on this account. */
	public static final int REASON_NO_TIME_LEFT = 19;
	/** System error. */
	public static final int REASON_SYSTEM_ERROR = 20;
	/** Access failed. */
	public static final int REASON_ACCESS_FAILED = 21;
	/** Game connection attempted through a restricted IP. */
	public static final int REASON_IP_RESTRICTED = 22;
	public static final int REASON_IGNORE = 23; // shows the copyright as all unused ones
	/** This week's usage time has finished. */
	public static final int REASON_WEEK_TIME_FINISHED = 30;
	// when using this, you mustn't terminate the connection!
	public static final int REASON_INVALID_SECURITY_CARD_NO = 31;
	/** Users who have not verified their age may not log in between the hours of 10:00 p.m. and 6:00 a.m. */
	public static final int REASON_TIME_LIMITATION_AGE_NOT_VERIFIED = 32;
	/** This server cannot be accessed by the coupon you are using. */
	public static final int REASON_INCORRECT_COUPON_FOR_SERVER = 33;
	/** You are using a computer that does not allow you to log in with two accounts at the same time. */
	public static final int REASON_USING_A_COMPUTER_NO_DUAL_BOX = 35;
	/**
	 * Your account is currently suspended because you have not logged into the game for some time.
	 * You may reactivate your account by visiting the PlayNC website (http://www.plaync.com/us/support/).
	 */
	public static final int REASON_SUSPENDED_INACTIVITY = 36;
	/**
	 * You must accept the User Agreement before this account can access Lineage II.\n
	 *  Please try again after accepting the agreement on the PlayNC website (http://www.plaync.co.kr).
	 */
	public static final int REASON_MUST_ACCEPT_AGREEMENT = 37;
	/**
	 * A guardian's consent is required before this account can be used to play Lineage II.\n
	 * Please try again after this consent is provided.
	 */
	public static final int REASON_GUARDIANS_CONSENT_NEEDED = 38;
	/**
	 * This account has declined the User Agreement or is pending a withdrawl request. \n
	 * Please try again after cancelling this request.
	 */
	public static final int REASON_PENDING_WITHDRAWL_REQUEST = 39;
	/** This account has been suspended. \nFor more information, please call the Customer's Center (Tel. 1600-0020). */
	public static final int REASON_SUSPENDED_PHONE_CC = 40;
	/**
	 * Your account can only be used after changing your password and quiz. \n
	 *  Services will be available after changing your password and quiz
	 * from the PlayNC website (http://www.plaync.co.kr).
	 */
	public static final int REASON_CHANGE_PASSWORD_AND_QUIZ = 41;
	/** You are currently logged into 10 of your accounts and can no longer access your other accounts. */
	public static final int REASON_ACCOUNT_LIMITATION = 42;
	/** The master account of your account has been restricted. */
	public static final int REASON_MASTER_ACCOUNT_RESTRICTED = 43;
	
	private final int _reason;
	
	public LoginFail(int reason, boolean accessLevel)
	{
		if (!accessLevel)
			_reason = reason;
		else
			_reason = getReasonFromBan(reason);
	}
	
	public LoginFail(int reason)
	{
		this(reason, false);
	}
	
	@Override
	protected void write(L2Client client)
	{
		writeC(0x01);
		writeC(_reason);
	}
	
	/**
	 * Returns LoginFail code from a given access level.
	 * @param accessLevel current banned account's accessLevel
	 * @return reason
	 */
	public static final int getReasonFromBan(int accessLevel)
	{
		switch (accessLevel)
		{
		// 1-10: automatic bans
		// These bans should be done straight from your website
			case -1:
				return REASON_SUSPENDED_INACTIVITY;
			case -2:
				return REASON_CHANGE_TEMP_PASSWORD;
			case -3:
				return REASON_CHANGE_PASSWORD_AND_QUIZ;
			case -4:
				return REASON_MASTER_ACCOUNT_RESTRICTED;
				// 10-20: specific bans
			case -10:
				return REASON_ACCOUNT_INFO_INCORRECT;
			case -11:
				return REASON_AGE_LIMITATION;
			case -12:
				return REASON_GUARDIANS_CONSENT_NEEDED;
			case -13:
				return REASON_GAME_TIME_EXPIRED;
			case -14:
				return REASON_NO_TIME_LEFT;
			case -15:
				return REASON_WEEK_TIME_FINISHED;
			case -16:
				return REASON_INCORRECT_COUPON_FOR_SERVER;
			case -17:
				return REASON_PENDING_WITHDRAWL_REQUEST;
			case -18:
				return REASON_ACCESS_FAILED;
			case -19:
				return REASON_IGNORE;
			default:
				return REASON_SUSPENDED_PHONE_CC;
		}
	}
}
