
#docker-build-test-push.groovy

Webhook
	https://user:pass@jenkins.host/generic-webhook-trigger/invoke

Jenkins job definition

	Buil Triggers
		Generic Webhook Trigger
			Post content parameters
				Variable: GIT_REPOSITORY
				Expression: $.repository.full_name
				Type: JSONPath

				Variable: GIT_REF
				Expression: $.ref
				Type: JSONPath

				Variable: GIT_PUSH
				Expression: $.push.changes
				Type: JSONPath


	Pipeline
		Pipeline Script from SCM
			docker-build-test-push.groovy

The jenkins pipeline needs the application repository to be structured like:
	rootdir
		|
		|-- code ( application code to be placed in apache Documentroot )
		|-- tests ( various tests: goss, acceptance tests, ... )
		|		|-- goss
		|			|-- goss.yaml
		|-- Dockerfile ( definition of container: code must be added from code directory )
