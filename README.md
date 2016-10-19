# Building dockers

There are four dockers provided: ramcloud, depsky, middleware and client demo. They can be built by using the command:

./build_dockers all

Individual dockers might be build by passing the arguments "ramcloud", "depsky", "mw", "client". Some dependecies are missing from the Git repository due to size, namely the OpenCV packages. The script will prompt for download if they aren't found, but they can be manually downloaded and extracted to the correct paths. The middleware docker requires OpenCV 3.0.0 in the folder opencv-3.0.0 in docker-mie which can be downloaded from https://github.com/Itseez/opencv/archive/3.0.0.zip. The client requires OpenCV 2.4.10 in the folder opencv-2.4.10 in demo/docker-client and can be downloaded from http://downloads.sourceforge.net/project/opencvlibrary/opencv-unix/2.4.10/opencv-2.4.10.zip.

The middleware docker contains all the server components, including ramcloud servers and depsky and so perform the role of a storage docker if required. The dockers ramcloud and depsky contain only the ramcloud servers and depsky respectively. They can only be used as storage dockers however they are smaller and faster to build. The ramcloud dockers are required for the ramcloud backend to work, however the depsky dockers were made for test purposes only. They only execute the local storage demo program to which the middleware can connect without using regular storage clouds. Remote "local" storage can be done by deploying a depsky docker in a server (or four servers) and passing their IPs to the middleware docker at the start. No performance tests were performed in this configuration.

# Running the dockers

To execute the depsky docker no arguments are used. The command line is:

docker run --rm --net=host --name=depsky depsky

--rm: will remove the container after execution stops. Can be ommited for persistence.
--net=host use the host network, allows for the middleware to easily connect to the server running inside the docker
--name=DepSky name of the container for easier management

To to stop the docker the command is:

docker stop DepSky

To restart, if the --rm flag is ommited, the command is:

docker start DepSky

The flag -a might be added to prevent the docker from going into the background. To remove the container if the --rm flag is ommited:

docker rm DepSky

The ramcloud docker has several arguments that can be used:

zk: Starts a zookeeper server. Deployed in the middleware in our architecture so not used in the tests but left as an option.
rcc: Starts a ramcloud coordinator.
rcs: Starts a ramcloud storage server
rc: Starts a ramcloud coordinator and a storage server. Equivalent to using both rcc and rcs.
rczk: Defines the IP address and port of the zookeeper server in the format "ip_address:port".
rcip: Defines the IP of the coordinator and storage servers
rccp: Defines the port of the coordinator.
rcsp: Defines the port of the storage server.

The rcc, rcs and rc arguments have a mandatory option afterwards which is the cluster id. Ramcloud servers are organized in clusters, storage servers will attempt to communicate with the coordinator responsible for their cluster id and will ignore all coordinators and storage servers that belong to another cluster. For our tests we created 4 clusters, one in each datacenter, with ids from 1 to 4. For testbench1 only one cluster with id 0 was used. To start the ramcloud storage in a datacenter the command line would be:

docker run --rm --net=host --name=rc ramcloud rc <cluster id> rczk <ip of middleware:port> rcip <ip of the server>

The IP provided to rcip is the IP of the server where the docker is running, and must be reacheable by the middleware server, as it will be that IP that it will try to connect. If the ramcloud servers and the middleware are in different networks, for example different datacenters like in testbench3, then it's necessary to create a tunnel between the networks so that the middleware can connect directly to the server. OpenSWAN was used to make testbench3 possible to perform.

The middleware docker supports the same options as the ramcloud docker plus:

mc: Starts the memcached server.
ds-local: Starts a local depsky server.

Both flags can be ommited, and ds-local doesn't need to be used when using the depsky docker.

Furthermore it also supports the following middleware options:

backend: Must be "depsky" or "ramcloud". Can't be ommited.
nocache: Disables the use of cache. Mandatory if the mc flag is ommited.
testbench: Can be testbench2 or testbench3. 
dsips: Must be followed by 1 or 4 and the same number of IP addresses, ie: dsips 1 127.0.0.1, or dsips 4 127.0.0.1 127.0.0.2 127.0.0.3 127.0.0.4

