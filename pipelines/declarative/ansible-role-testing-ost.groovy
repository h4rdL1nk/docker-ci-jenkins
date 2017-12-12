pipeline {
    agent any
    options {
        timestamps()
        disableConcurrentBuilds()
    }
    stages {
        stage('Create python virtualenv'){
            steps{
                ansiColor('xterm') {
                    echo "\u001B[32mBuilding virtualenv ...\u001B[0m"
                    sh script: """
                            virtualenv -p /usr/bin/python2.7 .venv
                            source .venv/bin/activate
                            pip install --upgrade -r requirements.txt
                    """
                }
            }
        }
        stage('Molecule role scenario setup'){
            steps{
                ansiColor('xterm') {
                    echo "\u001B[32mSetting up role scenario ...\u001B[0m"
                    withCredentials([usernamePassword(credentialsId: 'ost-epg-admin', passwordVariable: 'OS_PASSWORD', usernameVariable: 'OS_USERNAME')]) {
                        sh script: """
                            [ -e \$VIRTUAL_ENV ] && source .venv/bin/activate
                            molecule create -s openstack
                        """

                        sh script: """
                            [ -e \$VIRTUAL_ENV ] && source .venv/bin/activate
                            molecule dependency -s openstack
                        """
                    }
                }
            }
        }
        stage('Molecule role test'){
            steps{
                ansiColor('xterm') {
                    echo "\u001B[32mTesting ansible role ...\u001B[0m"
                    withCredentials([usernamePassword(credentialsId: 'ost-epg-admin', passwordVariable: 'OS_PASSWORD', usernameVariable: 'OS_USERNAME')]) {
                        sh script: """
                            [ -e \$VIRTUAL_ENV ] && source .venv/bin/activate
                            molecule converge -s openstack
                        """

                        sh script: """
                            [ -e \$VIRTUAL_ENV ] && source .venv/bin/activate
                            molecule idempotence -s openstack
                        """
                    }
                }
            }
        }
    }
    post{
        always{
            withCredentials([usernamePassword(credentialsId: 'ost-epg-admin', passwordVariable: 'OS_PASSWORD', usernameVariable: 'OS_USERNAME')]) {
                sh script: """
                            [ -e \$VIRTUAL_ENV ] && source .venv/bin/activate
                            molecule destroy -s openstack
                        """
            }
            deleteDir()
        }
    }
}
