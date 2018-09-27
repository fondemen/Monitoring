[![Build Status](https://travis-ci.org/fondemen/Monitoring.svg?branch=master)](https://travis-ci.org/fondemen/Monitoring)


[![codecov](https://codecov.io/gh/fondemen/Monitoring/branch/master/graph/badge.svg)](https://codecov.io/gh/fondemen/Monitoring)

# Monitoring
Monitoring of the ENSISA mechanics workshop developed with Java, ElasticSearch and Grafana.

This is a maintenance release of [Ahp06/Monitoring](https://github.com/Ahp06/Monitoring) developed by MM. Huynh-Phuc, Devie and Bendahi.

# Manual installation 

1 - To install ElasticSearch from the following link : https://www.elastic.co/fr/downloads/elasticsearch .
    For more information for how to install it : https://www.elastic.co/guide/en/elasticsearch/reference/current/_installation.html
    
2 - To install Grafana from the following link : https://grafana.com/grafana/download . 

# Docker compose

Instead of installing 	all by yourself, just use this compose file (replacing capital names):

```
version: '3.1'
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:6.3.1
    container_name: elasticsearch
    environment:
      - cluster.name=CLUSTER_NAME
      - bootstrap.memory_lock=true
      - "ES_JAVA_OPTS=-Xms1g -Xmx1g"
    restart: always
    ulimits:                                                                                                                                   
      memlock:
        soft: -1
        hard: -1
    volumes:
      - /DATA/ELASTICSEARCH:/usr/share/elasticsearch/data
    healthcheck:
        test: ["CMD-SHELL", "curl 'http://localhost:9200/_cat/health?h=status' 2>&1 | egrep -q 'yellow|green'"]
        interval: 30s
        timeout: 10s
        retries: 5
    networks:
      - monit

  grafana:
    image: grafana/grafana
    environment:
      - "GF_DOMAIN=MY.DOMAIN.COM"
      - "GF_SERVER_ROOT_URL=%(protocol)s://%(domain)s/SUBPATH/"
      - "GF_INSTALL_PLUGINS=natel-discrete-panel,grafana-piechart-panel"
      - "GF_SMTP_ENABLED=true"
      - "GF_SMTP_HOST=SMTP.DOMAIN.COM:25"
      - "GF_SMTP_FROM_ADDRESS=MONITORING@DOMAIN.COM"
    restart: always
    volumes:
      - /DATA/GRAFANA:/var/lib/grafana
    ports:                                                                                                                                     
      - "3000:3000"
    networks:
      - monit

  monitoring:
    build:
      context: .
      dockerfile: Dockerfile
    volumes:
      - /ETC/MONITORING/config.txt:/etc/config.txt
    restart: always
    depends_on:
      - elasticsearch
    networks:
      - monit
networks:
  monit:
    driver: bridge
```

# How to use : 

In the main class Monitoring.java, you can load a configuration file (.txt) compatible with JSON format or
you can create your configuration : 

```
//By file
MonitoringConfiguration config = new MonitoringConfiguration("...\\config.txt");

//With default constructor 
MonitoringConfiguration config = new MonitoringConfiguration(yourClusterNameES, yourHostES, yourPortES, yourHostSQL,...); 
```

By default ElasticSearch use 9200 and 9300 ports and the cluster name is "elasticsearch". 

After launching Grafana and ElasticSearch, you must configure a new datasource, see this link : http://docs.grafana.org/features/datasources/elasticsearch/ , and you can start Monitoring.java. 

This project was done to retrieve machine state changes, you have to adapt it to do another monitoring.
