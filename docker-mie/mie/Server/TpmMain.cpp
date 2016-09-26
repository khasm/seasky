#include "TpmClient.h"
#include <iostream>

using namespace std;

int main(int argc, char* argv[])
{
	if(verify("52.57.140.37"))
		cout<<"Success"<<endl;
	else
		cout<<"Failure"<<endl;
}