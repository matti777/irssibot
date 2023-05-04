#!/bin/sh

# IrssiBot start / stop script for Unix platforms

JAR_DIR=jars 

CLASS_PATH="$JAR_DIR/xerces.jar:$JAR_DIR/mysql.jar:classes/"

case "$1" in
  start)
	if [ X$2 = "X" ]; then
	    echo "Usage: $0 start config-file.xml"
	    exit 1
	fi
	echo -n "starting IrssiBot.. "
	export LANG=en_US
	java -cp $CLASS_PATH irssibot.core.Core $2
	echo "[done]"
	;;
  stop)
	echo -n "stopping IrssiBot.. "
	kill `ps axuwww | grep "irssibot\.core\.Core" | grep -v grep | awk '{print $2}'`
	echo "[done]"
	;;
  *)
	echo "Usage: $0 {start|stop} [config-file.xml]"
	exit 1
esac
