pipeline {
    agent any
    stages {
        stage('Create anchore images file'){
            steps{
                sh script: """
                    echo 'alpine:3.8' >> anchore-images.txt
                """
            }
        }
        stage('Analize images'){
            steps{
                anchore engineCredentialsId: 'anchore-users-admin', name: 'anchore-images.txt'
            }
        }
    }
}