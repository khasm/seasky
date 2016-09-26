#make run build accept arguments
ifeq (run,$(firstword $(MAKECMDGOALS)))
RUN_ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
$(eval $(RUN_ARGS):;@:)
endif

Server : Main.o MIEServer.o MIEServerMT.o SSEServer.o ServerUtil.o CashServer.o PaillierCashServer.o paillier.o\
 Storage.o DepskyStorage.o RamCloudStorage.o RamCloudClient.o ReedSol.o Config.o Memcached.o Mie.o
	/usr/bin/g++-6 -o Server Main.o MIEServer.o MIEServerMT.o SSEServer.o ServerUtil.o CashServer.o PaillierCashServer.o\
	 paillier.o Storage.o DepskyStorage.o RamCloudStorage.o RamCloudClient.o Memcached.o Mie.o\
	 ReedSol.o Config.o -lopencv_core -lopencv_features2d -lcrypto -lz -lgmp -ldl -pthread \
	 -L /opt/jdk1.8.0_91/jre/lib/amd64/server -ljvm -L/RAMCloud/obj.master -lramcloud -lJerasure

debug : Main.debug MIEServer.debug MIEServerMT.debug SSEServer.debug ServerUtil.debug CashServer.debug\
 PaillierCashServer.debug paillier.debug Storage.debug DepskyStorage.debug Mie.debug\
 RamCloudStorage.debug RamCloudClient.debug ReedSol.debug Config.debug Memcached.debug
	/usr/bin/g++-6 -o Server Main.o MIEServer.o MIEServerMT.o SSEServer.o ServerUtil.o CashServer.o PaillierCashServer.o\
	 paillier.o Storage.o DepskyStorage.o RamCloudStorage.o RamCloudClient.o Memcached.o Mie.o\
	 ReedSol.o Config.o -lopencv_core -lopencv_features2d -lcrypto -lz -lgmp -ldl -pthread \
	 -L /opt/jdk1.8.0_91/jre/lib/amd64/server -ljvm -L/RAMCloud/obj.master -lramcloud -lJerasure

Main.o : Main.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/opt/jdk1.8.0_91/include -I/opt/jdk1.8.0_91/include/linux/ -c Main.cpp

MIEServer.o : MIEServer.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/opt/jdk1.8.0_91/include -I/opt/jdk1.8.0_91/include/linux/ -c MIEServer.cpp

MIEServerMT.o : MIEServerMT.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/opt/jdk1.8.0_91/include -I/opt/jdk1.8.0_91/include/linux/ -c MIEServerMT.cpp

SSEServer.o : SSEServer.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -c SSEServer.cpp

ServerUtil.o : ServerUtil.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -c ServerUtil.cpp

CashServer.o : CashServer.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/ssl/include -c CashServer.cpp
PaillierCashServer.o : PaillierCashServer.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -c PaillierCashServer.cpp
paillier.o : paillier.c
	gcc -pthread -c paillier.c
Storage.o: Storage.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/RAMCloud/src -I/RAMCloud/obj.master -I/usr/local/include/jerasure -I /opt/jdk1.8.0_91/include -I /opt/jdk1.8.0_91/include/linux/ -c Storage.cpp
RamCloudUtil.o: RamCloudUtil.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/RAMCloud/src -I/RAMCloud/obj.master -c RamCloudUtil.cpp
DepskyStorage.o: DepskyStorage.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /opt/jdk1.8.0_91/include -I /opt/jdk1.8.0_91/include/linux/ -c DepskyStorage.cpp
RamCloudStorage.o: RamCloudStorage.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/usr/local/include/jerasure -I/RAMCloud/src -I/RAMCloud/obj.master -c RamCloudStorage.cpp
ReedSol.o: ReedSol.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/usr/local/include/jerasure -c ReedSol.cpp
Config.o: Config.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -c Config.cpp
Memcached.o: Memcached.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -c Memcached.cpp
RamCloudClient.o: RamCloudClient.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/RAMCloud/src -I/RAMCloud/obj.master -c RamCloudClient.cpp
Mie.o: Mie.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -c Mie.cpp
clean :
	rm Server *.o

ReedSolMain.o: ReedSolMain.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/usr/local/include/jerasure -g -c ReedSolMain.cpp

ReedSol: ReedSol.o ReedSolMain.o
	/usr/bin/g++-6 -std=c++17 -Wall -I/usr/local/include/jerasure -lJerasure -o ReedSol ReedSol.o ReedSolMain.o

reedsol_clean:
	rm -f ReedSol ReedSol.o ReedSolMain.o

Main.debug : Main.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c Main.cpp

MIEServer.debug : MIEServer.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c MIEServer.cpp

MIEServerMT.debug : MIEServerMT.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c MIEServerMT.cpp

SSEServer.debug : SSEServer.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c SSEServer.cpp

ServerUtil.debug : ServerUtil.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c ServerUtil.cpp

CashServer.debug : CashServer.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c CashServer.cpp

PaillierCashServer.debug : PaillierCashServer.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c PaillierCashServer.cpp

paillier.debug : paillier.c
	gcc -pthread -g -O0 -c paillier.c

Storage.debug: Storage.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/RAMCloud/src -I/RAMCloud/obj.master -I/usr/local/include/jerasure -I /opt/jdk1.8.0_91/include -I /opt/jdk1.8.0_91/include/linux/ -g -c Storage.cpp
RamCloudClient.debug: RamCloudClient.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/RAMCloud/src -I/RAMCloud/obj.master -g -c RamCloudClient.cpp
DepskyStorage.debug: DepskyStorage.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /opt/jdk1.8.0_91/include -I /opt/jdk1.8.0_91/include/linux/ -g -c DepskyStorage.cpp
RamCloudStorage.debug: RamCloudStorage.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/usr/local/include/jerasure -I/RAMCloud/src -I/RAMCloud/obj.master -g -c RamCloudStorage.cpp
ReedSol.debug: ReedSol.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/usr/local/include/jerasure -g -c ReedSol.cpp
Config.debug: Config.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -g -c Config.cpp
Memcached.debug: Memcached.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -g -c Memcached.cpp
Mie.debug: Mie.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -g -c Mie.cpp

run : Server
	LD_LIBRARY_PATH=/usr/local/opencv/opencv3.1/lib/:$$LD_LIBRARY_PATH ./Server $(RUN_ARGS) >teste.txt 2>&1 &

corrupt: corruptmain.o RamCloudStorage.o RamCloudClient.o BackendStorage.o ServerUtil.o ReedSol.o
	/usr/bin/g++-6 -o corrupt RamCloudStorage.o corruptmain.o RamCloudClient.o BackendStorage.o  ServerUtil.o \
	ReedSol.o -lcrypto -L/RAMCloud/obj.master -lramcloud -lz -lJerasure

corruptmain.o: corrupt_ramcloud.cpp
	/usr/bin/g++-6 -o corruptmain.o -std=c++17 -Wall -I/usr/local/include/jerasure -I/RAMCloud/src -I/RAMCloud/obj.master -c corrupt_ramcloud.cpp

rctest: rctest.o RamCloudClient.debug ServerUtil.debug
	/usr/bin/g++-6 -o rctest rctest.o RamCloudClient.o ServerUtil.o -L/RAMCloud/obj.master -lramcloud -lcrypto -lz

rctest.o: rctest.cpp
	/usr/bin/g++-6 -o rctest.o -std=c++17 -I/RAMCloud/src -I/RAMCloud/obj.master -g -c -Wall rctest.cpp

.PHONY: run clean
