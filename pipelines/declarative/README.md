
Webhook
	https://user:pass@jenkins.host/generic-webhook-trigger/invoke

Jenkins job definition

	Buil Triggers
		Generic Webhook Trigger
			Post content parameters
				Variable: GIT_REPOSITORY
				Expression: $.repository.name
				Type: JSONPath

				Variable: GIT_USERNAME
				Expression: $.repository.owner.username
				Type: JSONPath

				Variable: GIT_BRANCH
				Expression: $.push.changes
				Type: JSONPath

	Pipeline
		Pipeline Script from SCM
			docker-build-test-push.groovy

