#include "TpmClient.h"
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#include <netdb.h>
#include <cstring>
#include <fstream>
#include <memory>

using namespace std;

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

bool verify(const string& address)
{
	int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    struct hostent *server = gethostbyname(address.c_str());
    struct sockaddr_in serv_addr;
    bzero((char *) &serv_addr, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    bcopy((char *)server->h_addr,(char*)&serv_addr.sin_addr.s_addr,server->h_length);
    serv_addr.sin_port = htons(9977);
    if (connect(sockfd,(struct sockaddr*) &serv_addr,sizeof(serv_addr)) < 0)
    	return false;
    uint32_t nonce_size = 20;
    unsigned char nonce[nonce_size];
    for(unsigned i = 0; i < nonce_size; i++){
        nonce[i] = (unsigned char)'0'+i;
    }
    unsigned total = 0;
    while(total < nonce_size) {
        size_t n = send(sockfd, nonce + total, nonce_size - total, 0);
        if (n == (size_t)-1) { break; }
        total += n;
    }
    if(total != nonce_size){
    	close(sockfd);
    	return false;
    }
    ofstream out("nonce");
    out.write((char*)nonce, nonce_size);
    out.close();
    unsigned ans_size = 0;
    uint32_t b = 0;
    unsigned r = 0;
    while (r < sizeof(uint32_t)) {
        ssize_t n = read(sockfd, &b + r, sizeof(uint32_t) - r);
        if (n < 0){
        	close(sockfd);
        	return false;
        }
        r+=n;
    }
    ans_size = ntohl(b);
    if(0 == ans_size)
    	return false;
    char quote[ans_size];
    r = 0;
    while (r < ans_size) {
        ssize_t n = read(sockfd, &quote + r, ans_size - r);
        if (n < 0){
        	close(sockfd);
        	return false;
        }
        r+=n;
    }
    out.open("quote");
    out.write(quote, ans_size);
    out.close();
    string command = read_command("tpm_verifyquote pubkey hash nonce quote");
    if(command.empty())
    	return true;
    else
    	return false;
}