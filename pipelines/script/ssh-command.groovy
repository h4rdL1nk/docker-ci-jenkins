@Library('standardLibraries') _

node {

    def remote = [:]
    def hosts = [
        origin: 'jenkins.smartdigits.io',
        destination: 'opsmgr.smartdigits.io'
    ]
    
    withCredentials([sshUserPrivateKey(credentialsId: 'ssh-privkey-sdops', keyFileVariable: 'privkey', usernameVariable: 'userName')]) {
    
        stage('Notify'){
            checkout scm
            notify([ type: "slack-default-start" ])
        }

        stage('Configure credentials object'){
            remote.user = userName
            remote.identityFile = privkey
    	    remote.name = hosts.origin
            remote.host = hosts.origin
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
            remote.name = hosts.destination
            remote.host = hosts.destination 
            sshPut remote: remote, from: 'jenkins.tar.gz', into: '/tmp/jenkins.tar.gz'
            sshCommand remote: remote, command: '''
                #!/bin/bash
                
                BKDIR="/var/dockershared/storage/ds1_oplog/jenkins/"
                BKFILE_NUM=5
                
                sudo mv /tmp/jenkins.tar.gz \${BKDIR}jenkins-\$(date "+%Y%m%d").tar.gz
                sudo ls -rt \$BKDIR | head -\$(( \$(sudo ls \$BKDIR | wc -w) - \$BKFILE_NUM )) | while read F;do echo "Removing \$BKDIR/\$F ...";sudo rm \$BKDIR/\$F;done
            '''
        }

        stage('Notify end')
            notify([ type: "slack-default-end", message: "Backu finished successfully" ])
        }

    }
}