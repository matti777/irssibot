/*
 * $Id: OutputQueue.java,v 1.1 2002/11/08 11:14:31 dreami Exp $
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

import irssibot.util.log.Log;
import irssibot.protocol.*;

import java.util.*;
import java.io.*;

/**
 * This class represents a buffered output queue for a server connection.
 * It includes functionality for queuing mode pushes and traffic to the
 * server.<P>
 *
 * IRC servers have a 1024 -byte buffer for each client (this might be outdated
 * but should be accurate enough) and that buffer may not be overflown
 * or else the client is disconnected with 'Excess Flood'.<P>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.1 $
 * @see irssibot.core.ModeQueueElement
 */
public class OutputQueue extends Thread
{
    private static String moduleName = "OutputQueue";
    
    /**
     * Length of mode flushing interval (in milliseconds).
     */
    private static final long modeFlushInterval = 1000;
    /**
     * Timeout value (in milliseconds) for wait() in main loop.
     */
    private static final long waitPeriod = modeFlushInterval / 2;
    /**
     * Time of last mode flush
     */
    private long lastModeFlushTime = -1;
    /**
     * Output stream for server connection
     */
    private BufferedOutputStream out = null;
    /**
     * A Hashtable containing Vector objects as mode queues. Channel
     * name Strings are used as keys.
     */
    private Hashtable modeQueue = null;
    /**
     * The actual output queue. The elements are <code>String</code> objects
     * whose content is irc commands to be sent to the server.
     */
    private Vector outQueue = null;
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
    /**
     * Lock object for queue operations
     */
    private Object outLock = new Object();
    /**
     * Point of time when the current output flush interval started
     *
     * @see java.lang.System#currentTimeMillis()
     */
    private long intervalStarted = -1;
    /**
     * Number of bytes sent during this interval
     */
    private int intervalBytes = 0;
    
    /** 
     * Indicates whether this queueing thread is running or not.
     */
    private boolean alive = true;
    
    public OutputQueue(BufferedOutputStream out, long outFlushTime, int outMaxBytes)
    {
	super("OutputQueue");

	this.out = out;
	this.outFlushTime = outFlushTime;
	this.outMaxBytes = outMaxBytes;
	this.lastModeFlushTime = 0;

	modeQueue = new Hashtable();

	outQueue = new Vector(10, 5);
    }

    public String toString() { return moduleName; }

    /**
     * Outputs a line straight to the server past the queue.
     *
     * @param line output line
     */
    public void priorityOutput(String line) {
	synchronized ( outLock ) {
	    try {
		out.write(line.getBytes());
		out.flush();
	    } catch ( IOException e ) {
		Log.log(this, e);
	    }
	}
    }

    /**
     * Adds an output line (irc command) to output queue. Lines longer than
     * <code>outMaxBytes</code> are discarded without notice.
     *
     * @param line output line
     */
    public void output(String line) {
	if ( line.length() >= outMaxBytes ) {
	    return;
	}

	synchronized ( outLock ) {
	    outQueue.add(line);

	    outLock.notify();
	}
    }

    /**
     * Flushes output queue to the server at maximum rate of 
     * <code>outMaxBytes</code> bytes per <code>outFlushTime</code> 
     * time interval (ms).<P>
     *
     * NOTE: this method is synchronized from outside (in <code>run()</code>)
     * to <code>outLock</code>.<P>
     *
     * @return true if there are lines in the queue but we cannot send any more
     * during this interval. false if the queue is empty.
     * @see #outLock
     * @see #run()
     */
    private boolean doOutput() {
	long curTime = System.currentTimeMillis();

	// see if we may reset the interval
	if ( (curTime - intervalStarted) > outFlushTime ) {
	    intervalStarted = curTime;
	    intervalBytes = 0;
	}

	while ( outQueue.size() > 0 ) {
	    String s = (String)outQueue.elementAt(0);
	    if ( (s.length() + intervalBytes) < outMaxBytes ) {
		outQueue.remove(0);
		intervalBytes += s.length();
		
		try {
		    out.write(s.getBytes());
		    out.flush();
		} catch ( IOException e ) {
		    Log.log(this, e);
		}
	    } else {
		return true;
	    }
	}	

	return false;
    }

    /**
     * Pushes a mode to a channel's mode queue.
     *
     * @param element the ModeQueueElement for the new mode
     * @see irssibot.core.ModeQueueElement
     */
    public void pushMode(ModeQueueElement element) 
    {
	int index = 0;
	ModeQueueElement cur = null;
	synchronized ( outLock ) {
	    Vector queue = (Vector)modeQueue.get(element.getChannelName());
	    
	    // if no queue, create new one 
	    if( queue == null ) {
		queue = new Vector();
		modeQueue.put(element.getChannelName(),queue);
	    }
	    
	    if( element.getPriority() == ModeQueueElement.PRIORITY_IMMEDIATE ) {
		//send this one mode immediately
		//##TODO##
	    } else {
		// insert mode to queue by its priority 
		for( index = 0; index < queue.size(); index++ ) { 
		    cur = (ModeQueueElement)queue.elementAt(index);
		    if( element.getPriority() >= cur.getPriority() ) {
			break;
		    }
		}
	    }
	    
	    queue.insertElementAt(element, index);

	    outLock.notify();
	}
    }

