#ifndef REEDSOL
#define REEDSOL

#include <jerasure.h>
#include <reed_sol.h>
#include <map>
#include <string>
#include <vector>
#include <memory>

#define REED_BUFFER_SIZE 62000

void encode(const char* src, int src_size, int k, int m, int w, 
    std::map<std::string, std::shared_ptr<std::vector<char>>> &codes);

bool decode(std::map<std::string, std::shared_ptr<std::vector<char>>> &src, 
    int k, int m, int w, std::vector<char> &data, int src_size);

#endif