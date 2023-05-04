/*
 * $Id: ChannelTools.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
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
package irssibot.modules;

import irssibot.core.*;
import irssibot.protocol.*;
import irssibot.user.*;
import irssibot.util.StringUtil;
import irssibot.util.TimerCommand;
import irssibot.util.Timer;
import irssibot.util.log.Log;

import java.util.*;
import java.net.*;

/**
 * This module has basic functionality for maintaining/protecting 
 * IRC channels from privmsg/join/nick flood, clone attacks,
 * massdeops and such.<P>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $
 */
public class ChannelTools extends AbstractModule
{
    private static String moduleInfo = "Channel Tools $Revision: 1.2 $ for IrssiBot";

    /**
     * This Hashtable contains Vector objects containing banmasks as String.
     * The keys are of form "network-#channel" for each channel.
     */
    private Hashtable permbanList = null;
    /**
     * This Hashtable contains a ChanVars object for each channel.
     * The keys are of form "network-#channel" for each channel.
     */
    private Hashtable chanvars = null;
    private boolean changed = false;

    private Host host = null;
    private String source = null;
    private ServerConnection caller = null;

  /**
   * Default constructor
   *
   */
  public ChannelTools() {
    super(ChannelTools.class.getName());
  }

  /**
   * Returns channel vars for given channel on the current calling 
   * <code>ServerConnection</code>.<P>
   *
   * @param channelName name of channel to retrieve chanvars for
   * @return chanvars for channel. if not found, return default values.
   */
  private ChanVars getVars(String channelName) {
    String key = caller.getInstanceData().getNetwork() + "-" + channelName;
    ChanVars vars = (ChanVars)chanvars.get(key);
    if ( vars == null ) {
      Log.debug(this, "getVars(): creating empty chanvars for " + key);
      vars = new ChanVars();
    }
    
    return vars;
  }

    /**
     * Returns state.<P>
     *
     * @return state
     */
    public Properties getModuleState()
    {
	Properties props = null;
	String key = null;
	Vector v = null;
	String mask = null;

	if( changed ) {
	    props = new Properties();

	    // add permbans to state 
	    Enumeration keys = permbanList.keys();
	    while( keys.hasMoreElements() ) {
		key = (String)keys.nextElement();
		v = (Vector)permbanList.get(key);

		for( int i = 0; i < v.size(); i++ ) {
		    mask = (String)v.elementAt(i);
		    props.setProperty(key+"-permban"+i,mask);
		}
	    }

	    // add chanvars & floodvars to state 
	    keys = chanvars.keys();
	    while( keys.hasMoreElements() ) {
		key = (String)keys.nextElement();
		ChanVars vars = (ChanVars)chanvars.get(key);

		// chanvars
		String chanvars = vars.getBanTime() + " " + 
		    vars.getRejoinBanTime() + " " + 
		    ((vars.getEnforceResolving()) ? "1" : "0") + " " +
		    ((vars.getEnforceOpList()) ? "1" : "0");
		    
		props.setProperty(key + "-chanvars", chanvars);

		// floodvars
		String floodvars = vars.getJoinFlushInterval() + " " +
		    vars.getMaxCloneCount();
		props.setProperty(key + "-floodvars", floodvars);
	    }

	    changed = false;
	} 
	
	return props;
    }

    private void loadInitialState(Properties state, Core core) 
    {
	Vector instances = core.getServerInstances();	
	permbanList = new Hashtable();

	/* go through the server connections */
	for( int i = 0; i < instances.size(); i++ ) {
	    ServerConnection connection = (ServerConnection)instances.elementAt(i);
	    Enumeration channels = connection.getChannels().elements();

	    /* go through channels for this server connection */
	    while( channels.hasMoreElements() ) {
		Channel channel = (Channel)channels.nextElement();
		String key = connection.getInstanceData().getNetwork() + "-" + 
		    channel.getChannelName();

		Vector v = new Vector();

		/* go through list for bans matching current connection/channel */
		Enumeration bans = state.keys();
		while( bans.hasMoreElements() ) {
		    String maskKey = (String)bans.nextElement();
		    if( maskKey.startsWith(key+"-permban") ) {
			v.add(state.getProperty(maskKey));
		    }
		}

		// add permbans to hash 
		if( v.size() > 0 ) {
		    permbanList.put(key,v);
		}

		// parse chanvars 
		String varstr = state.getProperty(key+"-chanvars");
		ChanVars vars = ChanVars.parseFromChanvars(new ChanVars(), varstr);

		// parse floodvars
		varstr = state.getProperty(key + "-floodvars");
		vars = ChanVars.parseFromFloodvars(vars, varstr);

		chanvars.put(key,vars);
	    }
	}
    }

  /**
   * Returns a module info string 
   *
   * @return module info string
   */
  public String getModuleInfo() {
    return moduleInfo;
  }

