@Library('standardLibraries') _

def scmVars
def builtImage
def imageTag
def jenkinsVersion

pipeline {
    agent any
    parameters{
        string(name: 'VERSION', defaultValue: 'latest', description: 'Version to build')
    }
    environment{
        IMAGE_NAME = 'smartdigits/jenkins-sdi'
    }
    options {
        timestamps()
        disableConcurrentBuilds()
    }
    stages {
        stage('Set vars'){
            steps {
                script {
                    if(params.VERSION){
                        imageTag = "${params.VERSION}"
                    }
                    else{
                        imageTag = "latest"
                    }
                }
            }
        }
        stage('Code checkout'){
            steps {
                script {
                    scmVars = checkout scm
                    notify([ type: "slack-default-start" ])
                }
            }
        }
        stage('Build image'){
            steps{
                script{

                    def dockerfile = "Dockerfile.alpine-${imageTag}"

                    builtImage = docker.build("${env.DOCKERHUB_URL}/${env.IMAGE_NAME}:${imageTag}", "--pull --force-rm -f ${dockerfile} .")

                    jenkinsVersion = builtImage.inside{
                        sh(
                            returnStdout: true,
                            script: "java -jar /usr/share/jenkins/jenkins.war --version"
                        ).trim()
                    }

                }
            }
        }

        stage('Run dgoss container tests'){
            steps{
                withEnv(["GOSS_FILES_PATH=${WORKSPACE}/tests/goss/","GOSS_PATH=/usr/local/bin/goss","GOSS_FILES_STRATEGY=cp"]){
                    sh script: """
                        #!/bin/bash
                        dgoss run ${env.DOCKERHUB_URL}/${env.IMAGE_NAME}:${imageTag}
                        #dgoss run -v ${GOSS_PATH}:${GOSS_PATH} ${env.DOCKERHUB_URL}/${env.IMAGE_NAME}:${imageTag}
                    """
                }
            }
        }

        stage('Upload image'){
            steps{
                script{

                    docker.withRegistry("https://${env.DOCKERHUB_URL}", 'dockerhub-admin') {
                        builtImage.push()
                        builtImage.push("${jenkinsVersion}")
                    }

                }
            }
        }
    }
    post{
        always{
            script {
                notify([ type: "slack-default-end", message: "Jenkins version built: ${jenkinsVersion}" ])
            }
            //deleteDir()
            //sh script: "docker rmi ${builtImage.id}"
        }
    }
}
