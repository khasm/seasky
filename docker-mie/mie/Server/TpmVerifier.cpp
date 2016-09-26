#include "TpmVerifier.h"
#include "TpmCommon.h"
#include <string>
#include <iconv.h>
#include <langinfo.h>
#include <vector>
#include <fstream>
#include <iostream>
#include <cstring>

#include <trousers/trousers.h>

//adapted from tpm_quote_tools 1.0.2
#define DBG(message, tResult) printf("(Line %d, %s) %s returned 0x%08x. %s.\n\n",__LINE__,__func__,message, tResult,(char *)Trspi_Error_String(tResult));

using namespace std;

bool verifyServer(const string& address)
{
    //get uuid from file
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
        nonce[i] = (BYTE)'0'+i;
    }
    valid.ulExternalDataLength = nonce_size;
    valid.rgbExternalData = nonce;
    //select pcrs
    vector<int> pcrs;
    pcrs.push_back(16);
    //get quote
    TSS_RESULT result = getQuote(uuid, address, pcrs, valid);
    if (TSS_SUCCESS != result){
        DBG("get quote", result);
        return false;
    }
    //read hash
    in.open("hash");
    in.seekg(0, ios::end);
    size_t hash_size = in.tellg();
    in.seekg(0, ios::beg);
    BYTE hash[hash_size];
    in.read((char*)hash, hash_size);
    in.close();
    //read public key
    in.open("pubkey");
    in.seekg(0, ios::end);
    size_t pubkey_raw_size = in.tellg();
    in.seekg(0, ios::beg);
    BYTE pubkey_raw[pubkey_raw_size];
    in.read((char*)pubkey_raw, pubkey_raw_size);
    in.close();
    UINT32 blobType;
    BYTE pubkey[pubkey_raw_size];
    UINT32 pubkey_size = pubkey_raw_size;
    result = Tspi_DecodeBER_TssBlob(pubkey_raw_size, pubkey_raw, &blobType, &pubkey_size, pubkey);
    if (result){
        DBG("decode public key", result);
        exit(1);
    }
    if (TSS_BLOB_TYPE_PUBKEY != blobType) {
        fprintf(stderr, "Error while decoding public key, got wrong blob type");
        exit(1);
    }

    //create verify context
    TSS_HCONTEXT vContext;
    result = Tspi_Context_Create(&vContext);
    if (result){
        DBG("create context", result);
        exit(1);
    }
    //setup public key
    TSS_HKEY hPubAIK;
    int initFlags = TSS_KEY_TYPE_IDENTITY | TSS_KEY_SIZE_2048;
    result = Tspi_Context_CreateObject(vContext, TSS_OBJECT_TYPE_RSAKEY, initFlags, &hPubAIK);
    if (result){
        DBG("create public key object", result);
        exit(1);
    }
    result = Tspi_SetAttribData(hPubAIK, TSS_TSPATTRIB_KEY_BLOB, TSS_TSPATTRIB_KEYBLOB_PUBLIC_KEY,
        pubkey_size, pubkey);
    if (result){
        DBG("set up public key object", result);
        exit(1);
    }
    //set up hash for verification
    TPM_NONCE* hash_nonce;
    TPM_QUOTE_INFO2* info2 = (TPM_QUOTE_INFO2*)&hash;
    if(info2->fixed[2] == 'T'){
        cout<<"version 2"<<endl;
        hash_nonce = &((TPM_QUOTE_INFO2*)&hash)->externalData;
    }
    TPM_QUOTE_INFO* info = (TPM_QUOTE_INFO*)&hash;
    if(info->fixed[2] == 'O'){
        cout<<"version 1"<<endl;
        hash_nonce = &((TPM_QUOTE_INFO*)&hash)->externalData;
    }
    memcpy(hash_nonce, nonce, sizeof(TPM_NONCE));
    TSS_HHASH hHash;
    result = Tspi_Context_CreateObject(vContext, TSS_OBJECT_TYPE_HASH, TSS_HASH_SHA1, &hHash);
    if (result){
        DBG("create hash object", result);
        exit(1);
    }
    result = Tspi_Hash_UpdateHashValue(hHash, hash_size, hash);
    if (result){
        DBG("set up hash object", result);
        exit(1);
    }
    //verify signature
    result = Tspi_Hash_VerifySignature(hHash, hPubAIK, valid.ulValidationDataLength,
        valid.rgbValidationData);
    if (TSS_SUCCESS != result){
        DBG("verify quote", result);
        exit(1);
    }

	return false;
}