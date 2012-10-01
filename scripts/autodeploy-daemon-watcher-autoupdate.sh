#!/bin/bash

source '/etc/autodeploy.conf'
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

# downloads will use the mobile proxy
#export http_proxy=http://proxy.mobile.rz:3128

# direct archiva download url
AGENT_DOWNLOAD_URL=http://maven.corp.mobile.de/archiva/repository/autodeployment-releases/de/mobile/siteops/autodeployment-agent/LATEST-SNAPSHOT
# Netapp HTTP url  - availability may be delayed in the datacenter
#AGENT_DOWNLOAD_URL=http://maven-download.mobile.rz/maven/autodeployment-releases/de/mobile/siteops/autodeployment-agent/LATEST-SNAPSHOT
AGENT_DOWNLOAD_METADATA=$AGENT_DOWNLOAD_URL/maven-metadata.xml
AGENT_VERSION_INFO=$AUTODEPLOY_BASEDIR/agent-version-info
AGENT_DOWNLOAD_NAME=autodeployment-agent.jar
AGENT_JARFILE=$INSTALL_LOCATION/$AGENT_DOWNLOAD_NAME


# direct archiva download url
CLIENT_DOWNLOAD_URL=http://maven.corp.mobile.de/archiva/repository/autodeployment-releases/de/mobile/siteops/autodeploy-scripts/LATEST-SNAPSHOT
# Netapp HTTP url - availability may be delayed in the datacenter
#CLIENT_DOWNLOAD_URL=http://maven-download.mobile.rz/maven/autodeployment-releases/de/mobile/siteops/autodeploy-scripts/LATEST-SNAPSHOT
CLIENT_DOWNLOAD_METADATA=$CLIENT_DOWNLOAD_URL/maven-metadata.xml
CLIENT_VERSION_INFO=$AUTODEPLOY_BASEDIR/client-version-info
CLIENT_DOWNLOAD_NAME=autodeploy-scripts.jar
CLIENT_JARFILE=$INSTALL_LOCATION/$CLIENT_DOWNLOAD_NAME

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

			if [ -f $AGENT_VERSION_INFO ] ; then
				AGENT_CURRENT_VERSION=$(cat $AGENT_VERSION_INFO)
			else
				AGENT_CURRENT_VERSION=-2
			fi

			if [ -f $CLIENT_VERSION_INFO ] ; then
				CLIENT_CURRENT_VERSION=$(cat $CLIENT_VERSION_INFO)
			else
				CLIENT_CURRENT_VERSION=-2
			fi

			AGENT_TIMESTAMP=$($WGET -q -O - $AGENT_DOWNLOAD_METADATA | grep timestamp |sed -e 's|^[ \t]*<timestamp>\(.*\)</timestamp>[ \t\r]*|\1|')
			AGENT_BUILDNR=$($WGET -q -O - $AGENT_DOWNLOAD_METADATA | grep buildNumber |sed -e 's|^[ \t]*<buildNumber>\(.*\)</buildNumber>[ \t\r]*|\1|')

			# if timestamp is empty, force a download
			test -z "$AGENT_BUILDNR" && AGENT_BUILDNR=-3

			if [ $AGENT_BUILDNR -ne $AGENT_CURRENT_VERSION ] ; then
				echo "LATEST BUILD $AGENT_BUILDNR IS DIFFERENT THAN CURRENT VERSION $AGENT_CURRENT_VERSION. DOWNLOADING VERSION $AGENT_BUILDNR ..." 2>&1 |tee -a $WATCHER_DAEMON_LOG
				$WGET -O $INSTALL_LOCATION/$AGENT_DOWNLOAD_NAME $AGENT_DOWNLOAD_URL/autodeployment-agent-LATEST-${AGENT_TIMESTAMP}-${AGENT_BUILDNR}-jar-with-dependencies.jar 2>&1 |tee -a $WATCHER_DAEMON_LOG
				
				if [ $? -eq 0 ] ; then
					echo $AGENT_BUILDNR > $AGENT_VERSION_INFO
				else
					test -e $AGENT_VERSION_INFO && rm $AGENT_VERSION_INFO
					echo "error: Agent download failed." 2>&1 |tee -a $WATCHER_DAEMON_LOG
				fi
			fi

			CLIENT_TIMESTAMP=$($WGET -q -O - $CLIENT_DOWNLOAD_METADATA | grep timestamp |sed -e 's|^[ \t]*<timestamp>\(.*\)</timestamp>[ \t\r]*|\1|')
			CLIENT_BUILDNR=$($WGET -q -O - $CLIENT_DOWNLOAD_METADATA | grep buildNumber |sed -e 's|^[ \t]*<buildNumber>\(.*\)</buildNumber>[ \t\r]*|\1|')

			# if timestamp is empty, force a download
			test -z "$CLIENT_BUILDNR" && CLIENT_BUILDNR=-3

			if [ $CLIENT_BUILDNR -ne $CLIENT_CURRENT_VERSION ] ; then
				echo "LATEST BUILD $CLIENT_BUILDNR IS DIFFERENT THAN CURRENT VERSION $CLIENT_CURRENT_VERSION. DOWNLOADING VERSION $CLIENT_BUILDNR ..." 2>&1 |tee -a $WATCHER_DAEMON_LOG
				$WGET -O $INSTALL_LOCATION/$CLIENT_DOWNLOAD_NAME $CLIENT_DOWNLOAD_URL/autodeploy-scripts-LATEST-${CLIENT_TIMESTAMP}-${CLIENT_BUILDNR}-jar-with-dependencies.jar 2>&1 |tee -a $WATCHER_DAEMON_LOG
				
				if [ $? -eq 0 ] ; then
					echo $CLIENT_BUILDNR > $CLIENT_VERSION_INFO
				else
					test -e $CLIENT_VERSION_INFO && rm $CLIENT_VERSION_INFO
					echo "error: Deploy Scripts download failed." 2>&1 |tee -a $WATCHER_DAEMON_LOG
				fi
			fi

			RANDOM_SLEEP=$(( $RANDOM % 180 ))

			# the downloaded jar files must exist and not be empty. skip starting daemon and try download again
			if ! [ -s $CLIENT_JARFILE ] ; then
				echo "error: $CLIENT_JARFILE was not found. sleeping for $RANDOM_SLEEP secs then retrying download." 2>&1 |tee -a $WATCHER_DAEMON_LOG
				test -e $CLIENT_VERSION_INFO && rm $CLIENT_VERSION_INFO
				sleep $RANDOM_SLEEP
				continue
			elif ! [ -s $AGENT_JARFILE ] ; then
				echo "error: $AGENT_JARFILE was not found. sleeping for $RANDOM_SLEEP secs then retrying download." 2>&1 |tee -a $WATCHER_DAEMON_LOG
				test -e $AGENT_VERSION_INFO && rm $AGENT_VERSION_INFO
				sleep $RANDOM_SLEEP
				continue
			fi

			# do a test run of the deploy scripts
			$JAVA_BIN -jar $CLIENT_JARFILE |grep Autodeploy 
			EXIT_VALUE=$?
			if [ $EXIT_VALUE -ne 0 ] ; then
				echo "EXIT VALUE: $EXIT_VALUE"
				echo "error: $CLIENT_JARFILE appears to be corrupt.  Retrying download." 2>&1 |tee -a $WATCHER_DAEMON_LOG
				sleep $RANDOM_SLEEP
				test -e $CLIENT_VERSION_INFO && rm $CLIENT_VERSION_INFO
				continue
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
