#include "Mie.h"
#include "ServerUtil.h"
#include <set>
#include <iostream>

#include <fstream>

namespace MIE{

using namespace std;
using namespace cv;

const string IMG_INDEX_FILE = "imgIndex";
const string TXT_INDEX_FILE = "textIndex";
const string IMG_FEATURES_FILE = "imgFeatures";
const string TXT_FEATURES_FILE = "textFeatures";
const string CODEBOOK_FILE = "codebook.yml";

MIE::FeatureStreamer::FeatureStreamer(MIE* mie, string file, bool read, bool write, size_t size):
    aFeatureIndex(0), aWrite(write), aRead(read)
{
    aMie = mie;
    aFile = file;
    if(aRead){
        vector<char> buffer;
        timespec start_time = getTime();///network time
        aTotalFeaturesSize = aMie->aStorage->prepareRead(aFile);
        if(0 < aTotalFeaturesSize){
            int status = aMie->aStorage->read(aFile, buffer, 2 * sizeof(int));
            aMie->aNetworkFeatureTime += diffSec(start_time, getTime());
            if(4 <= status){
                int pos = 0;
                aNFeatures = readIntFromArr(buffer.data(), &pos);
                aFeatureSize = readIntFromArr(buffer.data(), &pos);
            }
            else{
                aNFeatures = 0;
                aFeatureSize = 0;
            }
        }
        else{
            aNFeatures = 0;
            aFeatureSize = 0;
        }
    }
    else{
        aNFeatures = 0;
        aFeatureSize = 0;
        aTotalFeaturesSize = 0;
    }
    if(aWrite){
        size_t total_size = 0;
        if(aRead && 0 < aTotalFeaturesSize)
            total_size = size + aTotalFeaturesSize;
        else
            total_size = 2 * sizeof(int) + size;
        timespec start_time = getTime();///network time
        aMie->aStorage->prepareWrite(aFile, total_size);
        aMie->aNetworkFeatureTime += diffSec(start_time, getTime());
    }
}

MIE::FeatureStreamer::~FeatureStreamer()
{
    timespec start_time = getTime();///network time
    if(aRead)
        aMie->aStorage->closeRead(aFile);
    if(aWrite)
        aMie->aStorage->finishWrite(aFile);
    aMie->aNetworkFeatureTime += diffSec(start_time, getTime());
}

int MIE::FeatureStreamer::getNextFeatures(int& id, Mat& mat, string& name)
{
    if(aFeatureIndex < aNFeatures){
        vector<char> buffer;
        int status = NO_ERRORS;

        timespec start_time = getTime();///network time
        status = aMie->aStorage->read(aFile, buffer, 3 * sizeof(int));
        aMie->aNetworkFeatureTime += diffSec(start_time, getTime());
        if(0 > status)
            return status;
        
        int pos = 0;
        id = readIntFromArr(buffer.data(), &pos);
        const int n_features = readIntFromArr(buffer.data(), &pos);
        const int name_size = readIntFromArr(buffer.data(), &pos);
        
        start_time = getTime();///network time
        status = aMie->aStorage->read(aFile, buffer, name_size + n_features * aFeatureSize 
            * sizeof(uint64_t));
        aMie->aNetworkFeatureTime += diffSec(start_time, getTime());

        if(0 > status)
            return status;
        pos = name_size;
        name = string(buffer.data(), name_size);

        mat.create(n_features, aFeatureSize,CV_32F);
        for (int j = 0; j < n_features; j++)
            for (int k = 0; k < aFeatureSize; k++)
                mat.at<float>(j,k) = readFloatFromArr(buffer.data(), &pos);
        aFeatureIndex++;
        return n_features * aFeatureSize * sizeof(float);
    }
    else{
        return OP_FAILED;
    }
}

bool MIE::FeatureStreamer::writeNextFeatures(int id, const Mat& mat, const string& name)
{
    int buffer_size = 3 * sizeof(int) + name.size() + mat.rows * aFeatureSize * sizeof(uint64_t);
    unique_ptr<char[]> buffer(new char[buffer_size]);
    int pos = 0;
    addIntToArr(id, buffer.get(), &pos);
    addIntToArr(mat.rows, buffer.get(), &pos); ///number of features
    addIntToArr(name.size(), buffer.get(), &pos);
    addToArr((void*)name.c_str(), name.size(), buffer.get(), &pos);
    for (int j = 0; j < mat.rows; j++) 
        for (int k = 0; k < aFeatureSize; k++)
            addFloatToArr(mat.at<float>(j,k), buffer.get(), &pos);
    timespec start_time = getTime();///network time
    bool status = aMie->aStorage->write(aFile, (char*)buffer.get(), buffer_size);
    aMie->aNetworkFeatureTime += diffSec(start_time, getTime());;
    return status;
}

int MIE::FeatureStreamer::getNextFeatures(string& name, vector<vector<char>>& keywords)
{
    if(aFeatureIndex < aNFeatures){
        vector<char> buffer;
        timespec start_time = getTime();
        int status = aMie->aStorage->read(aFile, buffer, 2 * sizeof(int));
        aMie->aNetworkFeatureTime += diffSec(start_time, getTime());
        if(0 > status)
            return OP_FAILED;
        int pos = 0;
        int name_size = readIntFromArr(buffer.data(), &pos);
        int n_keywords = readIntFromArr(buffer.data(), &pos);
        start_time = getTime();
        status = aMie->aStorage->read(aFile, buffer, name_size + n_keywords * aFeatureSize);
        aMie->aNetworkFeatureTime += diffSec(start_time, getTime());
        if(0 > status)
            return OP_FAILED;
        name = string(buffer.data(), name_size);
        pos = name_size;
        keywords.resize(n_keywords);
        for (int j = 0; j < n_keywords; j++) {
            keywords[j].resize(aFeatureSize);
            readFromArr(keywords[j].data(), aFeatureSize, buffer.data(), &pos);
        }
        aFeatureIndex++;
        return n_keywords * aFeatureSize;
    }
    else{
        return OP_FAILED;
    }
}

bool MIE::FeatureStreamer::writeNextFeatures(const std::string& name, const std::vector<
    std::vector<char>>& keywords)
{
    int n_keywords = (int)keywords.size();
    char buffer[2 * sizeof(int)];
    int pos = 0;
    addIntToArr(name.size(), buffer, &pos);
    addIntToArr(n_keywords, buffer, &pos);
    timespec start_time = getTime();
    bool status = aMie->aStorage->write(aFile, buffer, pos);
    if(status != NO_ERRORS)
        goto end;
    status = aMie->aStorage->write(aFile, name.data(), name.size());
    if(status != NO_ERRORS)
        goto end;
    for (int j = 0; j < n_keywords; j++){
        status = aMie->aStorage->write(aFile, keywords[j].data(), keywords[j].size());
        if(status != NO_ERRORS)
            goto end;
    }
    end:
    aMie->aNetworkFeatureTime += diffSec(start_time, getTime());
    return status;
}

bool MIE::FeatureStreamer::writeHeader(size_t nFeatures, int featureSize)
{
    int features_size;
    if(0 < featureSize)
        aFeatureSize = featureSize;
    features_size = aFeatureSize;
    int total_imgs = aNFeatures + nFeatures;
    char buffer[2 * sizeof(int)];
    int pos = 0;
    addIntToArr(total_imgs, buffer, &pos);
    addIntToArr(features_size, buffer, &pos);
    bool status = NO_ERRORS;
    timespec start_time = getTime();
    status = aMie->aStorage->write(aFile, buffer, pos);
    aMie->aNetworkFeatureTime += diffSec(start_time, getTime());
    return status;
}

MIE::MIE(Storage* storage) : aIndexTime(0), aTrainTime(0),
    aSearchTime(0), aNSearchingThreads(0), aNetworkIndexTime(0), aNetworkFeatureTime(0),
    aNImgs(0), aNextId(0), aNDocs(0), aInProgressSearches(0), aIndexing(false),
    aCurrentFeaturesSize(0)
{
    aStorage = storage;
    aMaxFeaturesSize = getMaxFeaturesSize();
    aMaxTempFeaturesSize = getMaxTempFeaturesSize();
    aImgFeatures = make_shared<map<int,Mat>>();
    aTextFeatures = make_shared<map<string,vector<vector<char>>>>();
    aImgIndex.reserve(clusters);
    for(unsigned i = 0; i < clusters; i++)
        aImgIndex.push_back(make_shared<map<int,int>>());
    aBowExtr = unique_ptr<BOWImgDescriptorExtractor>(new BOWImgDescriptorExtractor(
        DescriptorMatcher::create("BruteForce-L1")));
    vector<char> codebook_buffer;
    timespec start_time = getTime();
    int buffer_size = aStorage->read(CODEBOOK_FILE, codebook_buffer);
    aTrainTime += diffSec(start_time, getTime());
    if(0 < buffer_size){
        FileStorage fs;
        Mat codebook;
        string cbBuffer((const char*)(codebook_buffer.data()), codebook_buffer.size());
        fs.open(cbBuffer, FileStorage::MEMORY | FileStorage::READ);
        fs["codebook"] >> codebook;
        fs.release();
        aBowExtr->setVocabulary(codebook);
        cout<<"Read Codebook!"<<endl;
	}
    readIndex();
}

MIE::~MIE(){}

void MIE::addDoc(const char* data, const string& name)
{
    map<int,Mat> mats;
    vector<vector<char>> keywords;
    parseFeatures(data, mats, keywords, name);
    aImgFeaturesLock.lock();
    for(map<int, Mat>::iterator it = mats.begin(); it != mats.end(); ++it){
        aImgDocs[it->first] = name;
        (*aImgFeatures)[it->first] = it->second;
    }
    aImgFeaturesLock.unlock();
    aTextFeaturesLock.lock();
    (*aTextFeatures)[name] = keywords;
    aTextFeaturesLock.unlock();
}

set<QueryResult,cmp_QueryResult> MIE::search(const char* data, size_t& nResults)
{
    unique_lock<recursive_mutex> index_lock(aIndexLock);
    while(aIndexing)
        aIndexDone.wait(index_lock);
    aInProgressSearches++;
    index_lock.unlock();
    map<int,Mat> mat;
    vector<vector<char>> keywords;
    set<QueryResult,cmp_QueryResult> merged_results;
    nResults = parseFeatures(data, mat, keywords);
    unique_lock<mutex> tmp(aSearchTimeLock);
    if(0 == aNSearchingThreads){
        aStartSearchTime = getTime();
    }
    aNSearchingThreads++;
    tmp.unlock();
    set<QueryResult,cmp_QueryResult> img_results;
    set<QueryResult,cmp_QueryResult> text_results;
    if(0 == aNImgs)
        img_results = linearImgSearch(mat);
    else
        img_results = imgSearch(mat);
    if(0 == aNDocs)
        text_results = linearTextSearch(keywords);
    else
        text_results = textSearch(keywords);
    merged_results = mergeSearchResults(img_results, text_results);
    double time = diffSec(aStartSearchTime, getTime());
    tmp.lock();
    if(1 == aNSearchingThreads)
        aSearchTime += time;
    aNSearchingThreads--;
    tmp.unlock();
    index_lock.lock();
    aInProgressSearches--;
    if(0 == aInProgressSearches)
        aSearchesDone.notify_one();
    return merged_results;
}

int MIE::parseFeatures(const char* data, map<int,Mat>& mats, vector<vector<char>>& keywords,
    const string& name)
{
    int pos = 0;
    int n_results = 0;
    const int n_mats = readIntFromArr(data, &pos);
    if(name.empty())
        n_results = readIntFromArr(data, &pos);
    const int feature_size = readIntFromArr(data, &pos);
    const int n_keywords = readIntFromArr(data, &pos);
    const int keyword_size = readIntFromArr(data, &pos);
    int total_features_size = n_keywords * keyword_size;
    //store img features
    for(int i = 0; i < n_mats; i++){
        int n_features = readIntFromArr(data, &pos);
        Mat *mat;
        int id = 0;
        if(!name.empty()){
            id = aNextId++;
            total_features_size += n_features * feature_size * sizeof(float);
        }
        else{
            id = i; 
        }
        mat = &(mats[id]);
        mat->create(n_features, feature_size, CV_32F);
        for (int i = 0; i < n_features; i++)
            for (int j = 0; j < feature_size; j++)
                mat->at<float>(i,j) = float(readIntFromArr(data, &pos));
    }
    //store text features
    keywords.resize(n_keywords);
    for (int i = 0; i < n_keywords; i++) {
        keywords[i].resize(keyword_size);
        readFromArr(keywords[i].data(), keyword_size, data, &pos);
    }
    if(n_results < 0)
        n_results = 0;
    unique_lock<mutex> tmp(aCurrentFeaturesSizeLock);
    if(total_features_size + aCurrentFeaturesSize > aMaxFeaturesSize){
        thread worker(selfIndex, this);
        worker.detach();
    }
    else{
        aCurrentFeaturesSize += total_features_size;
    }
    return n_results;
}

set<QueryResult,cmp_QueryResult> MIE::imgSearch(const map<int,Mat>& features)
{
    map<string,double> queryResults;
    for(map<int,Mat>::const_iterator it = features.begin(); it != features.end(); ++it){
        Mat bowDesc;
        aBowExtr->compute(it->second,bowDesc);
        for (int i = 0; i < clusters; i++) {
            int queryTf = denormalize(bowDesc.at<float>(i),it->second.rows);
            if (queryTf > 0) {
                shared_ptr<map<int,int>> posting_list = aImgIndex[i];
                double idf = getIdf(aNImgs, posting_list->size());
                for (map<int,int>::iterator it2 = posting_list->begin(); it2 != posting_list->end();
                    ++it2){
                    double score = scaledTfIdf/*getTfIdf*/(queryTf, it2->second, idf);
                    unique_lock<mutex> tmp(aImgFeaturesLock);
                    string id = aImgDocs[it2->first];
                    tmp.unlock();
                    if (queryResults.count(id) == 0)
                        queryResults[id] = score;
                    else
                        queryResults[id] += score;
                }
            }
        }
    }
    return sort(queryResults);
}

set<QueryResult,cmp_QueryResult> MIE::linearImgSearch(const map<int,Mat>& features)
{
    map<string,double> queryResults;
    unique_lock<mutex> img_features_lock(aImgFeaturesLock);
    for(map<int,Mat>::const_iterator it = features.begin(); it != features.end(); ++it){
        for(map<int,Mat>::iterator it2 = aImgFeatures->begin(); it2 != aImgFeatures->end(); ++it2){
        double score = 0;
        int rows = 0;
        if(it->second.rows > it2->second.rows)
            rows = it2->second.rows;
        else
            rows = it->second.rows;
        for(int i = 0; i < rows; i++){
            for(int j = 0; j < it2->second.cols; j++){
                if(it->second.at<float>(i,j) == it2->second.at<float>(i,j)){
                    score++;
                }
            }
        }
        string id = aImgDocs[it2->first];
        if (queryResults.count(id) == 0)
            queryResults[id] = score;
        else
            queryResults[id] += score;
        }
    }
    return sort(queryResults);
}


set<QueryResult,cmp_QueryResult> MIE::textSearch(const vector<vector<char>>& keywords)
{
    map<vector<char>,int> query;
    for (size_t i = 0; i < keywords.size(); i++) {
        if (query.count(keywords[i]) == 0)
            query[keywords[i]] = 1;
        else
            query[keywords[i]]++;
    }
    map<string,double> queryResults;
    for (map<vector<char>,int>::iterator queryTerm = query.begin(); queryTerm != query.end();
        ++queryTerm) {
        map<vector<char>,shared_ptr<map<string,int>>>::iterator it_posting_list =
            aTextIndex.find(queryTerm->first);
        if(aTextIndex.end() == it_posting_list)
            continue;
        shared_ptr<map<string,int>> posting_list = it_posting_list->second;
        double idf = getIdf(aNDocs, posting_list->size());
        //cout<<aNDocs<<" "<<posting_list->size()<<" "<<idf;
        for (map<string,int>::iterator posting = posting_list->begin(); posting != posting_list->end();
            ++posting) {
            //float score = bm25L(posting->second, queryTerm->second, idf, docLength, avgDocLength);
            double score =  scaledTfIdf/*getTfIdf*/(queryTerm->second, posting->second, idf);
            //cout<<" "<<queryTerm->second<<" "<<posting->second<<" "<<score;
            if (queryResults.count(posting->first) == 0)
                queryResults[posting->first] = score;
            else
                queryResults[posting->first] += score;
        }
    }
    //cout<<endl;
    return sort(queryResults);
}

set<QueryResult,cmp_QueryResult> MIE::linearTextSearch(const vector<vector<char>>& keywords)
{
    map<string,double> queryResults;
    unique_lock<mutex> text_features_lock(aTextFeaturesLock);
    if(!keywords.empty()){
        for(vector<vector<char>>::const_iterator it = keywords.begin(); it != keywords.end(); ++it){
            for(map<string,vector<vector<char>>>::iterator it2 = aTextFeatures->begin(); 
                it2 != aTextFeatures->end(); ++it2){
                double score = 0;
                for(vector<vector<char>>::iterator it3 = it2->second.begin(); it3 != it2->second.end();
                    ++it3){
                    if(*it3 == *it)
                        score++;
                }
                queryResults[it2->first] = score;
            }
        }
    }
    else{
        for(map<string,vector<vector<char>>>::iterator it = aTextFeatures->begin(); it != 
            aTextFeatures->end(); ++it){
            if(it->second.empty())
                queryResults[it->first] = 1;
        }
    }
    return sort(queryResults);
}

set<QueryResult,cmp_QueryResult> MIE::mergeSearchResults(const set<QueryResult,cmp_QueryResult>&
    imgResults, const set<QueryResult,cmp_QueryResult>& textResults)
{
    const float sigma = 0.01f;
    //prepare ranks
    map<string,Rank> ranks;
    int i = 1;
    for(set<QueryResult,cmp_QueryResult>::iterator it = textResults.begin(); it != textResults.end();
        ++it){
        Rank qr;
        qr.textRank = i++;
        qr.imgRank = 0;
        ranks[it->docId] = qr;
    }
    i = 1;
    for(set<QueryResult,cmp_QueryResult>::iterator it = imgResults.begin(); it != imgResults.end();
        ++it){
        map<string,Rank>::iterator rank = ranks.find(it->docId);
        if (rank == ranks.end()) {
            Rank qr;
            qr.textRank = 0;
            qr.imgRank = i++;
            ranks[it->docId] = qr;
        }
        else{
            rank->second.imgRank = i++;
        }
    }
    map<string,double> queryResults;
    for(map<string,Rank>::iterator it = ranks.begin(); it != ranks.end(); ++it){
        double score = 0.f, df = 0.f;
        if (it->second.textRank > 0) {
            score = 1 / pow(it->second.textRank, 2);
            df++;
        }
        if (it->second.imgRank > 0) {
            score += 1 / pow(it->second.imgRank, 2);
            df++;
        }
        score *= log(df+sigma);
        queryResults[it->first] = score;
    }
    return sort(queryResults);
}

set<QueryResult,cmp_QueryResult> MIE::sort (const map<string,double>& queryResults)
{
    set<QueryResult,cmp_QueryResult> orderedResults;
    for (map<string,double>::const_iterator it = queryResults.begin(); it != queryResults.end(); ++it){
        QueryResult qr;
        qr.docId = it->first;
        qr.score = it->second;
        orderedResults.insert(qr);
    }
    return orderedResults;
}

void MIE::selfIndex(MIE* mie){
    mie->index(true);
}

void MIE::index(bool train)
{
    unique_lock<recursive_mutex> index_lock(aIndexLock);
    if(aIndexing)
        return;
    else
        aIndexing = true;
    index_lock.unlock();
    //get current features and assign new structures for future documents
    lock(aImgFeaturesLock, aTextFeaturesLock, aCurrentFeaturesSizeLock);
    unique_lock<mutex> img_features_lock(aImgFeaturesLock, adopt_lock);
    shared_ptr<map<int,Mat>> img_features = aImgFeatures;
    aNImgs += aImgFeatures->size();
    aImgFeatures.reset(new map<int,Mat>());

    unique_lock<mutex> text_features_lock(aTextFeaturesLock, adopt_lock);
    shared_ptr<map<string,vector<std::vector<char>>>> text_features = aTextFeatures;
    aNDocs += aTextFeatures->size();
    aTextFeatures.reset(new map<string,vector<std::vector<char>>>());

    unique_lock<mutex> current_features_size_lock(aCurrentFeaturesSizeLock, adopt_lock);
    aCurrentFeaturesSize = 0;

    img_features_lock.unlock();
    text_features_lock.unlock();
    current_features_size_lock.unlock();

    index_lock.lock();
    while(0 < aInProgressSearches)
        aSearchesDone.wait(index_lock);
    index_lock.unlock();
    for(unsigned i = 0; i < clusters; i++)
        aImgIndex[i]->clear();
    //aTextIndex.clear();
    indexImgs(*img_features);
    indexText(*text_features);
    persistImgIndex();
    persistTextIndex();
    index_lock.lock();
    aIndexing = false;
    aIndexDone.notify_all();
}

void MIE::indexImgs(const map<int,Mat>& imgFeatures, bool train)
{
    map<int,Mat> tmp_features;
    map<int,string> tmp_names;
    size_t write_buffer_size = 0;
    if(0 == aBowExtr->getVocabulary().rows || train){
        timespec start_time = getTime();///train time
        TermCriteria terminate_criterion;
        terminate_criterion.epsilon = FLT_EPSILON;
        BOWKMeansTrainer bowTrainer(clusters, terminate_criterion, 3, KMEANS_PP_CENTERS );
        RNG& rng = theRNG();
        aTrainTime += diffSec(start_time, getTime());
        FeatureStreamer stream(this, IMG_FEATURES_FILE);
        int tmp_id = 0;
        string tmp_name;
        Mat mat;
        while(0 < stream.getNextFeatures(tmp_id, mat, tmp_name)){
            if (rng.uniform(0.f,1.f) <= 0.1f){
                if(!mat.empty()){
                    start_time = getTime();
                    bowTrainer.add(mat);
                    //cout<<tmp_id<<" ";
                    aTrainTime += diffSec(start_time, getTime());
                }
            }
            if(aMaxTempFeaturesSize > stream.aTotalFeaturesSize){
                tmp_features[tmp_id] = mat;
                tmp_names[tmp_id] = tmp_name;
                write_buffer_size += 3 * sizeof(int) + tmp_name.size() + sizeof(uint64_t) * mat.rows *
                mat.cols;
            }
            unique_lock<mutex> img_features_lock(aImgFeaturesLock);
            if(aImgDocs.find(tmp_id) == aImgDocs.end()){
                aImgDocs[tmp_id] = tmp_name;
            }
            img_features_lock.unlock();
        }
        for(map<int,Mat>::const_iterator it = imgFeatures.begin(); it != imgFeatures.end(); ++it){
            if (rng.uniform(0.f,1.f) <= 0.1f){
                    if(!it->second.empty()){
                        start_time = getTime();
                        bowTrainer.add(it->second);
                        //cout<<it->first<<" ";
                        aTrainTime += diffSec(start_time, getTime());
                    }
            }
            unique_lock<mutex> img_features_lock(aImgFeaturesLock);
            write_buffer_size += 3 * sizeof(int) + aImgDocs[it->first].size() + sizeof(uint64_t) *
                it->second.rows * it->second.cols;
        }
        //cout<<endl;
        cout<<"build codebook with "<<bowTrainer.descriptorsCount()<<" descriptors!"<<endl;
        start_time = getTime();
        Mat codebook = bowTrainer.cluster();
        FileStorage fs;
        string ext(CODEBOOK_FILE);
        fs.open(ext, FileStorage::MEMORY | FileStorage::WRITE);
        fs << "codebook" << codebook;
        string buffer = fs.releaseAndGetString();
        aStorage->write(CODEBOOK_FILE, (char*)buffer.c_str(), buffer.size());
        fs.release();
        aBowExtr->setVocabulary(codebook);
        aTrainTime += diffSec(start_time, getTime());
    }

    unique_ptr<FeatureStreamer> stream;
    //index images older images features
    if(0 < tmp_features.size()){
        stream = unique_ptr<FeatureStreamer>(new FeatureStreamer(this, IMG_FEATURES_FILE, false, true,
            write_buffer_size));
        stream->writeHeader(imgFeatures.size() + tmp_features.size(), imgFeatures.begin()->second.cols);
        for(map<int,Mat>::iterator it = tmp_features.begin(); it != tmp_features.end(); ++it){
            index(it->first, it->second);
            stream->writeNextFeatures(it->first, it->second, tmp_names[it->first]);
        }
    }
    else{
        stream = unique_ptr<FeatureStreamer>(new FeatureStreamer(this, IMG_FEATURES_FILE, true, true,
            write_buffer_size));
        stream->writeHeader(imgFeatures.size(), imgFeatures.begin()->second.cols);
        int tmp_id = 0;
        string tmp_name;
        Mat mat;
        while(0 < stream->getNextFeatures(tmp_id, mat, tmp_name)){
            index(tmp_id, mat);
            stream->writeNextFeatures(tmp_id, mat, tmp_name);
        }
    }
    //index and store newer images features
    for(map<int,Mat>::const_iterator it=imgFeatures.begin(); it != imgFeatures.end(); ++it){
        index(it->first, it->second);
        unique_lock<mutex> tmp(aImgFeaturesLock);
        string name = aImgDocs[it->first];
        tmp.unlock();
        stream->writeNextFeatures(it->first, it->second, name);
    }
}

void MIE::index(int id, const Mat& mat)
{
    timespec start_time = getTime();///index time
    Mat bowDesc;
    aBowExtr->compute(mat, bowDesc);
    for (int i = 0; i < clusters; i++) {
        int val = denormalize(bowDesc.at<float>(i),mat.rows);
        if (val > 0)
            (*aImgIndex[i])[id] = val;
    }
    aIndexTime += diffSec(start_time, getTime());
}

void MIE::indexText(const map<string,vector<vector<char>>> textFeatures)
{
    int keywordSize = 0;
    int nTotalKeywords = 0;
    int names_size = 0;

    map<string,vector<vector<char>>>::const_iterator it = textFeatures.begin();
    while(0 == keywordSize && textFeatures.end() != it){
        if(!it->second.empty()){
            keywordSize = it->second[0].size();
        }
        nTotalKeywords += (int)it->second.size();
        names_size += (int)it->first.size();
        ++it;
    }
    while(textFeatures.end() != it){
        nTotalKeywords += (int)it->second.size();
        names_size += (int)it->first.size();
        ++it;
    }
    size_t write_buffer_size = 2 * sizeof(int) * textFeatures.size() + names_size +
        nTotalKeywords * keywordSize * sizeof(char);
    FeatureStreamer stream(this, TXT_FEATURES_FILE,true, true, write_buffer_size);
    stream.writeHeader(textFeatures.size(), keywordSize);

    string tmp_name;
    vector<vector<char>> keywords;
    while(0 < stream.getNextFeatures(tmp_name, keywords)){
        //index(tmp_name, keywords);
        stream.writeNextFeatures(tmp_name, keywords);
    }

    for (map<string,vector<vector<char>>>::const_iterator it = textFeatures.begin();
        it != textFeatures.end(); ++it) {
        index(it->first, it->second);
        stream.writeNextFeatures(it->first, it->second);
    }
}

void MIE::index(const string& name, const vector<vector<char>>& keywords)
{
    timespec start_time = getTime();///index time
    for (size_t i = 0; i < keywords.size(); i++) {
        map<vector<char>,shared_ptr<map<string,int>>>::iterator posting_list = 
            aTextIndex.find(keywords[i]);
        if (aTextIndex.end() == posting_list){
            shared_ptr<map<string,int>> new_posting_list = make_shared<map<string,int>>();
            (*new_posting_list)[name] = 1;
            aTextIndex[keywords[i]] = new_posting_list;
        } 
        else{
            map<string,int>::iterator posting = posting_list->second->find(name);
            if (posting_list->second->end() == posting)
                (*posting_list->second)[name] = 1;
            else
                posting->second++;
        }
    }
    aIndexTime += diffSec(start_time, getTime());
}

bool MIE::readIndex()
{
    timespec start_time = getTime();
    int status = aStorage->prepareRead(IMG_INDEX_FILE);
    aNetworkIndexTime += diffSec(start_time, getTime());
    if(0 > status){
        start_time = getTime();
        aStorage->closeRead(IMG_INDEX_FILE);
        aNetworkIndexTime += diffSec(start_time, getTime());
        return OP_FAILED;
    }
    start_time = getTime();
    status = aStorage->prepareRead(TXT_INDEX_FILE);
    aNetworkIndexTime += diffSec(start_time, getTime());
    if(0 > status){
        start_time = getTime();
        aStorage->closeRead(TXT_INDEX_FILE);
        aStorage->closeRead(IMG_INDEX_FILE);
        aNetworkIndexTime += diffSec(start_time, getTime());
        return OP_FAILED;
    }
    readImgIndex();
    readTxtIndex();
    start_time = getTime();
    aStorage->closeRead(TXT_INDEX_FILE);
    aStorage->closeRead(IMG_INDEX_FILE);
    aNetworkIndexTime += diffSec(start_time, getTime());
    return NO_ERRORS;
}

void MIE::persistImgIndex()
{
    size_t index_size = (int)aImgIndex.size();
    unsigned total_posting_list_size = 0;
    unsigned total_name_size = 0;
    unique_lock<mutex> img_features_lock(aImgFeaturesLock);
    for (size_t i = 0; i < index_size; i++){
        total_posting_list_size += aImgIndex[i]->size();
        for (map<int,int>::iterator it = aImgIndex[i]->begin(); it != aImgIndex[i]->end(); ++it){
            total_name_size += aImgDocs[it->first].size();
        }
    }
    img_features_lock.unlock();

    size_t file_size = 2 * sizeof(int) + sizeof(int) * index_size + total_posting_list_size * 3 * 
        sizeof(int) + total_name_size;
    
    timespec startTime = getTime();///network time
    aStorage->prepareWrite(IMG_INDEX_FILE, file_size);
    aNetworkIndexTime += diffSec(startTime, getTime());

    int pos = 0;
    char buffer[6 * sizeof(int)];
    addIntToArr(aNImgs, buffer, &pos);
    addIntToArr(index_size, buffer, &pos);

    for (size_t i = 0; i < index_size; i++) {
        int posting_list_size = (int)aImgIndex[i]->size();
        addIntToArr(posting_list_size, buffer, &pos);

        for (map<int,int>::iterator it = aImgIndex[i]->begin(); it != aImgIndex[i]->end(); ++it){
            addIntToArr(it->first, buffer, &pos);
            addIntToArr(it->second, buffer, &pos);
            img_features_lock.lock();
            string name = aImgDocs[it->first];
            img_features_lock.unlock();
            addIntToArr(name.size(), buffer, &pos);

            startTime = getTime();///network time
            aStorage->write(IMG_INDEX_FILE, buffer, pos);
            aStorage->write(IMG_INDEX_FILE, name.c_str(), name.size());
            aNetworkIndexTime += diffSec(startTime, getTime());

            pos = 0;
        }
    }
    startTime = getTime();///network time
    aStorage->finishWrite(IMG_INDEX_FILE);
    aNetworkIndexTime += diffSec(startTime, getTime());;
}

void MIE::readImgIndex()
{
    ///read imgIndex
    vector<char> buffer;
    timespec startTime = getTime();///index time
    aStorage->read(IMG_INDEX_FILE, buffer, 6 * sizeof(int));
    aNetworkIndexTime += diffSec(startTime, getTime());
    
    int pos = 0;
    int index_size = 0;
    aNImgs = readIntFromArr(buffer.data(), &pos);
    index_size = readIntFromArr(buffer.data(), &pos);

    for (int i = 0; i < index_size; i++){
        int posting_list_size = readIntFromArr(buffer.data(), &pos);
        for (int j = 0; j < posting_list_size; j++) {
            int img_id = readIntFromArr(buffer.data(), &pos);
            int score = readIntFromArr(buffer.data(), &pos);
            int name_size = readIntFromArr(buffer.data(), &pos);
            
            startTime = getTime();///index time
            if(j == posting_list_size - 1 && i + 1 < index_size)
                aStorage->read(IMG_INDEX_FILE, buffer, name_size + 4 * sizeof(int));
            else if(j < posting_list_size - 1)
                aStorage->read(IMG_INDEX_FILE, buffer, name_size + 3 * sizeof(int));
            else
                aStorage->read(IMG_INDEX_FILE, buffer, name_size);
            aNetworkIndexTime += diffSec(startTime, getTime());;

            aImgDocs[img_id] = string(buffer.data(), name_size);
            (*aImgIndex[i])[img_id] = score;
            pos = name_size;
        }
    }
}

void MIE::persistTextIndex()
{
    size_t index_size = aTextIndex.size();
    size_t keyword_size = aTextIndex.begin()->first.size();

    size_t total_posting_list_size = 0;
    unsigned total_name_size = 0;
    for (map<vector<char>,shared_ptr<map<string,int>>>::iterator it = aTextIndex.begin(); it !=
        aTextIndex.end(); ++it){
        total_posting_list_size += it->second->size();
        for(map<string,int>::iterator it2 = it->second->begin(); it2 != it->second->end(); ++it2)
            total_name_size += it2->first.size();
    }

    size_t file_size = 3 * sizeof(int) + keyword_size * sizeof(unsigned char) * index_size +
        sizeof(int) * index_size + total_posting_list_size * 2 * sizeof(int) + total_name_size;

    timespec startTime = getTime();///network time
    aStorage->prepareWrite(TXT_INDEX_FILE, file_size);
    aNetworkIndexTime += diffSec(startTime, getTime());
    
    //printf("Allocated buff with %d bytes\n", buffSize);
    char buffer[6 * sizeof(int)];
    int pos = 0;
    addIntToArr(aNDocs, buffer, &pos);
    addIntToArr(index_size, buffer, &pos);
    addIntToArr(keyword_size, buffer, &pos);

    for (map<vector<char>,shared_ptr<map<string,int>>>::iterator it = aTextIndex.begin(); it !=
        aTextIndex.end(); ++it){
        int posting_list_size = (int)it->second->size();
        addIntToArr(posting_list_size, buffer, &pos);

        for (map<string,int>::iterator it2 = it->second->begin(); it2 != it->second->end(); ++it2) {
            addIntToArr(it2->first.size(), buffer, &pos);
            addIntToArr(it2->second, buffer, &pos);
            startTime = getTime();///network time
            aStorage->write(TXT_INDEX_FILE, buffer, pos);
            aStorage->write(TXT_INDEX_FILE, it2->first.c_str(), it2->first.size());
            aNetworkIndexTime += diffSec(startTime, getTime());
            pos = 0;
        }
        startTime = getTime();///network time
        aStorage->write(TXT_INDEX_FILE, it->first.data(), keyword_size);
        aNetworkIndexTime += diffSec(startTime, getTime());
    }
    startTime = getTime();///network time
    aStorage->finishWrite(TXT_INDEX_FILE);
    aNetworkIndexTime += diffSec(startTime, getTime());
}

void MIE::readTxtIndex()
{
    vector<char> buffer;
    timespec startTime = getTime();//network time
    aStorage->read(TXT_INDEX_FILE, buffer, 6 * sizeof(int));
    aNetworkIndexTime += diffSec(startTime, getTime());

    int pos = 0;
    aNDocs = readIntFromArr(buffer.data(), &pos);
    int index_size = readIntFromArr(buffer.data(), &pos);
    int keyword_size = readIntFromArr(buffer.data(), &pos);

    for (int i = 0; i < index_size; i++){
        int posting_list_size = readIntFromArr(buffer.data(), &pos);
        shared_ptr<map<string,int>> aux(new map<string,int>());
        for (int j = 0; j < posting_list_size; j++){
            int name_size = readIntFromArr(buffer.data(), &pos);
            int score = readIntFromArr(buffer.data(), &pos);
            
            startTime = getTime();//network time
            if(j == posting_list_size - 1)
                if(i + 1 < index_size)
                    aStorage->read(TXT_INDEX_FILE, buffer, name_size + keyword_size + 3 * sizeof(int));
                else
                    aStorage->read(TXT_INDEX_FILE, buffer, name_size + keyword_size);
            else
                aStorage->read(TXT_INDEX_FILE, buffer, name_size + 2 * sizeof(int));
            aNetworkIndexTime += diffSec(startTime, getTime());

            string name(buffer.data(), name_size);
            pos = name_size;
            (*aux)[name] = score;
        }
        vector<char> key;
        key.reserve(keyword_size);
        key.insert(key.end(), buffer.data() + pos, buffer.data() + pos + keyword_size);
        aTextIndex[key] = aux;
        pos += keyword_size;
    }
}

double MIE::indexTime()
{
    return aIndexTime;
}

double MIE::networkIndexTime()
{
    return aNetworkIndexTime;
}

double MIE::trainTime()
{
    return aTrainTime;
}

double MIE::networkFeatureTime()
{
    return aNetworkFeatureTime;
}

double MIE::searchTime()
{
    unique_lock<mutex> tmp(aSearchTimeLock);
    return aSearchTime;
}

void MIE::resetTimes()
{
    unique_lock<recursive_mutex> index_lock(aIndexLock);
    while(aIndexing)
        aIndexDone.wait(index_lock);
    aIndexTime = 0;
    aNetworkIndexTime = 0;
    aTrainTime = 0;
    aNetworkFeatureTime = 0;
    index_lock.unlock();
    unique_lock<mutex> search_lock(aSearchTimeLock);
    aSearchTime = 0;
}

void MIE::wipe(const string& suffix)
{
    //lock all data structures
    unique_lock<recursive_mutex> index_lock(aIndexLock);
    while(0 < aInProgressSearches)
        aSearchesDone.wait(index_lock);
    lock(aImgFeaturesLock, aTextFeaturesLock, aCurrentFeaturesSizeLock);
    unique_lock<mutex> img_lock(aImgFeaturesLock, adopt_lock);
    unique_lock<mutex> txt_lock(aTextFeaturesLock, adopt_lock);
    unique_lock<mutex> fea_lock(aCurrentFeaturesSizeLock, adopt_lock);
    set<string> to_remove;
    //read img docs, this will handle all documents with images
    for(map<int,string>::const_iterator it = aImgDocs.begin(); it != aImgDocs.end(); ++it){
        to_remove.insert(it->second);
    }
    //read txt documents for the case when a document has no images
    //read in memory txt features
    for(map<string,vector<vector<char>>>::const_iterator it = aTextFeatures->begin();
        it != aTextFeatures->end(); ++it){
        to_remove.insert(it->first);
    }
    //read stored txt features
    {
        FeatureStreamer stream(this, TXT_FEATURES_FILE);
        vector<vector<char>> tpm_keywords;
        string tmp_name;
        while(0 < stream.getNextFeatures(tmp_name, tpm_keywords)){
            to_remove.insert(tmp_name);
        }
    }
    //remove all documents
    for(set<string>::const_iterator it = to_remove.begin(); it != to_remove.end(); ++it){
        aStorage->remove(*it+suffix);
    }
    //remove features and index files
    aStorage->remove(IMG_FEATURES_FILE);
    aStorage->remove(IMG_INDEX_FILE);
    aStorage->remove(TXT_FEATURES_FILE);
    aStorage->remove(TXT_INDEX_FILE);
    aStorage->remove(CODEBOOK_FILE);
    //clear memory
    aImgFeatures.reset(new map<int,Mat>());
    aNImgs = 0;
    aTextFeatures.reset(new map<string,vector<std::vector<char>>>());
    aNDocs = 0;
    aCurrentFeaturesSize = 0;
    aNextId = 0;
    aImgDocs.clear();
    for(unsigned i = 0; i < clusters; i++)
        aImgIndex[i]->clear();
    aTextIndex.clear();
    aBowExtr = unique_ptr<BOWImgDescriptorExtractor>(new BOWImgDescriptorExtractor(
        DescriptorMatcher::create("BruteForce-L1")));
    //clear times and cache
    resetTimes();
    aStorage->resetTimes();
    aStorage->resetCache();
}

}//end namespace