#
# Regular cron jobs for the autodeploy-agent package
#
0 4	* * *	root	[ -x /usr/bin/autodeploy-agent_maintenance ] && /usr/bin/autodeploy-agent_maintenance
