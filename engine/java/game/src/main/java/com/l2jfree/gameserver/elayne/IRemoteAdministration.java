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
package com.l2jfree.gameserver.elayne;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

/**
 * This interface defines methods that are known by the ELAYNE administration tool and the L2J Game Server. This interface is the way Elayne and the
 * Game Server communicate with each other.
 * 
 * @author polbat02
 */
public interface IRemoteAdministration extends Remote
{
	/**
	 * Get the number of players online at a certain time.
	 * 
	 * @return the amount of players in the server.
	 * @throws RemoteException
	 */
	public int getOnlineUsersCount(String rmiPassword) throws RemoteException;
	
	/**
	 * Perform an announcement in game that is broadcasted to every online player.
	 * 
	 * @param announcement --> The announcement that is broadcasted inside the server.
	 * @throws RemoteException
	 */
	public void announceToAll(String rmiPassword, String announcement) throws RemoteException;
	
	/**
	 * Attempts to send a private message to an online Player.
	 * 
	 * @param player --> The player that will receive the message.
	 * @param message --> The message to broadcast to the player.
	 * @return: 1 if message was sent and the player receive it. 2 if player is not online or is null. 3 other errors.
	 * @throws RemoteException
	 */
	public int sendPrivateMessage(String rmiPassword, String player, String message) throws RemoteException;
	
	/**
	 * Attempts to send a message to Any GMs online at one particular moment.
	 * 
	 * @param message
	 * @return number of GMs this message got broadcasted to
	 * @throws RemoteException
	 */
	public int sendMessageToGms(String rmiPassword, String message) throws RemoteException;
	
	/**
	 * This method will try to kick a player from the Server.
	 * 
	 * @param playerName --> The player that needs to be kicked from the server.
	 * @return: 1 if the player was found in game and was successfully kicked. 2 if the player was not found in game. 3 other errors.
	 * @throws RemoteException
	 */
	public int kickPlayerFromServer(String rmiPassword, String playerName) throws RemoteException;
	
	/**
	 * Attempt a server restart once the given seconds are over.
	 * 
	 * @param secondsUntilRestart
	 * @throws RemoteException
	 */
	public void scheduleServerRestart(String rmiPassword, int secondsUntilRestart) throws RemoteException;
	
	/**
	 * Attempt a server shut down once the given seconds are over.
	 * 
	 * @param secondsUntilShutDown
	 * @throws RemoteException
	 */
	public void scheduleServerShutDown(String rmiPassword, int secondsUntilShutDown) throws RemoteException;
	
	/**
	 * Attempt to abort a server restart/shut down procedure.
	 * 
	 * @throws RemoteException
	 */
	public void abortServerRestart(String rmiPassword) throws RemoteException;
	
	/**
	 * Reloads something in-game.
	 * 
	 * @param reloadProcedure --> Allowed procedures: 1(MULTISELL), 2(SKILLS), 3(NPC), 4(HTML), 5(ITEMS), 6(INSTANCE MANAGERS), 7(ZONES),
	 *                8(TELEPORTS), 9(SPAWNS).
	 * @throws RemoteException
	 */
	public void reload(String rmiPassword, int reloadProcedure) throws RemoteException;
	
	/**
	 * This method returns a map containing the information of all the players online at one particular moment.<br>
	 * For each key (String containing the name of a player), this method returns an Array of Strings following the object model of the
	 * L2CreatureBriefEntry class.
	 * 
	 * @return a map containing brief information of all the online players at a given moment.
	 * @throws RemoteException
	 */
	public Map<String, IRemotePlayer> getOnlinePlayersDetails(String rmiPassword) throws RemoteException;
	
	/**
	 * Returns information about a player in the "live" server.
	 * 
	 * @param playerName
	 * @return some basic Player Information for a player.
	 * @throws RemoteException
	 */
	public IRemotePlayer getPlayerInformation(String rmiPassword, String playerName) throws RemoteException;
}