  /**
   * Called upon loading the module.<P>
   *
   * @param state initial module state
   * @param core reference to core
   */
  public boolean onLoad(Properties state, Core core) {
    permbanList = new Hashtable();
    chanvars = new Hashtable();
    
    if ( state != null ) {
      loadInitialState(state,core);
    }
    
    return true;
  }

  /**
   * Called upon unloading the module.<P>
   */
  public void onUnload() {
    permbanList = null;
  }
  
  /**
   * Kicks a user from a given channel.<P>
   *
   * @param host Host of invoker
   * @param invoker User object of invoker
   * @param args command arguments
   * @param channel Channel where command takes place
   */
  private void commandK(Host host, User invoker, String args[], Channel channel) {
    String chanName = channel.getChannelName();
    if ( channel.isOp() && (args != null) && (args.length >= 1) &&
	 (invoker.isGlobalAdmin() || invoker.isChanAdmin(chanName) || 
	  invoker.isOp(chanName)) ) {
      Nick nick = channel.findNick(args[0]);

      // never kick self
      if( (nick != null) && !nick.getHost().equals(caller.getHost()) ) {
	User user = caller.findUser(nick.getHost());

	// dont kick channel admins/ops except if kicker is admin
	if ( (user == null) || 
	     !(user.isGlobalAdmin() || 
	       user.isChanAdmin(chanName) || 
	       user.isOp(chanName)) ||
	     (invoker.isGlobalAdmin() || invoker.isChanAdmin(chanName)) ) {
	  String kickMsg = StringUtil.join(args, 1);
	  if( kickMsg == null ) {
	    caller.write("KICK " + chanName + " " + 
			 nick.getHost().getNick() + "\n");
	  } else {
	    caller.write("KICK " + chanName + " " + 
			 nick.getHost().getNick() + " :" + kickMsg + "\n");
	  }
	}
      }
    }
  }

  /**
   * Bans & kicks a user from a given channel.<P>
   *
   * @param host Host of invoker
   * @param invoker User object of invoker
   * @param args command arguments
   * @param channel Channel where command takes place
   */
  private void commandBK(Host host,User invoker, String args[], Channel channel) {
    String chanName = channel.getChannelName();

    if ( channel.isOp() && (args != null) && (args.length >= 1)) {
      if ( invoker.isGlobalAdmin() || invoker.isChanAdmin(chanName) || 
	  invoker.isOp(chanName) ) {

	// find the Nick to be kicked 
	Nick nick = channel.findNick(args[0]);
	if( (nick != null) && !nick.getHost().equals(caller.getHost()) ) {
	  User user = caller.findUser(nick.getHost());

	  // dont kick channel admins/ops unless kicker is admin
	  if ( (user == null) || 
	      !(user.isGlobalAdmin() || 
		user.isChanAdmin(channel.getChannelName()) || 
		user.isOp(channel.getChannelName())) ||
	      (invoker.isGlobalAdmin() || invoker.isChanAdmin(chanName)) ) {
	    String kickMsg = StringUtil.join(args,1);
	    String banMask = "*!" + nick.getHost().getIdent() + "@" +
	      nick.getHost().getHost();
	    
	    caller.write("MODE " + chanName + " +b " +
			 banMask + "\n");
	    if( kickMsg == null ) {
	      caller.write("KICK " + chanName + " " +
			   nick.getHost().getNick()+"\n");
	    } else {
	      caller.write("KICK " + chanName + " " +
			   nick.getHost().getNick() + " :" + kickMsg + "\n");
	    }
	    
	    // set timer for unban
	    int bantime = getVars(channel.getChannelName()).getBanTime();
	    Timer.deploy(bantime * 60, 
			 new BanHandler(chanName, banMask, caller));
	  }
	}
      }
    }
  }
  
