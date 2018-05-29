FROM jenkins/jenkins:alpine

ARG DOCKER_GID

USER root

#Add SO needed packages
RUN apk update && apk add docker python2 py2-pip

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