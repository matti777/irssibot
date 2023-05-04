/*
 * $Id: Channel.java,v 1.1 2002/11/08 11:14:31 dreami Exp $
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
package irssibot.core;

import irssibot.protocol.*;
import irssibot.util.*;
import irssibot.util.log.Log;
import irssibot.user.*;

import java.util.Vector;

/**
 * Represents an IRC channel the bot is supposed to be on.<P>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.1 $
 */
public class Channel
{
    private String channelName = null;
    private String channelForcedModes = null;
    private String channelTopic = null;
    private String channelKey = null;

    /**
     * Allowed channel modes for the server.
     */
  private String serverChannelModes = "";
    /**
     * Original string containing forced modes (from config file) in
     * format +abc-de where a,b,c,d,e are mode characters. Will become redundant
     * when server connection has been completed and the parseForcedModes()
     * has been called.
     *
     * @see #parseForcedModes(String)
     */
    private String forcedModes = null;
    /**
     * String containing all channel modes that the bot will forcibly
     * uphold.
     */
    private String posModes = null;
    /**
     * String containing all channel modes that the bot will forcibly
     * keep unset.
     */
    private String negModes = null;
    /**
     * true if currently joined on the channel.
     */
    private boolean joined = false;
    /** 
     * Contains Nick objects 
     */
    private Vector nickList = null;
    /**
     * Contains Strings as banmasks
     */
    private Vector banList = null;
    /**
     * Contains Strings as ban exception masks
     */
    private Vector banexList = null;
    /**
     * Contains Strings as need-invite exception masks
     */
    private Vector invexList = null;
    /**
     * Reference to the owning connection
     */
    private ServerConnection serverConnection = null;
    /**
     * Returns channel modes that the bot will forcibly uphold
     */
    public String getPosModes() { return posModes; }
    /**
     * Returns channel modes that the bot will forcibly keep off
     */
    public String getNegModes() { return negModes; }

    /**
     * Indicates whether the WHO listing has been completed 
     * (set true on RPL_ENDOFWHO)
     */
    private boolean nicklistReady = false;
    /**
     * Indicates whether the MODE #channel b (ban) listing has been 
     * completed (set true on RPL_ENDOFBANLIST)
     */
    private boolean banlistReady = false; 

    public String getChannelName() { return channelName; }
    public String getChannelKey() { return channelKey; }
    public String getChannelTopic() { return channelTopic; }
    public Vector getBanList() { return banList; }
    public Vector getNickList() { return nickList; }
    /**
     * Indicates whether the bot is currently on this channel.
     *
     * @return true is bot is on this channel
     */
    public boolean isJoined() { return joined; }

    public void setChannelKey(String key) { channelKey = key; }

    public Channel(String channelName, String channelKey, String forcedModes,
		   ServerConnection serverConnection)
    {
	this.channelName = channelName.toLowerCase();
	this.channelKey = channelKey;
	this.serverConnection = serverConnection;
	this.forcedModes = forcedModes;
	nickList = new Vector();
	banList = new Vector();
	banexList = new Vector();
	invexList = new Vector();

	//	parseForcedModes(forceModes);
    }

    /**
     * Returns a String representation of the object.
     *
     * @return a String representation of the Channel object
     */
    public String toString() 
    {
	return "Channel (" + channelName + ")";
    }

    /**
     * Sets the server's allowed channel modes string. Calls parseForcedModes() to 
     * init the lists of upheld and kept off modes.
     * 
     * @param serverChannelModes list of allowed channel modes
     */
    public void setServerChannelModes(String serverChannelModes) 
    {
      if ( serverChannelModes == null ) {
	Log.info(this, "setServerChannelModes(): set null serverChannelModes!");
	this.serverChannelModes = "";
      } else {
	this.serverChannelModes = serverChannelModes;
      }

      parseForcedModes(forcedModes);
    }

    public void setServerConnection(ServerConnection serverConnection) 
    {
	this.serverConnection = serverConnection;
    }

    /**
     * On RPL_CHANNELMODEIS reply compare current channel mode to forced modes. 
     * MODES #channel is invoked on joining the channel to retrieve the channel
     * modes.<P>
     *
     * example:<br>
     * <code>:irc.cs.hut.fi 324 dasd #777-team +tn</code>
     *
     * @param message IrcMessage the RPL_CHANNELMODEIS message
     */
    public void onChannelModeIs(IrcMessage message)
    {
	String arguments[] = message.getArguments();
	String modes = arguments[2];

	if( (negModes != null) && (negModes.length() > 0) ) {
	    for( int i = 0; i < modes.length(); i++ ) {
		if( negModes.indexOf(modes.charAt(i)) != -1 ) {
		    serverConnection.write("MODE " + channelName + " -" + 
					   modes.charAt(i) + "\n");
		}
	    }
	}

	if( (posModes != null) && (posModes.length() > 0) ) {
	    for( int j = 0; j < posModes.length(); j++ ) {
		if( modes.indexOf(posModes.charAt(j)) == -1 ) {
		    serverConnection.write("MODE " + channelName + " +" + 
					   posModes.charAt(j) + "\n");
		}
	    }
	}
    }