    /**
     * Processes add-commands for this module
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandAdd(Host host,User invoker,String args[],Channel channel)
    {
	/* all commands require at least channel op */
	if( (args != null) && (args.length > 0) && 
	    (invoker.isGlobalAdmin() || invoker.isChanAdmin(channel.getChannelName()) ||
	     invoker.isOp(channel.getChannelName())) ) {
	    /* select subcommand */
	    if( args[0].equals("permban") && (args.length >= 2) ) {
		String key = caller.getInstanceData().getNetwork()+"-"+channel.getChannelName();
		Vector v = (Vector)permbanList.get(key);
		if( v == null ) { v = new Vector(); }
		
		String banMask = args[1].trim().toLowerCase();

		/* if nick part missing, add manually */
		if( banMask.indexOf('!') == -1 ) {
		    banMask = "*!"+banMask;
		}

		if( v.contains(banMask) ) {
		    write("Host " + banMask + " is already on my permban list for " + 
			  channel.getChannelName() + ".");
		} else {
		    v.add(banMask);
		    permbanList.put(key,v);
		    changed = true;

		    caller.write("MODE "+channel.getChannelName()+" +b "+banMask+"\n");
		    write("Added new permban "+banMask+" to "+channel.getChannelName()+".");
		}
	    } else if( args[0].equals("channel") && (args.length >= 2) ) {
		String channelName = args[1];
		String channelKey = null;

		if( invoker.isGlobalAdmin() ) {
		    if( args.length >= 3 ) {
			channelKey = args[2];
		    }
		    if( caller.addChannel(channelName,channelKey) ) {
			write("Added channel "+channelName.toLowerCase()+".");
		    } else {
			write("Channel "+channelName.toLowerCase()+" already exists.");
		    }
		}
	    }
	}
    }

    /**
     * Processes del-commands for this module
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandDel(Host host,User invoker,String args[],Channel channel)
    {
	/* all commands require at least channel op */
	if( (args != null) && (args.length > 0) && 
	    (invoker.isGlobalAdmin() || invoker.isChanAdmin(channel.getChannelName()) ||
	     invoker.isOp(channel.getChannelName())) ) {
	    /* select subcommand */
	    if( args[0].equals("permban") && (args.length >= 2) ) {
		String key = caller.getInstanceData().getNetwork()+"-"+channel.getChannelName();
		Vector v = (Vector)permbanList.get(key);

		if( v == null ) {
		    v = new Vector();
		}
		
		String banMask = args[1].trim().toLowerCase();
		if( v.remove(banMask) ) {
		    permbanList.put(key,v);
		    changed = true;

		    caller.write("MODE "+channel.getChannelName()+" -b "+banMask+"\n");
		    write("Removed permban "+banMask+" from "+channel.getChannelName()+".");
		}
	    } else if( args[0].equals("channel") && (args.length >= 2) ) {
		if( invoker.isGlobalAdmin() ) {
		    Channel chan = caller.findChannel(args[1]);
		    if( chan != null ) {
			if( caller.delChannel(chan) ) {
			    write("Channel "+chan.getChannelName()+" removed.");
			}
		    }
		}
	    } else if( args[0].equals("chankey") ) {
		channel.setChannelKey(null);
		write("Removed channel key from "+channel.getChannelName()+".");
	    }
	}
    }
 
    /**
     * Processes list-commands for this module
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandList(Host host,User invoker,String args[],Channel channel)
    {
	/* all commands require at least channel op */
	if( (args != null) && (args.length > 0) && 
	    (invoker.isGlobalAdmin() || invoker.isChanAdmin(channel.getChannelName()) ||
	     invoker.isOp(channel.getChannelName())) ) {
	    /* select subcommand */
	    if( args[0].equals("permbans") ) {
		String key = caller.getInstanceData().getNetwork() + "-" + 
		    channel.getChannelName();
		Vector v = (Vector)permbanList.get(key);
		if( v != null ) {
		    for( int i = 0; i < v.size(); i++ ) {
			String banMask = (String)v.elementAt(i);
			write(channel.getChannelName()+" permban: "+banMask);
		    }
		}
	    } else if( args[0].equals("chanvars") ) {
		String key = caller.getInstanceData().getNetwork() + "-" + 
		    channel.getChannelName();		
		ChanVars vars = (ChanVars)chanvars.get(key);
		if( vars != null ) {
		    write(channel.getChannelName()+" chanvars: " + 
			  "bantime=" + vars.getBanTime() + "(min) " + 
			  "rejoin-bantime=" + vars.getRejoinBanTime() + "(min) " + 
			  "enforce-hostname-resolving=" + 
			  ((vars.getEnforceResolving()) ? "1" : "0") + 
			  " enforce-op-list=" + 
			  ((vars.getEnforceOpList() ? "1" : "0")));
		}
	    } else if( args[0].equals("floodvars") ) {
		String key = caller.getInstanceData().getNetwork() + "-" + 
		    channel.getChannelName();		
		ChanVars vars = (ChanVars)chanvars.get(key);
		if( vars != null ) {
		    write(channel.getChannelName() + " floodvars: " + 
			  "join-flood-interval=" + 
			  vars.getJoinFlushInterval() + "(s) " + 
			  "max-clone-count=" + vars.getMaxCloneCount());
		}
	    } else if( args[0].equals("fmodes") ) {
		String ret = "Forced channel modes for " + 
		    channel.getChannelName() + ": ";
		if( channel.getPosModes() != null ) {
		    ret += "+" + channel.getPosModes();
		}
		if( channel.getNegModes() != null ) {
		    ret += "-" + channel.getNegModes();
		}
		write(ret);
	    } else if( args[0].equals("chankey") ) {
		if( channel.getChannelKey() == null ) {
		    write("No channel key set.");
		} else {
		    write("Channel key is: '"+channel.getChannelKey()+"'.");
		}
	    }
	}
    }

    /**
     * Processes edit-commands for this module
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandEdit(Host host, User invoker, String args[], Channel channel)
    {
	ChanVars vars = getVars(channel.getChannelName());

	// all commands require at least channel op 
	if( (args != null) && (args.length > 0) && 
	    (invoker.isGlobalAdmin() || invoker.isChanAdmin(channel.getChannelName()) ||
	     invoker.isOp(channel.getChannelName())) ) {
	    if( args[0].equals("chanvars") ) {
		// need global/chan admin
		if ( invoker.isGlobalAdmin() || 
		     invoker.isChanAdmin(channel.getChannelName()) ) {
		    String key = caller.getInstanceData().getNetwork() + "-" + 
			channel.getChannelName();
		    vars = ChanVars.parseFromChanvars(vars, StringUtil.join(args, 1));
		    chanvars.put(key, vars);
		    write("Edited chanvars for "+channel.getChannelName()+".");
		    changed = true;
		}
	    } else if( args[0].equals("floodvars") ) {
		// need global/chan admin
		if ( invoker.isGlobalAdmin() || 
		     invoker.isChanAdmin(channel.getChannelName()) ) {
		    String key = caller.getInstanceData().getNetwork() + "-" + 
			channel.getChannelName();
		    vars = ChanVars.parseFromFloodvars(vars, StringUtil.join(args, 1));
		    chanvars.put(key, vars);
		    write("Edited floodvars for " + channel.getChannelName() + ".");
		    changed = true;
		}
	    } else if( args[0].equals("chankey") ) {
		if( args.length == 2 && args[1] != null ) {
		    channel.setChannelKey(args[1]);
		    write("Set channel key for " + channel.getChannelName() + 
			  " to '" + args[1] + "'.");
		}
	    } else if( args[0].equals("fmodes") ) {
		if( invoker.isChanAdmin(channel.getChannelName()) || 
		    invoker.isGlobalAdmin() ) {
		    if( args.length > 1 ) {
			channel.parseForcedModes(args[1]);
			channel.doMaintain();

			String ret = "Forced channel modes for " + 
			    channel.getChannelName() + " are now: ";

			if( channel.getPosModes() != null ) {
			    ret += "+" + channel.getPosModes();
			}
			if( channel.getNegModes() != null ) {
			    ret += "-" + channel.getNegModes();
			}
			write(ret);
		    }
		}
	    } else if( args[0].equals("botnick") ) {
	      if( invoker.isGlobalAdmin() ) {
		if( ((args.length == 2) || (args.length == 3)) && args[1] != null && 
		    !args[1].equals("") ) {
		  caller.write("NICK "+args[1]+"\n");
		  caller.getInstanceData().setBotNick(args[1]);
		  
		  String msg = "Set botnick to '"+args[1]+"'";
		  if( args.length == 3 && args[2] != null && !args[2].equals("") ) {
		    caller.getInstanceData().setBotAltNick(args[2]);
		    msg += " and altnick to '"+args[2]+"'";
		  }
		  write(msg);
		}
	      }
	    }
	}
    }

    /**
     * Tries to join channel in args[0] with key in args[1] if given. 
     * The channel must exist in bots record.
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandJoin(Host host,User invoker,String args[],Channel channel)
    {
	/* requires global admin*/
	if( (args != null) && ((args.length == 1) || (args.length == 2)) && 
	    invoker.isGlobalAdmin() ) {
	    Channel chan = caller.findChannel(args[0]);
	    if( chan == null ) {
		write("No such channel "+args[0]+". Use add channel first.");
	    } else {
		if( !chan.isJoined() ) {
		    if( args.length == 2 ) {
			chan.setChannelKey(args[1]);
			write("Set channel key for " + chan.getChannelName() + 
			      " to '" + args[1] + "'.");
			caller.write("JOIN "+chan.getChannelName()+" "+args[1]+"\n");			
		    } else {
			caller.write("JOIN "+chan.getChannelName()+"\n");
		    }
		}
	    }
	}
    }
	    
    /**
     * Makes bot say something on a channel.
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandSay(Host host, User invoker, String args[], Channel channel)
    {
	if ( (args == null) || (args.length < 1) ) {
	    return;
	}
	
	String channelName = channel.getChannelName();
	
	if ( (invoker.isGlobalAdmin() || invoker.isChanAdmin(channelName) ||
	      invoker.isOp(channelName)) ) {
	    caller.write("PRIVMSG " + channelName + " :" + 
			 StringUtil.join(args) + "\n");
	}
    }

  /**
   * Executes <code>Channel.doMaintain()</code> for given channel.<P>a
   *
   * @param host Host of invoker
   * @param invoker User object of invoker
   * @param args command arguments
   * @param channel Channel where command takes place
   */
  private void commandMaintain(Host host, User invoker, String args[], Channel channel) {
    String channelName = channel.getChannelName();
    if ( (invoker.isGlobalAdmin() || invoker.isChanAdmin(channelName) ||
	  invoker.isOp(channelName)) ) {
      channel.doMaintain();
    }
  }

    /**
     * Leaves channel for given amount of seconds.<P>
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandLeave(Host host, User invoker, String args[], Channel channel)
    {
      int period = 10;

      if ( (args != null) && (args.length == 1) ) {
	try {
	  period = Integer.parseInt(args[0]);
	} catch ( NumberFormatException e ) {
	  // dont care
	}
      }

      if ( (period < 1) || (period > 3600) ) {
	write("period argument must be in range 1..3600");
	return;
      }
      
      String channelName = channel.getChannelName();

      if ( (invoker.isGlobalAdmin() || invoker.isChanAdmin(channelName) ||
	    invoker.isOp(channelName)) ) {
	Timer.deploy(period, new ChannelJoiner(channelName, caller));
	
	caller.write("PART " + channelName + " :leaving channel " + 
		     channelName + " for " +
		     period + " seconds\n");
      }
    }


    /**
     * Process command message. assuming valid channel argument.
     *
     * @param msg command msg string
     * @param channel valid channel name
     */
    private void processCmdMsg(Host host,String cmd,Channel channel,String args[]) 
    {
	User user = caller.findUser(host);

	/* all commands require user in bot */
	if ( (user != null) && channel.isJoined() ) {
	    if( cmd.equals("k") ) {
		commandK(host,user,args,channel);
	    } else if( cmd.equals("bk") ) {
		commandBK(host,user,args,channel);
	    } else if( cmd.equals("add") ) {
		commandAdd(host,user,args,channel);
	    } else if( cmd.equals("list") ) {
		commandList(host,user,args,channel);
	    } else if( cmd.equals("del") ) {
		commandDel(host,user,args,channel);
	    } else if( cmd.equals("edit") ) {
		commandEdit(host,user,args,channel);
	    } else if( cmd.equals("join") ) {
		commandJoin(host,user,args,channel);
	    } else if( cmd.equals("leave") ) {
		commandLeave(host, user, args, channel);
	    } else if( cmd.equals("maintain") ) {
	      commandMaintain(host, user, args, channel);
	    } else if( cmd.equals("say") ) {
	      commandSay(host, user, args, channel);
	    }
	}
    }

    /**
     * Handles -b MODEs. Re-bans any hostmasks that are defined in the
     * permban list.
     *
     * @param host Host of the unbanner
     * @param channel Channel on which the mode was set
     * @param target unbanned hostmask
     */
    private void doUnBan(Host host,Channel channel,String target)
    {
	String key = caller.getInstanceData().getNetwork()+"-"+channel.getChannelName();
	Vector v = (Vector)permbanList.get(key);
	    
	if( v != null ) {
	    for( int j = 0; j < v.size(); j++ ) {
		String banMask = (String)v.elementAt(j);
		if( banMask.equalsIgnoreCase(target) ) {
		    ModeQueueElement element = 
			new ModeQueueElement(Irc.MODE_BAN,ModeQueueElement.PRIORITY_NORMAL,
					     banMask,channel.getChannelName());
		    caller.getOutputQueue().pushMode(element);
		    
		    if( host.isWellformed() ) {
			caller.write("NOTICE "+host.getNick()+" :Do not unban "+banMask+", "+
				     "it is in my permban list.\n");
		    }
		}
	    }
	}
    }

  /**
   * Handles MODE changes.<P>
   *
   * @param message the mode message
   */
  private void doMode(ModeMessage message) {
    String modeString = message.getModeString();
    int index = 0;
    boolean polarity = true;
    Channel channel = caller.findChannel(message.getChannelName());
    Host host = message.getSourceHost();
    int len = modeString.length();
    String targetList[] = message.getTargetList();

    for ( int i = 0; i < len; i++ ) {
      switch( modeString.charAt(i) ) {
      case '-':   
	polarity = false;
	break;
      case '+':
	polarity = true;
	break;
      case 'o':
	String channelName = channel.getChannelName();
	ChanVars vars = getVars(channelName);

	if ( vars.getEnforceOpList() && polarity ) {
	  Nick nick = channel.findNick(targetList[index]);
	  Host oppedHost = nick.getHost();

	  if ( (nick == null) ) {
	    throw new IllegalStateException("Nick == null!");
	  }

	  User opped = caller.findUser(oppedHost);
	  boolean isSelf = oppedHost.getNick().equals(caller.getHost().getNick());

	  if ( !isSelf && ((opped == null) || !(opped.isOp(channelName))) ) {
	    ModeQueueElement mode = 
	      new ModeQueueElement(Irc.MODE_DEOP,
				   ModeQueueElement.PRIORITY_NORMAL,
				   oppedHost.getNick(),
				   channelName);
	    caller.getOutputQueue().pushMode(mode);
	  }
	}
	index++;
	break;
      case 'v':
      case 'I':
      case 'e':
      case 'k':
	index++;
	break;
      case 'l':
	if ( polarity ) { index++; }
	break;
      case 'b':
	if ( !polarity ) {
	  doUnBan(host, channel, targetList[index]);
	}
	index++;
	break;
      }
    }	    
  }	

    /**
     * Handles JOINs when the bot itself joins a channel.
     *
     * @param channel the Channel bot joins on
     */
    private void doSelfJoin(Channel channel)
    {
	Vector v = channel.getBanList();
	String key = caller.getInstanceData().getNetwork()+"-"+channel.getChannelName();
	Vector bans = (Vector)permbanList.get(key);

	/* if permbans not already banned, ban them */
	if( (v != null) && (bans != null) ) {
	    for( int i = 0; i < bans.size(); i++ ) {
		String permban = (String)bans.elementAt(i);
		boolean found = false;

		for( int j = 0; j < v.size(); j++ ) {
		    String mask = (String)v.elementAt(j);
		    if( permban.equalsIgnoreCase(mask) ) {
			found = true;
		    }
		}

		if( !found ) {
		    ModeQueueElement element = 
			new ModeQueueElement(Irc.MODE_BAN,ModeQueueElement.PRIORITY_NORMAL,
					     permban,channel.getChannelName());
		    caller.getOutputQueue().pushMode(element);
		}
	    }
	} 

	/* if no ChanVars for channels exist, set default ones */
	key = caller.getInstanceData().getNetwork() + "-" + channel.getChannelName();
	if( !chanvars.containsKey(key) ) {
	    chanvars.put(key,new ChanVars());
	    changed = true;
	}
    }

  /**
   * Checks that joiner's IP resolves into a hostname.
   *
   * @param user the joiner as user, or null if not user.
   * @param host host of the joiner
   * @param channel channel that was joined
   */
  private void enforceHostnameLookup(User user, Host host, Channel channel) {
    InetAddress address = null;
    boolean ban = false;
    
    try {
      address = InetAddress.getByName(host.getHost());
    } catch ( UnknownHostException e ) {
      ban = true;
      return;
    }

    if ( address.getHostAddress().equalsIgnoreCase(address.getHostName()) ) {
      if ( (user != null) && 
	   (user.isOp(channel.getChannelName()) || 
	    user.isGlobalAdmin() ||
	    user.isVoice(channel.getChannelName()) ||
	    user.isChanAdmin(channel.getChannelName())) ) {
      } else {
	ban = true;
      }
    } 
    
    if ( ban ) {
      String banMask = "*!" + host.getIdent() + "@" + host.getHost();
      ChanVars vars = getVars(channel.getChannelName());
      
      Log.info(this, "enforceHostnameLookup(): kicking & banning " + banMask);
      
      caller.write("MODE " + channel.getChannelName() +
		   " +b " + banMask + "\n");
      caller.write("KICK " + channel.getChannelName() + " " + 
		   host.getNick() + 
		   " :Hostname DNS lookup forced on this channel.\n");
      Timer.deploy(vars.getRejoinBanTime() * 60, 
		   new BanHandler(channel.getChannelName(), 
				  banMask, 
				  caller));
    }
  }

  /**
   * Checks for join flood.<P>
   *
   * @param user the joiner as user, or null if not user.
   * @param host host of the joiner
   * @param channel channel that was joined
   * @return whether the join caused join flood or not
   */
  private boolean checkJoinFlood(User user, Host host, Channel channel) {
    boolean ret = false;
    ChanVars vars = getVars(channel.getChannelName());
    String matching = host.getIdent() + "@" + host.getHost();
    
    if ( vars != null ) {
      Enumeration en = vars.getJoinHostList().elements();
      int count = 0;
      
      // calculate the number of matching join entries
      while ( en.hasMoreElements() ) {
	String s = (String)en.nextElement();
	
	if ( s.equals(matching) ) {
	  count++;
	  
	  if ( count >= vars.getMaxCloneCount() ) {
	    break;
	  }
	}
      }
      
      // if flood generated, kick & ban joiner
      if ( count >= vars.getMaxCloneCount() ) {
	String channelName = channel.getChannelName();
	
	if ( (user == null) || !(user.isOp(channelName) || 
				 user.isVoice(channelName) ||
				 user.isChanAdmin(channelName) ||
				 user.isGlobalAdmin()) ) {
	  String banMask = "*!" + host.getIdent() + "@" + host.getHost();
	  caller.write("MODE " + channelName + " +b " +
		       banMask + "\n");
	  caller.write("KICK " + channelName +
		       host.getNick() + " :Join flood detected on channel " + 
		       channelName + "\n");
	  Timer.deploy(vars.getRejoinBanTime() * 60, 
		       new BanHandler(channelName, banMask, caller));
	} else {
	  caller.write("NOTICE " + host.getNick() + 
		       " :Join flood detected on " + channelName + "\n");
	}
	ret = true;
      }
      
      // if interval full, flush the join host list
      vars.flushJoinHostList();
    }
    
    // add host to join flood host list 
    vars.getJoinHostList().add(matching);
    
    return ret;
  }
  
  /**
   * Handles JOINs.<P>
   *
   * @param message the join message
   */
  private void doJoin(JoinMessage message) {
    Channel channel = caller.findChannel(message.getJoinedChannelName());
    if ( channel == null ) {
      Log.error(this, "doJoin(): channel == null! JOIN: " + message.toString());
      return;
    }

    Host host = message.getJoinerHost();
    User user = caller.findUser(host);
    
    if ( host.getNick().equals(caller.getHost().getNick()) ) {
      doSelfJoin(channel);
    } else {
      boolean joinFlood = checkJoinFlood(user, host, channel);

      Log.debug(this, "doJoin(): checkJoinFlood() returned " + joinFlood + " for nick " + 
		host.getNick() + " on channel " + channel.getChannelName());
      
      if ( !joinFlood ) {
	// enforce hostname lookup
	if ( getVars(channel.getChannelName()).getEnforceResolving() ) {
	  enforceHostnameLookup(user, host, channel); 
	}
      }
    }
  }

    /**
     * Handles PRIVMSGs.<P>
     *
     * @param message PRIVMSG IrcMessage to process
     */
    private void doPrivmsg(IrcMessage message) 
    {
	Host host = new Host(message.getPrefix());
	Channel channel = null;
	String args[] = null;
	String cmd = null;

	String trailing = message.getTrailing();
	String arguments[] = message.getArguments();

	if( arguments[0].equalsIgnoreCase(caller.getHost().getNick()) ) {
	    /* PRIVMSG to bot */
	    this.source = host.getNick();
	    args = StringUtil.separate(trailing,' ');
	    if( (args != null) && (args.length >= 2) ) {
		channel = caller.findChannel(args[1]);
		cmd = args[0];
		args = StringUtil.range(args,2);
	    }
	} else {
	    /* PRIVMSG to channel */
	    channel = caller.findChannel(arguments[0]);
	    this.source = arguments[0];
	    if( (trailing.charAt(0) == '!') &&
		(trailing.length() > 1) ) {
		args = StringUtil.separate(trailing.substring(1), ' ');
		if( args != null ) {
		    cmd = args[0];
		    args = StringUtil.range(args, 1);
		}
	    }
	}

	if( (channel != null) && (cmd != null) ) {
	    processCmdMsg(host, cmd, channel, args);
	}
    }

  /**
   * Processes incoming IrcMessages from a ServerConnection. Sets instance 
   * variable caller to refer to the calling ServerConnection.<P>
   *
   * @param message IrcMessage to process
   * @param serverConnection invoking ServerConnection
   */
  protected void processMessage(IrcMessage message, ServerConnection serverConnection) {
    this.caller = serverConnection;
    String command = message.getCommand();
    String trailing = message.getTrailing();

    if ( message instanceof JoinMessage ) {
      doJoin((JoinMessage)message);
    } else if ( message instanceof ModeMessage ) {
      doMode((ModeMessage)message);
    } else {
      if ( command.equals("PRIVMSG") ) {
	if( (trailing != null) && 
	    (trailing.length() > 0) ) {
	  doPrivmsg(message);
	}
      }
    }

    // set per-request vars to null 
    this.caller = null;
    this.source = null;
  }

  /**
   * Sends a PRIVMSG to source (channel/user).<P>
   *
   * @param message message to send
   * @exception IllegalStateException thrown if source param was null
   */
  private void write(String message) {
    if ( source == null ) {
      Log.error(this, "write(): source == null!");
      throw new IllegalStateException("source == null!");
    }
    
    caller.write("PRIVMSG " + source + " :" + message + "\n");
  }
  
    /**
     * Joins a given channel when invoked by a Timer.
     *
     */
    class ChannelJoiner extends TimerCommand {
	private String channelName = null;
	private ServerConnection connection = null;
	
	ChannelJoiner(String channelName, ServerConnection connection) {
	    this.channelName = channelName;
	    this.connection = connection;
	}

	public void execute() {
	    connection.write("JOIN " + channelName + "\n");
	}
    }
  
    /**
     * Handles removing bans after a given period.
     *
     */
    class BanHandler extends TimerCommand {
	private String channelName = null;
	private String banMask = null;
	private ServerConnection connection = null;
	
	BanHandler(String channelName, String banMask, ServerConnection connection) {
	    this.channelName = channelName;
	    this.banMask = banMask;
	    this.connection = connection;
	}

	/**
	 * Removes the ban.
	 *
	 */
	public void execute() {
	    connection.write("MODE " + channelName + " -b " + banMask + "\n");
	}
    }
}

