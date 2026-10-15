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
package com.l2jfree.gameserver;

import com.l2jfree.L2AutoInitialization;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.network.NetworkServer;
import com.l2jfree.versionning.Version;

/**
 * @author noctarius
 */
public final class CoreInfo
{
	private CoreInfo()
	{
	}
	
	private static final CoreVersion coreVersion = new CoreVersion(GameServer.class);
	private static final CoreVersion commonsVersion = new CoreVersion(L2AutoInitialization.class);
	private static final CoreVersion networkVersion = new CoreVersion(NetworkServer.class);
	
	public static void showStartupInfo()
	{
		System.out.println("");
		System.out.println("");
		System.out.println("");
		System.out.println(" ___       ___           ___");
		System.out.println("/\\_ \\    /'___`\\   __  /'___\\");
		System.out.println("\\//\\ \\  /\\_\\ /\\ \\ /\\_\\/\\ \\__/  _ __    __     __");
		System.out.println("  \\ \\ \\ \\/_/// /__\\/\\ \\ \\ ,__\\/\\`'__\\/'__`\\ /'__`\\");
		System.out.println("   \\_\\ \\_  // /_\\ \\\\ \\ \\ \\ \\_/\\ \\ \\//\\  __//\\  __/");
		System.out.println("   /\\____\\/\\______/_\\ \\ \\ \\_\\  \\ \\_\\\\ \\____\\ \\____\\");
		System.out.println("   \\/____/\\/_____//\\ \\_\\ \\/_/   \\/_/ \\/____/\\/____/");
		System.out.println("                  \\ \\____/");
		System.out.println("                   \\/___/  [starting version: " + coreVersion.getVersionNumber() + "]");
	}
	
	public static final void versionInfo(L2Player activeChar)
	{
		activeChar.sendMessage(":__.     :_____:_____:_____:_____:_____:_____:");
		activeChar.sendMessage("|    |__|___   |__.     |     __|        |     __|     __|");
		activeChar.sendMessage("|         |   ___|   |     |     __|    ) _|     __|     __|");
		activeChar.sendMessage("|_____|_____|_____|__|    |__|__|_____|_____|");
		activeChar.sendMessage("l2jfree " + getVersionInfo() + ", gpl 3 license");
	}
	
	public static String getVersionInfo()
	{
		return coreVersion.versionInfo;
	}
	
	public static String[] getFullVersionInfo()
	{
		return new String[] { "l2jfree-core :    " + coreVersion.fullVersionInfo,
				"l2j-commons  :    " + commonsVersion.fullVersionInfo,
				"l2jfree-network:  " + networkVersion.fullVersionInfo };
	}
	
	private static final class CoreVersion extends Version
	{
		public final String versionInfo;
		public final String fullVersionInfo;
		
		public CoreVersion(Class<?> c)
		{
			super(c);
			
			versionInfo = String.format("%-6s [ %4s ]", getVersionNumber(), getRevisionNumber());
			fullVersionInfo = versionInfo + " - " + getBuildJdk() + " - " + getBuildTime();
		}
	}
}
