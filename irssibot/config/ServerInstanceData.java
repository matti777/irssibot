/*
 * $Id: ServerInstanceData.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
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
package irssibot.config;

import java.util.Vector;
import java.util.Hashtable;

/**
 * Container class for configuration data for a server instance (<server-instance>) 
 * in the config file.
 *
 * @author Matti Dahlbom
 * @version $Name:  $, $Revision: 1.2 $
 */
public class ServerInstanceData 
{
    private String network = null;
    private String userFilePath = null;
    private String botNick = null;
    private String botAltNick = null;
    private String realName = null;
    private String ident = null;
    private Vector serverList = null;
    private Hashtable channels = null;

    /**
     * Time interval (in milliseconds) during which max .<code>outMaxBytes</code> bytes
     * are allowed to be sent to the server.
     */
    private long outFlushTime = 1000;
    /**
     * Maximum number of bytes to be sent to the server during a 
     * <code>outFlushTime</code> ms time period.
     */
    private int outMaxBytes = 1024;

    public String getNetwork() { return network; }
    public String getUserFilePath() { return userFilePath; }
    public String getBotNick() { return botNick; }
    public String getBotAltNick() { return botAltNick; }
    public String getRealName() { return realName; }
    public String getIdent() { return ident; }
    public Vector getServerList() { return serverList; }
    public Hashtable getChannels() { return channels; }
    public long getOutFlushTime() { return outFlushTime; }
    public int getOutMaxBytes() { return outMaxBytes; }
 
    public ServerInstanceData(String network, String userFilePath, String botNick, 
			      String botAltNick, String realName, String ident, 
			      Vector serverList, Hashtable channels,
			      long outFlushTime, int outMaxBytes)
    {
	this.network = network;
	this.userFilePath = userFilePath;
	this.botNick = botNick;
	this.botAltNick = botAltNick;
	this.realName = realName;
	this.ident = ident;
	this.serverList = serverList;
	this.channels = channels;
	this.outFlushTime = outFlushTime;
	this.outMaxBytes = outMaxBytes;
    }

    public void setBotNick(String nick) {
	botNick = nick;
    }

    public void setBotAltNick(String nick) {
	botAltNick = nick;
    }
}


