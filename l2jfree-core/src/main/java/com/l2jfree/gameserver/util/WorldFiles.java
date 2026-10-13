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
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;

import org.slf4j.Logger;

/**
 * Tells a missing world file from a file that is there but cannot be read.
 * <p>
 * Absence is a {@link NoSuchFileException} from reading the path's attributes. An empty directory is the same. A path
 * of the wrong kind, a directory whose entries cannot be listed, or any other failure to read the attributes (a parent
 * that cannot be searched, for example) is a read failure: the path and the reason stay in the log, and the failure is
 * not described as a missing part.
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
	 * @return {@code true} when the caller must not open the file. A path that is not there is one INFO line. A path
	 * whose attributes cannot be read, or that is not a file, is one warning with the path.
	 */
	public static boolean skip(Logger log, String part, File file)
	{
		BasicFileAttributes attributes;
		try
		{
			attributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
		}
		catch (NoSuchFileException e)
		{
			log.info("World has no " + part);
			return true;
		}
		catch (IOException e)
		{
			unreadable(log, part, file, e);
			return true;
		}
		if (!attributes.isRegularFile())
		{
			log.warn("Cannot read " + part + ": " + file.getPath() + " is not a file");
			return true;
		}
		return false;
	}
	
	/** A file that exists could not be read. The warning names the path and the reason. */
	public static void unreadable(Logger log, String part, File file, Exception cause)
	{
		String reason = cause.getMessage() == null ? cause.toString() : cause.getMessage();
		log.warn("Cannot read " + part + ": " + file.getPath() + ": " + reason, cause);
	}
	
	public static Listed list(File dir, FilenameFilter filter)
	{
		Listed blocked = attributes(dir);
		if (blocked != null)
			return blocked;
		File[] files = filter == null ? dir.listFiles() : dir.listFiles(filter);
		return classify(dir, files);
	}
	
	public static Listed list(File dir, FileFilter filter)
	{
		Listed blocked = attributes(dir);
		if (blocked != null)
			return blocked;
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
	
	/**
	 * @return the outcome when the directory must not be listed, or {@code null} when it is a directory and listing
	 * may proceed. Only {@link NoSuchFileException} is absence. Every other failure to read the attributes is a read
	 * error, including a parent directory that cannot be searched.
	 */
	private static Listed attributes(File dir)
	{
		try
		{
			BasicFileAttributes attributes = Files.readAttributes(dir.toPath(), BasicFileAttributes.class);
			if (!attributes.isDirectory())
				return new Listed(Kind.UNREADABLE, new File[0], dir.getPath() + " is not a directory");
			return null;
		}
		catch (NoSuchFileException e)
		{
			return new Listed(Kind.MISSING, new File[0], null);
		}
		catch (IOException e)
		{
			return new Listed(Kind.UNREADABLE, new File[0], dir.getPath() + ": " + reason(e));
		}
	}
	
	private static Listed classify(File dir, File[] files)
	{
		if (files == null)
			return new Listed(Kind.UNREADABLE, new File[0], dir.getPath() + " could not be listed");
		if (files.length == 0)
			return new Listed(Kind.EMPTY, files, null);
		return new Listed(Kind.LISTED, files, null);
	}
	
	private static String reason(IOException cause)
	{
		String message = cause.getMessage();
		String type = cause.getClass().getSimpleName();
		if (message == null || message.isEmpty())
			return type;
		return type + ": " + message;
	}
}
