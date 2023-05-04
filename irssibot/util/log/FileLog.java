/*
 * $Id: FileLog.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
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

import java.io.*;
import java.util.Properties;

/**
 * Logger for file based logging.<P>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $
 */
public class FileLog implements CommonLog {
  /**
   * Output stream for current log file
   */
  private BufferedOutputStream out = null;
  /**
   * Init properties
   */
  private Properties properties = null;
  /**
   * Constructs. 
   *
   */
  public FileLog() {
  }

  /**
   * Returns path for property file from which this CommonLog was initialized.<P>
   *
   * @return path to property file
   */
  public String getPropertyFilePath() {
    return properties.getProperty(PROPERTY_FILE_PATH_PROPERTY_NAME);
  }

  /**
   * Logs a preformatted log message to file, appending a line
   * break character <code>\n</code>.
   *
   * @param message log message to write
   * @exception LogException if there is an error writing to file 
   */
  public void logMessage(String message) throws LogException {
    if ( out == null ) {
      throw new LogException("Output stream to log file not initialized!");
    }
    
    message += "\n";
    byte data[] = message.getBytes();
    
    try {
      out.write(data, 0, data.length);
      out.flush();
    } catch ( IOException e ) {
      throw new LogException(e);
    }
  }

  /**
   * Initializes this logger. Opens output stream to a new log file, creating the file.<P>
   *
   * @param properties init properties, or null if none supplied
   * @param IllegalArgumentException if bad or missing properties
   */
  public void init(Properties properties) {
    this.properties = properties;

    String path = properties.getProperty("logfile");
    if ( path == null ) {
      throw new IllegalArgumentException("Property logfile missing!");
    }

    try {
      File file = new File(path);
      out = new BufferedOutputStream(new FileOutputStream(file));
    } catch ( IOException e ) {
      throw new IllegalArgumentException("Error estabilishing output stream to file " + path +
					 ": " + e.getMessage());
    }
  }

  /**
   * Stops and deinits this logger. Closes the log file.<P>
   *
   */
  public void stop() {
    try {
      out.close();
    } catch ( IOException e ) {
      // dont care
    }

    out = null;
  }
}



