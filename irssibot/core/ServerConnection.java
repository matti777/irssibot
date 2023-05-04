/*
 * $Id: ServerConnection.java,v 1.1 2002/11/08 11:14:31 dreami Exp $
 *
 * IrssiBot - An advanced IRC automation ("bot")
 * Copyright (C) 2000-2007 Matti Dahlbom
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

import irssibot.config.*;
import irssibot.util.*;
import irssibot.util.log.Log;
import irssibot.user.*;
import irssibot.protocol.*;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Represents a connection to an IRC server.<P>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.1 $
 */
public class ServerConnection extends Thread
{
    private static String moduleName = "ServerConnection";

    private Core core = null;

    private BufferedInputStream serverIn = null;
    private BufferedReader serverInReader = null;
    private BufferedOutputStream serverOut = null;
    private Socket socket = null;
    private String errorMsg = null;
    private String statusString = null;

    private boolean connectionAlive = false;
    private boolean continueConnecting = true;

    private ServerInstanceData instanceData = null;
    private Vector users = null;
    private Vector channelConnects = null;
    private Hashtable channels = null;
    private Host botHost = null;
    private OutputQueue outputQueue = null;

    private String serverUserModes = null;
    private String serverChannelModes = null;

    private String currentServer = null;
    private int currentServerIndex = 0;
    private boolean useAltNick = false;
    private long lastSaveTime = 0;
    private boolean userDataChanged = false;
    private boolean connectionReady = false;

    private long nickRegainTime = 0;
    private long channelRejoinTime = 0;

    /**
     * Number of bytes read from the server
     */
    private long bytesRead = 0;
    /**
     * Number of bytes written to server
     */
    private long bytesWritten = 0;

    /**
     * Returns the allowed channel modes for the current server.
     *
     * @return possible channel modes for the server
     */
    public String getServerChannelModes() { return serverChannelModes; }

    /**
     * Constructs.<P>
     *
     * @param instanceData instance data from configuration file
     * @param core reference to bot core object
     */
    public ServerConnection(ServerInstanceData instanceData, Core core)
    {
	super(instanceData.getNetwork());

	this.core = core;
	Log.debug(this, "constructed (\""+instanceData.getNetwork()+"\")");

	this.instanceData = instanceData;
	this.channels = instanceData.getChannels();

	channelConnects = new Vector();

	// parse user file 
	UserFileParser parser = new UserFileParser(instanceData.getUserFilePath());
	if( parser.parse() ) {
	    users = parser.getUsers();
	} else {
	    users = new Vector();
	}

	lastSaveTime = System.currentTimeMillis();
    }

    public String toString() { return moduleName; }
    public Vector getUsers() { return users; }
    public ServerInstanceData getInstanceData() { return instanceData; }
    public String getConnectionStatus() { return statusString; }
    public Hashtable getChannels() { return channels; }
    public Host getHost() { return botHost; }
    public Vector getChannelConnects() { return channelConnects; }
    public ModuleHandler getModuleHandler() { return core.getModuleHandler(); }
    public OutputQueue getOutputQueue() { return outputQueue; }

    public void addUser(User user)
    {
	users.add(user);
    }

    /**
     * Returns an info string describing server traffic statistics.<P>
     *
     * @return server traffic statistics
     */
    public String getTrafficInfo() {
	return new String("Bytes sent to server: " + bytesWritten + 
			  " Bytes received from server: " + bytesRead);
    }

    /**
     * Deletes a user from bots user record.<P>
     *
     * @param user user to delete
     * @return true if succesful
     */
    public boolean delUser(User user) 
    {
	boolean ret = false;

	if( (user != null) && users.contains(user) ) {
	    ret = users.remove(user);
	}
	
	return ret;
    }

    /**
     * Broadcasts a message to all channels.<P>
     *
     * @param message message to broadcast
     */
    public void channelBroadcast(String message) 
    {
	Enumeration en = channels.elements();
	while( en.hasMoreElements() ) {
	    Channel channel = (Channel)en.nextElement();
	    write("PRIVMSG " + channel.getChannelName() + " :[BROADCAST] " + message + "\n");
	}
    }

