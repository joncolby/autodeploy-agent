#!/bin/bash

CONF=/etc/autodeploy.conf

if [ -f $CONF ] ; then
	source $CONF
fi

WATCHER_DAEMON_LOG=$AUTODEPLOY_LOGDIR/autodeploy-daemon-watcher.log

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

WGET=$(which wget)

#NODECONFIG=$AUTODEPLOY_BASEDIR/nodeconfig.xml
NODECONFIG="classpath:/default-node-config.xml"


PIDFILE=/var/run/autodeploy-agent.pid
INSTALL_LOCATION=$AUTODEPLOY_BASEDIR/lib

AGENT_JARFILE=$INSTALL_LOCATION/autodeployment-agent.jar
CLIENT_JARFILE=$INSTALL_LOCATION/autodeploy-scripts.jar

test -d $AUTODEPLOY_BASEDIR || mkdir -p $AUTODEPLOY_BASEDIR
test -d $INSTALL_LOCATION || mkdir -p $INSTALL_LOCATION
test -d $AUTODEPLOY_LOGDIR || mkdir -p $AUTODEPLOY_LOGDIR
test -d $AUTODEPLOY_TEMPDIR || mkdir -p $AUTODEPLOY_TEMPDIR 

stop() {
	if [ -f $PIDFILE ] ; then
		echo "stopping autodeploy agent"

		if command -v start-stop-daemon >/dev/null 2>&1 ; then
			start-stop-daemon --stop --quiet --pidfile $PIDFILE
		else
			test -f $PIDFILE && kill $(cat $PIDFILE) 2>/dev/null
			ps -eo pid,args |grep -i autodeploy|grep -v grep | grep java |awk '{ print $1 }' |xargs kill 2>/dev/null
		fi

		test -f $PIDFILE && rm $PIDFILE
	fi
}

while true ; do

			# make sure the autodeploy daemon is not running
			stop

			# do a test run of the deploy scripts
			$JAVA_BIN -jar $CLIENT_JARFILE |grep Autodeploy 
			EXIT_VALUE=$?
			if [ $EXIT_VALUE -ne 0 ] ; then
				echo "EXIT VALUE: $EXIT_VALUE"
				echo "error: $CLIENT_JARFILE appears to be corrupt." 2>&1 |tee -a $WATCHER_DAEMON_LOG
				exit 1
			fi

		if command -v start-stop-daemon >/dev/null 2>&1 ; then

		        start-stop-daemon \
			        --start \
			        --verbose \
			        --make-pidfile \
			        --pidfile $PIDFILE \
			        --chuid root \
			        --exec $JAVA_BIN -- \
				-Dlog.rootDir=/var/log/autodeploy \
			        -jar $AGENT_JARFILE \
			        --nodeconfig $NODECONFIG \
			        --server $ZOOKEEPER_SERVER \
			        --port $ZOOKEEPER_PORT 

		else

			nohup $JAVA_BIN -Dlog.rootDir=/var/log/autodeploy \
			-jar $AGENT_JARFILE \
			--nodeconfig $NODECONFIG \
			--server $ZOOKEEPER_SERVER \
			--port $ZOOKEEPER_PORT >/dev/null 2>&1 &

			echo $! > $PIDFILE
			wait $(cat $PIDFILE)

		fi

done
