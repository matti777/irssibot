/*
 * $Id: ModuleHandler.java,v 1.1 2002/11/08 11:14:31 dreami Exp $
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

import irssibot.modules.AbstractModule;
import irssibot.user.*;
import irssibot.util.log.Log;
import irssibot.protocol.*;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Properties;
import java.io.*;

/**
 * Manages modules for IrssiBot. the modules can be loaded and added to
 * handler at startup (from config file) or dynamically from IRC
 * using <code>core->loadModule()</code>.<P>
 * 
 * All calls to modules are wrapped inside a tight try - catch in 
 * order to detect exceptions in the module and remove it in that case.<P>
 *
 * @author Matti Dahlbom 
 * @version $Name:  $ $Revision: 1.1 $
 */
public class ModuleHandler {
  /**
   * Component info string to be returned by toString()
   */
  private static String info = "ModuleHandler";
  
  private Hashtable loadedModules = null;
  private AbstractModule moduleTable[] = null;
  private int numModules = 0;
  private Core core = null;

  /**
   * Constructs.<P>
   *
   * @param core reference to bot core
   */
  public ModuleHandler(Core core) {
    loadedModules = new Hashtable();
    this.core = core;
  }

  /**
   * Returns component info.<P>
   */
  public String toString() { 
    return info; 
  }

  /**
   * Returns the module table.<P>
   */
  public AbstractModule[] getModuleTable() {  
    return moduleTable; 
  }

  /**
   * Removes faulted module cleanly.<P>
   *
   * @param t cause to module fault
   * @param module the module error occurred in
   */
  private void handleModuleCrash(Throwable t, AbstractModule module) {
    Log.debug(this, "handleModuleCrash(): module " + 
	      module.getClass().getName() + 
	      " caused an " + t.getClass().getName() + 
	      " and was removed. Cause: " +
	      t.getMessage());
    Log.log(this, t);

    // call module.onUnload()
    module.onUnload();
    
    if ( !removeModule(module.getClass().getName()) ) {
      Log.debug(this, "handleModuleCrash(): error removing module " +
		module.getClass().getName());
    }
    
    reconstructTable();

    // notify core 
    core.globalChannelBroadcast("Module " + module.getClass().getName() + 
				" caused an " + t.getClass().getName() + 
				" and was removed. " + 
				"Cause: " + t.getMessage());
  }

  /**
   * Forwards an IrcMessage to all registered modules. All exceptions in the
   * the module are caught and handled.<P>
   *
   * @param message the IrcMessage to forward
   * @param caller the ServerConnection the message came from
   * @return true is successful. false indicates an error occurred in a
   * module and it was removed from module table.
   */
  public boolean forwardMessage(IrcMessage message, ServerConnection caller) {
    boolean ret = true;

    // add message to all module's message queue 
    for ( int i = 0; i < numModules; i++ ) {
      try {
	moduleTable[i].addMessage(message, caller);
      } catch ( Throwable t ) {
	handleModuleCrash(t, moduleTable[i]);
	ret = false;
      }
    }
    
    return ret;
  } 

    /**
     * for optimal access to modules, construct a table representation of
     * the contents of the hash table and maintain the number of 
     * loaded modules in numModules
     */
    private void reconstructTable()
    {
	numModules = loadedModules.size(); 
	moduleTable = null;
	moduleTable = new AbstractModule[numModules];

	Enumeration en = loadedModules.elements();
	int i = 0;
	while( en.hasMoreElements() ) {
	    moduleTable[i++] = (AbstractModule)en.nextElement();
	}
    }

    /**
     * Adds a module. The module's onLoad() is called after addition, 
     * and it starts receiving message events immediately.
     *
     * @param moduleClassName Java class name of module 
     * @param module AbstractModule to add
     * @return true if successful, or false if failed.
     * @exception IllegalStateException if module already loaded
     */
    public boolean addModule(String moduleClassName, AbstractModule module)
	throws IllegalStateException
    {
	boolean ret = false;

	Log.debug(this, "addModule(): adding module " +
		  module.getClass().getName());

	/* if module isnt yet loaded, load it up. */
	if( !loadedModules.containsKey(moduleClassName) ) {
	    loadedModules.put(moduleClassName,module);

	    /* notify module it was loaded */
	    ret = module.onLoad(loadModuleState(moduleClassName), core);
	} else {
	    Log.debug(this, "addModule(): module already loaded");
	    throw new IllegalStateException("module already loaded");
	}
	
	if( ret == false ) {
	    removeModule(moduleClassName);
	    return false;
	}

	/* module loaded ok. start the consumer thread */
	module.start();

	reconstructTable();

	return ret;
    }

  /**
   * Removes a module. The module's onUnload() is called on removal,
   * and it stops receiving message events.<P>
   * 
   * @param moduleClassName name o module to remove
   * @return true if successfully removed. false if could not remove
   */
  synchronized public boolean removeModule(String moduleClassName) {
    boolean ret = false;
    AbstractModule module = null;
    
    if( loadedModules.containsKey(moduleClassName) ) {
      module = (AbstractModule)loadedModules.get(moduleClassName);
      module.onUnload();
      module.killModule();
      
      ClassLoader loader = module.getClass().getClassLoader();
      loader = null;
      module = null;
      
      System.gc();
      
      loadedModules.remove(moduleClassName);
      ret = true;
    }
    
    reconstructTable();
    
    return ret;
  }

  /**
   * Loads state of a module from disk as a Properties object.<P>
   * 
   * @param moduleClassName the name of module class
   * @return Module's state as a Properties object or a null if state file not found.
   */
  private Properties loadModuleState(String moduleClassName) {
    Properties props = new Properties();
    String fileName = core.getModuleBaseDir();

    // form the state file path
    fileName += "/state/" + moduleClassName + ".state";
    
    try {
      Log.debug(this, "loading module state from " + fileName);
      FileInputStream inStream = new FileInputStream(fileName);
      props.load(inStream);
      Log.debug(this, "loadModuleState(): loaded state for " + moduleClassName);
    } catch( IOException e ) {
      Log.error(this, "loadModuleState(): failed to load module state, message: " + e.getMessage());
      props = null;
    }

    return props;
  }

  /**
   * Saves module state to disk as a Properties object.<P>
   *
   * @param module module whose state to save
   */
  public void saveModuleState(AbstractModule module) {
    Properties props = null;
    
    try {
      props = module.getModuleState();
    } catch ( Throwable t ) {
      Log.log(this, t);
      Log.debug(this, "saveModuleState(): module " + module.getClass().getName() + 
		" caused an error and was removed.");
      removeModule(module.getClass().getName());
      return;
    }
    
    if ( props != null ) {
      String fileName = core.getModuleBaseDir();

      // form the state file path
      fileName += "/state/";

      // make sure the dir exists. if not, create it
      File file = new File(fileName);
      if ( !file.exists() ) {
	Log.debug(this, "saveModuleState(): creating module state directory..");
	if ( !file.mkdir() ) {
	  Log.error(this, "saveModuleState(): failed to create module state directory!");
	  return;
	}
      }
      
      fileName += module.getClass().getName() + ".state";
      
      try {
	Log.debug(this, "saveModuleState(): saving module state to file " + fileName);
	FileOutputStream outStream = new FileOutputStream(fileName);
	props.store(outStream, "state file for " + module.getClass().getName());
      } catch ( IOException e ) {
	Log.debug(this, "saveModuleState(): failed to save state to " + fileName + 
		  ", message: " + e.getMessage());
	props = null;
      }
    }
  }
}













