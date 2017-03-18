#ifndef MIE_H
#define MIE_H

#include <mutex>
#include <condition_variable>
#include <vector>
#include <map>
#include <string>
#include <set>
#include <memory>
#include <atomic>
#include <opencv2/core/core.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <opencv2/highgui/highgui.hpp>
#include "Status.h"
#include "Storage.h"

namespace MIE{

static const int clusters = 1000;

struct QueryResult {
    std::string docId;
    double score;
};


struct cmp_QueryResult {
    bool operator() (const QueryResult& lhs, const QueryResult& rhs) const {
        return lhs.docId.compare(rhs.docId) != 0 && lhs.score >= rhs.score;
    }
};

struct Rank {
    int textRank = 0;
    int imgRank = 0;
};

class MIE{
    class FeatureStreamer{
        int aNFeatures;
        int aFeatureSize;
        int aFeatureIndex;
        std::string aFile;
        bool aWrite;
        bool aRead;
        MIE* aMie;

    public:
        long long aTotalFeaturesSize;
        FeatureStreamer(MIE* mie, std::string file, bool read = true, bool write = false,
            size_t size = 0);
        ~FeatureStreamer();
        int getNextFeatures(int& id, cv::Mat& mat, std::string& name);
        int getNextFeatures(std::string& name, std::vector<std::vector<char>>& keywords);
        bool writeHeader(size_t nFeatures, int featureSize);
        bool writeNextFeatures(int id, const cv::Mat& mat, const std::string& name);
        bool writeNextFeatures(const std::string& name, const std::vector<std::vector<char>>& keywords);
    };
    //statistics
    double aIndexTime;///time to index images
    double aTrainTime;///time to create codebook
    double aSearchTime;//time spent on searching
    timespec aStartSearchTime;//time first thread started searching
    unsigned int aNSearchingThreads;//how many threads searching
    std::mutex aSearchTimeLock;
    double aNetworkIndexTime;///time spent on upload/download of the index
    double aNetworkFeatureTime;//time spent on upload/download of features/codebook
    //image variables
    std::atomic<int> aNImgs;
    std::atomic<unsigned long> aNextId;
    std::map<int,std::string> aImgDocs;
    std::shared_ptr<std::map<int,cv::Mat>> aImgFeatures;
    std::mutex aImgFeaturesLock;
    std::vector<std::shared_ptr<std::map<int,int>>> aImgIndex;
    std::unique_ptr<cv::BOWImgDescriptorExtractor> aBowExtr;
    //text variables
    std::atomic<int> aNDocs;
    std::shared_ptr<std::map<std::string,std::vector<std::vector<char>>>> aTextFeatures;
    std::map<std::vector<char>,std::shared_ptr<std::map<std::string,int>>> aTextIndex;
    std::mutex aTextFeaturesLock;
    //management
    unsigned aInProgressSearches;
    bool aIndexing;
    std::mutex aIndexLock;//this lock controls access to all indexes
    std::condition_variable aSearchesDone;
    std::condition_variable aIndexDone;
    long long aMaxFeaturesSize;
    long long aCurrentFeaturesSize;
    std::mutex aCurrentFeaturesSizeLock;
    long long aMaxTempFeaturesSize;

    //reference to storage used by the server to reduce memory usage and reduce function calls
    Storage* aStorage;

    int parseFeatures(const char* data, std::map<int,cv::Mat>& mats, 
        std::vector<std::vector<char>>& keywords, const std::string& name = std::string());

    std::set<QueryResult,cmp_QueryResult> imgSearch(const std::map<int,cv::Mat>& features);
    std::set<QueryResult,cmp_QueryResult> linearImgSearch(const std::map<int,cv::Mat>& features);
    std::set<QueryResult,cmp_QueryResult> textSearch(const std::vector<std::vector<char>>& keywords);
    std::set<QueryResult,cmp_QueryResult> linearTextSearch(const std::vector<std::vector<char>>& keywords);
    std::set<QueryResult,cmp_QueryResult> mergeSearchResults(
        const std::set<QueryResult,cmp_QueryResult>& imgResults, 
        const std::set<QueryResult,cmp_QueryResult>& textResults);
    std::set<QueryResult,cmp_QueryResult> sort(const std::map<std::string,double>& queryResults);

    void indexImgs(const std::map<int,cv::Mat>& imgFeatures, bool train = false);
    void indexText(const std::map<std::string,std::vector<std::vector<char>>> textFeatures);
    void index(int id, const cv::Mat& mat);
    void index(const std::string& name, const std::vector<std::vector<char>>& keywords);
    void persistImgIndex();
    void persistTextIndex();
    bool readIndex();
    void readImgIndex();
    void readTxtIndex();

    static void selfIndex(MIE* mie);

public:
    MIE(Storage* storage);
    ~MIE();
    void addDoc(const char* data, const std::string& name);
    void index(bool train = false);
    std::set<QueryResult,cmp_QueryResult> search(const char* data, size_t& nResults);
    double indexTime();
    double networkIndexTime();
    double trainTime();
    double networkFeatureTime();
    double searchTime();
    void resetTimes();
};

}//end namespace
#endif