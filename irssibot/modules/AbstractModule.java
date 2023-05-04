/*
 * $Id: AbstractModule.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
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

import irssibot.protocol.*;
import irssibot.core.*;
import irssibot.util.log.Log;

import java.util.Vector;
import java.util.Properties;

/**
 * Base class for all modules for IrssiBot. Implements some basic
 * functionality common for all modules. The module acts as a
 * 'consumer' for the bot core, who 'produces' IrcMessage objects
 * to the module's processing queue.
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $ 
 */
public abstract class AbstractModule extends Thread 
{
    /**
     * Vector containing MessageData objects waiting for processing.
     */
    private final Vector messageQueue = new Vector(10, 5);
    /**
     * A lock object used for producer-consumer synchronization
     */
    private final Object processLock = new Object();
    /**
     * Indicates whether the module thread should continue executing
     * its run() loop.
     */
    private boolean alive = true;
    /**
     * A state variable indicating whether the module is executing ok.
     * When an exception occurs in the consumer thread, this variable is set
     * to the thrown exception and it is thrown to the producer thread
     * next time addMessage() is called.
     *
     * @see #addMessage(IrcMessage,ServerConnection)
     */
    private Exception consumerException = null;
  /**
   * The message causing the consumer exception.<P>
   */
  private IrcMessage consumerMessage = null;

    /**
     * Constructs with named thread.<P>
     *
     * @param name name for thread
     */
    protected AbstractModule(String name) {
	super(name + " module");
    }

    /**
     * Module should return a descriptive info string 
     * containing at least module's name and version.
     *
     * @return module info string
     */
    public abstract String getModuleInfo();
    /**
     * Gets module's state as a Properties object. If module does not wish to save state 
     * OR its state has not been changed since last getModuleState(), it should return null. 
     * This is to minimize disk access. 
     *
     * @return state of module as a Properties object
     */
    public Properties getModuleState() 
    {
	return null;
    }

    /**
     * called upon loading the module
     *
     * @param state the initial state of module as an Properties object, or
     *              null if no state was saved for module.
     * @param core a Core instance. this can be used to initialize module if no state was
     *             retrieved.
     * @return true if ok, false if error. modules returning false from onLoad() will
     *         be unloaded immediately.
     * @see irssibot.core.Core
     */
    public boolean onLoad(Properties state,Core core) 
    {
	return true;
    }

    /**
     * Called upon unloading the module.
     *
     */
    public void onUnload()
    {
	/* do nothing */
    }

    /**
     * Appends the new IrcMessage+ServerConnection pair to the end of the message queue. 
     * Access to the queue is synchronized. When a new message is added to the queue,
     * the waiting consumer thread is notify()'ed.
     * 
     * @param message the IrcMessage to append to the message queue
     * @exception Exception thrown if an exception was thrown by the
     * consumer thread in processMessage().
     * @see #processMessage(IrcMessage,ServerConnection)
     */
    public final void addMessage(IrcMessage message, ServerConnection serverConnection) 
	throws Exception
    {
	synchronized ( processLock ) {
	    // see if an exception was thrown by the consumer thread 
	    if( consumerException != null ) {
		alive = false;
		throw consumerException;
	    }

	    // add the message at the end of the queue 
	    messageQueue.add(new MessageData(message, serverConnection));

	    // notify the consumer thread 
	    processLock.notifyAll();
	}
    }

    /**
     * Fetches next message in queue and pass it on to processMessage(). If
     * message is empty, the thread wait()s.
     *
     * @see #processMessage(IrcMessage)
     */
    private final void fetchNextMessage() 
    {
	MessageData messageData = null;

	synchronized ( processLock ) {
	    while ( alive && (messageQueue.size() == 0) ) {
		try {
		    processLock.wait();
		} catch ( InterruptedException e ) {
		    // dont care
		}
	    }

	    if ( !alive ) {
		return;
	    }

	    // fetch & remove first message in queue 
	    messageData = (MessageData)messageQueue.remove(0);
	}
	    
	try {
	    processMessage(messageData.message, messageData.serverConnection);
	} catch ( Exception e ) {
	  Log.error(this, "fetchNextMessage(): caught " + e.getClass().getName() + 
		    ": " + e.getMessage() + ". Putting to queue..");
	  synchronized ( processLock ) {
	    consumerException = e;
	    consumerMessage = messageData.message;

	    Log.error(this, "fetchNextMessage(): causing message is: " + consumerMessage);
	  }
	}
    } 

  /**
   * Processes messages as they come in. Classes inheriting this 
   * class must override this method.<P>
   *
   * @param message the incoming IrcMessage
   */
  protected abstract void processMessage(IrcMessage message, 
					 ServerConnection serverConnection);

  /**
   * Kills module thread.<P>
   *
   */
  public void killModule() {
    Log.debug(this, "killModule()");
    
    synchronized ( processLock ) {
      alive = false;
      
      interrupt();
    }
  }

    /**
     * Thread loop. Continues execution until variable <b>alive</b> 
     * is set to value <b>false</b>.
     *
     */
    public final void run() 
    {
	while ( alive ) {
	    fetchNextMessage();
	}

	Log.debug(this, "run(): module thread '" + getName() + "' exiting..");
    }
}

/**
 * Storage class for storing data about a IrcMessage and the
 * ServerConnection that sent it.
 *
 */
class MessageData 
{
    /**
     * The actual message
     */
    public IrcMessage message = null;
    /**
     * The connection from whom the message came from
     */
    public ServerConnection serverConnection = null;

    public MessageData(IrcMessage message,ServerConnection serverConnection)
    {
	this.message = message;
	this.serverConnection = serverConnection;
    }
}








