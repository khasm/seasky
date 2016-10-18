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
    if(argc >= 2){
        int current_arg = 1;
        int backend = BACKEND_UNDEFINED;
        bool cache = true;
        int model = 0;
        vector<string> ips;
        while(argc > current_arg){
            if(strcasecmp(argv[current_arg], "depsky") == 0){
                backend = BACKEND_DEPSKY;
                current_arg++;
            }
            else if(strcasecmp(argv[current_arg], "ramcloud") == 0){
                backend = BACKEND_RAMCLOUD;
                current_arg++;
            }
            else if(strcasecmp(argv[current_arg], "nocache") == 0){
                cache = false;
                current_arg++;
            }
            else if(strcasecmp(argv[current_arg], "testbench2") == 0){
                model = 1;
                current_arg++;
            }
            else if(strcasecmp(argv[current_arg], "testbench3") == 0){
                model = 2;
                current_arg++;
            }
            else if(strcasecmp(argv[current_arg], "dsips") == 0){
                if(current_arg + 1 >= argc){
                    printf("Must provide at least one ip with dsips option: dsips <1|4>\
                     <list of ips>\n");
                    return 1;
                }
                int n_ips = stoi(argv[current_arg + 1]);
                if(current_arg + 1 + n_ips >= argc){
                    printf("Number of ips do not match\n");
                    return 1;
                }
                for(int i = 0; i < n_ips; i++){
                    ips.push_back(argv[current_arg + 2 + i]);
                }
                current_arg += 2 + n_ips;
            }
        }
        if(BACKEND_UNDEFINED == backend){
            printf("Backend was not defined. It must be one of depsky or ramcloud\n");
            return 1;
        }
        server = new MIEServerMT(backend, cache, model, ips);
        server->startServer();
    }
    else{
        printf("Usage: %s <backend> [nocache] [testbench2|testbench3] [dsips <1|4> <list of ips>\n", argv[0]);
        return 1;
    }
    return 0;
}

