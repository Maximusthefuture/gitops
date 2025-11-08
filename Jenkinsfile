pipeline {
    agent any

    environment {
        // Registry configuration - Update this to your registry
        REGISTRY_URL = '10.108.98.54:80'  // Local minikube registry
        // REGISTRY_URL = 'harbor.local'   // Or Harbor
        // REGISTRY_URL = 'docker.io/yourusername'  // Or Docker Hub

        IMAGE_NAME = 'java-app'

        // Extract feature name from branch
        FEATURE_NAME = "${env.GIT_BRANCH.replace('origin/', '').replace('feature/', '')}"
        IMAGE_TAG = "${FEATURE_NAME}-${env.BUILD_NUMBER}"

        // GitOps repository
        GITOPS_REPO = 'https://github.com/Maximusthefuture/gitops.git'
        GITOPS_BRANCH = "feature/${FEATURE_NAME}"

        // Credentials IDs (configure these in Jenkins)
        DOCKER_CREDS = credentials('docker-registry-creds')
        GITHUB_TOKEN = credentials('github-token')
    }

    stages {
        stage('Checkout') {
            steps {
                echo "================================================"
                echo "🚀 Building feature: ${FEATURE_NAME}"
                echo "📦 Image tag: ${IMAGE_TAG}"
                echo "🌿 Branch: ${env.GIT_BRANCH}"
                echo "================================================"
                checkout scm
            }
        }

        stage('Build Application') {
            steps {
                echo "📦 Building Java application with Maven..."
                dir('java-repo') {
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('Run Tests') {
            steps {
                echo "🧪 Running unit tests..."
                dir('java-repo') {
                    sh 'mvn test'
                }
            }
            post {
                always {
                    junit 'java-repo/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "🐳 Building Docker image..."
                dir('java-repo') {
                    script {
                        // Build image with platform specification for compatibility
                        sh """
                            docker build \
                              --platform linux/amd64 \
                              -t ${REGISTRY_URL}/${IMAGE_NAME}:${IMAGE_TAG} \
                              -t ${REGISTRY_URL}/${IMAGE_NAME}:${FEATURE_NAME} \
                              .
                        """
                    }
                }
            }
        }

        stage('Push to Registry') {
            steps {
                echo "📤 Pushing image to registry: ${REGISTRY_URL}"
                script {
                    docker.withRegistry("http://${REGISTRY_URL}", 'docker-registry-creds') {
                        // Push both tags: feature-name-buildnum and feature-name
                        sh """
                            docker push ${REGISTRY_URL}/${IMAGE_NAME}:${IMAGE_TAG}
                            docker push ${REGISTRY_URL}/${IMAGE_NAME}:${FEATURE_NAME}
                        """
                    }
                }
            }
        }

        stage('Update GitOps Repository') {
            steps {
                echo "📝 Updating GitOps repository..."
                script {
                    sh """
                        # Clean up any previous checkout
                        rm -rf gitops-temp

                        # Clone GitOps repo
                        git clone https://${GITHUB_TOKEN}@github.com/Maximusthefuture/gitops.git gitops-temp
                        cd gitops-temp

                        # Configure git
                        git config user.name "Jenkins CI"
                        git config user.email "jenkins@ci.local"

                        # Create or checkout feature branch
                        git checkout -b ${GITOPS_BRANCH} 2>/dev/null || git checkout ${GITOPS_BRANCH}

                        # Pull latest changes if branch exists remotely
                        git pull origin ${GITOPS_BRANCH} || true

                        # Create overlay directory
                        mkdir -p envs/feature-${FEATURE_NAME}

                        # Create kustomization.yaml
                        cat > envs/feature-${FEATURE_NAME}/kustomization.yaml <<'EOF'
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: preview-feature-${FEATURE_NAME}

resources:
- ../../base

images:
- name: ${REGISTRY_URL}/java-app
  newTag: ${IMAGE_TAG}

patches:
- patch: |-
    - op: replace
      path: /spec/rules/0/host
      value: feature-${FEATURE_NAME}.local
  target:
    kind: Ingress
    name: auth-service

commonLabels:
  environment: preview
  branch: feature-${FEATURE_NAME}
  managed-by: argocd
  jenkins-build: "${BUILD_NUMBER}"

commonAnnotations:
  git-branch: "feature/${FEATURE_NAME}"
  description: "Preview environment for ${FEATURE_NAME}"
  jenkins-build-url: "${BUILD_URL}"
  image-tag: "${IMAGE_TAG}"
  deployed-at: "\$(date -u +%Y-%m-%dT%H:%M:%SZ)"
EOF

                        # Replace variables in kustomization.yaml
                        sed -i "s/\\\${FEATURE_NAME}/${FEATURE_NAME}/g" envs/feature-${FEATURE_NAME}/kustomization.yaml
                        sed -i "s|\\\${REGISTRY_URL}|${REGISTRY_URL}|g" envs/feature-${FEATURE_NAME}/kustomization.yaml
                        sed -i "s/\\\${IMAGE_TAG}/${IMAGE_TAG}/g" envs/feature-${FEATURE_NAME}/kustomization.yaml
                        sed -i "s/\\\${BUILD_NUMBER}/${BUILD_NUMBER}/g" envs/feature-${FEATURE_NAME}/kustomization.yaml
                        sed -i "s|\\\${BUILD_URL}|${BUILD_URL}|g" envs/feature-${FEATURE_NAME}/kustomization.yaml

                        # Commit and push
                        git add envs/feature-${FEATURE_NAME}/

                        # Only commit if there are changes
                        if ! git diff --staged --quiet; then
                            git commit -m "Update ${FEATURE_NAME} to build ${BUILD_NUMBER}

Image: ${REGISTRY_URL}/${IMAGE_NAME}:${IMAGE_TAG}
Build URL: ${BUILD_URL}

[skip ci]"
                            git push origin ${GITOPS_BRANCH}
                            echo "✅ GitOps repository updated successfully"
                        else
                            echo "ℹ️  No changes to commit"
                        fi

                        # Cleanup
                        cd ..
                        rm -rf gitops-temp
                    """
                }
            }
        }

        stage('Notify ArgoCD') {
            steps {
                script {
                    echo """
================================================
✅ Build Complete!
================================================

📦 Image: ${REGISTRY_URL}/${IMAGE_NAME}:${IMAGE_TAG}
🌿 GitOps Branch: ${GITOPS_BRANCH}
📁 Overlay Path: envs/feature-${FEATURE_NAME}/
🚀 Namespace: preview-feature-${FEATURE_NAME}
🔗 Build URL: ${BUILD_URL}

ArgoCD will automatically discover and deploy within 3 minutes.

📊 Monitor deployment:
  kubectl get applications -n argocd | grep ${FEATURE_NAME}
  kubectl get pods -n preview-feature-${FEATURE_NAME}

🌐 Access application (once deployed):
  kubectl port-forward -n preview-feature-${FEATURE_NAME} svc/auth-service 8080:8080
  curl http://localhost:8080/

================================================
                    """
                }
            }
        }
    }

    post {
        success {
            echo '✅ Pipeline completed successfully! 🎉'
            // Add notification here (Slack, email, etc.)
            // slackSend color: 'good', message: "Build Success: ${FEATURE_NAME}"
        }
        failure {
            echo '❌ Pipeline failed! Please check the logs.'
            // Add notification here
            // slackSend color: 'danger', message: "Build Failed: ${FEATURE_NAME}"
        }
        always {
            echo '🧹 Cleaning up workspace...'
            cleanWs()
        }
    }
}
