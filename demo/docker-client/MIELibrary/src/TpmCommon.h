#ifndef SEASKY_TPM_COMMON_H
#define SEASKY_TPM_COMMON_H

#include <tss/tspi.h>
#include <string>
#include <vector>
#include <string>

void log(const std::string& msg);

TSS_RESULT getQuote(const TSS_UUID& uuid, const std::string& address, const std::vector<int>& pcrs,
	TSS_VALIDATION& valid);
#endif