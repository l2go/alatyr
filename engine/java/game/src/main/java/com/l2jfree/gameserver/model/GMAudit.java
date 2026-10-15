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
package com.l2jfree.gameserver.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.persistence.WorldTransaction;

public class GMAudit
{
	public static void auditGMAction(L2Player gm, String type, String action, String param)
	{
		if (Config.GM_AUDIT)
		{
			String gm_name = gm.getAccountName() + " - " + gm.getName();
			String target = "null";
			
			L2Object targetChar = gm.getTarget();
			if (targetChar != null)
				target = targetChar.getObjectId() + " - " + targetChar.getName();
			
			auditGMAction(gm_name, target, type, action, param);
		}
	}
	
	public static void auditGMAction(String gm_name, String target, String type, String action, String param)
	{
		if (Config.GM_AUDIT)
		{
			// Losing the last audit rows in a crash is accepted: the transaction does not wait for the disk
			WorldTransaction.run("GM audit of " + gm_name, () -> {
				Connection con = null;
				try
				{
					con = L2DatabaseFactory.getInstance().getConnection(con);
					
					// Same as SET LOCAL synchronous_commit = off (the setting ends with the transaction)
					PreparedStatement setting =
							con.prepareStatement("SELECT set_config('synchronous_commit', 'off', true)");
					setting.execute();
					setting.close();
					
					PreparedStatement statement =
							con.prepareStatement("INSERT INTO gm_audit (gm_name, target, action_type, action, parameters) VALUES (?,?,?,?,?)");
					
					statement.setString(1, gm_name == null ? "" : gm_name);
					statement.setString(2, target == null ? "null" : target);
					statement.setString(3, type == null ? "" : type);
					statement.setString(4, action == null ? "" : action);
					statement.setString(5, param == null ? "" : param);
					
					statement.executeUpdate();
					statement.close();
				}
				catch (SQLException e)
				{
					throw new IllegalStateException("Could not store the GM audit row", e);
				}
				finally
				{
					L2DatabaseFactory.close(con);
				}
			});
		}
	}
}
