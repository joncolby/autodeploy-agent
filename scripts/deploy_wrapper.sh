#!/bin/bash

if [ -n "$JAVA_HOME" ] ; then
        JAVA_BIN=$JAVA_HOME/bin/java
elif [ -e "/opt/java/bin/java" ] ; then
        JAVA_BIN="/opt/java/bin/java"
else
	JAVA_BIN=$(which java)
fi

if [ ! -e "$JAVA_BIN" ] ; then
        echo "java executable could not be found. set JAVA_HOME or add java to the root user PATH"
        exit 1
fi

SUN=$($JAVA_BIN -version 2>&1|grep 'Java(TM)')

if [ -z "$SUN" ] ; then
        echo "Autodeploy requires Java from Sun"
        exit 1
fi

$JAVA_BIN -jar /opt/autodeploy/lib/ca-kijiji-autodeploy-scripts.jar $1
