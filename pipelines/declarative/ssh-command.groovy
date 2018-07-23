@Library('standardLibraries') _

node {

    def remote = [:]
    
    withCredentials([sshUserPrivateKey(credentialsId: 'ssh-privkey-sdops', keyFileVariable: 'privkey', usernameVariable: 'userName')]) {
    
        stage('Init GIT'){
            sh script: 'git init'
            notify([ type: "slack-default-start" ])
        }

        stage('Configure credentials object'){
            remote.user = userName
            remote.identityFile = privkey
    	    remote.name = 'jenkins.smartdigits.io'
            remote.host = 'jenkins.smartdigits.io'
            remote.allowAnyHosts = true
        }

        stage('Generate backup file over SSH'){
            sshCommand remote: remote, command: '''
    		    sudo tar -c --exclude="workspace/*" /var/lib/jenkins/ | gzip -9 >/tmp/jenkins.tar.gz
    	    '''
        }

        stage('Get backup file over SCP'){
            sshGet remote: remote, from: '/tmp/jenkins.tar.gz', into: 'jenkins.tar.gz', override: true
            sshRemove remote: remote, path: '/tmp/jenkins.tar.gz'
        }

        stage('Upload backup file over SCP'){
            remote.name = 'opsmgr.smartdigits.io'
            remote.host = 'opsmgr.smartdigits.io' 
            sshPut remote: remote, from: 'jenkins.tar.gz', into: '/tmp/jenkins.tar.gz'
            sshCommand remote: remote, command: '''
                #!/bin/bash
                
                BKDIR="/var/dockershared/storage/ds1_oplog/jenkins/"
                BKFILE_NUM=5
                
                sudo mv /tmp/jenkins.tar.gz \${BKDIR}jenkins-\$(date "+%Y%m%d").tar.gz
                sudo ls -rt \$BKDIR | head -\$(( \$(sudo ls \$BKDIR | wc -w) - \$BKFILE_NUM )) | while read F;do echo "Removing \$BKDIR/\$F ...";sudo rm \$BKDIR/\$F;done
            '''
        }

        post{
            notify([ type: "slack-default-end" ])
        }

    }

}