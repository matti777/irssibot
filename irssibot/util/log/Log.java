/*
 * $Id: Log.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
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

import java.util.Date;
import java.util.StringTokenizer;
import java.text.SimpleDateFormat;

import java.io.PrintStream;
import java.io.ByteArrayOutputStream;

/**
 * Logging system for IrssiBot.<P>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $
 */
public class Log {
  // debug levels
  public static int DEBUG_LOW = 2; 
  public static int DEBUG_NORMAL = 3;
  public static int DEBUG_HIGH = 10;
  public static int DEBUG_OFF = 99;

  /**
   * Current debug level
   */
  private static int debugLevel = DEBUG_NORMAL;
  /**
   * Date formatter for log messages
   */
  private static SimpleDateFormat sdf = new SimpleDateFormat("'['HH':'mm':'ss']'");
  /**
   * Current logger
   */
  private static CommonLog logger = null;
  
  /**
   * Disallow instantiating this class
   *
   */
  private Log() {
  }
  
  /**
   * Initializes logging system with a given logger.<P>
   *
   * @param logger logger object to use
   */
  public static void init(CommonLog logger, String dateFormatString) {
    Log.logger = logger;
    if ( dateFormatString != null ) {
      sdf = new SimpleDateFormat(dateFormatString);
    }
  }
  
  /**
   * Returns the current logger.<P>
   */
  public static CommonLog getLogger() {
    return logger;
  }
  
  /**
   * Returns the current date format string.<P>
   */
  public static String getDateFormatString() {
    return sdf.toPattern();
  }

  /**
   * Stops the current logger and deinits logging.<P>
   */
  public static void stop() {
    logger.stop();
    logger = null;
  }

  /**
   * Sets the debug level.<P>
   *
   * @param level new debug level
   * @return old logging level
   * @exception IllegalArgumentException if level is out
   * of range
   */
  public static int setDebugLevel(int level) {
    if ( (level < 0) || (level > DEBUG_OFF) ) {
      throw new IllegalArgumentException("level out of range!");
    }
    
    Log.info("Log", "setDebugLevel(): setting log level to " + level);
	
	
    int old = debugLevel;
    debugLevel = level;
    return old;
  }

  /**
     * Returns the current debug level.<P>
     *
     * @return current debug level
     */
  public static int getDebugLevel() {
    return debugLevel;
  }

  /**
   * Prints a logging message via current logger.<P>
   *
   * @param obj the source of this log message
   * @param prefix log message prefix
   * @param message log message
   */
  private static void logMessage(Object obj, String prefix, String message) {
    if ( logger == null ) {
      throw new IllegalStateException("Logging system not initialized!");
    }

    String tName = Thread.currentThread().getName();
    String time = sdf.format(new Date());
    String source = " ";
    if ( obj != null ) {
      source += obj.toString();
    }

    try {
      logger.logMessage(time + " " + prefix + 
			" <" + tName + ">" + 
			source + ": " + message);
    } catch ( LogException e ) {
      Throwable t = e.getRootCause();
      if ( t != null ) {
	System.err.println("Log.logMessage(): error: " + t.getMessage());
      }
      e.printStackTrace();
    }
  }

  /**
   * Prints a debug message via current logger with given
   * debug level.<P>
   *
   * @param obj the source of this log message
   * @param message log message
   * @param level log level to use
   * @exception IllegalArgumentException if level is out
   * of range
   */
  public static void debug(Object obj, String message, int level) {
    if ( (level < 0) || (level >= DEBUG_OFF) ) {
      throw new IllegalArgumentException("level out of range!");
    }

    if ( level >= debugLevel ) {
      logMessage(obj, "DEBUG", message);
    }
  }
 
  /**
   * Prints a debug message to log with default
   * debug level.<P>
   *
   * @param obj the source of this log message
   * @param message log message
   */
  public static void debug(Object obj, String message) {
    if ( DEBUG_NORMAL >= debugLevel ) {
      logMessage(obj, "DEBUG", message);
    }
  }
 
  /**
   * Prints an info message to log.<P>
   *
   * @param obj the source of this log message
   * @param message log message
   */
  public static void info(Object obj, String message) {
    logMessage(obj, "INFO", message);
  }

  /**
   * Prints an error message to log.<P>
   *
   * @param obj the source of this log message
   * @param message log message
   */
  public static void error(Object obj, String message) {
    logMessage(obj, "ERROR", message);
  }

  /**
   * Prints a server message to log. This must <b>only</b> be used 
   * by the bot core.<P>
   *
   * @param obj the source of this log message
   * @param message log message
   */
  public static void server(String message) {
    logMessage(null, "SERVER:", message);
  }

  /**
   * Prints an error description to the log based on
   * a Throwable causing the error. Use this method to log all uncatched
   * exceptions.<P>
   *
   * @param obj the source of this log message
   * @param t the Throwable causing the error
   */
  public static void log(Object obj, Throwable t) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(4 * 1024);
    PrintStream p = new PrintStream(out);

    t.printStackTrace(p);
    String s = out.toString();
    StringTokenizer st = new StringTokenizer(s, "\n");

    error(obj, "Caught " + t.getClass().getName() + "!");
	
    while ( st.hasMoreTokens() ) {
      logMessage(null, "", st.nextToken());
    }
  }
}


