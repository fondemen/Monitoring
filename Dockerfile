FROM openjdk:8-jdk-alpine AS build
RUN apk add --no-cache git maven
RUN git clone --depth 1 https://github.com/fondemen/Monitoring.git
# Downloading maven dependencies
RUN cd Monitoring ; mvn install clean -DskipTests=true -Dmaven.javadoc.skip=true -B -V
# Updating git and maven in case of cache
ADD https://api.github.com/repos/fondemen/Monitoring/git/refs/heads/master ../version.json
RUN cd Monitoring;git reset --hard;git pull && mvn package

FROM openjdk:8-jre-alpine                                                                                                              
#ADD monitoring-0.0.1-SNAPSHOT.jar /var/local                                                                                                   
COPY --from=build Monitoring/target/monitoring-0.0.1-SNAPSHOT.jar /var/local/monitoring-0.0.1-SNAPSHOT.jar
#ADD config.txt /etc/                                                                                                                          
VOLUME /etc/config.txt                                                                                                                         
CMD ["java","-jar","/var/local/monitoring-0.0.1-SNAPSHOT.jar","/etc/config.txt"]