    /**
     * Adds a channel connect.<P>
     *
     * @param sourceChannel channel to forward from
     * @param destinationNetwork network (ServerConnection) to forward to
     * @param destinationChannel channel on destination network to forward to
     * @return null if successful, otherwise error message
     */
    public String addChannelConnect(String sourceChannel,ServerConnection destinationNetwork,
				    String destinationChannel)
    {
	if( !channels.containsKey(sourceChannel) )
	    return new String("invalid source channel");

	if( !destinationNetwork.getChannels().containsKey(destinationChannel) )
	    return new String("invalid destination channel");

	channelConnects.add(new ChannelConnect(sourceChannel, destinationNetwork, 
					       destinationChannel));

	return null;
    }

    /**
     * Removes all channel connects
     *
     */
    public void removeChannelConnects()
    {
	channelConnects.clear();
    }

    /**
     * Finds a User by exact (in-casesensitive) name.<P>
     * 
     * @param name name of user as specified in config file / bots user record.
     * @return User object, or null if no such user for bot.
     */    
    public User findUser(String name)
    {
	// go through all users 
	for( int i = 0; i < users.size(); i++ ) {
	    User user = (User)users.elementAt(i);
	    if( user.getName().equalsIgnoreCase(name) ) {
		return user;
	    }
	}

	return null;
    }

    /**
     * Finds a User by host. user must be logged in.
     *
     * @param host host 
     * @return User object, or null if not found or not logged in.
     * @see irssibot.user.User#isLoggedIn()
     */
    public User findUser(Host host)
    {
	Vector hosts = null;

	for( int i = 0; i < users.size(); i++ ) {
	    User user = (User)users.elementAt(i);
	    if( user.hasMatchingHost(host) && user.isLoggedIn() ) {
		if( user.isDynamic() ) {
		    if( host.equals(user.getLoginHost()) ) return user;
		} else {
		    return user;
		}
	    }
	}
	return null;
    }

    /**
     * finds a user matching given host in bot's user record. need not
     * be logged in. the returned User should not be used in any action
     * requiring login, but merely in inspection.
     *
     * @param host host 
     * @return User
     * @see irssibot.user.User#isLoggedIn
     */
    private User findMatchingUser(Host host)
    {
	Vector hosts = null;

	/* go through all users */
	for( int i = 0; i < users.size(); i++ ) {
	    User user = (User)users.elementAt(i);
	    hosts = user.getHosts();
	    /* go through all user's hosts */
	    for( int j = 0; j < hosts.size(); j++ ) {
		Host userHost = (Host)hosts.elementAt(j);
		if( host.matches(userHost) ) {
		    return user;
		}
	    }
	}
	return null;
    }

    /**
     * finds a Channel matching given name or null if not found
     *
     * @param name name of channel
     * @return Channel/null
     */
    public Channel findChannel(String name)
    {
      if( name != null ) {
	return (Channel)channels.get(name.toLowerCase());
      } else {
	Log.error(this, "findChannel(): name == null!");
	return null;
      }
    }

