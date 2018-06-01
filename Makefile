
DOCKER_GID:=$(shell awk '/^docker/{match($$0,/[0-9]{3}/);print substr($$0,RSTART,RLENGTH)}' /etc/group)
GOSS_BIN:=/usr/local/bin/goss
DGOSS_BIN:=/usr/local/bin/dgoss
JENKINS_DOCKER_IMG=jenkins-ci-dev
JENKINS_BUILD_DIR=jenkins-server
JENKINS_PORT=8888

.DEFAULT_GOAL:=help

server-build:
	docker build --rm --pull --build-arg DOCKER_GID=$(DOCKER_GID) -t $(JENKINS_DOCKER_IMG) $(JENKINS_BUILD_DIR)

server-test: 
	GOSS_PATH=$(GOSS_BIN) GOSS_FILES_PATH=$(JENKINS_BUILD_DIR)/test/goss dgoss run $(JENKINS_DOCKER_IMG)
	
server-run: server-clean
	docker run --name $(JENKINS_DOCKER_IMG)-run -d -p$(JENKINS_PORT):8080 -v /var/run/docker.sock:/var/run/docker.sock:ro $(JENKINS_DOCKER_IMG)
	@echo 'Run server-pass in a few seconds command to get the jenkins activation password'

server-clean:
	@-docker rm --force $(JENKINS_DOCKER_IMG)-run >/dev/null 2>&1

server-pass:
	@docker logs $(JENKINS_DOCKER_IMG)-run 2>&1 | egrep "^[a-f0-9]{32}$$"

dependencies:
	sudo curl -L https://raw.githubusercontent.com/aelsabbahy/goss/master/extras/dgoss/dgoss -o $(DGOSS_BIN)
	sudo chmod +rx /usr/local/bin/dgoss
	sudo curl -L https://github.com/aelsabbahy/goss/releases/download/v0.3.6/goss-linux-amd64 -o $(GOSS_BIN)

help:
	@echo 
	@echo '  Commands'
	@echo '  --------'
	@awk -F: '/^([a-z-]+):/{printf("  %s\n",$$1);}' Makefile
	@echo
