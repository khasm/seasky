Server : Main.o MIEServerMT.o ServerUtil.o Storage.o DepskyStorage.o RamCloudStorage.o \
	RamCloudClient.o ReedSol.o Config.o Memcached.o Mie.o
	/usr/bin/g++-6 -o Server Main.o MIEServerMT.o ServerUtil.o Storage.o DepskyStorage.o RamCloudStorage.o \
	RamCloudClient.o Memcached.o Mie.o ReedSol.o Config.o -lopencv_core -lopencv_features2d \
	-lcrypto -lz -lgmp -ldl -pthread -L /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server -ljvm \
	-L/RAMCloud/obj -lramcloud -lJerasure

debug : Main.debug MIEServerMT.debug ServerUtil.debug Storage.debug DepskyStorage.debug Mie.debug\
 	RamCloudStorage.debug RamCloudClient.debug ReedSol.debug Config.debug Memcached.debug
	/usr/bin/g++-6 -o Server Main.o MIEServerMT.o ServerUtil.o Storage.o DepskyStorage.o RamCloudStorage.o \
	RamCloudClient.o Memcached.o Mie.o ReedSol.o Config.o -lopencv_core -lopencv_features2d -lcrypto \
	-lz -lgmp -ldl -pthread -L /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server -ljvm -L/RAMCloud/obj\
	 -lramcloud -lJerasure

TpmLoader: TpmLoader.o TpmCommon.o
	g++ -o tpm_loader TpmLoader.o TpmCommon.o -ltspi
TpmLoader.o: TpmLoader.cpp
	g++ -std=c++11 -Wall -g -c TpmLoader.cpp
TpmCommon.o: TpmCommon.cpp
	g++ -std=c++11 -Wall -g -c TpmCommon.cpp
Main.o : Main.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/usr/lib/jvm/java-8-openjdk-amd64/include -I/usr/lib/jvm/java-8-openjdk-amd64/include/linux/ -c Main.cpp
MIEServerMT.o : MIEServerMT.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/usr/lib/jvm/java-8-openjdk-amd64/include -I/usr/lib/jvm/java-8-openjdk-amd64/include/linux/ -c MIEServerMT.cpp
ServerUtil.o : ServerUtil.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -c ServerUtil.cpp
Storage.o: Storage.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/RAMCloud/src -I/RAMCloud/obj -I/usr/local/include/jerasure -I /usr/lib/jvm/java-8-openjdk-amd64/include -I /usr/lib/jvm/java-8-openjdk-amd64/include/linux/ -c Storage.cpp
RamCloudUtil.o: RamCloudUtil.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/RAMCloud/src -I/RAMCloud/obj -c RamCloudUtil.cpp
DepskyStorage.o: DepskyStorage.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/lib/jvm/java-8-openjdk-amd64/include -I /usr/lib/jvm/java-8-openjdk-amd64/include/linux/ -c DepskyStorage.cpp
RamCloudStorage.o: RamCloudStorage.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/usr/local/include/jerasure -I/RAMCloud/src -I/RAMCloud/obj -c RamCloudStorage.cpp
ReedSol.o: ReedSol.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/usr/local/include/jerasure -c ReedSol.cpp
Config.o: Config.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -c Config.cpp
Memcached.o: Memcached.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -c Memcached.cpp
RamCloudClient.o: RamCloudClient.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/RAMCloud/src -I/RAMCloud/obj -c RamCloudClient.cpp
Mie.o: Mie.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -c Mie.cpp
clean :
	rm Server *.o

Main.debug : Main.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c Main.cpp
MIEServerMT.debug : MIEServerMT.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c MIEServerMT.cpp
ServerUtil.debug : ServerUtil.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -pthread -g -O0 -c ServerUtil.cpp
Storage.debug: Storage.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/RAMCloud/src -I/RAMCloud/obj -I/usr/local/include/jerasure -I /usr/lib/jvm/java-8-openjdk-amd64/include -I /usr/lib/jvm/java-8-openjdk-amd64/include/linux/ -g -c Storage.cpp
RamCloudClient.debug: RamCloudClient.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/RAMCloud/src -I/RAMCloud/obj -g -c RamCloudClient.cpp
DepskyStorage.debug: DepskyStorage.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/lib/jvm/java-8-openjdk-amd64/include -I /usr/lib/jvm/java-8-openjdk-amd64/include/linux/ -g -c DepskyStorage.cpp
RamCloudStorage.debug: RamCloudStorage.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/usr/local/include/jerasure -I/RAMCloud/src -I/RAMCloud/obj -g -c RamCloudStorage.cpp
ReedSol.debug: ReedSol.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I/usr/local/include/jerasure -g -c ReedSol.cpp
Config.debug: Config.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -g -c Config.cpp
Memcached.debug: Memcached.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -g -c Memcached.cpp
Mie.debug: Mie.cpp
	/usr/bin/g++-6 -std=c++17 -Wall -I /usr/local/opencv/opencv3.1/include -g -c Mie.cpp

.PHONY: run clean
