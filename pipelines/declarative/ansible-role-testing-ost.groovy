pipeline {
    agent any
    options {
        timestamps()
        disableConcurrentBuilds()
    }
    stages {
        stage('Checkout code'){
            steps{
                script{
                    codeCo = checkout scm:[
                                $class: 'GitSCM',
                                poll: true,
                                branches: [[
                                    name: "*/*"
                                ]],
                                userRemoteConfigs: [[
                                    credentialsId: 'visitor',
                                    url: "https://pdihub.hi.inet/smartdigitsre/ansible-role-smd-bootstrap"
                                ]]
                            ]
                }
            }
        }
        stage('Create and test molecule environment'){
            steps{
                withCredentials([usernamePassword(credentialsId: 'ost-epg-admin', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
                    sh script: "make create"
                    sh script: "make dependency"
                    sh script: "make converge"
                    sh script: "make idempotence"
                }
            }
        }
    }
    post{
        success{
            withCredentials([usernamePassword(credentialsId: 'ost-epg-admin', usernameVariable: 'OS_USERNAME', passwordVariable: 'OS_PASSWORD')]) {
                    sh script: "make destroy"
            }
            deleteDir()
        }
    }
}