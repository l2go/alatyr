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
package com.l2jfree.loginserver.tools.accountmanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import com.l2jfree.loginserver.LoginConfig;
import com.l2jfree.L2AutoInitialization;
import com.l2jfree.L2Registry;
import com.l2jfree.loginserver.beans.Accounts;
import com.l2jfree.loginserver.services.AccountsServices;
import com.l2jfree.loginserver.services.exception.AccountModificationException;

/**
 * Enhanced L2JFree account manager.
 * @author savormix
 */
public final class AccountManager extends L2AutoInitialization
{
	private static final String[] CMD = { "register", "change", "setlevel", "cancel", "list", "quit" };
	private static final String SPACE = " ";
	private final AccountsServices accountService;
	
	private AccountManager()
	{
		LoginConfig.load();
		L2Registry.loadRegistry();
		accountService = L2Registry.getAccountsServices();
	}
	
	private final int[] parseBirth(String s)
	{
		int[] result = { 1900, 1, 1 };
		try
		{
			StringTokenizer st = new StringTokenizer(s, "/");
			result[0] = Integer.parseInt(st.nextToken());
			result[1] = Integer.parseInt(st.nextToken());
			result[2] = Integer.parseInt(st.nextToken());
		}
		catch (Exception e)
		{
			_log.warn("Failed parsing -b!");
		}
		return result;
	}
	
	private final int parseInt(String s, String param)
	{
		try
		{
			return Integer.parseInt(s);
		}
		catch (Exception e)
		{
			_log.warn("Failed parsing " + param + "!");
		}
		return 0;
	}
	
	private final void handleRequest(String cmd)
	{
		if (cmd.startsWith(CMD[0]))
		{
			StringTokenizer st = new StringTokenizer(cmd, SPACE);
			st.nextToken();
			if (st.countTokens() < 4 || st.countTokens() > 8)
			{
				_log.warn("You must give a valid command.");
				return;
			}
			String acc = st.nextToken();
			String pass = st.nextToken();
			String level = st.nextToken();
			int[] birth = new int[] { 1900, 1, 1 };
			int gs = 0;
			while (st.hasMoreTokens())
			{
				String id = st.nextToken();
				if (id.equals("-b"))
					birth = parseBirth(st.nextToken());
				else if (id.equals("-gs"))
					gs = parseInt(st.nextToken(), id);
			}
			try
			{
				accountService.addOrUpdateAccount(acc, pass, level, birth[0], birth[1], birth[2], gs);
				_log.info("Account " + acc + " has been registered.");
			}
			catch (AccountModificationException e)
			{
				_log.error(e.getMessage(), e);
			}
		}
		else if (cmd.startsWith(CMD[1]))
		{
			StringTokenizer st = new StringTokenizer(cmd, SPACE);
			st.nextToken();
			if (st.countTokens() < 3 || st.countTokens() > 10)
			{
				_log.warn("You must give a valid command.");
				return;
			}
			Accounts acc = accountService.getAccountById(st.nextToken());
			if (acc == null)
				return;
			while (st.hasMoreTokens())
			{
				String id = st.nextToken();
				if (id.equals("-p"))
					acc.setPlainPassword(st.nextToken());
				else if (id.equals("-b"))
				{
					int[] birth = parseBirth(st.nextToken());
					acc.setBirthYear(birth[0]);
					acc.setBirthMonth(birth[1]);
					acc.setBirthDay(birth[2]);
				}
				else if (id.equals("-gs"))
					acc.setLastServerId(parseInt(st.nextToken(), id));
				else if (id.equals("-a"))
					acc.setAccessLevel(Integer.valueOf(parseInt(st.nextToken(), id)));
			}
			try
			{
				accountService.addOrUpdateAccount(acc);
				_log.info("Account " + acc.getLogin() + " has been updated.");
			}
			catch (AccountModificationException e)
			{
				_log.error(e.getMessage(), e);
			}
		}
		else if (cmd.startsWith(CMD[2]))
			try
			{
				String acc = cmd.split(SPACE)[1];
				String level = cmd.split(SPACE)[2];
				accountService.changeAccountLevel(acc, level);
				_log.info("Account " + acc + " has been leveled to " + level + ".");
			}
			catch (ArrayIndexOutOfBoundsException e)
			{
				_log.warn("You must specify an account name and the new access level.");
			}
			catch (AccountModificationException e)
			{
				_log.error(e.getMessage(), e);
			}
		else if (cmd.startsWith(CMD[3]))
			try
			{
				String acc = cmd.split(SPACE)[1];
				accountService.deleteAccount(acc);
				_log.info("Account " + acc + " has been cancelled.");
			}
			catch (ArrayIndexOutOfBoundsException e)
			{
				_log.warn("You must specify an account name to cancel registration.");
			}
			catch (AccountModificationException e)
			{
				_log.error(e.getMessage(), e);
			}
		else if (cmd.equals(CMD[4]))
		{
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
			List<Accounts> list = accountService.getAccountsInfo();
			for (Accounts a : list)
				if (a.getLastactive() != null)
					_log.info(a.getLogin() + " (lvl " + a.getAccessLevel() + ") last active "
							+ sdf.format(new Date(a.getLastactive().longValue())) + " from IP " + a.getLastIp()
							+ " in world " + a.getLastServerId());
				else
					_log.info(a.getLogin() + " (lvl " + a.getAccessLevel() + ")");
			_log.info("Total accounts: " + list.size() + ".");
		}
		else if (cmd.equals(CMD[5]))
			System.exit(0);
		else
			_log.warn("Invalid command. Try again.");
	}
	
	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException
	{
		AccountManager am = new AccountManager();
		_log.info("/=========================\\");
		_log.info("| L2JFree Account Manager |");
		_log.info("\\=========================/");
		_log.info("Available commands:");
		_log.info(CMD[0] + " [account name] [password] [access level] {-b Year/Month/Day} {-gs last world id}");
		_log.info("EXAMPLE: " + CMD[0] + " me mypass 200 -b 99/2/22 -gs 1");
		_log.info(CMD[1]
				+ " [account name] {-p new password} {-a new access level} {-b new birth date} {-gs last world id}");
		_log.info(CMD[2] + " [account name] [new level]");
		_log.info(CMD[3] + " [account name]");
		_log.info(CMD[4] + " - list all registered accounts");
		_log.info(CMD[5] + " - exit the application");
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line;
		while ((line = br.readLine()) != null)
			am.handleRequest(line.trim().toLowerCase());
		br.close();
	}
}
