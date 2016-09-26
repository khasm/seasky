#!/bin/bash
PROG=$1
WORKDIR=`pwd`
if [ "$PROG" == "zk" ]; then
    cd /usr/share/zookeeper/bin
    /usr/share/zookeeper/bin/zkServer.sh start
fi
cd $WORKDIR
shift
PROG=$1
shift
if [ -a backup.ramcloud ]; then
    rm backup.ramcloud
fi
if [ "$PROG" == "coordinator" ]; then
    obj.master/coordinator -C tcp:host="$1",port="$2" -x zk:"$3"  --clusterName test-cluster
elif [ "$PROG" == "server" ]; then
    obj.master/server -L tcp:host="$1",port="$2" -x zk:"$3" --totalMasterMemory 4096 -f backup.ramcloud --segmentFrames 256 -r 0 --clusterName test-cluster
else
    echo "unknown"
fi