/**
 * Container class for a channel information.<P>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $
 */
class ChanVars 
{
  /**
   * Ban time for bk in minutes
   */
  private int banTime;
  /**
   * Ban time for auto-rejoin ban in minutes (NOT YET IMPLEMENTED)
   */
  private int rejoinBanTime;
  /**
   * Whether to enforce dns resolving for hostnames
   */
  private boolean enforceResolving = false;
  /**
   * Whether to enforce op list by deopping non-ops
   */
  private boolean enforceOpList = false;
  /**
   * Time of last join-host flush
   */
  private long lastJoinHostFlushTime = -1;
  /**
   * Join-host list. Elements are String objects in form of 'ident@host'.
   */
  private Vector joinHostList = null;
  /**
   * Length of join-host flush interval in seconds. Defaults to 
   * 10s.
   */ 
  private long joinFlushInterval = 10;
  /**
   * Number of identical joins in flush period to generate join flood.
   * Defaults to 2.
   */
  private int maxCloneCount = 2;

  /**
   * If join flood flush interval full, flush the list and
   * reset interval.
   */
  public void flushJoinHostList() {
    long now = System.currentTimeMillis();
    if ( (now - lastJoinHostFlushTime) >= (joinFlushInterval * 1000)) {
      joinHostList.clear();
      lastJoinHostFlushTime = now;
    }
  }

