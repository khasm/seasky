In testbench3 the middleware and the storage servers are deployed in different datacenters.
For depsky only the middleware docker needs to be executed. For ramcloud execute each of the storage_X scripts in a different storage server. For Amazon EC2 tunnels must be estabilshed between the different VPCs beforehand or the middleware won't be able to communicate with the storage servers

Middleware docker:
./server_testbench3 <backend> [nocache]

Ramcloud dockers:
./storage_X <zookeeper ip:zookeeper port> <own server ip>