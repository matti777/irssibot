/*
 * $Id: ChannelConnect.java,v 1.1 2002/11/08 11:14:31 dreami Exp $
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
 */
package irssibot.core;

/**
 * Represents a unidirectional 'connection' between two IRC channels
 * the bot forwards things said on source channel to the destination channel
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.1 $
 */
public class ChannelConnect
{
    /**
     * Source channel (on the current network).
     */
    private String sourceChannel = null;
    /**
     * Destination server connection (network).
     */
    private ServerConnection destinationNetwork = null;
    /**
     * Destination channel on destination network.
     */
    private String destinationChannel = null;

    public ChannelConnect(String sourceChannel, ServerConnection destinationNetwork,
			  String destinationChannel)
    {
	this.sourceChannel = sourceChannel;
	this.destinationNetwork = destinationNetwork;
	this.destinationChannel = destinationChannel;
    }

    /**
     * Returns source channel name (on the current network).
     */
    public String getSourceChannel() { 
	return sourceChannel;
    }

    /**
     * Returns destination server connection (network).
     */
    public ServerConnection getDestinationNetwork() {
	return destinationNetwork;
    }

    /**
     * Returns destination channel name on destination network.
     */
    public String getDestinationChannel() {
	return destinationChannel;
    }
}











