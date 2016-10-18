#!/bin/sh

java -Xms2g -Xmx2g -verbose:gc -cp depsky.jar:lib/DepSkyDependencies.jar depskys.clouds.drivers.localStorageService.ServerThread