    /**
     * Attempts to connect to an irc server.
     *
     * @param server address in form of ip:port:password
     * if no port/password given, using defaults 6667/null.
     */
    private void connect(String addr)
    {
	boolean isOk = true;
	String ip = null;
	String pass = null;
	int port = 6667;

	Log.debug(this, "connect()");

	/* mark instance as not connected */
	currentServer = null;
	statusString = "not connected";
	connectionReady = false;

	String addrData[] = StringUtil.separate(addr,':');

	if( addrData.length < 1 ) {
	    Log.debug(this, "Bad server address");
	    isOk = false;
	} else {
	    if( addrData.length > 2 ) pass = addrData[2];
	    if( addrData.length > 1 ) {
		try {
		    port = Integer.parseInt(addrData[1]);
		} catch( NumberFormatException ne ) {
		    /* bad port value; die */
		    Log.debug(this, "connect(): invalid port. NumberFormatException: " +
			      ne.getMessage());
		    isOk = false;
		    continueConnecting = false;
		}
	    }
	    ip = addrData[0];
	}

	/* if server address is well-formed, attempt to connect */
	if( isOk ) {
	    /* connect to irc server */
	    currentServer = addr;
	    statusString = "connecting to " + addr;
	    Log.debug(this, statusString);
	    try {
	       InetAddress serverAddr = InetAddress.getByName(ip);
	       if ( (core.getBindIP() != null) && 
		    (core.getBindIP().length() > 0) ) {
		  InetAddress bindAddr = InetAddress.getByName(core.getBindIP());
		  Log.debug(this, "binding to local IP " + 
			    bindAddr.getHostAddress());
		  socket = new Socket(serverAddr, port, bindAddr, 0);
	       } else {
		  socket = new Socket(serverAddr, port);
	       }
	       
		serverIn = new BufferedInputStream(socket.getInputStream());
		serverInReader = new BufferedReader(new InputStreamReader(serverIn));
		serverOut = new BufferedOutputStream(socket.getOutputStream());
		socket.setSoTimeout(1000);
	    } catch( IOException e ) {
		Log.log(this, e);
		
		isOk = false;
		currentServer = null;
	    }
	    
	    if( isOk ) {
		// create & launch an output queue 
		if ( outputQueue != null ) {
		    Log.debug(this, "connect(): killing existing OutputQueue..");
		    outputQueue.killQueue();
		    try {
			outputQueue.join();
		    } catch ( InterruptedException e ) {
			// do nothing
		    }
		    outputQueue = null;
		}
		outputQueue = new OutputQueue(serverOut, instanceData.getOutFlushTime(), 
					      instanceData.getOutMaxBytes());
		outputQueue.start();

		Log.debug(this, "connected, sending client data to server..");

		// send data about bot/client 
		if( (pass != null) && !pass.equals("") ) {
		    write("PASS " + pass + "\n");
		}

		String nickLine = null;
		if( !useAltNick ) {
		    Log.debug(this, "using nick \"" + instanceData.getBotNick() + "\"");
		    nickLine = "NICK " + instanceData.getBotNick();
		} else {
		    Log.debug(this, "using alt nick "+instanceData.getBotAltNick() + "\"");
		    nickLine = "NICK " + instanceData.getBotAltNick();
		}

		write(nickLine + "\n");
		Log.info(this, "connect(): wrote: " + nickLine);

		String userLine = 
		    "USER " + instanceData.getIdent() + " hut " + ip + 
		    " :" + instanceData.getRealName();
		write(userLine + "\n");
		Log.info(this, "connect(): wrote: " + userLine);

		connectionAlive = true;
		currentServer = addr;
	    } 
	} 
    } 

    /**
     * Sets the ServerConnection's status string.
     *
     * @param status New status string
     */
    private void setStatusString(String statusString) 
    {
	this.statusString = statusString;
    }

