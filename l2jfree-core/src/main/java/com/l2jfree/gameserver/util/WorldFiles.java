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
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;

import org.slf4j.Logger;

/**
 * Tells a missing world file from a file that is there but cannot be read.
 * <p>
 * A path that does not exist is an absent part of the world. An empty directory is the same. A path that exists and is
 * the wrong kind, or a directory whose entries cannot be listed, is a read failure: the path and the reason stay in
 * the log, and the failure is not described as a missing part.
 */
public final class WorldFiles
{
	public enum Kind
	{
		MISSING, EMPTY, LISTED, UNREADABLE
	}
	
	public static final class Listed
	{
		private final Kind _kind;
		private final File[] _files;
		private final String _detail;
		
		private Listed(Kind kind, File[] files, String detail)
		{
			_kind = kind;
			_files = files;
			_detail = detail;
		}
		
		public Kind kind()
		{
			return _kind;
		}
		
		public File[] files()
		{
			return _files;
		}
		
		public String detail()
		{
			return _detail;
		}
	}
	
	private WorldFiles()
	{
	}
	
	/**
	 * @return {@code true} when the caller must not open the file. Absence is one INFO line. A path that exists and is
	 * not a file is one warning with the path.
	 */
	public static boolean skip(Logger log, String part, File file)
	{
		if (!file.exists())
		{
			log.info("World has no " + part);
			return true;
		}
		if (!file.isFile())
		{
			log.warn("Cannot read " + part + ": " + file.getPath() + " is not a file");
			return true;
		}
		return false;
	}
	
	/** A file that exists could not be opened. The warning names the path and the reason. */
	public static void unreadable(Logger log, String part, File file, IOException cause)
	{
		String reason = cause.getMessage() == null ? cause.toString() : cause.getMessage();
		log.warn("Cannot read " + part + ": " + file.getPath() + ": " + reason, cause);
	}
	
	public static Listed list(File dir, FilenameFilter filter)
	{
		if (!dir.isDirectory())
			return classify(dir, null);
		File[] files = filter == null ? dir.listFiles() : dir.listFiles(filter);
		return classify(dir, files);
	}
	
	public static Listed list(File dir, FileFilter filter)
	{
		if (!dir.isDirectory())
			return classify(dir, null);
		File[] files = filter == null ? dir.listFiles() : dir.listFiles(filter);
		return classify(dir, files);
	}
	
	/**
	 * @return {@code true} when the caller should not read the directory. A missing or empty directory is one INFO
	 * line. A directory that cannot be listed is one warning with the path and the reason.
	 */
	public static boolean leaveOut(Logger log, String part, Listed listed)
	{
		if (listed.kind() == Kind.UNREADABLE)
		{
			log.warn("Cannot read " + part + ": " + listed.detail());
			return true;
		}
		if (listed.files().length == 0)
		{
			log.info("World has no " + part);
			return true;
		}
		return false;
	}
	
	private static Listed classify(File dir, File[] files)
	{
		if (!dir.exists())
			return new Listed(Kind.MISSING, new File[0], null);
		if (!dir.isDirectory())
			return new Listed(Kind.UNREADABLE, new File[0], dir.getPath() + " is not a directory");
		if (files == null)
			return new Listed(Kind.UNREADABLE, new File[0], dir.getPath() + " could not be listed");
		if (files.length == 0)
			return new Listed(Kind.EMPTY, files, null);
		return new Listed(Kind.LISTED, files, null);
	}
}
