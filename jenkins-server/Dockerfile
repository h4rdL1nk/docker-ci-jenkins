FROM jenkins/jenkins:alpine

HEALTHCHECK --start-period=10s --interval=5s --retries=3 CMD curl -sk https://localhost:443 -o/dev/null \
    || curl -sk https://localhost:8443 -o/dev/null \
    || curl -s http://localhost:80 -o/dev/null \
    || curl -s http://localhost:8080 -o/dev/null \
    || exit 1

ARG DOCKER_GID=973
ARG JENKINS_HTTP_PORT=8080
ARG TZ=Europe/Madrid

ENV JENKINS_OPTS "--httpPort=${JENKINS_HTTP_PORT}"
ENV JAVA_OPTS "-Djava.awt.headless=true -Xmx1024m -Djenkins.install.runSetupWizard=false -Dcom.sun.jndi.ldap.connect.pool.protocol=DIGEST-MD5 -Dorg.apache.commons.jelly.tags.fmt.timeZone=${TZ}"

USER root

#Add SO needed packages
RUN apk update --no-cache
RUN apk add --no-cache docker go python2 py2-pip musl-dev python2-dev libffi-dev openssl-dev make

#Install required python packages
ADD ./requirements.txt /requirements.txt
RUN pip install --upgrade -r /requirements.txt

#Change docker GID to match /var/run/docker.sock permissions
RUN sed -i "s/^\(docker:x:\)[0-9]\{3,\}\(:.*\)/\1${DOCKER_GID}\2/g" /etc/group
RUN addgroup jenkins docker

USER jenkins

#Install required plugins
ADD ./plugins.txt /tmp/plugins.txt
RUN /usr/local/bin/install-plugins.sh < /tmp/plugins.txt