    /**
     * Parses forcemodes string (for example: +nt-slikm).<P>
     *
     * @param forcedModesList the mode string
     * @return true if ok. false if attempting to set unsupported mode char
     * or passed null argument.
     */
    public boolean parseForcedModes(String forcedModesList) 
    {
	boolean polarity = true;
	String disallowedModes = "";

	if( forcedModesList == null ) {
	    return false;
	}

	posModes = "";
	negModes = "";

	for( int i = 0; i < forcedModesList.length(); i++ ) {
	    char c = forcedModesList.charAt(i);

	    switch( c ) {
	    case '+': 
		polarity = true;
		break;
	    case '-':
		polarity = false;
		break;
	    default:
		if( serverChannelModes.indexOf(c) == -1 ) {
		    disallowedModes += c;
		} else {
		    if( polarity ) {
			if( (negModes.indexOf(c) == -1) ) {
			    posModes += c;
			}
		    } else {
			if( posModes.indexOf(c) == -1 ) {
			    negModes += c;
			}
		    }
		}
	    }
	}
	Log.debug(this, "parseForcedModes(): disallowed modes " + disallowedModes);

	return true;
    }

    /**
     * Called on RPL_ENDOFWHO. If ban list also done, sends fake 
     * JOIN messages to modules.<P>
     *
     * @param message the RPL_ENDOFWHO message
     */
    public void onEndOfWhoMsg(IrcMessage message)
    {
	nicklistReady = true;
	if( nicklistReady && banlistReady ) {
	    String joinedMessage = ":" + serverConnection.getHost().toString() + 
		" JOIN :" + channelName;
	    IrcMessage joinMessage = IrcMessage.parse(joinedMessage);
	    serverConnection.getModuleHandler().forwardMessage(joinMessage,
							       serverConnection);
	}
    }

    /**
     * Called on RPL_ENDOFBANLIST. If who list also done, sends fake
     * JOIN messages to modules.<P>
     *
     * @param message the RPL_ENDOFBANLIST message
     */
    public void onEndOfBanListMsg(IrcMessage message)
    {
	banlistReady = true;
	if( nicklistReady && banlistReady ) {
	    String joinedMessage = ":" + serverConnection.getHost().toString() +  
		" JOIN :" + channelName;
	    IrcMessage joinMessage = IrcMessage.parse(joinedMessage);
	    serverConnection.getModuleHandler().forwardMessage(joinMessage,
							       serverConnection);
	}
    }

    /**
     * Called on JOIN on this channel. If joiner is self (bot), invoke a WHO query 
     * to retrieve nameslist.<P>
     *
     * @param message the JOIN message
     */
    public void onJoin(IrcMessage message)
    {
	Host host = new Host(message.getPrefix());

	/* add to nick list */
	Nick nick = new Nick(host,false,false);
	nickList.add(nick);

	/* check if joiner is the bot itself */
	if( serverConnection.getHost().matches(host) ) {
	    joined = true;

	    /* get nick list */
	    invokeWho();

	    /* get ban list */
	    invokeBanList();

	    /* get channel modes */
	    serverConnection.write("MODE " + channelName + "\n");
	} else {
	    /* forward JOIN message to modules */
	    serverConnection.getModuleHandler().forwardMessage(message,serverConnection);

	    User user = serverConnection.findUser(host);
	    if( user != null ) {
		/* only do if op on channel */
		if( isOp() ) {
		    /* auto-op */
		    if( user.isOp(channelName) ) {
			ModeQueueElement mode = 
			    new ModeQueueElement(Irc.MODE_OP,ModeQueueElement.PRIORITY_NORMAL,
						 host.getNick(),channelName);
			serverConnection.getOutputQueue().pushMode(mode);
		    }  

		    /* auto-voice */
		    if( user.isVoice(channelName) ) {
			ModeQueueElement mode = 
			    new ModeQueueElement(Irc.MODE_VOICE,ModeQueueElement.PRIORITY_NORMAL,
						 host.getNick(),channelName);
			serverConnection.getOutputQueue().pushMode(mode);
		    }
		}
	    }
	}
    }

