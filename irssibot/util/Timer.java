/*
 * $Id: Timer.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
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
package irssibot.util;

/**
 * A generic timer. The timer is deployed as its own 
 * thread with a given number of seconds and a 
 * <code>TimerCommand</code> object reference. After the timer
 * period, the <code>execute()</code> method of the
 * <code>TimerCommand</code> object is invoked.
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $ 
 */
public class Timer extends Thread {
    /**
     * Indicates whether this timer is still alive
     */
    private boolean isRunning = true;
    /**
     * Wait lock
     */
    private Object waitLock = new Object();
    /**
     * Wait period
     */
    private int period = -1;
    /**
     * TimerCommand to execute
     */
    private TimerCommand command = null;

    /**
     * Inits the timer. 
     *
     * @param period number of seconds to wait before executing 
     * command
     * @param command object whose execute() method to call after
     * given period
     */
    private Timer(int period, TimerCommand command) {
	this.period = period;
	this.command = command;
    }

    /**
     * Deploys a Timer with given wait period and command object.
     * After <code>period</code> seconds have passed, call the 
     * <code>execute()</code> method of the <code>command</code> object.
     *
     * @param period number of seconds to wait before executing 
     * command
     * @param command object whose execute() method to call after
     * given period
     * @exception IllegalArgumentException if period is out of 
     * range or command parameter is null
     */
    public static Timer deploy(int period, TimerCommand command) {
	if ( (period <= 0) || (period > 3600) ) {
	    throw new IllegalArgumentException("period out of range!");
	}
	
	if ( command == null ) {
	    throw new IllegalArgumentException("command is null!");
	}

	Timer timer = new Timer(period, command);
	timer.start();

	return timer;
    }

    /**
     * Performs the actual wait / execute invokation.
     */
    public void run() {
	long millisPeriod = period * 1000;
	long now = System.currentTimeMillis();

	synchronized ( waitLock ) {
	    while ( isRunning && 
		    ((System.currentTimeMillis() - now) < millisPeriod) ) {
		try {
		    waitLock.wait(millisPeriod);
		} catch ( InterruptedException e ) {
		    // dont care
		}
	    }
	}

	command.execute();
    }

    /**
     * Kills this timer, ending the wait.
     *
     */
    public void killTimer() {
	synchronized ( waitLock ) {
	    isRunning = false;
	    interrupt();
	}
    }
}


	       
