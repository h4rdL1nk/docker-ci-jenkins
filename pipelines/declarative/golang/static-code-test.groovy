pipeline {
    agent any 
    environment {
        CODE_PATH = 'src/application'
    }
    stages {
        stage('SCM') {
            steps {
                checkout([
                    $class: 'GitSCM', 
                    branches: [[name: '*/master']], 
                    doGenerateSubmoduleConfigurations: false, 
                    extensions: [[
                        $class: 'SubmoduleOption', 
                        disableSubmodules: false, 
                        parentCredentials: false, 
                        recursiveSubmodules: true, 
                        reference: '', 
                        trackingSubmodules: false
                    ]], 
                    submoduleCfg: [], 
                    userRemoteConfigs: [[
                        credentialsId: 'pdihub-ro', 
                        url: 'https://github.com/h4rdL1nk/testapp'
                    ]]
                ])
            }
        }
        stage('Static code test') { 
            steps {
                withEnv(["GOPATH=${JENKINS_HOME}/workspace/${JOB_NAME}","NO_PROXY=go.googlesource.com"]) {
                    sh script: '''
                        #!/bin/bash
                        set +x
                        
                        linters="--enable=vet --enable=errcheck --enable=gotype"
                        
                        go get -u github.com/alecthomas/gometalinter \\
                                  github.com/360EntSecGroup-Skylar/goreporter \\
                                  github.com/kisielk/errcheck \\
                                  golang.org/x/tools/cmd/gotype
                                  
                        ${GOPATH}/bin/gometalinter --disable-all \${linters} --aggregate --errors ${GOPATH}/${CODE_PATH}/...
                    '''
                }
            }
        }
    }
}
