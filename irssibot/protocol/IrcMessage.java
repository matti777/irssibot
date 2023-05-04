/*
 * $Id: IrcMessage.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
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
package irssibot.protocol;

import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Represents an IRC protocol message from server.<P>
 * 
 * The BNF definition for an irc message is:<P>
 * <pre>
 * message    =  [ ":" prefix SPACE ] command [ params ] crlf
 * params     =  *14( SPACE middle ) [ SPACE ":" trailing ]
 * </pre>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $
 * @see RFC2812: IRC Client Protocol 
 */
public class IrcMessage {
  /**
     * Prefix part of the message, or <b>null</b> if none supplied.
     */
  protected String prefix = null;
  /**
     * Command part of the message
     */
  protected String command = null;
  /**
     * Parameters of the message, or <b>null</b> if none supplied.
     */
  protected String arguments[] = null;
  /**
     * Trailing part of the message, or <b>null</b> if none supplied.
     */
  protected String trailing = null;
  /**
   * The original actual message string.
   */
  protected String raw = null; 

  /**
   * Default constructor for creating empty reusable instance.<P>
   *
   */
  public IrcMessage() {
    // do nothing
  }

  /**
   * Constructs with all the parts already parsed.<P>
   *
   * @param prefix prefix part
   * @param command command part
   * @param arguments arguments
   * @param trailing trailing part
   * @param raw the raw message
   */
  protected IrcMessage(String prefix, String command, String arguments[], 
		       String trailing, String raw) {
    this.prefix = prefix;
    this.command = command;
    this.arguments = arguments;
    this.trailing = trailing;
    this.raw = raw;
  }

  /**
     * Sets the instance data to given values. Used for instance reuse.<P>
     *
     * @param prefix prefix part
     * @param command command part
     * @param arguments arguments
     * @param trailing trailing part
     * @param raw the raw message
     */
  protected void setData(String prefix, String command, String arguments[], 
			 String trailing, String raw) {
    this.prefix = prefix;
    this.command = command;
    this.arguments = arguments;
    this.trailing = trailing;
    this.raw = raw;
  }

  /**
     * Parses an raw irc message and returns an <code>IrcMessage</code> 
     * instance for it.<P>
     *
     * @param raw raw message
     * @return new instance
     * @exception IllegalArgumentException if the input cannot be interpreted
     * as a valid IRC message.
     */
  public static IrcMessage parse(String raw) {
    String prefix = null;
    String command = null;
    String arguments[] = null;
    String trailing = null;
    StringTokenizer st = new StringTokenizer(raw, " ");
    String token = null;

    if ( st.hasMoreTokens() ) {
      token = st.nextToken();
    } else {
      throw new IllegalArgumentException("Bad message syntax: " + raw);
    }

    if ( token.charAt(0) == ':' ) {
      // parse prefix & command
      prefix = token.substring(1);

      if ( st.hasMoreTokens() ) {
	command = st.nextToken();
      } else {
	throw new IllegalArgumentException("Bad message syntax: " + raw);
      }
    } else {
      // message has no prefix
      command = token;
    }

    if ( (command == null) || command.equals("") ) {
      throw new IllegalArgumentException("Command cannot be null or empty! " + 
					 "command = " + command);
    }

    // parse parameters
    Vector v = new Vector(5, 5);
    while ( st.hasMoreTokens() ) {
      token = st.nextToken();

      // check for trailing part
      if ( token.charAt(0) == ':' ) {
	trailing = token.substring(1);

	// parse whole trailing part and we're through
	while ( st.hasMoreTokens() ) {
	  trailing += " " + st.nextToken();
	}
      } else {
	v.add(token);
      }
    }

    // create arguments -array
    int size = v.size();
    if ( size > 0 ) {
      arguments = new String[size];
	    
      for ( int i = 0; i < size; i++ ) {
	arguments[i] = (String)v.elementAt(i);
      }
    }

    if ( command.equals("JOIN") ) {
      return new JoinMessage(prefix, command, arguments,
			     trailing, raw);
    } else {
      return new IrcMessage(prefix, command, arguments, trailing, raw);
    }
  }

  /**
   * Returns the raw original message string.<P>
   *
   * @param raw message string
   */
  public String toString() {
    return raw;
  }

  /**
     * Returns the prefix part, or <code>null</code> if none supplied.<P>
     *
     * @return prefix part
     */
  public String getPrefix() {
    return prefix;
  }

  /**
     * Returns the command part.<P>
     *
     * @return command part
     */
  public String getCommand() {
    return command;
  }

  /**
     * Returns the arguments, or <code>null</code> if none supplied.<P>
     *
     * @return arguments
     */
  public String[] getArguments() {
    return arguments;
  }

  /**
     * Returns the trailing part, or <code>null</code> if none supplied.<P>
     *
     * @return trailing part
     */
  public String getTrailing() {
    return trailing;
  }
}











