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


//based on tpm_quote_tools 1.0.2 code and testsuit 0.3 of trousers

#define DBG(message, tResult) printf("(Line %d, %s) %s returned 0x%08x. %s.\n\n",__LINE__,__func__,message, tResult,(char *)Trspi_Error_String(tResult))
/*
 * Load hash values in TPM. Should actually be done by the TPM as there is no chain of trust here.
 */

using namespace std;

const static uint32_t NONCE_SIZE = 20;

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

bool createUUID(TSS_HTPM *hTPM, TSS_UUID **uuid)
{
	TSS_RESULT result = Tspi_TPM_GetRandom(*hTPM, sizeof(TSS_UUID), (BYTE **)uuid);
	if(TSS_SUCCESS != result)
		return false;
	//Variant and version bits according to
	//http://www.opengroup.org/dce/info/draft-leach-uuids-guids-01.txt
	//version 4
	(*uuid)->usTimeHigh &= 0x0FFF;
	(*uuid)->usTimeHigh |= (4 << 12);
	//variant 10xxxxxx
	(*uuid)->bClockSeqHigh &= 0x3F;
	(*uuid)->bClockSeqHigh |= 0x80;
	ofstream out("uuid", ios::binary);
	out.write((const char*)*uuid, sizeof(**uuid));
	out.close();
	return true;
}

bool createAIK(TSS_HCONTEXT *hContext, TSS_HTPM *hTPM, TSS_UUID uuid)
{
	//load SRK
	TSS_HKEY hSRK;
	TSS_RESULT result = Tspi_Context_LoadKeyByUUID(*hContext, TSS_PS_TYPE_SYSTEM,
		TSS_UUID_SRK, &hSRK);
	if (TSS_SUCCESS != result)
		return false;
	TSS_HPOLICY hSrkPolicy;
	result = Tspi_GetPolicyObject(hSRK, TSS_POLICY_USAGE, &hSrkPolicy);
	if (TSS_SUCCESS != result)
		return false;
	//no support for user defined passwords on the tpm, not a bug
	BYTE srkSecret[] = TSS_WELL_KNOWN_SECRET;
	result = Tspi_Policy_SetSecret(hSrkPolicy, TSS_SECRET_MODE_SHA1, sizeof(srkSecret), srkSecret);
	if (TSS_SUCCESS != result)
		return false;
	//assign policy to tpm object
	TSS_HPOLICY hTPMPolicy;
	result = Tspi_GetPolicyObject(*hTPM, TSS_POLICY_USAGE, &hTPMPolicy);
	if (TSS_SUCCESS != result) {
		return false;
	}
	result = Tspi_Policy_SetSecret(hTPMPolicy, TSS_SECRET_MODE_SHA1, sizeof(srkSecret), srkSecret);
	if (TSS_SUCCESS != result) {
		return false;
	}
	//create CA key
	TSS_HKEY hPCA;
	result = Tspi_Context_CreateObject(*hContext, TSS_OBJECT_TYPE_RSAKEY,
		TSS_KEY_TYPE_LEGACY|TSS_KEY_SIZE_2048, &hPCA);
	if (TSS_SUCCESS != result)
		return false;
	/* manually creating a key is described in tcg/highlevel/tpm/Tspi_TPM_CreateIdentity.c
	   of testsuit-0.3 from trousers */
	result = Tspi_Key_CreateKey(hPCA, hSRK, 0);
	if (TSS_SUCCESS != result)
		return false;
	//create aik
	TSS_HKEY hAIK;
	result = Tspi_Context_CreateObject(*hContext, TSS_OBJECT_TYPE_RSAKEY,
		TSS_KEY_TYPE_IDENTITY | TSS_KEY_SIZE_2048, &hAIK);
	if(TSS_SUCCESS != result)
		return false;
	TSS_HPOLICY hAikPolicy;
	result = Tspi_GetPolicyObject(hAIK, TSS_POLICY_USAGE, &hAikPolicy);
	if(TSS_SUCCESS != result)
		return false;
	result = Tspi_Policy_SetSecret(hAikPolicy, TSS_SECRET_MODE_SHA1, sizeof(srkSecret), srkSecret);
	if(TSS_SUCCESS != result)
		return false;
	BYTE lab[] = {};
	BYTE *blob;
	UINT32 blobLen;
	result = Tspi_TPM_CollateIdentityRequest(*hTPM, hSRK, hPCA, 0, lab, hAIK, TSS_ALG_AES, &blobLen,
		&blob);
	if(TSS_SUCCESS != result)
		return false;
	/* free blob, its not going to be used (it contains the certificate request that should be sent
	   to the CA to sign on a real use case)*/
	Tspi_Context_FreeMemory(*hContext, blob);
	//register key in the tpm
	result = Tspi_Context_RegisterKey(*hContext, hAIK, TSS_PS_TYPE_SYSTEM, uuid, TSS_PS_TYPE_SYSTEM,
		TSS_UUID_SRK);
	if (TSS_SUCCESS != result)
		return false;
	//get public key
	result = Tspi_GetAttribData(hAIK, TSS_TSPATTRIB_KEY_BLOB, TSS_TSPATTRIB_KEYBLOB_PUBLIC_KEY,
		&blobLen, &blob);
	if(TSS_SUCCESS != result)
		return false;
	//write public key to disk
	UINT32 derBlobLen = 0;
	result = Tspi_EncodeDER_TssBlob(blobLen, blob, TSS_BLOB_TYPE_PUBKEY, &derBlobLen, NULL);
	if(TSS_SUCCESS != result)
		return false;
	BYTE derBlob[derBlobLen];
	result = Tspi_EncodeDER_TssBlob(blobLen, blob, TSS_BLOB_TYPE_PUBKEY, &derBlobLen, derBlob);
	if(TSS_SUCCESS != result)
		return false;
	ofstream out("pubkey", ios::binary);
	out.write((const char*)derBlob, derBlobLen);
	out.close();
	return true;
}

