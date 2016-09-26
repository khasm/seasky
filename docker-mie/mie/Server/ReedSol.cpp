#include "ReedSol.h"
#include <string.h>
#include <queue>
using namespace std;

/***
*
*Adapted from the Depsky implementation and Jerasure examples
*
***/
int getBufferSize(int k, int w)
{
    ///find closest valid buffer size
    int up = REED_BUFFER_SIZE;
    int down = REED_BUFFER_SIZE;
    int buffer_size;
    while(up%(k*w*sizeof(long)) != 0 && down%(k*w*sizeof(long)) != 0){
        up++;
        down--;
    }
    if(up%(k*w*sizeof(long)) == 0){
        buffer_size = up;
    }
    else{
        buffer_size = down;
    }
    return buffer_size;
}

void encode(const char *src, int src_size, int k, int m, int w, map<string, 
    shared_ptr<vector<char>>> &codes)
{
    ///find closest valid buffer size
    int buffer_size = getBufferSize(k, w);
    ///define new size, block size and number of loops
    int new_size = src_size;
    int block_size;
    int reads;
    while(new_size%(k*w*sizeof(long)) != 0)
        new_size++;
    while(new_size%buffer_size != 0)
        new_size++;
    if(new_size <= buffer_size){
        ///adjust buffer for small files
        block_size = new_size/k;
        reads = 1;
    }
    else{
        block_size = buffer_size/k;
        reads = new_size/buffer_size;    
    }
    int split = new_size/k;
    ///initialize auxiliary matrixes
    char** data = new char*[k];
    char** coding = new char*[m];
    int* matrix = reed_sol_vandermonde_coding_matrix(k, m, w);
    ///setup return buffers
    int count = 0;
    for(int i=1; i<=k; i++){
        char tmp[10];
        sprintf(tmp, "k%d", i);
        string key(tmp);
        shared_ptr<vector<char>> out_buffer = codes[key];
        if(out_buffer == NULL){
            out_buffer = make_shared<vector<char>>();
            codes[key] = out_buffer;
        }
        out_buffer->reserve(split);
        ///copy src data to final buffers
        for(int j = 0; j < split; j++){
            if(count < src_size){
                out_buffer->push_back(src[count++]);
            }
            else{
                out_buffer->push_back('\0');   
            }
        }
    }
    for(int i=1; i<=m; i++){
        char tmp[10];
        sprintf(tmp, "m%d", i);
        string key(tmp);
        shared_ptr<vector<char>> out_buffer = codes[key];
        if(out_buffer == NULL){
            out_buffer = make_shared<vector<char>>();
            codes[key] = out_buffer;
        }
        out_buffer->resize(split);
    }
    for(int n_reads = 0; n_reads < reads; n_reads++){
        //adjust data pointers
        for(int i=1; i<=k; i++){
            char tmp[10];
            sprintf(tmp, "k%d", i);
            string key(tmp);
            data[i-1] = codes[key]->data()+n_reads*block_size;
        }
        //adjust coding pointers
        for(int i=1; i<=m; i++){
            char tmp[10];
            sprintf(tmp, "m%d", i);
            string key(tmp);
            coding[i-1] = codes[key]->data()+n_reads*block_size;
        }
        jerasure_matrix_encode(k, m, w, matrix, data, coding, block_size);
    }
    delete[] data;
    delete[] coding;
    free(matrix);
}

bool decode(map<string, shared_ptr<vector<char>>> &src, int k, int m, int w, vector<char> &data,
    int src_size)
{
    ///set up auxiliary structures
    int* erased =  new int[k+m];
    int* erasures = new int[k+m];
    char** tmpdata = new char*[k];
    char** coding = new char*[m];
    int* matrix = reed_sol_vandermonde_coding_matrix(k, m, w);
    int blocksize = 0;
    int numErased = 0;
    ///get data blocks
    for(int i  = 1; i <= k; i++){
        char tmp[10];
        sprintf(tmp, "k%d", i);
        string key(tmp);
        map<string, shared_ptr<vector<char>>>::iterator it = src.find(key);
        if(it == src.end()){
            erased[i-1] = 1;
            erasures[numErased] = i-1;
            numErased++;
        }
        else{
            blocksize = it->second->size();
            tmpdata[i-1] = it->second->data();
        }
    }
    ///get coding blocks
    for(int i = 1; i <= m; i++){
        char tmp[10];
        sprintf(tmp, "m%d", i);
        string key(tmp);
        map<string, shared_ptr<vector<char>>>::iterator it = src.find(key);        
        if(it == src.end()){
            erased[k+i-1] = 1;
            erasures[numErased] = k+i-1;
            numErased++;
        }
        else{
            blocksize = it->second->size();
            coding[i-1] = it->second->data();
        }
    }
    ///set up dummies
    for(int i = 0; i < numErased; i++){
        if(erasures[i] < k)
            tmpdata[erasures[i]] =  new char[blocksize];
        else
            coding[erasures[i] - k] = new char[blocksize];
    }
    erasures[numErased] = -1;
    bool res = true;
    if(jerasure_matrix_decode(k, m, w, matrix, 1, erasures, tmpdata, coding, blocksize) == -1)
        res = false;
    ///piece together the data
    if(res){
        data.clear();
        data.reserve(src_size);
        int offset = 0;
        for(int i = 0; i < k; i++){
            if(offset + blocksize < src_size){
                data.insert(data.end(), tmpdata[i], tmpdata[i]+blocksize);
                offset += blocksize;
            }
            else{
                data.insert(data.end(), tmpdata[i], tmpdata[i]+(src_size-offset));
                break;
            }
        }
    }
    ///free memory
    for(int i = 0; i < numErased; i++){
        if(erasures[i] < k)
            delete[] tmpdata[erasures[i]];
        else
            delete[] coding[erasures[i] - k];
    }
    delete[] erased;
    delete[] erasures;
    delete[] tmpdata;
    delete[] coding;
    free(matrix);
    return res;
}