    /**
     * Handle RPL_USERHOST reply message: 
     *
     * <ul>
     *     <li>Finish up connecting
     *     <li>Parse bot host from message
     *     <li>Join channels
     * </ul>
     *
     * @param message the RPL_USERHOST message
     */
    private void doUserHost(IrcMessage message)
    {
	setStatusString("connected to " + currentServer);

	String trailing = message.getTrailing();
	if ( trailing == null ) {
	    Log.error(this, "doUserHost(): trailing == null in " + message.toString());
	    return;
	}

	// parse bot host 
	int equalsIndex = trailing.indexOf('=');
	String parsedNick = trailing.substring(0, equalsIndex);
	
	// remove possible preceding '*' 
	if( parsedNick.endsWith("*") ) {
	    parsedNick = parsedNick.substring(0, (parsedNick.length() - 1));
	}

	// check that we are talking about the bot itself 
	String arguments[] = message.getArguments();
	if( !parsedNick.equalsIgnoreCase(arguments[0]) ) {
	  Log.info(this, "doUserHost(): RPL_USERHOST for other than bot: " + 
		   parsedNick + " - " + 
		   arguments[0]);
	  return;
	} else {
	  Log.debug(this, "doUserhost(): RPL_USERHOST for bot");
	}

	String parsedHost = trailing.substring(equalsIndex + 2);
	
	botHost = new Host(parsedNick + "!" + parsedHost);

	// ensure we get actual hostname and not IP (EFNet hack) 
	try {
	    InetAddress address = InetAddress.getByName(botHost.getHost());
	    botHost.setHost(address.getHostName());
	} catch ( UnknownHostException e ) {
	    Log.log(this, e);
	}
	    
	Log.debug(this, "bot host from RPL_USERHOST: " + botHost.toString());

	// join channels 
	Enumeration en = instanceData.getChannels().elements();
	while( en.hasMoreElements() ) {
	    Channel channel = (Channel)en.nextElement();
	    channel.setServerConnection(this);
	    channel.setServerChannelModes(serverChannelModes);

	    Log.debug(this, "joining channel "+channel.getChannelName()+"..");
	    if( channel.getChannelKey() != null ) {
		write("JOIN " + channel.getChannelName() + " " + 
		      channel.getChannelKey() + "\n");
	    } else {
		write("JOIN " + channel.getChannelName() + "\n");
	    }
	}
	connectionReady = true;	
    }

    /**
     * Returns XML representation of this server connection to be 
     * written to configuration file.
     *
     * @return XML representing this server instance 
     */
    public String getXML()
    {
	String ret = "";

	ret += "  <server-instance network=\""+instanceData.getNetwork()+"\">\n";
	ret += "    <bot-info nick=\"" + instanceData.getBotNick() + "\"\n" +
	    "              altnick=\"" + instanceData.getBotAltNick() + "\"\n" +
	    "              ident=\"" + instanceData.getIdent() + "\"\n" +
	    "              realname=\""+instanceData.getRealName()+"\" />\n";
	ret += "    <output flush-time-ms=\"" + instanceData.getOutFlushTime() + 
	  "\" max-output-bytes=\"" + instanceData.getOutMaxBytes() + "\" />\n";
	ret += "    <user-file path=\""+instanceData.getUserFilePath()+"\" />\n";
	ret += "    <server-list>\n";
	
	Vector v = instanceData.getServerList();
	for( int i = 0; i < v.size(); i++ ) {
	    String addr = (String)v.elementAt(i);
	    ret += "      <address>"+addr+"</address>\n";
	}

	ret += "    </server-list>\n";
	ret += "    <channel-list>\n";
	
	Enumeration keys = channels.keys();
	while( keys.hasMoreElements() ) {
	    String key = (String)keys.nextElement();
	    Channel channel = (Channel)channels.get(key);
	    ret += "      <channel name=\""+channel.getChannelName()+"\" key=\"";
	    if( channel.getChannelKey() != null ) {
		ret += channel.getChannelKey();
	    }
	    String fmodes = "";
	    if( channel.getPosModes() != null ) {
		fmodes += "+" + channel.getPosModes();
	    }
	    if( channel.getNegModes() != null ) {
		fmodes += "-" + channel.getNegModes();
	    }
	    ret += "\" forcedmodes=\"" + fmodes + "\" />\n";
	}
	ret += "    </channel-list>\n";
	ret += "  </server-instance>\n";

	return ret;
    }

