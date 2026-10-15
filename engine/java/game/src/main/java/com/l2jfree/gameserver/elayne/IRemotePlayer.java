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

/**
 * This interface defines "virtual" players from the server and implements methods that ease the organization of information from players inside the
 * server.
 * 
 * @author polbat02
 * 
 */
public interface IRemotePlayer extends Remote
{
	/**
	 * This method returns the name of a certain player.
	 * 
	 * @return
	 * @throws RemoteException
	 */
	public String getName() throws RemoteException;
	
	public String getAccount() throws RemoteException;
	
	public int getObjectId() throws RemoteException;
	
	public int getLevel() throws RemoteException;
	
	public int online() throws RemoteException;
	
	public int getAccessLevel() throws RemoteException;
	
	public int getSex() throws RemoteException;
}
