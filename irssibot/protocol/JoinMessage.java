/*
 * $Id: JoinMessage.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
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

import irssibot.user.Host;

/**
 * Represents an IRC protocol JOIN message.<P>
 * 
 * The JOIN message format is:<br>
 * <pre>
 * :nick!ident@host JOIN #channel
 *
 * OR:
 *
 * :nick!ident@host JOIN :#channel
 * </pre>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $
 * @see RFC2812: IRC Client Protocol 
 */
public class JoinMessage extends IrcMessage {
  /**
   * Joiners host
   */
  private Host joinerHost = null;
  /**
   * Joined channels name 
   */
  private String joinedChannelName = null;

  /**
   * Constructs with all the parts already parsed. Sets 
   * <code>JoinMessage</code> specific data.<P>
   *
   * @param prefix prefix part
   * @param command command part
   * @param arguments arguments
   * @param trailing trailing part
   * @param raw the raw message
   * @exception IllegalArgumentException if message format is invalid
   */
  protected JoinMessage(String prefix, String command, String arguments[], 
			String trailing, String raw) {
    super(prefix, command, arguments, trailing, raw);

    // extract joiners host
    joinerHost = new Host(prefix);

    // extract channel name
    if ( (arguments != null) && (arguments.length >= 1) ) {
      joinedChannelName = arguments[0];
    } else if ( trailing != null ) {
      joinedChannelName = trailing;
    } else {
      throw new IllegalArgumentException("No channel name in JOIN message!");
    }
  }

  /**
   * Returns the joiners host.<P>
   */
  public Host getJoinerHost() {
    return joinerHost;
  }

  /**
   * Returns the joined channels name.<P>
   */
  public String getJoinedChannelName() {
    return joinedChannelName;
  }
}








