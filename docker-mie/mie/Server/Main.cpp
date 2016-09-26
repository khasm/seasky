//
//  Main.cpp
//  MIE
//
//  Created by Bernardo Ferreira on 30/04/15.
//  Copyright (c) 2015 NovaSYS. All rights reserved.
//

#include "Server.h"
#include "MIEServerMT.h"
#include <csignal>

using namespace MIE;
using namespace std;

static Server *server;

void signalHandler(int sig){
    cout<<endl<<"Caught SIGINT"<<endl;
    delete server;
    exit(0);
}

int main(int argc, const char * argv[]) {
    signal(SIGINT, signalHandler);
    setvbuf(stdout, NULL, _IONBF, 0);
    if(argc == 2){
        if (strcasecmp(argv[1], "mie") == 0 || strcasecmp(argv[1], "mieMT") == 0){
            printf("Incorrect number of arguments. Please give a server name, e.g. \"MIE\" or \"MIEMT\" and the backend, e.g. \"Depsky\" or \"Ramcloud\"\n");
            return 1;
        }
        else{
            printf("Server command not recognized! Available Servers: \"mieMT\"");
            return 1;
        }
    }
    if(argc >= 3){
        int backend = BACKEND_UNDEFINED;
        bool cache = true;
        int model = 0;
        if(argc == 3){
            if(strcasecmp(argv[2], "depsky") == 0)
                backend = BACKEND_DEPSKY;
            else if(strcasecmp(argv[2], "ramcloud") == 0)
                backend = BACKEND_RAMCLOUD;
        }
        else if(argc == 4){
            if(strcasecmp(argv[2], "depsky") == 0 || strcasecmp(argv[3], "depsky") == 0)
                backend = BACKEND_DEPSKY;
            else if(strcasecmp(argv[2], "ramcloud") == 0 || strcasecmp(argv[2], "ramcloud") == 0)
                backend = BACKEND_RAMCLOUD;

            if(strcasecmp(argv[2], "nocache") == 0 || strcasecmp(argv[3], "nocache") == 0)
                cache = false;
            else if(strcasecmp(argv[2], "cache") == 0 || strcasecmp(argv[2], "cache") == 0){}

            if(strcasecmp(argv[2], "testbench2") == 0 || strcasecmp(argv[3], "testbench2") == 0)
                model = 1;
            else if(strcasecmp(argv[2], "testbench3") == 0 || strcasecmp(argv[3], "testbench3") == 0)
                model = 2;
        }
        else if(argc == 5){
            if(strcasecmp(argv[2], "depsky") == 0 || strcasecmp(argv[3], "depsky") == 0 ||
                strcasecmp(argv[4], "depsky") == 0)
                backend = BACKEND_DEPSKY;
            else if(strcasecmp(argv[2], "ramcloud") == 0 || strcasecmp(argv[2], "ramcloud") == 0 || 
                strcasecmp(argv[4], "ramcloud") == 0)
                backend = BACKEND_RAMCLOUD;

            if(strcasecmp(argv[2], "nocache") == 0 || strcasecmp(argv[3], "nocache") == 0 ||
                strcasecmp(argv[4], "nocache") == 0)
                cache = false;
            else if(strcasecmp(argv[2], "cache") == 0 || strcasecmp(argv[2], "cache") == 0 ||
                strcasecmp(argv[4], "cache") == 0){}

            if(strcasecmp(argv[2], "testbench2") == 0 || strcasecmp(argv[3], "testbench2") == 0 ||
                strcasecmp(argv[4], "testbench2") == 0)
                model = 1;
            else if(strcasecmp(argv[2], "testbench3") == 0 || strcasecmp(argv[3], "testbench3") == 0 ||
                strcasecmp(argv[4], "testbench3") == 0)
                model = 2;
        }
        if (strcasecmp(argv[1], "mieMT") == 0){
            if(backend == BACKEND_UNDEFINED){
                printf("Unrecognized backend. Available Backends: \"Depsky\" and \"RamCloud\"\n");
                return 1;
            }
            server = new MIEServerMT(backend, cache, model);
        }
        else{
            printf("Server command not recognized! Available Servers: \"mieMT\"");
            return 1;
        }
    }
    else{
        printf("Incorrect number of arguments. Please give a server name, e.g. \"MIEMT\" and the backend, e.g. \"Depsky\" or \"Ramcloud\"\n");
        return 1;
    }
    server->startServer();
    return 0;
}