    /**
     * Flushes the mode queue to the server.
     *
     * @param channelName name of channel whose mode queue to push
     * @param queue mode queue for the channel
     */
    private void doModeFlush(String channelName, Vector queue)
    {
	String opModeList = "";
	String opTargetList = "";
	boolean opPolarity = true;
	int opModes = 0;
	String banModeList = "";
	String banTargetList = "";
	boolean banPolarity = true;
	int banModes = 0;

	Log.debug(this, "doModeFlush(): " + channelName + " " + queue.size());

	for( int i = 0; i < queue.size(); i++ ) {
	    ModeQueueElement element = (ModeQueueElement)queue.elementAt(i);
	    switch( element.getMode() ) {
	    case Irc.MODE_OP: 
		if( !opPolarity ) {
		    opPolarity = true;
		    opModeList += "+";
		} else if( opModes == 0 ) {
		    opModeList = "+";
		}
		opModeList += "o";
		opTargetList += element.getTarget()+" ";
		opModes++;
		break;
	    case Irc.MODE_DEOP: 
		if( opPolarity ) {
		    opPolarity = false;
		    opModeList += "-";
		} else if( opModes == 0 ) {
		    opModeList = "-";
		}
		opModeList += "o";
		opTargetList += element.getTarget()+" ";
		opModes++;
		break;
	    case Irc.MODE_VOICE: 
		if( !opPolarity ) {
		    opPolarity = true;
		    opModeList += "+";
		} else if( opModes == 0 ) {
		    opModeList = "+";
		}
		opModeList += "v";
		opTargetList += element.getTarget()+" ";
		opModes++;
		break;
	    case Irc.MODE_DEVOICE: 
		if( opPolarity ) {
		    opPolarity = false;
		    opModeList += "-";
		} else if( opModes == 0 ) {
		    opModeList = "-";
		}
		opModeList += "v";
		opTargetList += element.getTarget()+" ";
		opModes++;
		break;
	    case Irc.MODE_BAN: 
		if( !banPolarity ) {
		    banPolarity = false;
		    banModeList += "+";
		} else if( banModes == 0 ) {
		    banModeList = "+";
		}
		banModeList += "b";
		banTargetList += element.getTarget()+" ";
		banModes++;
		break;
	    case Irc.MODE_UNBAN: 
		if( banPolarity ) {
		    banPolarity = false;
		    banModeList += "-";
		} else if( banModes == 0 ) {
		    banModeList = "-";
		}
		banModeList += "b";
		banTargetList += element.getTarget()+" ";
		banModes++;
		break;
	    default:
		break;
	    }

	    /* push max 3 modes at a time */
	    if( opModes == 3 ) {
		output("MODE "+channelName+" "+opModeList+" "+opTargetList.trim()+"\n");
		opModes = 0;
		opPolarity = true;
		opModeList = "";
		opTargetList = "";
	    }
	    if( banModes == 3 ) {
		output("MODE "+channelName+" "+banModeList+" "+banTargetList.trim()+"\n");
		banModes = 0;
		banPolarity = true;
		banModeList = "";
		banTargetList = "";
	    }
	}
	if( opModes > 0 ) {
	    output("MODE " + channelName + " " + opModeList + " " + opTargetList.trim() + "\n");
	}
	if( banModes > 0 ) {
	    output("MODE " + channelName + " " + banModeList + " " + banTargetList.trim() + "\n");
	}

	// empty queue 
	queue.clear();
    }
    
    /**
     * After certain interval flushes the mode queue. Pumps the contents of
     * the output queue to the server at maximum rate of <code>outMaxBytes</code> bytes
     * per <code>outFlushTime</code> time interval (ms).
     *
     */
    public void run()
    {
	Enumeration keys = null;
	String key = null;
	Vector queue = null;	    
	
	Log.debug(this, "run(): starting..");

	intervalStarted = System.currentTimeMillis();
	intervalBytes = 0;

	while ( alive ) {
	    synchronized ( outLock ) {
		while ( (outQueue.size() < 1) && 
			(modeQueue.size() < 1) ) {
		    try {
			if ( modeQueue.size() > 0 ) {
			    outLock.wait(waitPeriod);
			} else {
			    outLock.wait();
			}
		    } catch ( InterruptedException e ) {
			if ( !alive ) {
			    break;
			}
		    }
		}

		// check mode queue
		if ( modeQueue.size() > 0 ) {
		    long now = System.currentTimeMillis();
		    if ( (now - lastModeFlushTime) >= modeFlushInterval ) {
			lastModeFlushTime = now;

			keys = modeQueue.keys();
			while( keys.hasMoreElements() ) {
			    key = (String)keys.nextElement();
			    doModeFlush(key, (Vector)modeQueue.get(key));
			    modeQueue.remove(key);
			}
		    } else {
			continue;
		    }
		}

		// check output queue
		if ( doOutput() ) {
		    long sleepTime = outFlushTime - 
			(System.currentTimeMillis() - intervalStarted);
		    try {
			outLock.wait(sleepTime);
		    } catch ( InterruptedException e ) {
			// dont care
		    }
		}
	    }
	}

	Log.debug(this, "run(): thread mode queue exiting..");
    }

    /**
     * Kills the queue
     *
     */
    public void killQueue()
    {
	alive = false;
	interrupt();
    }
}










