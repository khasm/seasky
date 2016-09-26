#include <iostream>
#include <stdexcept>
#include <stdio.h>
#include <string>
#include <memory>
#include <fstream>
#include <iconv.h>
#include <langinfo.h>
#include <string.h>

#include <tss/tspi.h>
#include <trousers/trousers.h>
#include "TpmCommon.h"


#define DBG(message, tResult) printf("(Line %d, %s) %s returned 0x%08x. %s.\n\n",__LINE__,__func__,message, tResult,(char *)Trspi_Error_String(tResult))
/*
 * Load hash values in TPM. Should actually be done by the TPM as there is no chain of trust here.
 */

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

char hex2bin(char hex)
{
	char bin = 0;
	if('a' <= hex && 'f' >= hex){
		bin = hex - 'a' + 10;
	}
	else if('A' <= hex && 'F' >= hex){
		bin = hex - 'A' + 10;
	}
	else if('0' <= hex && '9' >= hex){
		bin = hex - '0';
	}
	return bin;
}

char* hexString2bin(string hex, char* bin)
{
	if(hex.size()%2 != 0){
		hex.insert(hex.begin(), '0');
	}
	int index = 0;
	for(size_t i = 0; i < hex.size() - 1; i+=2){
		char b = hex2bin(hex[i]);		
		bin[index] = b << 4;
		b = hex2bin(hex[i+1]);
		bin[index] = bin[index] | b;
		index++;
	}
	return bin;
}

int main(int argc, char*argv[])
{
	/*string root_dir_path = read_command("docker inspect mie | grep RootDir");
	size_t pos = root_dir_path.find("\"RootDir\": \"");
	if(pos == string::npos){
		cerr<<"Couldn't find root dir for docker image"<<endl;
		exit(1);
	}
	size_t offset = pos + strlen("\"RootDir\": \"");
	string root_path = root_dir_path.substr(offset, root_dir_path.size() - offset - 2);
	string hash_raw = read_command("find "+root_path+
		" -type f -print0 | sort -z | xargs -0 sha1sum | sha1sum");*/
	string hash_raw = read_command("docker inspect mie | sha1sum");
	string hash = hash_raw.substr(0, 40);
	char bin_hash[20];
	hexString2bin(hash, bin_hash);
	
	TSS_HCONTEXT hContext;
	TSS_HTPM hTPM;
	TSS_RESULT result;

	result = Tspi_Context_Create(&hContext);
	if(result){
		DBG("Create Context", result);
		exit(1);
	}

	result = Tspi_Context_Connect(hContext, NULL);
	if(result){
		DBG("Context Connect", result);
		exit(1);
	}

	result=Tspi_Context_GetTpmObject(hContext,&hTPM);
	if(result){
		DBG("Get TPM Handle", result);
		exit(1);
	}
	//reset pcr to make sure it is in a known and predictable state
	TSS_HPCRS hPcrs;
	result = Tspi_Context_CreateObject(hContext, TSS_OBJECT_TYPE_PCRS, 0, &hPcrs);
	if(result){
		DBG("Create HPCRS", result);
		exit(1);
	}
	result = Tspi_PcrComposite_SelectPcrIndex(hPcrs, 16);
	if(result){
		DBG("Select PCR", result);
		exit(1);
	}
	result = Tspi_TPM_PcrReset(hTPM, hPcrs);
	if(result){
		DBG("Reset PCR", result);
		exit(1);
	}
	//write pcr
	UINT32 PCR_result_length;
	BYTE *Final_PCR_Value;

	result = Tspi_TPM_PcrExtend(hTPM, 16, 20, (BYTE*)bin_hash, NULL, &PCR_result_length,
		&Final_PCR_Value);
	if(result){
		DBG("Write PCR", result);
		exit(1);
	}

	//get pcr hash value
    ifstream in("uuid");
    in.seekg(0, ios::end);
    size_t uuid_len = in.tellg();
    TSS_UUID uuid;
    if(sizeof(uuid) != uuid_len){
        in.close();
        return false;
    }
    in.seekg(0, ios::beg);
    in.read((char*)&uuid, sizeof(uuid));
    in.close();
 
	TSS_VALIDATION valid;
    //generate nonce
    UINT32 nonce_size = 20;
    BYTE nonce[nonce_size];
    for(unsigned i = 0; i < nonce_size; i++){
        nonce[i] = (BYTE)'0';
    }
    valid.ulExternalDataLength = nonce_size;
    valid.rgbExternalData = nonce;
    //select pcrs
    vector<int> pcrs;
    pcrs.push_back(16);
    //get quote
    result = getQuote(uuid, "", pcrs, valid);
    if (TSS_SUCCESS != result){
        DBG("get quote", result);
    }
    ofstream out("hash");
    out.write((char*)valid.rgbData, valid.ulDataLength);
    out.close();

}