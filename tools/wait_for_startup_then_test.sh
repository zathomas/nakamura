#!/bin/sh
if [ $# -lt 1 ] ; then
  echo "Usage: $0 <path-to-sling-logfile>"
  echo "Path can either be absolute or relative to the current directory."
  exit 0
fi
echo "Waiting for startup..."
while ! [ -e $1 ];do sleep 2; done
while ! grep "org.sakaiproject.nakamura.world BundleEvent STARTED" $1 1> /dev/null ;do sleep 2; done
echo "Startup complete..."
./tools/runalltests-with-exit-code.sh

exit $?