    /**
     * run()
     * 
     */
    public void run()
    {
	String msg = null;
	int msgLen = 0;

	currentServerIndex = 0;

	while( continueConnecting ) {
	    connectionAlive = false;

	    while( !connectionAlive ) {
		String address = 
		    (String)instanceData.getServerList().elementAt(currentServerIndex++);
		if( currentServerIndex >= instanceData.getServerList().size() ) {
		    currentServerIndex = 0;
		}

		// attempt connecting
		connect(address);
	    }

	    // init timer variables 
	    nickRegainTime = System.currentTimeMillis();
	    channelRejoinTime = System.currentTimeMillis();

	    // the main loop 
	    while( continueConnecting && connectionAlive ) {
		msg = null;

		// read server messages 
		try {
		    while ( msg == null ) {
			try {
			    msg = serverInReader.readLine();
			} catch ( InterruptedIOException e ) {
			    // dont care
			}
		    }

		    // log server message 
		    Log.server(msg);

		    bytesRead += msg.length();

		    IrcMessage message = IrcMessage.parse(msg);
		    
		    // process message from server 
		    processServerMessage(message);
		} catch( IOException e ) {
		    // connection closed
		    continueConnecting = false;
		    connectionAlive = false;
		    Log.log(this, e);
		    break;
		}

		/* check user file write -timer event */
		long now = System.currentTimeMillis();
		if( (now - lastSaveTime) > 300000 ) {
		  /* write user file && prompt modulehandler to save module states */
		  writeUserFile();
		  core.saveModuleStates(false);

		    lastSaveTime = now;

		    /* force finalization and garbage collect */
		    System.runFinalization();
		    System.gc();
		}	

		/* handle misc timed events */
		if( connectionReady && continueConnecting ) {
		    /* every 15 seconds, attempt to regain bot nick */
		    if( (now - nickRegainTime) >= 15000 ) {
			if( !instanceData.getBotNick().equalsIgnoreCase(botHost.getNick()) ) {
			    write("NICK "+instanceData.getBotNick()+"\n");
			}
			nickRegainTime = now;
		    }
		    
		    /* every 15 seconds, attempt to rejoin channels if not on them */
		    if( (now - channelRejoinTime) >= 15000 ) {
			Enumeration elements = channels.elements();
			while( elements.hasMoreElements() ) {
			    Channel channel = (Channel)elements.nextElement();
			    if( !channel.isJoined() ) {
				if( channel.getChannelKey() == null ) {
				    Log.debug(this, "run(): attempting to rejoin channel " +
					      channel.getChannelName());
				    write("JOIN "+channel.getChannelName()+"\n");
				} else {
				    Log.debug(this, "run(): attempting to rejoin channel " +
					      channel.getChannelName() +
					      " with key " + channel.getChannelKey());
				    write("JOIN " + channel.getChannelName() + " " + 
					  channel.getChannelKey()+"\n");
				}
			    }
			}
			channelRejoinTime = now;
		    }
		}
	    } // while( continueConnecting && connectionAlive ) {
	    
	    // thread dying - close socket and clean up 
	    try { 
		Log.debug(this, "run(): closing connection");
		serverIn.close();
		serverOut.close();
		socket.close(); 

		serverIn = null; 
		serverOut = null; 
	    } catch( IOException e ) { 
		Log.log(this, e);
	    }	
	} // while ( continueConnection ) {

	if( errorMsg != null ) {
	    Log.debug(this, "ERROR: " + errorMsg);
	}

	Log.debug(this, "Thread \"" + getName() + "\"  exiting..");

	currentServer = null;
	statusString = "not connected";

	outputQueue.killQueue();
	outputQueue = null;
    }

