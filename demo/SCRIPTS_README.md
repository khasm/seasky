#Introduction


Scripts allow testbenches to be executed in sequence and to create graphs about several measurements without the need to pass all the commands as arguments. Instead the commands are in a file and the only runtime arguments needed are the script flag and script name (with the possibility of specifying a specific folder to look for scripts). Any testset created with a script can also be created by using command line arguments, but it's easier to just write a script and it allows for easier replication of tests.


#Syntax


Scripts follow a syntax of one option per line with the option name being the first word, followed by a separator ('=') and the option value. The option value itself might be just a single word or an complex set of instructions. The class mie.utils.TestSetGenerator is responsible for the initial parsing of the script and by the removal of comments. Comments are made with either "//" for single line comments and "/*" and "*/" for multi line comments. Comments can be made anywhere in the script as they will be removed before the actual parsing of options. For example:

ip=local/*This is a random comment*/host

ip=local/*
This
is
a
random
comment
*/host

will lead to:

ip=localhost

If using "//" then everything afterwards on that line will be ignored.

#Options

Unless otherwise specified all options are supposed to be used only once. If they more than once in a script only the last one will be considered.

ip: Specifies the ip of the server.

threads: Number of clients to run. Multiple values can be specified in a single line. The value 1-5 will execute the testset with 1, 2, 3, 4 and 5 clients. If it's instead 1-5 step 2 then the testset will be executed with 1, 3 and 5 clients. The range limits are always included, so 2-5 step 2 would lead to 2, 4 and 5 clients. A sequence can be also be indicated by commas like 1,3,4,9 which would execute 1, 3, 4 and 9 clients. Both options can be combined like 1-5,9,7,15-20 step 2 which would lead to 1,3,5,9,7,15,17,19 and 20 clients

path: Folder where to look for datasets. If the client runs inside a docker then this folder must be acessible from the docker, and the path specified in thi option must be the path to it in the docker.

mode: Must be explicit or descriptive. When running a testset with command line arguments it will be explicit by default, if running from a script it will be descriptive. This indicates wether the commands specified in the 'ops' options are to be interpreted literally (explicit) or just hints on the proportions and the actual commands generated automatically (descriptive). This also influences the server behaviour after a 'ops' sequence is executed. In explicit mode no changes are made to the server while in descriptive mode the server resets and all data in it as well as measurements are erased. This allows to chain several 'ops' commands without having to manually restart the server. This behaviour can however be changed and it's possible to indicate that no reset should be made in descriptive mode, or that it should be made in explicit. It's not possible to specify a mode, a 'ops' option, another mode followed by a second 'ops'. Both 'ops' will run on the second specified mode.

cache: Wether to use cache on the client or the server. This can be indicated by client-server (to use cache on both sides), just client or server, none to not use cache at all. It's possible to specify a sequence with commas. For example client-server,none,client will run the testset first with cache on both sides, then without any cache and finally with cache only on the client.

compare: Runs a local comparision of CBIR and AES.

ops: Specifies the actual operations to run. These options are handled in the mie.utils.TestSet class. A more detailed explanation is presented in the next section.

output: Specifies how to present the results. The default is to write on the screen. The other options are none and create a graph. Currently writting on the screen will always be made unless the output is completely disabled first. Graph options are specified in detail in the last section.

#Ops

There are several commands that can be specified in the 'ops' option and the argument they receive can change depending on the mode of the script. Furthermore, not all of them will result in an action of the client to the server. Those will be explained first.

#Management ops

title: Specifies the name of this set of operations. This is used when creating graphs to identify data series that are grouped by the sequence of operations performed. If it's ommited and is needed then the full sequence of operations as written in the script will be used. It has no effect if graphs are not being created.

wipe/nowipe: Indicates that the server must reset (or not reset) after this sequence is completed, regardless of mode. While this produces an action from the client to the server is placed here as this reset will always be done after all the commands are done, will be executed by a single client (so even if there are 20 clients only one wipe command will be sent) and it's effects are no longer counted for any measurements.

wait: Receives a number as argument. Clients will execute all operations up to this one, then sleep for the indicated amount (in seconds) before continuing. If at any point all threads are paused in a wait command then the total time measurement stops running and only starts when a thread finishes the wait and resumes execution.

sync: Synchronizes all clients. Only has meaning when running more than one client. Clients that reach a sync command will stop until all others have also reached the sync command. Only after all clients have reached it will they continue.

base: Receives as argument a number with a suffix of either 'u' or 'm'. The number represents the number of documents to upload and the suffix if they are unstructured ('u') or mime ('m'). This is similar to executing "add 0 X; index w; clear" with X being the number indicated with the exception that times are not counted during it's execution. It will always be executed by a single client, regardless of how many clients were specified and it's used mainly to setup an initial state before running other operations, for example running search tests.

#Test ops

