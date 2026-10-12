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
package com.l2jfree.gameserver.util;

import java.io.File;

/**
 * The files the world writes while it runs: leaderboards, admin bookmarks, and similar state.
 * <p>
 * They live in the {@code state} directory of the working directory, never next to the datapack, because the
 * datapack is read-only in the image. The working directory is a volume there, so the files survive
 * a restart and an image update.
 */
public final class RuntimeData
{
	private static final File DIRECTORY = new File("state");
	
	private RuntimeData()
	{
	}
	
	/**
	 * @param name a path relative to the state directory
	 * @return the file, with its parent directory created
	 */
	public static File file(String name)
	{
		final File file = new File(DIRECTORY, name);
		final File parent = file.getParentFile();
		if (!parent.isDirectory())
			parent.mkdirs();
		return file;
	}
}
