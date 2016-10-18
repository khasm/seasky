In testbench2 the middleware and the storage servers are deployed in the same datacenter in the cloud, but different servers.
For depsky only the middleware docker needs to be executed. For ramcloud execute each of the storage_X scripts in a different storage server.

Middleware docker:
./server_testbench2 <backend> [nocache]

Ramcloud dockers:
./storage_X <zookeeper ip:zookeeper port> <own server ip>