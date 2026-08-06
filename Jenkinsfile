#!/usr/bin/env groovy

pipeline {
    agent {
        label 'maven-3-9-5-eclipse-temurin-21'
    }
    environment {
        TAG = "${env.BRANCH_NAME.replace('/','-')}"
        MAVEN_OPTS="-Xmx1g"
        MINIO_HOST = "http://192.168.250.150:9000"
        MINIO_CREDENTIALS = "jenkins-ci-minio"
        REGISTRY = "registry.integratedmodelling.org"
        REGISTRY_CREDENTIALS = "registry-jenkins-credentials"

        VERSION_DATE = sh(
                    script: "date '+%Y-%m-%dT%H:%M:%S'",
                    returnStdout: true).trim()
        RESOURCES_CONTAINER = "resources-service-21"
        RESOURCE_SERVICE = "resources-main"
        RUNTIME_CONTAINER = "runtime-service-21"
        RUNTIME_SERVICE = "runtime-main"
        RESOLVER_CONTAINER = "resolver-service-21"
        RESOLVER_SERVICE = "resolver-main"
        REASONER_CONTAINER = "reasoner-service-21"
        REASONER_SERVICE = "reasoner-main"
        BASE_CONTAINER = "klab-base-21:dd2b778c852f20ad9c82fe6e12d5723e23e3dd19"
        DOCKER_HOST = "192.168.250.215"
        DOCKER_STACK = "klab"
    }
    stages {
        stage('Maven Build') {
            steps {
                script {
                    currentBuild.description = "${env.BRANCH_NAME} build with container tag: ${env.TAG}"
                }
                sh './mvnw clean source:jar package -DskipTests'
            }
        }
        stage('Maven Install') {
            steps {
               script {
                   jibBuild = 'jib:build -Djib.httpTimeout=180000'
                   dockerBuild = sh(script: "git log -1 --pretty=%B | grep -qi '\\[docker build\\]'", returnStatus: true)
                   env.JIB = (env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'develop' || dockerBuild == 0) ? jibBuild : ''
               }
               echo "${env.BRANCH_NAME} build with container tag: ${env.TAG}"
               withCredentials([usernamePassword(credentialsId: "${env.REGISTRY_CREDENTIALS}", passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                   sh "./mvnw clean source:jar install -DskipTests -U ${env.JIB}"
               }
            }
        }
        stage('Maven Deploy') {
            when {
                anyOf { branch 'develop'; branch 'master' }
            }
            steps {
                configFileProvider([configFile(fileId: '1f5f24a2-9839-4194-b2ad-0613279f9fba', variable: 'MAVEN_SETTINGS_XML')]) {
                    sh './mvnw --settings $MAVEN_SETTINGS_XML deploy -DskipTests'
                }
            }
        }
        stage('Deploy artifacts') {
            when {
                anyOf { branch 'develop'; branch 'master' }
            }
            steps {
                withCredentials([sshUserPrivateKey(credentialsId: 'jenkins-im-communication', keyFileVariable: 'identity')]) {
                    sh './mvnw --projects klab.core.api javadoc:javadoc'
                    sh 'rsync --archive --progress --delete --rsh="ssh -i ${identity} -o StrictHostKeyChecking=no" klab.core.api/target/reports/apidocs/ ubuntu@192.168.250.200:repos/documents.production.compose/javadocs/'
                }
                withCredentials([usernamePassword(credentialsId: "${env.MINIO_CREDENTIALS}", passwordVariable: 'SECRETKEY', usernameVariable: 'ACCESSKEY')]) {
                    sh 'mc alias set minio $MINIO_HOST $ACCESSKEY $SECRETKEY'
                    sh """
                       mc rm --recursive --force minio/klab/products/klab/ || echo "klab/products/klab/ does not exists"
                       mc cp --recursive ./klab.distribution/target/distribution/ minio/klab/products/klab
                       """
                }
            }
        }
        stage('Update services') {
            when {
                expression { env.JIB != '' }
            }
            steps {
                script {
                    // Each service image to pull and update
                    def services = [
                        [name: "${REASONER_SERVICE}", container: "${REASONER_CONTAINER}"],
                        [name: "${RESOLVER_SERVICE}", container: "${RESOLVER_CONTAINER}"],
                        [name: "${RUNTIME_SERVICE}",  container: "${RUNTIME_CONTAINER}"],
                        [name: "${RESOURCE_SERVICE}", container: "${RESOURCES_CONTAINER}"]
                    ]
                    sshagent(["bc3-im-services"]) {
                        services.each { svc ->
                            sh """
                            ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -l bc3 ${DOCKER_HOST} '
                                echo "Updating ${svc.name} service..."
                                docker pull ${REGISTRY}/${svc.container}:${TAG}
                            '
                            """
                        }

                        // Once all images are pulled, recreate containers via docker compose
                        sh """
                        ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -l bc3 ${DOCKER_HOST} '
                            cd ~/repos/klab-services-infrastructure/docker || exit 1
                            echo "Bringing up updated containers..."
                            docker compose up -d
                        '
                        """
                    }
                }
            }
        }
    }
}