    /**
     * Called on TOPIC change.<P>
     *
     * example:<br>
     * <code>
     * :reaperi!~EvilEd@fisherman.tky.hut.fi TOPIC #777-team 
     * :http://777-team.org/Ircstats/ | or or
     * </code>
     *
     * @param message the TOPIC change message
     */
    public void onTopic(IrcMessage message)
    {
	Host host = new Host(message.getPrefix());

	channelTopic = message.getTrailing();
    }

    /**
     * Called on RPL_TOPIC.<P>
     *
     * @param message the TOPIC message
     */
    public void onTopicMsg(IrcMessage message)
    {
	channelTopic = message.getTrailing();
    }

    /**
     * Called on PART from this channel.<P>
     *
     * @param message the PART message
     */
    public void onPart(IrcMessage message)
    {
	Host host = new Host(message.getPrefix());

	if( serverConnection.getHost().matches(host) ) {
	    // bot self parted
	    joined = false;
	} else {
	    nickList.remove(findNick(host.getNick()));
	}
    }	

    /**
     * Called on KICK from this channel.<P>
     *
     * @param message the KICK message
     */
    public void onKick(IrcMessage message)
    {
	String arguments[] = message.getArguments();

	Nick nick = findNick(arguments[1]);
	if( serverConnection.getHost().equals(nick.getHost()) ) {
	    // bot self was kicked 
	    joined = false;
	} else {
	    nickList.remove(nick);
	}
    }

    /**
     * Called on QUIT from this channel.<P>
     *
     * @param message the QUIT message
     */
    public void onQuit(IrcMessage message)
    {
	Host quittingHost = new Host(message.getPrefix());
	nickList.remove(findNick(quittingHost.getNick()));
    }

    /**
     * Goes through channels nick list and ops/voices channel ops/voices without
     * ops/voices currently. Also forces the channel modes on the forcelist.
     *
     */
    public void doMaintain() 
    {
	Log.debug(this, "doMaintain()");

	for( int i = 0; i < nickList.size(); i++ ) {
	    Nick nick = (Nick)nickList.elementAt(i);
	    User user = serverConnection.findUser(nick.getHost());
	    if( user != null ) {
		if( user.isOp(channelName) && !nick.isOp() ) {
		    ModeQueueElement mode = 
			new ModeQueueElement(Irc.MODE_OP,ModeQueueElement.PRIORITY_NORMAL,
					     nick.getHost().getNick(),channelName);
		    serverConnection.getOutputQueue().pushMode(mode);
		} 

		if( user.isVoice(channelName) && !nick.isVoice() ) {
		    ModeQueueElement mode = 
			new ModeQueueElement(Irc.MODE_VOICE,ModeQueueElement.PRIORITY_NORMAL,
					     nick.getHost().getNick(),channelName);
		    serverConnection.getOutputQueue().pushMode(mode);
		}
	    }
	}

	// provoke mode forcing by doing MODE #channel
	serverConnection.write("MODE " + channelName + "\n");
    }

