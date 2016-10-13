jdk-8u91-linux-x64.tar.gz must be downloaded from http://www.oracle.com/technetwork/java/javase/downloads/java-archive-javase8-2177648.html#jdk-8u91-oth-JPR and placed inside the docker-mie folder

opencv-3.0.0 must be downloaded from https://github.com/Itseez/opencv/archive/3.0.0.zip, decompressed and placed in docker-mie folder.

docker-ramcloud contains the ramcloud coordinator and storage servers. each storage cloud must be running one of these dockers

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

TPM verification is implemented in the client side using C++. The java client access these functions by using JNI. For the verification to succeed the files "uuid", "pubkey" and "hash" must be in the working directory of the client. These files are generated automatically by running the tpm_loader executable on the middleware server. They need to be manually copied to the client in a trusted environment (the tpm_loader itself its only a simulation of what the TPM should actually do and would not be executed in a real use case, the generation of these files is just for convenience as they could be created using the tpm_quote_tools package). On the server side only the tpm emulador and trousers need to be running for the verification to be performed. Trousers needs to be configured to accept remote connections and to allow the quote and loadkey operations remotely. This is done by uncommenting the lines

\# port = 30003

and

\# remote_ops = quote,loadkey

in the tcsd.conf file (usually located in /usr/local/etc/tcsd.conf). In trousers 0.3.13 there is a bug that uncommenting those lines will block all connections, remote and local. The provided version has that bug fixed. More recent versions can be downloaded from "https://sourceforge.net/p/trousers/trousers/ci/master/tree/".

Start the tpm emulator:

sudo tpmd -d -f

-d: debug output (optional)
-f: keep process in foreground (optional, will be moved to background if ommited)

Start trousers:

sudo tcsd -e -f

-f: keep process in foreground (optional, will be moved to background if ommited)

-e: connect to software TPMs over tcp

Client verification is a proof of concept and currently doesn't affect operations, it just outputs to stdout if the verification succeeded and affects an internal variable based on the result, which could be used to perform decisions based on the verification result.