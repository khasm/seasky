# docker-client

The docker-client contains the code for the demo docker. It will perform a series of tests and give an output on the times measured. Before building the docker the datasets folder must be populated. For the tests 1000 images and tags of the MIR-FLICKR dataset was used. The dataset can be downloaded from http://press.liacs.nl/mirflickr/mirdownload.html. The images used were the first 1000 from the images0.zip and the respective tags from tags.zip.
The images must be in the folder datasets/flickr_imgs and the tags must be in the folder flickr_tags. The demo client expects the images to be named im<id>.jpg and the tags to be named tags<id>.txt, with id being a numerical id starting on 0. For convenience the docker will rename all images and tags in those folders to the expected naming convention (the files outside the docker will remain unaffected). It is possible that this renaming breaks association of tags and images, if for example the image cool_image.jpg is associated with the tags cool_tags.txt then the image might be renamed to im546.jpg and the tags to tags375.txt, instead of tags546.txt, however this shouldn't happen with the default names of the MIR-FLICKR dataset.

# Testbenches

The testbench folders contain scripts with the configuration used to run the dockers during each testbench. Although they simplify running the tests, building the dockers and starting the respective script in the correct servers is still required.

#Output

The after each test the client will output several evaluations. All the time measurements are in seconds. For the client side there is:

client cache: Indicates if the client cache was active during the test
verified: Indicates if the middleware passed the TPM verification
client cache hit ratio: Indicates the hit ratio of the client cache
CBIR Encryption: Time spent in the CBIR algorithm
Symmetric Encryption: Time spent in symmetric encryption
Misc: Indicates how much time, in seconds, the client spent is the encryption proccess, but not in the CBIR or symmetric encryption. This is the time spent to format the output buffer that the client application will receive.
featureTime: Time spent to extract features
indexTime: Time spent to compute indexable features (images only)
cryptoTime: Sum of the previous encryption times (CBIR Encryption, Symmetric Encryption, Misc)
cloudTime: Time spent in network operation, sending and receving data.
total_time: Total time spent doing the test operations

For the server the measurements are:

Train time: Time spent in the training phase
Index time: Time spent indexing features
Search time: Time spent on search operations
Network add time: Total time that the storage module spent on uploads for user data
Network parallel add time: Real time that the storage module spent on uploads for user data
Network get time: Total time that the storage module spent on downloads for user data
Network parallel get time: Real time that the storage module spent on downloads for user data
Network feature time: Time spent uploading or downloading features from the storage servers
Network index time: Time spent uploading or downloading features from the storage servers
Network upload time: Total time spent uploading data
Network parallel upload: Real time spent uploading data
Network download time: Total time spent downloading data
Network parallel download time: Real time spent downloading data
Network time: Sum of the times spent uploading and downloading.
Hit Ratio: hit ratio of the server cache

Several network times have two version, a regular one and a parallel one. This comes from the fact that the server is multithreaded and several uploads or downloads can occur at the same time. The total time is the sum of the times that each operation took, ie, if a upload took 10 seconds and another took 11 seconds then the total time is 21 seconds. However it is possible that the upload who took 11 seconds started at instant 0, and the other upload, that took 10 seconds, start at instant 1. Both will end at instant 11, so the real time will be 11 seconds. In the thesis the parallel versions of the times were used.
