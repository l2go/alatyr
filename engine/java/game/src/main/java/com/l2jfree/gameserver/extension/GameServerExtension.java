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
package com.l2jfree.gameserver.extension;

/**
 * A piece of the server that starts after the world is up and closes before the world shuts down.
 * <p>
 * {@link #close()} may be called more than once and must be safe to repeat. No provider means the server starts and
 * stops exactly as it did before.
 */
public interface GameServerExtension extends AutoCloseable
{
	void start(GameServerContext context);
	
	@Override
	void close();
}
