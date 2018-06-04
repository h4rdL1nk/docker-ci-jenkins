@Library('standardLibraries') _

def imgTag
def imgAwsTag
def codeCo
def awsEnv
def gitPushBranch
def gitRepoUrl
def isBitbucket = true
def isGithub = true
def awsRegion = 'eu-west-1'
def awsAppEnv = ''
def awsEcrImg = ''

pipeline {
    agent {
        label 'workeraws'
    }
    parameters {
        string(name: 'DEPARTMENT', defaultValue: 'dummy')
        string(name: 'APP_NAME', defaultValue: 'dummy')
        booleanParam(name: 'DEPLOY', defaultValue: false)
    }
    options {
        timestamps()
        disableConcurrentBuilds()
    }
    stages {
        stage('Checkout code'){
            steps{
                script{

                    try{ 
                        gitPushBranch = GIT_PUSH_0_new_name 
                    } catch(ex) { isBitbucket = false }
                    try{
                        def refValues = GIT_REF.split('/')
                        gitPushBranch = refValues[2]  
                    } catch(ex) { isGithub = false }

                    if ( isGithub ){
                        gitRepoUrl = "https://github.com/${GIT_REPOSITORY}"
                    }
                    if ( isBitbucket ){
                        gitRepoUrl = "https://bitbucket.org/${GIT_REPOSITORY}"
                    }

                    codeCo = checkout scm:[
                                $class: 'GitSCM',
                                poll: true,
                                branches: [[
                                    name: "*/${gitPushBranch}"
                                ]],
                                userRemoteConfigs: [[
                                    credentialsId: 'bitbucket-ro',
                                    url: "${gitRepoUrl}"
                                ]]
                            ]
                }
            }
        }
        stage('Docker image build') {
            steps {
                sh 'docker build -t jenkins-${JOB_NAME}-${BUILD_NUMBER}-img .'
            }
        }
        stage('Docker image tests') {
            when{
                expression{
                    //Reversed output code, because for Jenkins the value 1 is true
                    def gossCheck = sh script: "[ ! -e tests/goss/goss.yaml ]", returnStatus: true
                    return gossCheck
                }
            }
            steps{
                sh 'cd tests/goss && dgoss run jenkins-${JOB_NAME}-${BUILD_NUMBER}-img'
            }
        }
        stage('Application acceptance tests') {
            when{
                expression{
                    def codeceptionCheck = sh script: "[ ! -d tests/codeception ]", returnStatus: true
                    return codeceptionCheck
                }
            }
            steps{
                sh script: """
                    #!/bin/bash +x
                    docker run -d -e ENV=dev --name jenkins-${JOB_NAME}-${BUILD_NUMBER}-run jenkins-${JOB_NAME}-${BUILD_NUMBER}-img
                    docker exec -i jenkins-${JOB_NAME}-${BUILD_NUMBER}-run composer require 'codeception/codeception:*'
                    docker exec -i jenkins-${JOB_NAME}-${BUILD_NUMBER}-run php vendor/bin/codecept run -c tests/codeception --no-colors --json
                    """
            }
        }
        stage('Push image to AWS') {
            steps{
                script{
                    awsEcrImg = dockerPushImageAws([
                        awsRegion: "${awsRegion}",
                        awsCredId: "aws-${DEPARTMENT}-admin",
                        localImageTag: "jenkins-${JOB_NAME}-${BUILD_NUMBER}-img",
                        pushImageTag: "${APP_NAME}:${codeCo.GIT_COMMIT}"
                    ])  
                }
            }
        }
        stage('Deploy application to AWS'){
            steps{
                script{
                    switch(gitPushBranch){
                        case 'master':
                            awsAppEnv = 'pro'
                            break
                        case ['testing','pre']:
                            awsAppEnv = 'pre'
                            break    
                        default: 
                            awsAppEnv = 'pre'
                            break     
                    }

                    if( awsAppEnv.indexOf('pro') >= 0 ){
                        confirmValue = input message: "Confirm deployment for ${awsAppEnv.toUpperCase()} environment", parameters: [string(defaultValue: '', description: '', name: 'WO identifier')]
                    }  

                    echo "Deploying image: ${awsEcrImg}"

                    awsEcsDeployApp([
                        awsRegion: "${awsRegion}",
                        awsCredId: "aws-${DEPARTMENT}-admin",
                        ecsClusterRegex: "^.*/CL.*-${awsAppEnv}\$",
                        ecsServiceRegex: "^.*/SVC-${APP_NAME}",
                        awsEcrImg: "${awsEcrImg}",
                        awsAppEnv: "${awsAppEnv}",
                        awsAppName: "${APP_NAME}",
                        deployTimeout: "120"
                    ])   
                }    
            }
        }
    }
    post {
        always {
            emailext(
                from: "jenkins-ci@app.madisonmk.com",
                to: "luismiguel.saez@madisonmk.com",
                mimeType: 'text/html',
                subject: "[${currentBuild.currentResult}] ${BUILD_DISPLAY_NAME} ${JOB_NAME}",
                body: "<br>Finalizado ${JOB_NAME} ${BUILD_NUMBER}<br>Nodo:${NODE_NAME}",
                attachLog: true
            )

            deleteDir()

            sh script: """
                if `docker inspect jenkins-${JOB_NAME}-${BUILD_NUMBER}-run >/dev/null 2>&1`
                then
                    docker rm --force jenkins-${JOB_NAME}-${BUILD_NUMBER}-run
                fi
                if `docker inspect jenkins-${JOB_NAME}-${BUILD_NUMBER}-img >/dev/null 2>&1`
                then
                    docker rmi jenkins-${JOB_NAME}-${BUILD_NUMBER}-img
                fi
                if `docker inspect ${imgAwsTag} >/dev/null 2>&1`
                then
                    docker rmi ${imgAwsTag}
                fi
                """, returnStdout: false
        }
    }
}
