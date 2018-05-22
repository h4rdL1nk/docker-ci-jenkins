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
                        sh (
                            returnStdout: true,
                            script: "sed '/<version>/!d ; s/<version>\\(.*\\)<\\/version>/\\1/' \${JENKINS_HOME}/config.xml"
                        ).trim()
                    }

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
                notify([ type: "slack-default-end" ])
            }
            deleteDir()
        }
    }
}
