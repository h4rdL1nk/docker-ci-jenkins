@Library('standardLibraries') _

def remote = [:]

pipeline {

    agent any
    
    stages{

        stage('Send notification'){
            steps{
                script{
                    notify([ type: "slack-default-start" ])
                }
            }
        }

        stage('Backup process'){
                
            steps{

                withCredentials([sshUserPrivateKey(credentialsId: 'ssh-privkey-sdops', keyFileVariable: 'privkey', usernameVariable: 'userName')]) {

                    script{
                        
                        script{
                            remote = [
                                user: userName,
                                identityFile: privKey,
                                name: 'jenkins.smartdigits.io',
                                host: 'jenkins.smartdigits.io',
                                allowAnyHosts: true
                            ]
                        }

                        sshCommand remote: remote, command: '''
                            sudo tar -c --exclude="workspace/*" /var/lib/jenkins/ | gzip -9 >/tmp/jenkins.tar.gz
                        '''

                        sshGet remote: remote, from: '/tmp/jenkins.tar.gz', into: 'jenkins.tar.gz', override: true
                        
                        sshRemove remote: remote, path: '/tmp/jenkins.tar.gz'                            
                    
                        sshPut remote: remote, from: 'jenkins.tar.gz', into: '/tmp/jenkins.tar.gz'
                    
                        sshCommand remote: remote, command: '''
                            #!/bin/bash
                        
                            BKDIR="/var/dockershared/storage/ds1_oplog/jenkins/"
                            BKFILE_NUM=5
                        
                            sudo mv /tmp/jenkins.tar.gz \${BKDIR}jenkins-\$(date "+%Y%m%d").tar.gz
                            sudo ls -rt \$BKDIR | head -\$(( \$(sudo ls \$BKDIR | wc -w) - \$BKFILE_NUM )) | while read F;do echo "Removing \$BKDIR/\$F ...";sudo rm \$BKDIR/\$F;done
                        '''
                    }

                }

            }

        }

    }

    post{
        always{
            deleteDir()
            script{
                notify([ type: "slack-default-end" ])
            }
        }
    }

}
