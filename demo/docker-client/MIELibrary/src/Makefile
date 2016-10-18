all:
	g++ -o ../bin/TpmClient.so -Wall -shared -fPIC -I /usr/lib/jvm/java-8-openjdk-amd64/include -I /usr/lib/jvm/java-8-openjdk-amd64/include/linux/ TpmClient.cpp java_wrapper_TpmVerifier.cpp TpmCommon.cpp -ltspi
