/*
 * $Id: CommonLog.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
 *
 * IrssiBot - An advanced IRC automation ("bot")
 * Copyright (C) 2000 Matti Dahlbom
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * mdahlbom@cc.hut.fi
 */
package irssibot.util.log;

import java.util.Properties;

/**
 * Common log interface for logging system.<P>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $
 */
public interface CommonLog {
  /**
   * Property name for property file path.<P>
   */
  public static String PROPERTY_FILE_PATH_PROPERTY_NAME = "logger-property-file-path";
  /**
   * Writes a log message to log.<P>
   *
   * @param message preformatted log message to be written as it is
   * @exception LogException if logging error occurs
   */
  public void logMessage(String message) throws LogException;
  /**
   * Initializes this logger.<P>
   *
   * @param properties init properties, or null if none supplied
   */
  public void init(Properties properties);
  /**
   * Stops and deinits this logger.<P>
   *
   */
  public void stop();
  /**
   * Returns path for property file from which this CommonLog was initialized.<P>
   *
   * @return path to property file
   */
  public String getPropertyFilePath();
}











