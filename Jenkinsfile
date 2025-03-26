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
        RUNTIME_CONTAINER = "runtime-service-21"
        RESOLVER_CONTAINER = "resolver-service-21"
        REASONER_CONTAINER = "reasoner-service-21"
        BASE_CONTAINER = "klab-base-21:dd2b778c852f20ad9c82fe6e12d5723e23e3dd19"
        VM_IP = "192.168.250.215"
        VM_DOCKER_PATH = "/repos/klab-services-infrastructure/docker"
    }
    stages {
        stage('Build') {
            steps {
                script {
                    currentBuild.description = "${env.BRANCH_NAME} build with container tag: ${env.TAG}"
                }
                sh './mvnw clean source:jar package'
            }
        }
        stage('Install with JIB') {
            steps {
                echo "${env.BRANCH_NAME} build with container tag: ${env.TAG}"
                withCredentials([usernamePassword(credentialsId: "${env.REGISTRY_CREDENTIALS}", passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                    sh './mvnw clean source:jar install -DskipTests -U jib:build -Djib.httpTimeout=180000'
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
                       mc rm --recursive --force minio/klab/p2/org.integratedmodelling.klab.api/ || echo "klab/p2/org.integratedmodelling.klab.api/ does not exists"
                       mc cp --recursive ./p2/org.integratedmodelling.klab.api/target/repository/ minio/klab/p2/org.integratedmodelling.klab.api/
                       mc rm --recursive --force minio/klab/p2/org.integratedmodelling.klab.modeler/ || echo "klab/p2/org.integratedmodelling.klab.modeler/ does not exists"
                       mc cp --recursive ./p2/org.integratedmodelling.klab.modeler/target/repository/ minio/klab/p2/org.integratedmodelling.klab.modeler/
                       mc rm --recursive --force minio/klab/products/klab/ || echo "klab/products/klab/ does not exists"
                       mc cp --recursive ./klab.distribution/target/distribution/ minio/klab/products/klab
                       """
                }
            }
        }
        stage('Update services') {
            steps {
                sshagent(["bc3-im-services"]) {
                     sh "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -l bc3 ${VM_IP} docker pull registry.integratedmodelling.org/resources-service-21:${TAG}"
                     sh "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -l bc3 ${VM_IP} ${VM_DOCKER_PATH}/docker compose up -d"
                }
            }
        }
    }
}
