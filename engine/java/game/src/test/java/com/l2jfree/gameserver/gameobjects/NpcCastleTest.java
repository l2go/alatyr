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
package com.l2jfree.gameserver.gameobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.gameserver.model.entity.Castle;

class NpcCastleTest
{
	@Test
	@DisplayName("no castle is index 0, and a second lookup keeps that answer")
	void noCastleIsRemembered()
	{
		NpcCastle lookup = new NpcCastle();
		
		assertThat(lookup.resolve(false, null, null)).isEqualTo(NpcCastle.NONE);
		assertThat(lookup.inTown()).isFalse();
		
		Castle later = mock(Castle.class);
		when(later.getCastleId()).thenReturn(3);
		assertThat(lookup.resolve(true, later, later)).isEqualTo(NpcCastle.NONE);
		assertThat(lookup.inTown()).isFalse();
		verify(later, never()).getCastleId();
	}
	
	@Test
	@DisplayName("an NPC outside a town is not in town when the world has no castle")
	void outsideTownIsNotInTown()
	{
		NpcCastle lookup = new NpcCastle();
		
		lookup.resolve(false, null, null);
		
		assertThat(lookup.inTown()).isFalse();
	}
	
	@Test
	@DisplayName("without a castle the shop keeps the catalog price, the multisell adds nothing, and the treasury is not paid")
	void noCastleChargesNothing()
	{
		assertThat(NpcCastle.shopRate(null)).isEqualTo(1.0);
		assertThat(NpcCastle.multisellRate(null)).isEqualTo(0.0);
		
		NpcCastle.addPurchaseTax(null, 50L);
	}
	
	@Test
	@DisplayName("a castle keeps its own tax rate for both a shop and a multisell, and an owned castle is paid")
	void castleKeepsItsTax()
	{
		Castle castle = mock(Castle.class);
		when(castle.getCastleId()).thenReturn(1);
		when(castle.getTaxRate()).thenReturn(1.15);
		when(castle.getOwnerId()).thenReturn(7);
		
		NpcCastle lookup = new NpcCastle();
		assertThat(lookup.resolve(true, castle, null)).isEqualTo(1);
		assertThat(lookup.inTown()).isTrue();
		assertThat(NpcCastle.shopRate(castle)).isEqualTo(1.15);
		assertThat(NpcCastle.multisellRate(castle)).isEqualTo(1.15);
		
		NpcCastle.addPurchaseTax(castle, 50L);
		verify(castle).addToTreasury(50L);
	}
	
	@Test
	@DisplayName("an unowned castle is not paid")
	void unownedCastleIsNotPaid()
	{
		Castle castle = mock(Castle.class);
		when(castle.getOwnerId()).thenReturn(0);
		
		NpcCastle.addPurchaseTax(castle, 50L);
		
		verify(castle, never()).addToTreasury(50L);
	}
	
	@Test
	@DisplayName("a town castle is preferred, and a town without one uses the closest castle")
	void townCastleIsPreferred()
	{
		Castle town = mock(Castle.class);
		Castle closest = mock(Castle.class);
		when(town.getCastleId()).thenReturn(2);
		when(closest.getCastleId()).thenReturn(9);
		
		assertThat(NpcCastle.indexOf(true, town, closest)).isEqualTo(2);
		assertThat(NpcCastle.indexOf(true, null, closest)).isEqualTo(9);
		assertThat(NpcCastle.indexOf(false, null, closest)).isEqualTo(9);
		assertThat(NpcCastle.indexOf(false, null, null)).isEqualTo(NpcCastle.NONE);
	}
}
