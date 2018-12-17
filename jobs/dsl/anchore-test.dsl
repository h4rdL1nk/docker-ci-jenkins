pipelineJob("anchore-test") {
	description()
	keepDependencies(false)
	definition {
		cpsScm {
			scm {
				git {
					remote {
						github("h4rdL1nk/docker-ci-jenkins", "https")
					}
					branch("*/master")
				}
			}
			scriptPath("pipelines/declarative/anchore-image-scan.groovy")
		}
	}
	disabled(false)
}