#include "Config.h"
#include <fstream>

namespace MIE{

using namespace std;

///*****************default values*****************///
///search
static const size_t default_results_size = 20;
///cache
static const int default_expire_time = 3600; //1hour
static const string default_memcached_host = "127.0.0.1";
static const string default_memcached_port = "11211";
///storage
static const int default_max_file_size = 350*1024*1024; ///350MBs
///ramcloud info
static const string default_ramcloud_host = "127.0.0.1";
static const string default_ramcloud_port = "2181";
static const string default_cluster_name = "test-cluster";
static const string default_table_name = "MIE";
static const uint32_t default_ramcloud_clusters = 4;
static const int default_reed_sol_w = 8;
static const int default_reed_sol_k = 2;
static const int default_reed_sol_m = 2;
///rsa signature info
static const int default_rsa_bits = 2048;
static const int default_rsa_exp = 65537;
//memory management
static const long long default_max_features_size = 3*1024*1024*1024L; //3GBs
static const long long default_max_temp_features_size = 3*1024*1024*1024L; //3GBs
///*****************runtime values*****************///
///search
static size_t results_size = default_results_size;
///cache
static int expire_time = default_expire_time;
static string memcached_host = default_memcached_host;
static string memcached_port = default_memcached_port;
///storage
static int max_file_size = default_max_file_size;
///ramcloud info
static string ramcloud_host = default_ramcloud_host;
static string ramcloud_port = default_ramcloud_port;
static string cluster_name = default_cluster_name;
static string table_name = default_table_name;
static uint32_t ramcloud_clusters = default_ramcloud_clusters;
static int reed_sol_w = default_reed_sol_w;
static int reed_sol_k = default_reed_sol_k;
static int reed_sol_m = default_reed_sol_m;
///rsa signature info
static int rsa_bits = default_rsa_bits;
static int rsa_exp = default_rsa_exp;
//memory management
static size_t max_features_size = 3*1024*1024*1024L; //3GBs
static size_t max_temp_features_size = 3*1024*1024*1024L; //3GBs

void readConfig(const string& configFile){
    string file;
    if(configFile.empty())
        file = string("config.conf");
    else
        file.assign(configFile);
    ifstream in(file);
    while(in.is_open() && !in.eof()){
        string line;
        getline(in, line);
        if(!line.empty() && !(line[0] == '#')){
            string value, key;
            size_t pos = line.find_first_of(':');
            if(pos != string::npos){
                key = line.substr(0, pos);
                value = line.substr(pos+2);
                if(key.compare("zookeeper host") == 0){
                    ramcloud_host = value;
                }
                else if(key.compare("zookeeper port") == 0){
                    ramcloud_port = value;
                }
                else if(key.compare("cluster name") == 0){
                    cluster_name = value;
                }
                else if(key.compare("table name") == 0){
                    table_name = value;
                }
                else if(key.compare("clusters") == 0){
                    ramcloud_clusters = stoi(value);
                }
                else if(key.compare("rsa key size") == 0){
                    rsa_bits = stoi(value);
                }
                else if(key.compare("rsa exponent") == 0){
                    rsa_exp = stoi(value);
                }
                else if(key.compare("reed solomon w") == 0){
                    reed_sol_w = stoi(value);
                }
                else if(key.compare("reed solomon m") == 0){
                    reed_sol_m = stoi(value);
                }
                else if(key.compare("reed solomon k") == 0){
                    reed_sol_k = stoi(value);
                }
                else if(key.compare("max file size") == 0){
                    max_file_size = stoi(value);
                }
                else if(key.compare("cache expire time") == 0){
                    expire_time = stoi(value);
                }
                else if(key.compare("memcached host") == 0){
                    memcached_host = value;
                }
                else if(key.compare("memcached port") == 0){
                    memcached_port = value;
                }
                else if(key.compare("max features size") == 0){
                    memcached_port = value;
                }
                else if(key.compare("max temp features size") == 0){
                    memcached_port = value;
                }
                else if(key.compare("results size") == 0){
                    results_size = stoi(value);
                }
            }
        }
    }
    in.close();
}

int getExpireTime()
{
    return expire_time;
}

int getMaxFileSize()
{
    return max_file_size;
}

long long getMaxFeaturesSize()
{
    return max_features_size;
}

long long getMaxTempFeaturesSize()
{
    return max_temp_features_size;
}

size_t getResultsSize()
{
    return results_size;
}

string getMemcachedHost()
{
    return memcached_host;
}

string getMemcachedPort()
{
    return memcached_port;
}

string getRamcloudHost()
{
    return ramcloud_host;
}

string getRamcloudPort()
{
    return ramcloud_port;
}

string getClusterName()
{
    return cluster_name;
}

string getTableName()
{
    return table_name;
}

uint32_t getRamcloudClusters()
{
    return ramcloud_clusters;
}

int getReedSolW()
{
    return reed_sol_w;
}

int getReedSolK()
{
    return reed_sol_k;
}

int getReedSolM()
{
    return reed_sol_m;
}

int getRSABits()
{
    return rsa_bits;
}

int getRSAExp()
{
    return rsa_exp;
}

}//end namespace mie