    /**
     * Handles mode changes<P>
     *
     * example:<br>
     * <code>
     * :dreami!^matti@xxx-team.org MODE #777-team +vo-o latex sintetik latex
     * </code>
     *
     * @param message the NICK message
     */
    public void onMode(IrcMessage message)
    {
	String execute = "";
	String arguments[] = message.getArguments();
	String modeString = arguments[1];
	boolean polarity = false;
	Nick nick = null;

	Host host = new Host(message.getPrefix());

	for( int i = 0; i < modeString.length(); i++ ) {
	    switch( modeString.charAt(i) ) {
	    case '-':   
		polarity = false;
		break;
	    case '+':
		polarity = true;
		break;
	    case 'o':
		execute += ( polarity ) ? "O" : "o";
		break;
	    case 'v':
		execute += ( polarity ) ? "V" : "v";
		break;
	    case 'b':
		execute += ( polarity ) ? "B" : "b";
		break;
	    case 'I':
		execute += ( polarity ) ? "I" : "i";
		break;
	    case 'e':
		execute += ( polarity ) ? "E" : "e";
		break;
	    case 'k':
		execute += ( polarity ) ? "K" : "k";
		break;
	    case 'l':
		execute += ( polarity ) ? "L" : "l";
		break;
	    }

	    // look for violating mode forcing 
	    if( (posModes != null) && (negModes != null) ) {
		if( polarity ) {
		    if( negModes.indexOf(modeString.charAt(i)) != -1 ) {
			serverConnection.write("MODE " + channelName + " -" + 
					       modeString.charAt(i) + "\n");
		    }
		} else {
		    if( posModes.indexOf(modeString.charAt(i)) != -1 ) {
			serverConnection.write("MODE " + channelName + " +" + 
					       modeString.charAt(i) + "\n");
		    }
		}
	    }
	}

	for( int i = 0, index = 2; i < execute.length(); i++ ) {
	    switch( execute.charAt(i) ) {
	    case 'O':
		/* op */
		nick = findNick(arguments[index]);
		if( nick != null) {
		    nick.setOp(true);
		    if( nick.getHost().getNick().equals(serverConnection.getHost().getNick()) ) {
			doMaintain();
		    }
		    Log.debug(this, "mode change: "+nick.getHost().toString()+" +o");
		}
		index++;
		break;
	    case 'o':
		/* deop */
		nick = findNick(arguments[index]);
		if( nick != null) {
		    nick.setOp(false);
		    Log.debug(this, "mode change: "+nick.getHost().toString()+" -o");
		}
		index++;
		break;
	    case 'V':
		/* voice */
		nick = findNick(arguments[index]);
		if( nick != null) {
		    nick.setVoice(true);
		    Log.debug(this, "mode change: "+nick.getHost().toString()+" +v");
		}
		index++;
		break;
	    case 'v':
		/* devoice */
		nick = findNick(arguments[index]);
		if( nick != null) {
		    nick.setVoice(false);
		    Log.debug(this, "mode change: "+nick.getHost().toString()+" -v");
		}
		index++;
		break;
	    case 'B':
		/* ban */
		banList.add(arguments[index]);
		index++;
		break;
	    case 'b':
		/* unban */
		banList.remove(arguments[index]);
		index++;
		break;
	    case 'I':
		/* need-invite exception added */
		index++;
		break;
	    case 'i':
		/* need-invite exception removed */
		index++;
		break;
	    case 'E':
		/* ban exception added */
		index++;
		break;
	    case 'e':
		/* ban exception removed */
		index++;
		break;
	    case 'K':
		/* channel key set */
		index++;
		break;
	    case 'k':
		/* channel key removed */
		index++;
		break;
	    case 'L':
		/* channel limit set */
		index++;
		break;
	    case 'l':
		/* channel limit removed (does not have target) */
		break;
	    }
	}
    }

    /**
     * Checks whether bot is op on this channel
     *
     * @return <ul>
     *           <li>true if op
     *           <li>false if not op
     *         </ul>
     */
    public boolean isOp() 
    {
	boolean ret = false;

	Nick myself = findNick(serverConnection.getHost().getNick());
	if( myself != null ) {
	    ret = myself.isOp();
	}

	return ret;
    }
	
    /**
     * handles nick changes 
     *
     * :dreami!^matti@777-team.org NICK :dreamiz
     * @param message the NICK message
     */
    public void onNick(IrcMessage message)
    {
	Host host = new Host(message.getPrefix());
	Nick nick = findNick(host.getNick());  
	nick.setNick(message.getTrailing()); 
    }

    /**
     * Handles RPL_BANLIST messages.<P>
     *
     * :irc.cs.hut.fi 367 asdasd #777-team *!*@*.pl
     * @param message the RPL_BANLIST message
     */
    public void onBanListMsg(IrcMessage message)
    {
	String arguments[] = message.getArguments();
	String mask = arguments[2].toLowerCase();
	if( !banList.contains(mask) ) {
	    banList.add(mask);
	}
    }

    /**
     * search nick list for nick with name 
     *
     * @param name nickname to look for
     * @return correct Nick object or null if not found
     */
    public Nick findNick(String name)
    {
	for( int i = 0; i < nickList.size(); i++ ) {
	    Nick nick = (Nick)nickList.elementAt(i);
	    if( nick.getHost().getNick().equalsIgnoreCase(name) ) {
		return nick;
	    }
	}
	return null;
    }

    /**
     * Invoke WHO on channel to retrieve data about nicks on channel 
     */
    private void invokeWho()
    {
	/* clear out nick list */
	nickList.clear();

	/* invoke WHO */
	Log.debug(this, "sending 'WHO "+channelName+"' to server");
	serverConnection.write("WHO "+channelName+"\n");
    }

    /**
     * Invoke MODE #channel b on channel to retrieve channel's banlist
     *
     */ 
    private void invokeBanList()
    {
	/* clear banlist */
	banList.clear();
	Log.debug(this, "sending 'MODE "+channelName+" b' to server");
	serverConnection.write("MODE "+channelName+" b\n");
    }

    /**
     * Callback function for processing WHO reply
     *
     * @param message WHO message
     */
    public void processWhoReply(IrcMessage message)
    {
	String arguments[] = message.getArguments();
	String name = arguments[5];
	String hostString = arguments[2] + "@" + arguments[3];
	boolean isVoice = arguments[6].equals("H+");
	boolean isOp = arguments[6].equals("H@");
	Host host = new Host(name + "!" + hostString);

	Nick nick = new Nick(host, isOp, isVoice);
	nickList.add(nick);
    }
}
















