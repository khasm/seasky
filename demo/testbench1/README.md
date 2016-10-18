Testbench1 consists of all the middleware and storage components running in a single server and the client running in another machine. The middleware docker must be deployed in the server, the ramcloud and depsky dockers aren't necessary.

In the server execute:
./server_testbench1 <backend> [nocache]

In the client execute
./client <server ip> [cache_mode]