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
package com.l2jfree.loginserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.config.Deployment;
import com.l2jfree.config.L2Properties;

/**
 * The configuration of the login module.<br>
 * It has static fields initialized from {@code config/loginserver.properties}. The database address, the
 * database role, and the bind address can be overridden by the deployment, see {@link Deployment}.
 * 
 * @author mkizub
 */
public final class LoginConfig
{
	private static final Logger _log = LoggerFactory.getLogger(LoginConfig.class);
	
	/** Number of login tries before IP ban gets activated, default 10 */
	public static int LOGIN_TRY_BEFORE_BAN;
	/** Number of seconds the IP ban will last, default 10 minutes */
	public static int LOGIN_BLOCK_AFTER_BAN;
	
	// Access to database
	/** Driver to access to database */
	public static String DATABASE_DRIVER;
	/** Path to access to database */
	public static String DATABASE_URL;
	/** Database login */
	public static String DATABASE_LOGIN;
	/** Database password */
	public static String DATABASE_PASSWORD;
	public static int DATABASE_MAX_CONNECTIONS;
	public static int DATABASE_MIN_IDLE_CONNECTIONS;
	
	/** Configuration files */
	/** Properties file for login server configurations */
	public static final String LOGIN_CONFIGURATION_FILE = "./config/loginserver.properties";
	
	/** Client login port/host */
	public static String LOGIN_SERVER_HOSTNAME;
	public static int LOGIN_SERVER_PORT;
	
	/** Connections one address may open: warn and refuse limits over a short and a long period, see the properties file */
	public static int ACCEPT_WARN = 10;
	public static int ACCEPT_REJECT = 20;
	public static int ACCEPT_SECONDS = 10;
	public static int ACCEPT_WARN_LONG = 30;
	public static int ACCEPT_REJECT_LONG = 60;
	public static int ACCEPT_SECONDS_LONG = 60;
	
	/** Show licence or not just after login (if false, will directly go to the Server List */
	public static boolean SHOW_LICENCE;
	
	public static boolean AUTO_CREATE_ACCOUNTS;
	public static int GM_MIN;
	
	public static boolean SECURITY_CARD_LOGIN;
	public static String SECURITY_CARD_ID;
	
	public static void load()
	{
		_log.info("loading login config");
		try
		{
			L2Properties serverSettings = new L2Properties(LOGIN_CONFIGURATION_FILE);
			
			LOGIN_SERVER_HOSTNAME = Deployment.value("BIND", serverSettings.getProperty("LoginServerHostname", "0.0.0.0"));
			LOGIN_SERVER_PORT = Integer.parseInt(serverSettings.getProperty("LoginServerPort", "2106"));
			ACCEPT_WARN = Integer.parseInt(serverSettings.getProperty("AcceptWarn", "10"));
			ACCEPT_REJECT = Integer.parseInt(serverSettings.getProperty("AcceptReject", "20"));
			ACCEPT_SECONDS = Integer.parseInt(serverSettings.getProperty("AcceptSeconds", "10"));
			ACCEPT_WARN_LONG = Integer.parseInt(serverSettings.getProperty("AcceptWarnLong", "30"));
			ACCEPT_REJECT_LONG = Integer.parseInt(serverSettings.getProperty("AcceptRejectLong", "60"));
			ACCEPT_SECONDS_LONG = Integer.parseInt(serverSettings.getProperty("AcceptSecondsLong", "60"));
			
			LOGIN_TRY_BEFORE_BAN = Integer.parseInt(serverSettings.getProperty("LoginTryBeforeBan", "10"));
			LOGIN_BLOCK_AFTER_BAN = Integer.parseInt(serverSettings.getProperty("LoginBlockAfterBan", "600"));
			GM_MIN = Integer.parseInt(serverSettings.getProperty("GMMinLevel", "100"));
			
			DATABASE_DRIVER = serverSettings.getProperty("Driver", "org.postgresql.Driver");
			DATABASE_URL = Deployment.value("DB_URL", serverSettings.getProperty("URL", "jdbc:postgresql://localhost/l2jfree"));
			DATABASE_LOGIN = Deployment.value("LOGIN_DB_USER", serverSettings.getProperty("Login", "l2jfree_login"));
			DATABASE_PASSWORD = Deployment.secret("LOGIN_DB_PASSWORD", serverSettings.getProperty("Password", ""));
			DATABASE_MAX_CONNECTIONS = Integer.parseInt(serverSettings.getProperty("MaximumDbConnections", "20"));
			DATABASE_MIN_IDLE_CONNECTIONS = Integer.parseInt(serverSettings.getProperty("MinimumDbIdleConnections", "1"));
			
			SHOW_LICENCE = Boolean.parseBoolean(serverSettings.getProperty("ShowLicence", "true"));
			
			AUTO_CREATE_ACCOUNTS = Boolean.parseBoolean(serverSettings.getProperty("AutoCreateAccounts", "True"));
			
			SECURITY_CARD_LOGIN = Boolean.parseBoolean(serverSettings.getProperty("UseSecurityCardToLogin", "False"));
			SECURITY_CARD_ID = serverSettings.getProperty("SecurityCardID", "l2jfree");
		}
		catch (IllegalStateException e)
		{
			// a deployment secret that cannot be read: the message names the variable
			throw e;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new Error("Failed to Load " + LOGIN_CONFIGURATION_FILE + " File.");
		}
	}
	
	// it has no instances
	private LoginConfig()
	{
	}
}
