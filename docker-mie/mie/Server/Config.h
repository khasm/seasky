#ifndef CONFIG
#define CONFIG
#include <string>

namespace MIE{

void readConfig(const std::string& configFile = std::string());

int getExpireTime();

int getMaxFileSize();

long long getMaxFeaturesSize();

long long getMaxTempFeaturesSize();

size_t getResultsSize();

std::string getMemcachedHost();

std::string getMemcachedPort();

std::string getRamcloudHost();

std::string getRamcloudPort();

std::string getClusterName();

std::string getTableName();

uint32_t getRamcloudClusters();

int getReedSolW();

int getReedSolK();

int getReedSolM();

int getRSABits();

int getRSAExp();

}//end namespace mie
#endif