
node {
  stage('Remote SSH') {
    withCredentials([sshUserPrivateKey(credentialsId: 'ssh-privkey-sdops', keyFileVariable: 'privkey', usernameVariable: 'userName')]) {
	def remote = [:]
        remote.user = userName
        remote.identityFile = privkey
	remote.name = '192.168.247.101'
        remote.host = '192.168.247.101'
        remote.allowAnyHosts = true
        sshCommand remote: remote, command: 'uptime'
    }
  }
}

