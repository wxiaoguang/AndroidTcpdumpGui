#!/usr/bin/env bash


DIR=`dirname $0`
DIR=`cd $DIR && pwd -P`
cd $DIR

ASSETS_BIN_ARM=app/src/main/assets/bin/arm
mkdir -p $ASSETS_BIN_ARM

if [ ! -f $ASSETS_BIN_ARM/tcpdump ]; then
    wget http://www.androidtcpdump.com/download/4.7.4/tcpdump -O $ASSETS_BIN_ARM/tcpdump
fi

if [ ! -f $ASSETS_BIN_ARM/busybox ]; then
    wget https://busybox.net/downloads/binaries/busybox-armv5l -O $ASSETS_BIN_ARM/busybox
fi

echo "Assembling AndroidTcpdumpGui ..."
if gradle :app:assembleRelease; then
    if [ -f app/build/outputs/apk/app-release.apk ]; then
        cp app/build/outputs/apk/app-release.apk ./AndroidTcpdumpGui.apk
    fi
    echo "Done"
else
    echo "Error"
fi
