@Library('standardLibraries') _

node {

    def remote = [:]
    def hosts = [
        origin: '',
        destination: ''
    ]
    
    withCredentials([sshUserPrivateKey(credentialsId: 'ssh-privkey-sdops', keyFileVariable: 'privkey', usernameVariable: 'userName')]) {
    
		stage('Notify'){
            checkout scm
            notify([ type: "slack-default-start" ])
        }

		stage('Configure credentials object'){
			remote.user = userName
			remote.identityFile = privkey
			remote.name = hosts.destination
			remote.host = hosts.destination
			remote.allowAnyHosts = true
		}

		try{
			stage('Write config file'){
				writeFile file: 'rsnapshot.cfg', text: """
config_version	1.2
snapshot_root	/var/dockershared/storage/ds1_oplog/rsnapshot/
cmd_cp			/usr/bin/cp
cmd_rm			/usr/bin/rm
cmd_rsync		/usr/bin/rsync
cmd_ssh			/usr/bin/ssh
cmd_logger		/usr/bin/logger
cmd_du			/usr/bin/du
retain			daily	7
retain			weekly	4
retain			monthly	6
verbose			3
loglevel		3
logfile			/var/log/rsnapshot
lockfile		/var/run/rsnapshot.pid
rsync_long_args	-avz
ssh_args		-p22 -i /tmp/identity.pem
backup			${userName}@${hosts.origin}:/var/dockershared/registry/	registry.smartdigits.io/
				"""
			}

			stage('Print config file'){
				sh script: "cat rsnapshot.cfg"
			}

			stage('Upload files'){
				sshPut remote: remote, from: privkey, into: '/tmp/identity.pem', override: true
				sshCommand remote: remote, command: 'chmod 0600 /tmp/identity.pem'
				sshPut remote: remote, from: 'rsnapshot.cfg', into: '/tmp/rsnapshot.conf', override: true
			}

		}
		catch(all){
			stage('Remove files after failure'){
				echo 'Execution error: ' + all.toString()
				sshRemove remote: remote, path: '/tmp/identity.pem'
				sshRemove remote: remote, path: '/tmp/rsnapshot.conf'
				currentBuild.result = 'FAILURE'
			}
			stage('Notify end'){
            	notify([ type: "slack-default-end", message: "Backu finished with errors" ])
        	}
		}

		stage('Change permissions'){
			remote.name = hosts.origin
			remote.host = hosts.origin
			sshCommand remote: remote, command: 'sudo chown -R cloud-user. /var/dockershared/registry'
		}	

		stage('Execute backup'){
			remote.name = hosts.destination
			remote.host = hosts.destination
			sshCommand remote: remote, command: '''
				sudo /usr/bin/rsnapshot -c /tmp/rsnapshot.conf daily
				sudo /usr/bin/rsnapshot -c /tmp/rsnapshot.conf weekly
				sudo /usr/bin/rsnapshot -c /tmp/rsnapshot.conf monthly
			'''
		}

		stage('Remove files'){
			sshRemove remote: remote, path: '/tmp/identity.pem'
			sshRemove remote: remote, path: '/tmp/rsnapshot.conf'
		}

		stage('Notify end'){
            notify([ type: "slack-default-end", message: "Backup finished successfully" ])
        }
    }
}