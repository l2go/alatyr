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
package com.l2jfree.gameserver.gameobjects.status;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.HuntTestPlayers;

class AttackableHpCreditTest
{
	@Test
	void actualHpPathRecordsBeforeReducingHpAndKeepsQuestNotification()
	{
		L2Attackable mob = mock(L2Attackable.class);
		L2Player attacker = HuntTestPlayers.mockPlayer();
		AttackableStatus status = mock(AttackableStatus.class, CALLS_REAL_METHODS);
		when(status.getActiveChar()).thenReturn(mob);
		when(status.getCurrentHp()).thenReturn(10.0);
		doReturn(false).when(status).setCurrentHp0(anyDouble());
		status.reduceHp0(1000, attacker, false, false, true);
		org.mockito.InOrder order = org.mockito.Mockito.inOrder(mob, status);
		order.verify(mob).recordHuntDamage(attacker, 1000, 10);
		order.verify(mob).addDamage(attacker, 1000);
		order.verify(status).setCurrentHp0(0);
	}
}
