#include "TpmClient.h"
#include "TpmCommon.h"
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#include <netdb.h>
#include <cstring>
#include <fstream>
#include <memory>
#include <arpa/inet.h>
#include <openssl/rand.h>
#include <openssl/evp.h>
#include <openssl/err.h>

using namespace std;

static const uint32_t NONCE_SIZE = 20;
static const uint32_t MAX_RND_BYTES = 16;

bool seedPrng()
{
    int ret = RAND_load_file("/dev/random", 32);
    if(32 != ret){
        log("Error reading /dev/random");
        return false;
    }
    else{
        return true;
    }
}

bool digestNonce(unsigned char* nonce, uint32_t size)
{
    ///create context and initialize variables
    EVP_MD_CTX* ctx = EVP_MD_CTX_create();
    unsigned char buffer[EVP_MAX_MD_SIZE];
    unsigned hash_size = 0;
    if(!EVP_DigestInit_ex(ctx, EVP_sha1(), NULL)){
        ERR_print_errors_fp(stderr);
        log("Error in context initialize for OpenSSL");
        return false;
    }
    ///do digest
    if(!EVP_DigestUpdate(ctx, nonce, size)){
        ERR_print_errors_fp(stderr);
        log("Error updating digest in OpenSSL");
        goto cleanup;
    }
    if(!EVP_DigestFinal_ex(ctx, buffer, &hash_size)){
        ERR_print_errors_fp(stderr);
        log("Error finalizing digest in OpenSSL");
        goto cleanup;
    }
    //use first size bytes of buffer as nonce
    memcpy(nonce, buffer, size);
    //cleanup
    cleanup:
    EVP_MD_CTX_destroy(ctx);
    if(0 < hash_size && size <= hash_size){
        return true;
    }
    else{
        return false;
    }
}

bool generateNonce(unsigned char* nonce, uint32_t size)
{
    /* size should always be NONCE_SIZE but make sure this function
    is never executed outside of the bounds it can support */
    if(0 == size || NONCE_SIZE < size){
        log("Nonce size out of bounds");
        return false;
    }
    static uint32_t counter = 0;
    counter++;
    uint32_t tmp = htonl(counter);
    if(4 >= size){
        /* unsafe nonce, can easily be predicted and an attacker
        can prepare a response ahead of time */
        memcpy(nonce, ((char*)&tmp) + 4 - size, size);
        return digestNonce(nonce, size);
    }
    else{
        memcpy(nonce, (char*)&tmp, 4);
    }
    static bool seeded = false;
    if(!seeded){
        if(seedPrng()){
            seeded = true;
        }
        else{
            log("Couldn't seed PRNG");
            return false;
        }
    }
    uint32_t rnd_bytes = MAX_RND_BYTES < size - 4 ? MAX_RND_BYTES : size - 4;
    if(!RAND_bytes(nonce + 4, rnd_bytes)){
        log("Couldn't generate random bytes");
        return false;
    }
    return digestNonce(nonce, size);
}

bool verify(const string& address)
{
    //get uuid from file
    ifstream in("uuid");
    in.seekg(0, ios::end);
    size_t uuid_len = in.tellg();
    TSS_UUID uuid;
    if(sizeof(uuid) != uuid_len){
        in.close();
        log("TPM: Couldn't load uuid");
        return false;
    }
    in.seekg(0, ios::beg);
    in.read((char*)&uuid, sizeof(uuid));
    in.close();

    TSS_VALIDATION valid;
    //generate nonce
    BYTE nonce[NONCE_SIZE];
    if(!generateNonce(nonce, NONCE_SIZE)){
        log("TPM: Nonce generation failed");
        return false;
    }
    valid.ulExternalDataLength = NONCE_SIZE;
    valid.rgbExternalData = nonce;
    //select pcrs
    vector<int> pcrs;
    pcrs.push_back(16);
    //get quote
    TSS_RESULT result = getQuote(uuid, address, pcrs, valid);
    if(TSS_SUCCESS != result){
        log("TPM: Unable to get quote");
        return false;
    }
    //read hash
    in.open("hash");
    in.seekg(0, ios::end);
    size_t hash_size = in.tellg();
    if(0 == hash_size){
        log("TPM: Empty hash");
        return false;
    }
    in.seekg(0, ios::beg);
    BYTE hash[hash_size];
    in.read((char*)hash, hash_size);
    in.close();
    //read public key
    in.open("pubkey");
    in.seekg(0, ios::end);
    size_t pubkey_raw_size = in.tellg();
    if(0 == pubkey_raw_size){
        log("TPM: Empty pubkey");
        return false;
    }
    in.seekg(0, ios::beg);
    BYTE pubkey_raw[pubkey_raw_size];
    in.read((char*)pubkey_raw, pubkey_raw_size);
    in.close();
    UINT32 blobType;
    BYTE pubkey[pubkey_raw_size];
    UINT32 pubkey_size = pubkey_raw_size;
    result = Tspi_DecodeBER_TssBlob(pubkey_raw_size, pubkey_raw, &blobType, &pubkey_size, pubkey);
    if(TSS_SUCCESS != result){
        log("TPM: Couldn't decode key");
        return false;
    }
    if(TSS_BLOB_TYPE_PUBKEY != blobType) {
        log("TPM: Wrong key type");
        return false;
    }
    //create verify context
    TSS_HCONTEXT vContext;
    result = Tspi_Context_Create(&vContext);
    if(TSS_SUCCESS != result){
        log("TPM: Couldn't create context");
        return false;
    }
    //setup public key
    TSS_HKEY hPubAIK;
    int initFlags = TSS_KEY_TYPE_IDENTITY | TSS_KEY_SIZE_2048;
    result = Tspi_Context_CreateObject(vContext, TSS_OBJECT_TYPE_RSAKEY, initFlags, &hPubAIK);
    if(TSS_SUCCESS != result){
        log("TPM: Couldn't create public key object");
        return false;
    }
    result = Tspi_SetAttribData(hPubAIK, TSS_TSPATTRIB_KEY_BLOB, TSS_TSPATTRIB_KEYBLOB_PUBLIC_KEY,
        pubkey_size, pubkey);
    if(TSS_SUCCESS != result){
        log("TPM: Couldn't setup public key");
        return false;
    }
    //set up hash for verification
    TPM_NONCE* hash_nonce;
    TPM_QUOTE_INFO2* info2 = (TPM_QUOTE_INFO2*)&hash;
    if(info2->fixed[2] == 'T'){
        hash_nonce = &((TPM_QUOTE_INFO2*)&hash)->externalData;
    }
    TPM_QUOTE_INFO* info = (TPM_QUOTE_INFO*)&hash;
    if(info->fixed[2] == 'O'){
        hash_nonce = &((TPM_QUOTE_INFO*)&hash)->externalData;
    }
    memcpy(hash_nonce, nonce, sizeof(TPM_NONCE));
    TSS_HHASH hHash;
    result = Tspi_Context_CreateObject(vContext, TSS_OBJECT_TYPE_HASH, TSS_HASH_SHA1, &hHash);
    if(TSS_SUCCESS != result){
        log("TPM: Couldn't create hash object");
        return false;
    }
    result = Tspi_Hash_UpdateHashValue(hHash, hash_size, hash);
    if(TSS_SUCCESS != result){
        log("TPM: Couldn't update hash value");
        return false;
    }
    //verify signature
    result = Tspi_Hash_VerifySignature(hHash, hPubAIK, valid.ulValidationDataLength,
        valid.rgbValidationData);
    if(TSS_SUCCESS != result){
        log("TPM: Signature verification failed");
        return false;
    }
    return true;
}