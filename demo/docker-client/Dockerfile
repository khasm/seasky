FROM ubuntu:16.04
RUN apt-get update && apt-get install -y openjdk-8-jdk automake autoconf libgtk2.0-dev pkg-config libtool libssl-dev build-essential python-dev python-numpy libtbb2 libtbb-dev libjpeg-dev libpng-dev libtiff-dev libjasper-dev libdc1394-22-dev cmake git libavcodec-dev libavformat-dev libswscale-dev ant libtspi-dev libtspi1 gnuplot
COPY ./trousers-0.3.13 /trousers
WORKDIR "/trousers"
RUN ./bootstrap.sh && ./configure && make && make install
COPY ./opencv-2.4.10 /opencv-2.4.10
WORKDIR "/opencv-2.4.10"
RUN mkdir build
WORKDIR "/opencv-2.4.10/build"
RUN export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 && cmake -Wno-dev -DBUILD_SHARED_LIBS=OFF .. && make && make install && cp /usr/local/share/OpenCV/java/opencv-2410.jar /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/ext/
COPY ./MIECryptoProvider /MIECryptoProvider
COPY javax.mail.jar /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/ext/
COPY bcprov-ext-jdk15on-153.jar /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/ext/
WORKDIR "/MIECryptoProvider"
RUN javac -d bin -cp /usr/local/share/OpenCV/java/opencv-2410.jar @java-files && cd src && make && cd ../bin && jar cvf MIEProvider.jar @class-files && cp MIEProvider.jar /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/ext/
COPY ./update_java.security ./update_java.security
RUN chmod +x ./update_java.security && ./update_java.security
#COPY ./fix-dataset /fix-dataset
#COPY ./datasets /datasets
#RUN chmod +x /fix-dataset && /fix-dataset
COPY ./MIELibrary /MIELibrary
WORKDIR "/MIELibrary"
RUN cd src && make && cd .. && javac -d bin -cp /usr/local/share/OpenCV/java/opencv-2410.jar `find src -name *.java`
COPY ./keys /MIELibrary/keys
COPY ./run_tests /MIELibrary/run_tests
RUN chmod +x ./run_tests
COPY ./uuid /MIELibrary/
COPY ./pubkey /MIELibrary/
COPY ./hash /MIELibrary/
ENTRYPOINT ["/MIELibrary/run_tests"]

