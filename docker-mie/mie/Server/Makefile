Server : Main.o MIEServerMT.o ServerUtil.o Storage.o DepskyStorage.o RamCloudStorage.o \
	RamCloudClient.o ReedSol.o Config.o Memcached.o Mie.o
	/usr/bin/g++-6 -o Server Main.o MIEServerMT.o ServerUtil.o Storage.o DepskyStorage.o RamCloudStorage.o \
	RamCloudClient.o Memcached.o Mie.o ReedSol.o Config.o -lopencv_core -lopencv_features2d \
	-lcrypto -lz -lgmp -ldl -pthread -L /opt/jdk1.8.0_91/jre/lib/amd64/server -ljvm \
	-L/RAMCloud/obj.master -lramcloud -lJerasure

debug : Main.debug MIEServerMT.debug ServerUtil.debug Storage.debug DepskyStorage.debug Mie.debug\
 	RamCloudStorage.debug RamCloudClient.debug ReedSol.debug Config.debug Memcached.debug
	/usr/bin/g++-6 -o Server Main.o MIEServerMT.o ServerUtil.o Storage.o DepskyStorage.o RamCloudStorage.o \
	RamCloudClient.o Memcached.o Mie.o ReedSol.o Config.o -lopencv_core -lopencv_features2d -lcrypto \
	-lz -lgmp -ldl -pthread -L /opt/jdk1.8.0_91/jre/lib/amd64/server -ljvm -L/RAMCloud/obj.master\
	 -lramcloud -lJerasure

TpmServer: TpmServer.cpp
	/usr/bin/g++-6 -o tpm_server TpmServer.cpp -pthread
TpmClient: TpmClient.o TpmMain.o
	/usr/bin/g++-6 -o tpm_client TpmClient.o TpmMain.o
TpmClient.o: TpmClient.cpp
	/usr/bin/g++-6 -std=c++11 -Wall -g -c TpmClient.cpp
TpmMain.o: TpmMain.cpp
	/usr/bin/g++-6 -std=c++11 -Wall -g -c TpmMain.cpp
TpmLoader: TpmLoader.o TpmCommon.o
	/usr/bin/g++-6 -o tpm_loader TpmLoader.o TpmCommon.o -ltspi
TpmLoader.o: TpmLoader.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -g -c TpmLoader.cpp
TpmVerifier: TpmVerifier.o TpmTest.o TpmCommon.o
	/usr/bin/g++-6 -o tpm_verifier TpmVerifier.o TpmTest.o TpmCommon.o -ltspi
TpmTest.o: TpmTest.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -g -c TpmTest.cpp
Main.o : Main.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/opt/jdk1.8.0_91/include -I/opt/jdk1.8.0_91/include/linux/ -c Main.cpp
MIEServerMT.o : MIEServerMT.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/opt/jdk1.8.0_91/include -I/opt/jdk1.8.0_91/include/linux/ -c MIEServerMT.cpp
ServerUtil.o : ServerUtil.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -c ServerUtil.cpp
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
TpmCommon.o: TpmCommon.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -g -c TpmCommon.cpp
TpmVerifier.o: TpmVerifier.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -g -c TpmVerifier.cpp
clean :
	rm Server *.o

Main.debug : Main.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c Main.cpp
MIEServerMT.debug : MIEServerMT.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c MIEServerMT.cpp
ServerUtil.debug : ServerUtil.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c ServerUtil.cpp
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

.PHONY: run clean