    /**
     * Processes message from server.<P>
     * 
     * @param message message to be processed 
     */
    private void processServerMessage(IrcMessage message)
    { 
	Channel channel = null;
	String command = message.getCommand();
	String arguments[] = message.getArguments();
	String trailing = message.getTrailing();

	if( command.equals("PRIVMSG") ) {
	    processPrivmsg(message);

	    core.getModuleHandler().forwardMessage(message, this);
	} else if( command.equals("ERROR") ) {
	    // ERROR: close connection 
	    connectionAlive = false;
	    continueConnecting = true;
	    errorMsg = trailing;
	} else if( command.equals("PING") ) {
	    write("PONG " + message.getTrailing() + "\n");
	} else if( command.equals("MODE") ) {
	    if( arguments[0] != null ) {
		channel = findChannel(arguments[0]);
		
		if( channel != null ) {
		    channel.onMode(message);
		}
	    }

	    core.getModuleHandler().forwardMessage(message, this);
	} else if( command.equals("JOIN") ) {
	    String chanName = null;
	    if( trailing != null )
		chanName = trailing;
	    else 
		chanName = arguments[0].toLowerCase();

	    channel = findChannel(chanName);

	    // if channel not found, create new one 
	    if( channel == null ) {
		channel = new Channel(chanName,null,null,this);
		channels.put(chanName,channel);
	    }

	    // Channel.onJoin() handles sending to modules 
	    channel.onJoin(message);
	} else if( command.equals(Irc.RPL_CHANNELMODEIS) ) {
	    // channel MODE reply. send to correct Channel 
	    channel = findChannel(arguments[1]);

	    if( channel != null ) {
		channel.onChannelModeIs(message);
	    } 
	} else if( command.equals(Irc.RPL_WHOREPLY) ) {
	    // WHO reply. send to correct Channel 
	    channel = findChannel(arguments[1]);

	    if( channel != null ) {
		channel.processWhoReply(message);
	    } else {
		Log.error(this, "processServerMessage(): channel == null in WHO reply " +
			  message.toString());
	    }
	} else if( command.equals("PART") ) {
	    // PART, send to correct Channel
	    channel = findChannel(arguments[0]);
	    if( channel != null ) {
		channel.onPart(message);

		core.getModuleHandler().forwardMessage(message, this);
	    } else {
		Log.debug(this, "NULL channel: " + arguments[0]);
	    }

	} else if( command.equals("TOPIC") ) {
	    channel = findChannel(arguments[0]);
	    channel.onTopic(message);

	    core.getModuleHandler().forwardMessage(message, this);
	} else if( command.equals("KICK") ) {
	    channel = findChannel(arguments[0]);
	    channel.onKick(message);

	    core.getModuleHandler().forwardMessage(message, this);
	} else if( command.equals("QUIT") ) {
	    processQuit(message);

	    core.getModuleHandler().forwardMessage(message, this);
	} else if( command.equals("NICK") ) {
	    processNick(message);

	    core.getModuleHandler().forwardMessage(message, this);
	} else if( command.equals(Irc.RPL_TOPIC) ) {
	    channel = findChannel(arguments[1]);
	    channel.onTopicMsg(message);
	} else if( command.equals(Irc.RPL_BANLIST) ) {
	    channel = findChannel(arguments[1]);
	    channel.onBanListMsg(message);
	} else if( command.equals(Irc.RPL_ENDOFBANLIST) ) {
	    channel = findChannel(arguments[1]);
	    channel.onEndOfBanListMsg(message);
	} else if( command.equals(Irc.RPL_ENDOFWHO) ) {
	    channel = findChannel(arguments[1]);
	    channel.onEndOfWhoMsg(message);
	} else if( command.equals(Irc.ERR_NICKNAMEINUSE)    || 
		   command.equals(Irc.ERR_ERRONEUSNICKNAME) || 
		   command.equals(Irc.ERR_UNAVAILRESOURCE) ) {
	    useAltNick = !useAltNick;
	} else if( command.equals(Irc.RPL_MYINFO) ) {
	    // parse allowed user/channel modes out of 004:
	    // :irc2.fi.quakenet.eu.org 004 TuneX server u2.10 dioswkgX biklmnopstv
	    serverUserModes = arguments[3];
	    serverChannelModes = "";

	    // filter out modes o,b,v,k 
	    for( int i = 0; i < arguments[4].length(); i++ ) {
		if( arguments[4].charAt(i) != 'o' &&
		    arguments[4].charAt(i) != 'v' &&
		    arguments[4].charAt(i) != 'k' &&
		    arguments[4].charAt(i) != 'b' ) {
		    serverChannelModes += arguments[4].charAt(i);
		}
	    }
	} else if( command.equals(Irc.RPL_WELCOME) ) {
	    // server WELCOME; send USERHOST <botnick> 
	    Log.debug(this, "sending 'USERHOST " + arguments[0] + "'");
	    write("USERHOST " + arguments[0] + "\n");
	} else if( command.equals(Irc.RPL_USERHOST) ) {
	    doUserHost(message);
	}
    }

