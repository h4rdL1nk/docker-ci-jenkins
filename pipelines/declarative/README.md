


Buil Triggers
	Generic Webhook Trigger
		Post content parameters
			Variable: GIT_REPOSITORY
			Expression: $.repository.name
			Type: JSONPath

			Variable: GIT_USERNAME
			Expression: $.actor.username
			Type: JSONPath

			Variable: GIT_BRANCH
			Expression: $.push.changes.new.name
			Type: JSONPath

Pipeline
	Pipeline Script from SCM
		docker-build-test-push.groovy

