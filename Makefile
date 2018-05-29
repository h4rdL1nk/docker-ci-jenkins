
DOCKER_GID:=$(shell awk '/^docker/{match($$0,/[0-9]{3}/);print substr($$0,RSTART,RLENGTH)}' /etc/group)
JENKINS_DOCKER_IMG=jenkins-ci-dev
JENKINS_BUILD_DIR=jenkins
JENKINS_PORT=8888
APP_DOCKER_IMG=app
APP_BUILD_DIR=.
APP_DOCKERFILE=Dockerfile.build

app-build:
	docker build --rm --pull -t $(APP_DOCKER_IMG) -f $(APP_DOCKERFILE) $(APP_BUILD_DIR)

app-clean:
	-docker rm --force $(APP_DOCKER_IMG)-run

server-build:
	docker build --rm --pull --build-arg DOCKER_GID=$$(DOCKER_GID) -t $(JENKINS_DOCKER_IMG) $(JENKINS_BUILD_DIR)

server-run: clean
	docker run --name $(JENKINS_DOCKER_IMG)-run -d -p$(JENKINS_PORT):8080 -v /var/run/docker.sock:/var/run/docker.sock:ro $(DOCKER_IMG)
	@echo 'Run get-pass in a few seconds command to get the jenkins activation password'

server-clean:
	-docker rm --force $(JENKINS_DOCKER_IMG)-run

server-pass:
	docker logs $(JENKINS_DOCKER_IMG)-run 2>&1 | egrep "^[a-f0-9]{32}$$"