    /**
     * Processes PRIVMSGs to either a channel or to bot himself.
     *
     * @param message message
     */
    private void processPrivmsg(IrcMessage message)
    {
	String source = null;
	boolean isCoreCall = false;
	String prefix = message.getPrefix();
	String trailing = message.getTrailing();
	String arguments[] = message.getArguments();

	Host host = new Host(prefix);

	if( trailing.startsWith("VERSION") ) {
	    // CTCP VERSION 
	    String nick = prefix.substring(0, prefix.indexOf('!'));
	    write("NOTICE " + nick + " :VERSION " + Core.botVersion + "\n");
	} else {
	    // look for source (message to channel or to bot) 
	    if( arguments[0].equals(botHost.getNick()) ) {
		source = host.getNick();
		if( trailing.startsWith("core->") ) {
		    isCoreCall = true;
		}
	    } else {
		source = arguments[0];
		if( trailing.startsWith("!core->") ) {
		    // alter message so that preceding ! is removed 
		    trailing = trailing.substring(1);
		    isCoreCall = true;
		}
	    }
	    
	    // look for core calls 
	    if( isCoreCall ) {
		// has to be global admin to call core 
		User user = findUser(host);
		if( (user != null) && user.isGlobalAdmin() ) {
		    String ret = core.processCoreCall(message, trailing, this, source);
		    if( ret != null ) {
			write("PRIVMSG " + source + " :core replied: " + ret + "\n");
		    }
		}
	    } else {
		// other than core call
		if( arguments[0].equals(botHost.getNick()) ) {
		    // handle special commands login, logout 
		    String args[] = StringUtil.separate(trailing, ' ');
		    if( (args != null) && (args.length >= 2) ) {
			if( args[0].equals("login") ) {
			    User user = findMatchingUser(host);
			    if( user != null ) {
				// look for password 
				if( args[1].equals(user.getPassword()) ) {
				    user.doLogin(host);
				    write("PRIVMSG " + source + " :Logged in as " + 
					  user.getLoginHost().toString() +
					  ". Login will expire in 60 minutes.\n");
				    doMaintain();
				    Log.debug(this, "login ok for " + user.getName());
				} else {
				    Log.debug(this, "login failed for " + user.getName());
				}
			    }
			} else if( args[0].equals("logout") ) {
			    User user = findMatchingUser(host);
			    if( user != null ) {
				// look for password 
				if( args[1].equals(user.getPassword()) ) {
				    user.doLogout();
				    write("PRIVMSG " + source + " :Logged out.\n");
				} else {
				    Log.debug(this, "logout failed: " +
					      args[1] + " " + user.getPassword());
				}
			    }
			}
		    }
		} else {
		    // forward to all channel connects 
		    for( int i = 0; i < channelConnects.size(); i++ ) {
			ChannelConnect connect = (ChannelConnect)channelConnects.elementAt(i);
			if( arguments[0].equals(connect.getSourceChannel()) ) {
			    ServerConnection dest = connect.getDestinationNetwork();
			    Hashtable h = dest.getChannels();
			    Channel chan = (Channel)h.get(connect.getDestinationChannel());
			    if( chan.isJoined() ) {
				String msg = "<" + host.getNick() + "> " + trailing;
				dest.write("PRIVMSG " + 
					   connect.getDestinationChannel() + 
					   " :" + msg + "\n");
			    }
			}
		    }
		}
	    }
	}
    }

    /**
     * Forwards a NICK message to all channels and handles changes 
     * to own nick.<P>
     *
     * @param message message to send
     */
    private void processNick(IrcMessage message)
    {
	Host host = new Host(message.getPrefix());
	
	// see if self changing nick 
	if( botHost.getNick().equals(host.getNick()) ) {
	    Log.debug(this, "changed nick to " + message.getTrailing());
	    botHost.setNick(message.getTrailing());
	} 

	Enumeration en = channels.elements();
	while( en.hasMoreElements() ) {
	    Channel channel = (Channel)en.nextElement();
	    // if nick is on channel, invoke onNick() 
	    if( channel.findNick(host.getNick()) != null ) {
		channel.onNick(message);
	    }
	}
    }