bool bootstrapTPM(TSS_HCONTEXT *hContext, TSS_HTPM *hTPM, TSS_UUID **uuid)
{
	if(!createUUID(hTPM, uuid))
		return false;
	if(!createAIK(hContext, hTPM, **uuid))
		return false;
	return true;
}

bool getUUID(TSS_HCONTEXT *hContext, TSS_HTPM *hTPM, TSS_UUID *uuid)
{
	//verify if aik, pubkey and uuid files exist
	ifstream uuid_in("uuid");
	ifstream aik_in("aik");
	ifstream pubkey_in("pubkey");
	if(!uuid_in || !aik_in || !pubkey_in){
		//if any of these files doesn't exist recreate them all
		TSS_UUID *tmp = NULL;
		if(!bootstrapTPM(hContext, hTPM, &tmp))
			return false;
		//copy uuid and return
		memcpy(uuid, tmp, sizeof(*uuid));
		Tspi_Context_FreeMemory(*hContext, (BYTE*)tmp);
		return true;
	}
	//read uuid
	uuid_in.seekg(0, ios::end);
    size_t uuid_len = uuid_in.tellg();
    if(sizeof(*uuid) != uuid_len){
        return false;
    }
    uuid_in.seekg(0, ios::beg);
    uuid_in.read((char*)uuid, sizeof(*uuid));
    return true;
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
	
	//create context and connect
	TSS_HCONTEXT hContext;
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
	//get tpm object
	TSS_HTPM hTPM;
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
	//retrieve or generate uuid and aik
	TSS_UUID uuid;
	if(!getUUID(&hContext, &hTPM, &uuid)){
		printf("Error getting UUID\n");
		exit(1);
	}
 
	TSS_VALIDATION valid;
    //generate pseudo nonce, doesn't matter the value
    BYTE nonce[NONCE_SIZE];
    valid.ulExternalDataLength = NONCE_SIZE;
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