jdk-8u91-linux-x64.tar.gz must be downloaded from http://www.oracle.com/technetwork/java/javase/downloads/java-archive-javase8-2177648.html#jdk-8u91-oth-JPR and placed inside the docker-mie folder

opencv-3.0.0 must be downloaded from https://github.com/Itseez/opencv/archive/3.0.0.zip, decompressed and placed in docker-mie folder.

docker-ramcloud contains ramcloud coordinator and storage servers. each storage cloud must be running one of these dockers

starting the docker might be done with a command similar to:

docker run --net=host --name=rc ramcloud rc <cluster id> rczk <ip of middleware server>:2181 rcip <ip of the machine running this docker>

cluster id goes from 1 to 4 and its used to identify storage clouds. rcip must be an address attached to the machine. if the the middleware server is in a different network (for example in different datacenters behind nat) then a tunnel must be created between the two networks (we used OpenSWAN to create tunnels for testbench 3).

the middleware can be run with a command similar to:

docker run --net=host --name=miec mie zk mw miemt ramcloud nocache testbench3

where mw means start the middleware server. everything after are arguments for it, everything before are additional programs for the docker to execute in the background

zk: zookeeper
mc: memcached
rcc: ramcloud coordinator
rcs: ramcloud storage server
rc: ramcloud coordinator and server
ds-local: local depsky storage

some of those programs might require additional arguments. although possible given the correct ip configurations, the ramcloud servers are not meant to be executed in the same machine as the middleware server.

for depsky the middleware server can be run with a command similar to:

docker run --net=host --name=work mie mw miemt depsky nocache testbench3

testbench3 indicates where to store the buckets. there is no need to run other dockers. if a tesbench is not given the local depsky storage is used.

//*****************TPM***********************//

TPM verification is implemented in the client side without extra processes but requires additional support on the server side. after creating the uuid and aik and placing them in the client directory tpm_loader must be executed. this will update the pcr with an hash of the docker inspect mie command. this commands gives metadata information about the image currently available to execute in the docker, including the hashes of the layers that make up the image as well as creation and modification dates. if the overlay driver is available is it possible to generate an hash of the actual contents of the image (this code is commented out as ubuntu 14.04 avaiable on the amazon clouds doesn't support overlay natively). after executing tpm_loader an hash file will be generated. this hash must be placed in the client directory so the client can verify the pcr status.
After this the tpm_server must be executed to act as proxy between the client and the tpm. Because of a bug with trousers remote operations will always fail. Although there is already a version with that bug fixed quote2 operations are not available remotely which forces the execution of the quote operation locally. The verifyquote operation is done on the client side using the uuid, pubkey and hash that were placed there before hand.
Client verification is a proof of concept and currently doesn't affect operations, it just outputs to stdout if the verification succeeded and affects an internal variable based on the result.