  public ChanVars(int banTime, int rejoinBanTime, boolean enforceResolving,
		  boolean enforceOpList, 
		  long joinFlushInterval, int maxCloneCount)
  {
    this.banTime = banTime;
    this.rejoinBanTime = rejoinBanTime;
    this.enforceResolving = enforceResolving;
    this.enforceOpList = enforceOpList;
    this.joinFlushInterval = joinFlushInterval;
    this.maxCloneCount = maxCloneCount;

    joinHostList = new Vector(30, 10);
    lastJoinHostFlushTime = System.currentTimeMillis();
  }

  /**
   * Default constructor
   */
  public ChanVars()
  {
    this(30, 5, false, false, 10, 2);
  }

  /**
   * Parses a floodvars string and returns a newv ChanVars object with
   * the given floodvars settings.
   *
   * @param vars current chanvars to adjust
   * @param varstr String containing the values
   * @return the original ChanVars object with adjusted floodvars, or the 
   * the exactly original ChanVars if the floodvars values were bad.
   */
  static ChanVars parseFromFloodvars(ChanVars vars, String varstr) {
    String parts[] = StringUtil.separate(varstr,' ');
    if( (parts != null) && (parts.length == 2) ) {
      long joinFlushInterval = -1;
      int maxCloneCount = -1;

      try {
	joinFlushInterval = Long.parseLong(parts[0]);
	maxCloneCount = Integer.parseInt(parts[1]);
      } catch( NumberFormatException e ) {
	return vars;
      }

      // do some checking
      if ( (joinFlushInterval < 1) || (maxCloneCount < 2) ) {
	return vars;
      }

      vars.setJoinFlushInterval(joinFlushInterval);
      vars.setMaxCloneCount(maxCloneCount);
	    
      return vars;
    }
    return vars;
  }