For the ramcloud backend testbench2 and testbench3 are equivalent as it's the clouds where the dockers are deployed that determines which test is done. For DepSky testbench2 will create buckets in the same datacenter as the middleware and testbench3 will create buckets in different datacenters. If this flag is ommited it will default to testbench1. In this case RamCloud will assume a single cluster and will make the distition between storage server by using tables. For DepSky it will use the local storage server provided with it. In case this local server is deployed in a different docker, possibly in a different server, the dsips flag can be used to indicate the IP addresses in which the servers are running.

The client demo supports the following options:

add: Performs an upload test of 1000 documents
index: Performs an index test
search: Performs a search test
get: Performs a download test
print: print times and statistics from the server
clear: resets times and statistics in the server
full: performs a full set of tests: add, index, search and get

For the get test it is possible to select the options:

cache_client80: will perform a download with a cache on the client side with 80% hit rate
cache_client100: will perform a download with a cache on the client side with 100% hit rate
cache_server80: will perform a download with a cache on the server side with 80% hit rate
cache_server100: will perform a download with a cache on the server side with 100% hit rate
double_cache: will perform a download with a cache on the client and on the server, both with 80% hit rate

These options can be ommited, in which case it will default to not using the cache. These selections must be used in conjuction with selecting to use cache or not on the server, as they will only enable or disable the cache on the client side and select a sequence of downloads that will get the desired hit rate on the cache. In some cases extra extra downloads of files might be done to preload the files in the cache for the desired hit rates. When ommiting these flags the sequence doesn't contain any repeated documents which will cause an hit rate of 0% regardless of the settings used in the server or client.

When using the full option the client times for the index will not be correctly measured as the client doesn't need to wait for the index operation to finish, however to be able to perform the following tests the server must have finish the index operation. Because of this the client will wait for the index to finish causing a inflated time measurement. This doesn't affect the server times however, and can be ignored as the index operation for the client consists in sending 3 bytes and receiving 1 byte. Any client measurement for this operation would be mostly derived from the RTT as no computation is performed client side. To perform an index without having the client wait for the server then the commands "add index" can be used, or just index if the upload was already done. The full option also accepts the same get options for the cache.

The IP of the middleware server can be defined with the flag "ip <address>" before the commands.
Examples:

Perform a full test, no cache

docker run --rm --net=host --name=client mie_client ip <middleware ip> full

Perform an upload and index without waiting and check for the times of indexing

docker run --rm --net=host --name=client mie_client ip <middleware ip> add clear index
<wait for indexing to finish>
docker run --rm --net=host --name=client mie_client ip <middleware ip> print

The "clear" between "add" and "index" will clear the times affected by the upload so that the print command will show only the times of the index. The add command itself will print the times when it's done so those values aren't lost. The "full" command will call clear when necessary.

docker-ramcloud contains the ramcloud coordinator and storage servers. each storage cloud must be running one of these dockers

Some scripts in the demo folder provide shortcuts to start the dockers in the settings used for the tests. The "uuid", "pubkey" and "hash" files are related to the TPM and must be replaced by the correct ones (described in the next section) before building the docker for the TPM verification to work.

# TPM

TPM verification is implemented in the client side using C++. The java client access these functions by using JNI. For the verification to succeed the files "uuid", "pubkey" and "hash" must be in the working directory of the client. These files are generated automatically by running the tpm_loader executable on the middleware server (docker-mie/mie/Server/tpm_loader, to compile use "make TpmLoader"). They need to be manually copied to the client in a trusted environment (the tpm_loader itself its only a simulation of what the TPM should actually do and would not be executed in a real use case, the generation of these files is also just for convenience as they could be created using the tpm_quote_tools package). On the server side only the tpm emulador and trousers need to be running for the verification to be performed. Trousers needs to be configured to accept remote connections and to allow the quote and loadkey operations remotely. This is done by uncommenting the lines

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