add/addmime: Uploads either unstructured or mime documents. In explicit mode it receives either one or two arguments. If it's one it will upload a document with that id, if it's two it will upload all documents in the range specified by the arguments, inclusive. In descriptive mode it receives one argument which is the proportion of uploads to be made.

search/searchmime: Similar to add/addmime but instead of uploading it searches for documents.

get/getmime: Similar to add/addmime but can receive an extra argument in either mode which specifies the cache mode to be used. If any cache mode is specified then the range of ids specified by arguments will work just as a hint of which ids can be selected in other to create a sequence of retrievals that satisfies as close as possible the target hit ratio of the cache. In descriptive mode the range is taken from the documents upload during a previous 'base' comand and not from the arguments.

index: Starts an indexing operation on the server. The argument 'w' or 'wait' might be used to tell the client to wait until the indexing is finished. In this case the time used for all indexing operations reported by the server will then be substracted from the times measured by the client, reducing the error created by having the client wait for the indexing to finish since the indexing is done completely server side without any extra input from the clients.

reset: Clears the client and server cache.

clear: Resets all measurements.

An important note is required for the descriptive mode. The arguments that each operation receives is a proportion and not a percentage. So the following examples will all result in 50% of uploads and 50% of downloads:

add 50; get 50
add 1; get 1
add 100000000; get 100000000
add 0.1; get 0.1

The following examples will result in 100% searches:

search 1
search 923537443
search 0.1

and they are all equivalent to:

search 1; add 0; get 0
search 923537443; add 0; get 0
search 0.1; add 0; get 0

Proportions are calculated by the sum of all arguments used in all operations then calculating the percentage on the argument of each operations. So the example

add 3; get 2; search 5

would have a total of 10. 30% would be uploads (3/10), 20% would be downloads (2/10) and 50% would be searches (5/10).

#Graphs

Graphs are created by using GNUPlot. This is done by creating a data file, a GNUPlot script and then executing the program. All options are handled in the class mie.utils.GNUPlot. Graph options follow the syntax of <option>:<value>. Available options are:

x: Indicates which data to put on the X axis. Can be number of clients or sequence of operations at this time.

serie: Indicates how to group up different testsets. Has the same valid options as 'x'.

y: Indicates which data will be in the Y axis. Can be total time, operations per time unit, total bytes uploaded, searched or downloaded, bytes uploaded, searched or downloaded per time unit, search hit ratio and average score of documents searched. For the time unit they can be specified in nanoseconds, milliseconds, seconds, minutes or hours. For byte values they can be specified in byte, kilobyte, megabyte or gigabyte. The default is seconds and bytes. Hit ratio and average score have no units. This doesn't cover all the measurements that are done, however adding new options is just a matter of adding the measurement in the list of operations and associate it with the correct measurement (see the enum GraphOption in GNUPlot.java for examples).

seriename: This will overwrite all data series names. Should be used when there is only one data serie to place in the data file but the graph will be composed of several data files. See the depsky/ramcloud example in the scripts folder.

title: Title of the graph

outfile: File name that will be used on the data files, GNUPlot script and the final graph.

outdir: Folder where to write the output files.

extra: Extra data files that are required to create the final graph. If those files are not found then only the data file will be created.

extradir: Folder where to look for the extra files.

#Examples

The scripts folder has some examples, and the output folder contain the data files, scripts and graphs created by using those scripts. Here is only a brief explanation of each example.

add_search: The output files are add_search.dat (data file), add_search.gnuplot (GNUPlot script) and add_search.png (graph file). This script runs 3 sequences of operations, starting with 60% uploads and 40% searches and finishing with 40% uploads and 60% searches. It uses an initial dataset of 100 documents and will run all 3 sequences with 1,2,3,4 and 5 clients (resulting in a total of 15 testsets, it might take a while to finish). The graph created shows the relation between the number of operations per second and the number of threads for each sequence.

precision: The output files are precision_1.dat, precision_2.dat (data files), precision_1.gnuplot, precision_2.gnuplot (GNUPlot scripts), precison_1.png and precision_2.png (graph files). This is an example of creating several graphs from a single script execution. The first graph (precision_1) compares the precision between mime and unstructured documents while the second (precision_2) compare the time needed to run the tests.

depsky/ramcloud: This is an example of using two scripts to generate a single graph. The script files are depsky and ramcloud and the output files are ramcloud.dat, depsky.dat, depsky.gnuplot and depsky.png. In this case the output files are dependent on the order that the scripts are executed. In the depsky script the graph options state that it requires the file ramcloud.dat. In the ramcloud script it states that it requires the depsky.dat file. This makes it so the script that runs first will fail to create a graph, but will still create a data file. The second script will then find the required data file and create the graph. The graph compares the time taken for several operations between depsky and ramcloud. This comparision required two scripts as the server only runs one backend. Running both backends would affect the performance and consequently the results of the tests, so to run this kind of tests the server must be shutdown and restarted with the other backend after the first set of tests.