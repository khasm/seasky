//
//  Server.h
//  MIE
//
//  Created by Bernardo Ferreira on 11/01/16.
//  Copyright © 2016 NovaSYS. All rights reserved.
//

#ifndef Server_h
#define Server_h

namespace MIE{

class Server {
    
public:
    virtual void startServer() = 0;
    virtual ~Server(){};
};

}//end namespace
#endif /* Server_h */