    /**
     * Forwards a QUIT message to all channels.<P>
     *
     * @param message message to send
     */
    private void processQuit(IrcMessage message)
    {
	Enumeration en = channels.elements();
	while( en.hasMoreElements() ) {
	    Channel channel = (Channel)en.nextElement();
	    if( channel.isJoined() ) {
		channel.onQuit(message);
	    }
	}
    }

    /**
     * Adds a channel to bots channels hash and makes bot join the channel.<P>
     *
     * @param channelName name of channel
     * @param channelKey channel key to use
     * @return true if successful, false if channel already define for this server connection
     */
    public boolean addChannel(String channelName, String channelKey)
    {
	boolean ret = false;
	
	// check channel name 
	if( channelName.charAt(0) == '#' ) {
	    if( !channels.containsKey(channelName.toLowerCase()) ) {
		Channel channel = new Channel(channelName, channelKey, null, this);
		if( channelKey != null ) 
		    write("JOIN "+channel.getChannelName()+" "+channel.getChannelKey()+"\n");
		else
		    write("JOIN "+channel.getChannelName()+"\n");
		
		channels.put(channel.getChannelName(),channel);
		ret = true;
	    }
	}
	return ret;
    }

    /**
     * Removes a named channel from bots channels hash and makes the
     * bot leave the channel.<P>
     *
     * @param channel Channel object of channel to remove
     * @return true on success, false if no such channel was found
     */
    public boolean delChannel(Channel channel)
    {
	boolean ret = false;
	
	if( channels.containsKey(channel.getChannelName()) ) {
	    write("PART "+channel.getChannelName()+"\n");;
	    channels.remove(channel.getChannelName());
	    ret = true;
	}
	return ret;
    }

    /**
     * QUIT from IRC server and finish execution.
     * 
     * @param quitMessage message to send to IRC server as quit message
     */
    public void quit(String quitMessage)
    {
	outputQueue.priorityOutput("QUIT :" + quitMessage + "\n");

	continueConnecting = false;
	connectionAlive = false;
    }

    /**
     * Calls doMaintain() for all channels
     *
     */
    public void doMaintain() 
    {
	Enumeration en = instanceData.getChannels().elements();
	while( en.hasMoreElements() ) {
	    Channel channel = (Channel)en.nextElement();
	    channel.doMaintain();
	}
    }

    /**
     * Notifies ServerConnection that its user data has
     * been changed and it should rewrite the user file soon.
     *
     */
    public void notifyUserDataChanged()
    {
	userDataChanged = true;
	doMaintain();
    }

    /**
     * Writes user file to disk.
     *
     */
    public void writeUserFile()
    {
	String ret = null;
	boolean isOk = true;
	FileWriter fileWriter = null;

	if( userDataChanged ) {
	    Log.debug(this, "writing userfile..");

	    ret = "<users-file>\n";
	    for( int i = 0; i < users.size(); i++ ) {
		User user = (User)users.elementAt(i);
		ret += user.getXML()+"\n";
	    }
	    ret += "</users-file>\n";
	    
	    File            destFile;
	    FileWriter      destFileWriter;
	    
	    destFile = new File(instanceData.getUserFilePath());
	
	    try { 
		destFileWriter = new FileWriter(destFile);
		destFileWriter.write(ret,0,(int)ret.length());
		destFileWriter.flush();
		destFileWriter.close();
	    } catch( IOException e ) {
		e.printStackTrace();
		isOk = false;
	    }
	    userDataChanged = false;
	}
    }	

    /**
     * Write bytes/string to server. This function will be replaced by a bursting
     * one to avoid excess flood.<P>
     *
     * @param str string to write
     */
    synchronized public void write(String str)
    {
	if( (str != null) && (serverOut != null) ) {
	    outputQueue.output(str);

	    bytesWritten += str.length();
	} else {
	    Log.error(this, "write(): str = " + str + ", serverOut = " + serverOut);
	}
    } 
}








