FROM ubuntu:16.04
RUN apt-get update && apt-get install -y openjdk-8-jdk && mkdir depsky
COPY ./lib /depsky/lib
COPY ./depsky.jar /depsky/
COPY ./Run_LocalStorage.sh /depsky/
WORKDIR /depsky
ENTRYPOINT ["/depsky/Run_LocalStorage.sh"]