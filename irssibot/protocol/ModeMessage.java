/*
 * $Id: ModeMessage.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
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
 * Represents an IRC protocol MODE message.<P>
 * 
 * The JOIN message format is:<br>
 * <pre>
 * :nick!ident@host MODE #channel [+modes][-modes] [target1] [target2] ..
 * </pre>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $
 * @see RFC2812: IRC Client Protocol 
 */
public class ModeMessage extends IrcMessage {
  /**
   * Sources host
   */
  private Host sourceHost = null;
  /**
   * Channel name
   */
  private String channelName = null;
  /**
   * Mode string
   */
  private String modeString = null;
  /**
   * Target list
   */
  private String targetList[] = null;
  /**
   * Whether this MODE describes a channel's mode change or a user's mode
   * change on a channel.
   */
  private boolean channelMode = true;

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
  protected ModeMessage(String prefix, String command, String arguments[], 
			String trailing, String raw) {
    super(prefix, command, arguments, trailing, raw);

    int count = arguments.length;
    if ( count < 2 ) {
      throw new IllegalArgumentException("Bad MODE message: " + toString());
    }

    sourceHost = new Host(prefix);
    channelName = arguments[0];
    modeString = arguments[1];

    if ( count > 2 ) {
      channelMode = false;

      count = count - 2;
      targetList = new String[count];
      for ( int i = 0; i < count; i++ ) {
	targetList[i] = arguments[i + 2];
      }
    }
  }

  /**
   * Returns the source host.<P>
   */
  public Host getSourceHost() {
    return sourceHost;
  }

  /**
   * Returns the channel name.<P>
   */
  public String getChannelName() {
    return channelName;
  }

  /**
   * Returns the modestring.<P>
   */
  public String getModeString() {
    return modeString;
  }

  /**
   * Returns the target list.<P>
   */
  public String[] getTargetList() {
    return targetList;
  }

  /**
   * Returns true if this MODE message describes a channel mode change, and false 
   * if it describes a user's mode change on a channel.<P>
   */
  public boolean isChannelMode() {
    return channelMode;
  }
}





