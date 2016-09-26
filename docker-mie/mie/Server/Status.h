#ifndef MIE_STATUS_H
#define MIE_STATUS_H

namespace MIE{

enum Return_Codes{
	OP_FAILED			= 0,
    NO_ERRORS   		= 1, 
    NOT_FOUND  			= -1,
    NETWORK_ERROR 		= -2,
    MEMCACHED_ERROR 	= -3,
 	NOT_IMPLEMENTED		= -4,
 	RAMCLOUD_EXCEPTION	= -5,
 	INVALID_STATE		= -6,
 	END_OF_FILE			= -7,
 	DEPSKY_ERROR		= -8,
 	NO_QUORUM			= -9,
 	OP_FINISHED			= -10,
};

}//end namespace
#endif