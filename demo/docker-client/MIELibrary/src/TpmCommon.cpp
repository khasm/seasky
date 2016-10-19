#include "TpmCommon.h"
#include <iconv.h>
#include <langinfo.h>
#include <iostream>

using namespace std;

void log(const string& msg)
{
    cerr<<msg<<endl;
}

vector<UNICODE> toutf16le(const string& address)
{
    vector<UNICODE> ans;
    size_t n = address.size();
    if (0 < n){
        iconv_t cd = iconv_open("UTF-16LE", nl_langinfo(CODESET));
        if (cd != (iconv_t)-1){
            size_t len = 2*(n + 1);	/* Max output size */
            ans.resize(len);
            char *inbuf = (char*)address.data();
            size_t inbytesleft = n + 1;
            char *outbuf = (char*)ans.data();
            size_t outbytesleft = len;
            size_t rc = iconv(cd, &inbuf, &inbytesleft, &outbuf, &outbytesleft);
            if (rc == (size_t)-1 || inbytesleft != 0) {
                ans.clear();
            }
        }
        iconv_close(cd);
    }
    return ans;
}

TSS_RESULT getQuote(const TSS_UUID& uuid, const std::string& address, const std::vector<int>& pcrs,
	TSS_VALIDATION& valid)
{
    TSS_HCONTEXT hContext;
    TSS_HTPM hTPM;
    TSS_RESULT result;
    //create context and connect to tpm
    result = Tspi_Context_Create(&hContext);
    if(TSS_SUCCESS != result){
        log("TPM: GetQuote: Context creation failed");
        return result;
    }
    if(!address.empty()){
    	vector<UNICODE> host = toutf16le(address);
    	result = Tspi_Context_Connect(hContext, host.data());
    }
    else{
    	result = Tspi_Context_Connect(hContext, NULL);	
	}
	if(TSS_SUCCESS != result){
        log("TPM: GetQuote: Context connection failed");
		Tspi_Context_Close(hContext);
        return result;
	}
	result = Tspi_Context_GetTpmObject(hContext,&hTPM);
	if(TSS_SUCCESS != result){
        log("TPM: GetQuote: get TPM object failed");
		Tspi_Context_Close(hContext);
        return result;
	}
    ///get srk handle and set secret, required to be able to load aik afterwards
    TSS_UUID SRK_UUID = TSS_UUID_SRK;
    TSS_HKEY hSRK;
    result = Tspi_Context_LoadKeyByUUID(hContext, TSS_PS_TYPE_SYSTEM, SRK_UUID, &hSRK);
    if(TSS_SUCCESS != result){
        log("TPM: GetQuote: load SRK key failed");
        Tspi_Context_CloseObject(hContext, hTPM);
        Tspi_Context_Close(hContext);
        return result;
    }
    TSS_HPOLICY hSrkPolicy;
    result = Tspi_GetPolicyObject(hSRK, TSS_POLICY_USAGE, &hSrkPolicy);
    if(TSS_SUCCESS != result){
        log("TPM: GetQuote: get key policy failed");
        Tspi_Context_Close(hContext);
        return result;
    }
    BYTE srkSecret[] = TSS_WELL_KNOWN_SECRET;
    result = Tspi_Policy_SetSecret(hSrkPolicy, TSS_SECRET_MODE_SHA1, sizeof(srkSecret), srkSecret);
    if(TSS_SUCCESS != result){
        log("TPM: GetQuote: set key policy failed");
        Tspi_Context_Close(hContext);
        return result;
    }
    //get key handle
    TSS_HKEY hAIK;
    result = Tspi_Context_LoadKeyByUUID(hContext, TSS_PS_TYPE_SYSTEM, uuid, &hAIK);
    if(TSS_SUCCESS != result){
        log("TPM: GetQuote: load aik failed");
        Tspi_Context_Close(hContext);
        return result;
    }
    //select pcrs used for the quote
    TSS_HPCRS hPcrs;
    result = Tspi_Context_CreateObject(hContext, TSS_OBJECT_TYPE_PCRS, 0, &hPcrs);
    if(TSS_SUCCESS != result){
        log("TPM: GetQuote: create pcr object failed");
        Tspi_Context_Close(hContext); 
        return result;
    }
    for(vector<int>::const_iterator it = pcrs.begin(); it != pcrs.end(); ++it){
        result = Tspi_PcrComposite_SelectPcrIndex(hPcrs, *it);
        if(TSS_SUCCESS != result){
            log("TPM: GetQuote: select pcr failed");
            Tspi_Context_Close(hContext);
            return result;
        }
    }
    //get quote
    result = Tspi_TPM_Quote(hTPM, hAIK, hPcrs, &valid);
    //Tspi_Context_Close(hContext);
    return result;
}