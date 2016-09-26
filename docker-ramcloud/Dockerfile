FROM ubuntu:16.04
RUN apt-get update && apt-get install -y build-essential git-core doxygen libpcre3-dev protobuf-compiler libprotobuf-dev libcrypto++-dev libevent-dev libboost-all-dev libgtest-dev libzookeeper-mt-dev zookeeper libssl-dev --no-install-recommends
COPY ./ramcloud /RAMCloud
WORKDIR "/RAMCloud"
RUN make EXTRACXXFLAGS="-DIBV_QPT_RAW_ETH=IBV_QPT_RAW_PACKET" -j2 DEBUG=no
COPY ./start_script /start_script
ENTRYPOINT ["/start_script"]

