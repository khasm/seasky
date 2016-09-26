#!/bin/bash

if [ "$EUID" -ne 0  ]
then
	echo "Script needs root privileges"
	exit
fi

#rm -f /run/tpm/tpmd_socket:0
#sudo killall tcsd

tpmd -d
tcsd -e