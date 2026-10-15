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
package com.l2jfree.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The seed-loop decisions that do not need a running server. */
class SeedLoopTest
{
	@Test
	void aLaterStopKeepsItsPlaceInsteadOfTheOldDestination() throws SmokeException
	{
		SeedLoop.View view = moving();
		SeedLoop.armMove(view, 100, 0, -3104);
		SeedLoop.apply(view, moveTo(7, 100, 0, -3104, 0, 0, -3104));
		SeedLoop.apply(view, stopAt(7, 40, 5, -3104));
		SeedLoop.adoptDestination(view);
		
		assertThat(view.x).isEqualTo(40);
		assertThat(view.y).isEqualTo(5);
		assertThat(view.z).isEqualTo(-3104);
	}
	
	@Test
	void aLaterValidateLocationKeepsItsPlace() throws SmokeException
	{
		SeedLoop.View view = moving();
		SeedLoop.armMove(view, 100, 0, -3104);
		SeedLoop.apply(view, moveTo(7, 100, 0, -3104, 0, 0, -3104));
		SeedLoop.apply(view, validateAt(7, 40, 5, -3100));
		SeedLoop.adoptDestination(view);
		
		assertThat(view.x).isEqualTo(40);
		assertThat(view.y).isEqualTo(5);
		assertThat(view.z).isEqualTo(-3100);
	}
	
	@Test
	void anOlderDestinationDoesNotConfirmTheNewRequest() throws SmokeException
	{
		SeedLoop.View view = moving();
		SeedLoop.armMove(view, 100, 0, -3104);
		SeedLoop.apply(view, moveTo(7, 100, 0, -3104, 0, 0, -3104));
		SeedLoop.armMove(view, 250, 0, -3104);
		SeedLoop.apply(view, moveTo(7, 100, 0, -3104, 40, 0, -3104));
		
		assertThat(view.acceptedMove).isFalse();
		assertThat(SeedLoop.unansweredMove(view, 40, 0, -3104, 250, 0, -3104).getMessage())
				.contains("neither MoveToLocation nor ActionFailed arrived");
		
		SeedLoop.apply(view, moveTo(7, 250, 0, -3104, 40, 0, -3104));
		assertThat(view.acceptedMove).isTrue();
		SeedLoop.adoptDestination(view);
		assertThat(view.x).isEqualTo(250);
	}
	
	@Test
	void actionFailedBesideTheConfirmationIsNotARefusal() throws SmokeException
	{
		SeedLoop.View view = moving();
		SeedLoop.armMove(view, 100, 0, -3104);
		SeedLoop.apply(view, new byte[] { 0x1f });
		SeedLoop.apply(view, moveTo(7, 100, 0, -3104, 0, 0, -3104));
		
		assertThat(view.acceptedMove).isTrue();
		assertThat(view.moveRejected).isTrue();
		SeedLoop.adoptDestination(view);
		assertThat(view.x).isEqualTo(100);
	}
	
	@Test
	void aSilentMoveNamesThePlacesAndActionFailedWhenItArrived() throws SmokeException
	{
		SeedLoop.View view = moving();
		SeedLoop.armMove(view, 100, 0, -3104);
		
		assertThat(SeedLoop.unansweredMove(view, 1, 2, 3, 100, 0, -3104).getMessage())
				.contains("neither MoveToLocation nor ActionFailed arrived")
				.contains("from 1,2,3")
				.contains("to 100,0,-3104");
		
		SeedLoop.apply(view, new byte[] { 0x1f });
		assertThat(SeedLoop.unansweredMove(view, 1, 2, 3, 100, 0, -3104).getMessage())
				.contains("ActionFailed arrived");
	}
	
	private static SeedLoop.View moving()
	{
		SeedLoop.View view = new SeedLoop.View(0);
		view.objectId = 7;
		return view;
	}
	
	private static byte[] moveTo(int objectId, int destX, int destY, int destZ, int fromX, int fromY, int fromZ)
	{
		return new PacketWriter().writeC(0x2f).writeD(objectId).writeD(destX).writeD(destY).writeD(destZ).writeD(fromX)
				.writeD(fromY).writeD(fromZ).toByteArray();
	}
	
	private static byte[] stopAt(int objectId, int x, int y, int z)
	{
		return new PacketWriter().writeC(0x47).writeD(objectId).writeD(x).writeD(y).writeD(z).writeD(0).toByteArray();
	}
	
	private static byte[] validateAt(int objectId, int x, int y, int z)
	{
		return new PacketWriter().writeC(0x79).writeD(objectId).writeD(x).writeD(y).writeD(z).writeD(0).toByteArray();
	}
}