  /**
   * Parses a chanvars value string. The values are space-separated.<P>
   *
   * @param vars current chanvars to adjust
   * @param varstr String containing the values
   * @return the original ChanVars object with adjusted chanvars, or the 
   * the exactly original ChanVars if the chanvars values were bad.
   */
  static ChanVars parseFromChanvars(ChanVars vars, String varstr) {
    String parts[] = StringUtil.separate(varstr,' ');
    if( (parts != null) && (parts.length == 4) ) {
      int banTime = 0;
      int rejoinBanTime = 0;
      boolean enforceResolving = false;
      boolean enforceOpList = false;

      try {
	banTime = Integer.parseInt(parts[0]);
	rejoinBanTime = Integer.parseInt(parts[1]);
	enforceResolving = (Integer.parseInt(parts[2]) == 1);
	enforceOpList = (Integer.parseInt(parts[3]) == 1);
      } catch( NumberFormatException e ) {
	return vars;
      }

      // do some checking
      if ( (banTime < 1) || (rejoinBanTime < 1) ) {
	return vars;
      }

      vars.setBanTime(banTime);
      vars.setRejoinBanTime(rejoinBanTime);
      vars.setEnforceResolving(enforceResolving);
      vars.setEnforceOpList(enforceOpList);
    }

    return vars;
  }

  // getters
  public int getBanTime() { return banTime; }
  public int getRejoinBanTime() { return rejoinBanTime; }
  public boolean getEnforceResolving() { return enforceResolving; }
  public boolean getEnforceOpList() { return enforceOpList; }
  public long getLastJoinHostFlushTime() { return lastJoinHostFlushTime; }
  public Vector getJoinHostList() { return joinHostList; }
  public long getJoinFlushInterval() { return joinFlushInterval; }
  public int getMaxCloneCount() { return maxCloneCount; }

  // setters
  public void setBanTime(int banTime) {
    this.banTime = banTime;
  }

  public void setRejoinBanTime(int rejoinBanTime) {
    this.rejoinBanTime = rejoinBanTime;
  }

  public void setEnforceResolving(boolean enforceResolving) {
    this.enforceResolving = enforceResolving;
  }

  public void setJoinFlushInterval(long joinFlushInterval) {
    this.joinFlushInterval = joinFlushInterval;
  }

  public void setMaxCloneCount(int maxCloneCount) {
    this.maxCloneCount = maxCloneCount;
  }

  public void setEnforceOpList(boolean enforceOpList) {
    this.enforceOpList = enforceOpList;
  }
}    
