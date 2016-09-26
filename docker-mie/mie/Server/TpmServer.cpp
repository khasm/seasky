#include "ThreadPool.h"
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <unistd.h>
#include <iostream>
#include <cstring>
#include <fstream>

using namespace std;

const size_t BUFFER_SIZE = 20;

/* Executes a bash command and returns both its stdout and stderr in a string */
string read_command(string comm){
	char buffer[128];
	comm.append(" 2>&1");
	string result = "";
	shared_ptr<FILE> pipe(popen(comm.c_str(), "r"), pclose);
	if (!pipe)
		throw runtime_error("popen() failed!");
	while(fgets(buffer, 128, pipe.get()) != NULL)
		result += buffer;
	return result;
}

void clientThread(int newsockfd)
{
	char buffer[BUFFER_SIZE];
	int r = 0;
    while (r < BUFFER_SIZE) {
        ssize_t n = read(newsockfd, buffer + r, BUFFER_SIZE-r);
        if (n < 0){
        	cerr<<"ERROR reading from socket"<<endl;
        	close(newsockfd);
        }
        r+=n;
    }
    ofstream out("nonce");
    out.write(buffer, BUFFER_SIZE);
    out.close();
    string command = read_command("sudo tpm_getquote uuid nonce quote 16");
    if(command.empty()){
    	ifstream in("quote");
    	in.seekg(0, ios::end);
    	size_t quote_size = in.tellg();
    	in.seekg(0, ios::beg);
    	char quote[quote_size];
    	in.read(quote, quote_size);
    	in.close();
    	uint32_t x = htonl(quote_size);
    	unsigned total = 0;
    	while(total < sizeof(uint32_t)) {
	        size_t n = send(newsockfd, &x + total, sizeof(uint32_t) - total, 0);
	        if (n == -1) { break; }
	        total += n;
	    }
	    total = 0;
	    while(total < quote_size) {
	        size_t n = send(newsockfd, quote + total, quote_size - total, 0);
	        if (n == -1) { break; }
	        total += n;
	    }
    }
    else{
    	uint32_t x = 0;
    	unsigned total = 0;
    	while(total < sizeof(uint32_t)) {
	        size_t n = send(newsockfd, &x + total, sizeof(uint32_t) - total, 0);
	        if (n == -1) { break; }
	        total += n;
	    }
    }
    close(newsockfd);
}

int main(int argc, char*argv[])
{
	int n_threads = sysconf(_SC_NPROCESSORS_ONLN);
    
    int sockfd, portno;
    struct sockaddr_in serv_addr;
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0){
        cerr<<"ERROR opening socket"<<endl;
        exit(1);
    }
    memset((char *) &serv_addr, 0, sizeof(serv_addr));
    portno = 9977;
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_addr.s_addr = INADDR_ANY;
    serv_addr.sin_port = htons(portno);
    if (bind((int)sockfd, (const struct sockaddr *) &serv_addr,(socklen_t)sizeof(serv_addr)) < 0){
        cerr<<"ERROR on binding"<<endl;
    }
    listen(sockfd,512);
    ThreadPool pool(/*n_threads*/1);
    while (true) {
        struct sockaddr_in cli_addr;
        socklen_t clilen = sizeof(cli_addr);
        int newsockfd = accept(sockfd, (struct sockaddr *) &cli_addr, &clilen);
        pool.enqueue(clientThread, newsockfd);
    }
}