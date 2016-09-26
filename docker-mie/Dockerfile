FROM ramcloud:latest
RUN apt-get update && apt-get install -y build-essential cmake libgtk2.0-dev pkg-config libavcodec-dev libavformat-dev libswscale-dev python-dev python-numpy libtbb2 libtbb-dev libjpeg-dev libpng-dev libtiff-dev libjasper-dev libdc1394-22-dev automake libgmp-dev libtool software-properties-common && add-apt-repository ppa:ubuntu-toolchain-r/test && apt-get update && apt-get install -y gcc-6 g++-6
COPY ./opencv-3.0.0 /opencv-3.0.0
WORKDIR "/opencv-3.0.0"
RUN cmake . && make && make install
COPY ./jdk-8u91-linux-x64.tar.gz /jdk-8u91-linux-x64.tar.gz
WORKDIR "/"
RUN tar -xzvf jdk-8u91-linux-x64.tar.gz && mv jdk1.8.0_91 /opt && update-alternatives --install /usr/bin/java java /opt/jdk1.8.0_91/bin/java 1 && update-alternatives --install /usr/bin/javac javac /opt/jdk1.8.0_91/bin/javac 1 && update-alternatives --set java /opt/jdk1.8.0_91/bin/java && update-alternatives --set javac /opt/jdk1.8.0_91/bin/javac
COPY ./memcached /memcached
WORKDIR "/memcached"
RUN ./autogen.sh && ./configure && make
WORKDIR "/"
COPY ./gf-complete /gf-complete
WORKDIR "/gf-complete"
RUN ./autogen.sh && ./configure && make && make install
WORKDIR "/"
COPY ./jerasure /jerasure
WORKDIR "/jerasure"
RUN autoreconf --force --install && ./configure && make && make install
WORKDIR "/"
COPY ./mie /mie
WORKDIR "/mie/Server"
RUN make CC=/usr/bin/g++-6 && useradd jj
WORKDIR "/"
COPY ./start_script /
ENTRYPOINT ["/start_script"]
