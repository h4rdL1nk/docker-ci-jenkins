#!/bin/bash

docker network create -d bridge -o "com.docker.network.driver.mtu=1400" smd_infra_ci

### JENKINS

docker run \
    --log-driver json-file \
    --log-opt max-size=50m \
    --log-opt max-file=1 \
    --name smd_infra_jenkins \
    --restart=unless-stopped \
    -m 1024m \
    -e JAVA_OPTS="-Djava.awt.headless=true -Xmx512m -Dcom.sun.jndi.ldap.connect.pool.protocol=DIGEST-MD5 -Dorg.apache.commons.jelly.tags.fmt.timeZone=Europe/Madrid" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v /var/lib/jenkins:/var/jenkins_home:rw \
    --network smd_infra_ci \
    -p 8443:8443 -p 8080:8080 -d jenkins:alpine

### ANCHORE

docker run \
    --log-driver json-file \
    --log-opt max-size=50m \
    --log-opt max-file=1 \
    --name smd_infra_ci_anchore_postgres \
    --restart=unless-stopped \
    -v ./db:/var/lib/postgresql/data/pgdata/:Z \
    -e POSTGRES_PASSWORD=anc0r3dbP4ss \
    -e PGDATA=/var/lib/postgresql/data/pgdata/ \
    --network smd_infra_ci \
    -d postgres:9

docker run \
    --log-driver json-file \
    --log-opt max-size=50m \
    --log-opt max-file=1 \
    --name smd_infra_ci_anchore_engine \
    --restart=unless-stopped \
    -e ANCHORE_ADMIN_PASS=4dm1n \
    -e ANCHORE_HOST_ID=smd_infra_ci_anchore_engine \
    -e ANCHORE_DB_HOST_ID=smd_infra_ci_anchore_postgres \
    -e ANCHORE_DB_USER= \
    -e ANCHORE_DB_PASS=anc0r3dbP4ss \
    -p 8228:8228 \
    -p 8338:8338 \
    -v ./config:/config/:Z \
    -v /var/run/docker.sock:/var/run/docker.sock \
    --network smd_infra_ci \
    -d anchore-engine:latest
