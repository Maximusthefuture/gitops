# Jenkins CI/CD Workflow with Docker Registry

Complete guide for setting up Jenkins-based CI/CD pipeline with Docker registry and ArgoCD GitOps workflow.

## Architecture Overview

```
Developer Push → Jenkins → Build & Test → Push to Registry → Update GitOps Repo → ArgoCD Deploy
```

### Workflow Details

```
┌─────────────┐
│  Developer  │
└──────┬──────┘
       │ git push feature/xyz
       ▼
┌─────────────────────┐
│   GitHub/GitLab     │
│  (Source Code)      │
└──────┬──────────────┘
       │ webhook trigger
       ▼
┌─────────────────────┐
│      Jenkins        │
│  ┌───────────────┐  │
│  │ 1. Checkout   │  │
│  │ 2. Build JAR  │  │
│  │ 3. Run Tests  │  │
│  │ 4. Build Image│  │
│  │ 5. Push Image │  │
│  │ 6. Update K8s │  │
│  └───────────────┘  │
└──────┬──────────────┘
       │ docker push
       ▼
┌─────────────────────┐
│  Docker Registry    │
│  (Harbor/DockerHub) │
│  10.108.98.54:80    │
└─────────────────────┘
       │ image available
       ▼
┌─────────────────────┐
│   GitOps Repo       │
│  (This repo)        │
│  envs/feature-xyz/  │
└──────┬──────────────┘
       │ monitors changes
       ▼
┌─────────────────────┐
│      ArgoCD         │
│  Auto-discovers     │
│  feature branches   │
└──────┬──────────────┘
       │ kubectl apply
       ▼
┌─────────────────────┐
│    Kubernetes       │
│  preview-feature-xyz│
└─────────────────────┘
```

## Prerequisites

- Jenkins server with Docker support
- Docker registry (local or cloud)
- Kubernetes cluster with ArgoCD
- GitHub/GitLab repository
- GitHub Personal Access Token

## Setup Steps

### 1. Docker Registry Setup

#### Option A: Local Registry (Minikube)

```bash
# Enable minikube registry addon
minikube addons enable registry

# Get registry IP
export REGISTRY_IP=$(kubectl get svc registry -n kube-system -o jsonpath='{.spec.clusterIP}')
echo "Registry IP: $REGISTRY_IP:80"

# Test registry
minikube ssh "docker pull hello-world && docker tag hello-world localhost:5000/hello-world && docker push localhost:5000/hello-world"
```

#### Option B: Harbor Registry

```bash
# Install Harbor using Helm
helm repo add harbor https://helm.goharbor.io
helm install harbor harbor/harbor \
  --set expose.type=nodePort \
  --set expose.tls.enabled=false \
  --set externalURL=http://harbor.local

# Get admin password
kubectl get secret harbor-core -o jsonpath='{.data.HARBOR_ADMIN_PASSWORD}' | base64 -d

# Login
docker login harbor.local -u admin
```

#### Option C: Docker Hub

```bash
# Login to Docker Hub
docker login

# Use images like: docker.io/username/java-app:tag
```

### 2. Jenkins Setup

#### Install Jenkins Plugins

Required plugins:
- Docker Pipeline
- Git Plugin
- GitHub Plugin
- Kubernetes Plugin
- Pipeline
- Credentials Binding

```bash
# Install via Jenkins UI or CLI
jenkins-cli install-plugin docker-workflow git github kubernetes credentials-binding workflow-aggregator
```

#### Configure Jenkins Credentials

1. **GitHub Token** (for webhook and repo access)
   - Manage Jenkins → Credentials → Add Credentials
   - Kind: Secret text
   - ID: `github-token`
   - Secret: Your GitHub token

2. **Docker Registry Credentials**
   - Kind: Username with password
   - ID: `docker-registry-creds`
   - Username/Password: Registry credentials

3. **Kubernetes Config** (if deploying from Jenkins)
   - Kind: Secret file
   - ID: `kubeconfig`
   - File: Your kubeconfig file

### 3. Jenkinsfile Configuration

Create `Jenkinsfile` in your application repository:

```groovy
pipeline {
    agent any

    environment {
        // Registry configuration
        REGISTRY_URL = '10.108.98.54:80'  // or harbor.local, docker.io
        IMAGE_NAME = 'java-app'

        // Extract feature name from branch
        FEATURE_NAME = "${env.GIT_BRANCH.replace('feature/', '')}"
        IMAGE_TAG = "${FEATURE_NAME}-${env.BUILD_NUMBER}"

        // GitOps repository
        GITOPS_REPO = 'https://github.com/Maximusthefuture/gitops.git'
        GITOPS_BRANCH = "feature/${FEATURE_NAME}"

        // Credentials
        DOCKER_CREDS = credentials('docker-registry-creds')
        GITHUB_TOKEN = credentials('github-token')
    }

    stages {
        stage('Checkout') {
            steps {
                echo "Building feature: ${FEATURE_NAME}"
                checkout scm
            }
        }

        stage('Build Application') {
            steps {
                dir('java-repo') {
                    sh 'mvn clean package -DskipTests'
                }
            }
        }

        stage('Run Tests') {
            steps {
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
                dir('java-repo') {
                    script {
                        docker.build("${REGISTRY_URL}/${IMAGE_NAME}:${IMAGE_TAG}")
                    }
                }
            }
        }

        stage('Push to Registry') {
            steps {
                script {
                    docker.withRegistry("http://${REGISTRY_URL}", 'docker-registry-creds') {
                        docker.image("${REGISTRY_URL}/${IMAGE_NAME}:${IMAGE_TAG}").push()
                        docker.image("${REGISTRY_URL}/${IMAGE_NAME}:${IMAGE_TAG}").push("${FEATURE_NAME}")
                    }
                }
            }
        }

        stage('Update GitOps Repository') {
            steps {
                script {
                    // Clone GitOps repo
                    sh """
                        rm -rf gitops-temp
                        git clone https://${GITHUB_TOKEN}@github.com/Maximusthefuture/gitops.git gitops-temp
                        cd gitops-temp

                        # Create or checkout feature branch
                        git checkout -b ${GITOPS_BRANCH} 2>/dev/null || git checkout ${GITOPS_BRANCH}

                        # Create overlay directory
                        mkdir -p envs/feature-${FEATURE_NAME}

                        # Create kustomization.yaml
                        cat > envs/feature-${FEATURE_NAME}/kustomization.yaml <<EOF
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
EOF

                        # Commit and push
                        git config user.name "Jenkins CI"
                        git config user.email "jenkins@ci.local"
                        git add envs/feature-${FEATURE_NAME}/
                        git diff --staged --quiet || git commit -m "Update ${FEATURE_NAME} to build ${BUILD_NUMBER} [skip ci]"
                        git push origin ${GITOPS_BRANCH}

                        cd ..
                        rm -rf gitops-temp
                    """
                }
            }
        }

        stage('Notify ArgoCD') {
            steps {
                echo """
                ✅ Build Complete!

                📦 Image: ${REGISTRY_URL}/${IMAGE_NAME}:${IMAGE_TAG}
                🌿 Branch: ${GITOPS_BRANCH}
                📁 Overlay: envs/feature-${FEATURE_NAME}/
                🚀 Namespace: preview-feature-${FEATURE_NAME}

                ArgoCD will automatically discover and deploy within 3 minutes.

                Monitor: kubectl get applications -n argocd | grep ${FEATURE_NAME}
                """
            }
        }
    }

    post {
        success {
            echo '✅ Pipeline completed successfully!'
        }
        failure {
            echo '❌ Pipeline failed!'
        }
        always {
            cleanWs()
        }
    }
}
```

### 4. Jenkins Job Configuration

#### Option A: Multibranch Pipeline (Recommended)

1. New Item → Multibranch Pipeline
2. Configure:
   - **Branch Sources**: Git
   - **Repository URL**: `https://github.com/Maximusthefuture/gitops.git`
   - **Credentials**: Select your GitHub token
   - **Behaviors**:
     - Discover branches: Filter by name (with regular expression) `^feature/.*`
   - **Build Configuration**: by Jenkinsfile
   - **Script Path**: `Jenkinsfile`
   - **Scan Multibranch Pipeline Triggers**: Periodically if not otherwise run (1 hour)

3. Save and Scan Now

#### Option B: Pipeline with GitHub Webhook

1. New Item → Pipeline
2. Configure:
   - **Build Triggers**: GitHub hook trigger for GITScm polling
   - **Pipeline**: Pipeline script from SCM
   - **SCM**: Git
   - **Repository URL**: Your repo
   - **Branch Specifier**: `*/feature/*`

3. Configure GitHub webhook:
   - Go to GitHub repository → Settings → Webhooks
   - Add webhook: `http://jenkins.local/github-webhook/`
   - Content type: `application/json`
   - Events: Just the push event

### 5. ArgoCD Configuration

Your ApplicationSet is already configured for dynamic branch discovery. Just ensure you have the GitHub token secret:

```bash
# Create GitHub token secret for ArgoCD
kubectl create secret generic github-token \
  -n argocd \
  --from-literal=token=YOUR_GITHUB_TOKEN

# Apply ApplicationSet (if not already applied)
kubectl apply -f argocd/applicationset-feature-branches.yaml
```

## Complete Workflow Example

### Developer Workflow

```bash
# 1. Create feature branch
git checkout -b feature/payment-gateway

# 2. Make changes to application
cd java-repo
vim src/main/java/com/example/PaymentController.java

# 3. Commit and push
git add .
git commit -m "Add payment gateway integration"
git push origin feature/payment-gateway
```

### What Happens Automatically

1. **GitHub** receives push
2. **Webhook** triggers Jenkins job
3. **Jenkins** pipeline runs:
   - ✓ Checks out code
   - ✓ Builds JAR with Maven
   - ✓ Runs unit tests
   - ✓ Builds Docker image
   - ✓ Pushes image to registry as `java-app:payment-gateway-123`
   - ✓ Updates GitOps repo: creates/updates `envs/feature-payment-gateway/`
   - ✓ Commits and pushes to `feature/payment-gateway` branch
4. **ArgoCD** (within 3 minutes):
   - ✓ Discovers new branch `feature/payment-gateway`
   - ✓ Creates Application `preview-payment-gateway`
   - ✓ Syncs from feature branch
   - ✓ Deploys to namespace `preview-feature-payment-gateway`
5. **Kubernetes**:
   - ✓ Creates namespace
   - ✓ Deploys pods with new image
   - ✓ Creates service and ingress

### Accessing Your Feature Environment

```bash
# Check application status
kubectl get applications -n argocd | grep payment-gateway

# Check pods
kubectl get pods -n preview-feature-payment-gateway

# Port forward to access
kubectl port-forward -n preview-feature-payment-gateway svc/auth-service 8080:8080

# Test
curl http://localhost:8080/payment/status
```

## Advanced Configurations

### Using Harbor Registry with Jenkins

```groovy
environment {
    REGISTRY_URL = 'harbor.local'
    REGISTRY_PROJECT = 'java-apps'
    IMAGE_NAME = "${REGISTRY_PROJECT}/java-app"
}

stage('Push to Harbor') {
    steps {
        script {
            docker.withRegistry("http://${REGISTRY_URL}", 'harbor-creds') {
                docker.image("${REGISTRY_URL}/${IMAGE_NAME}:${IMAGE_TAG}").push()
            }
        }
    }
}
```

### Adding Quality Gates

```groovy
stage('SonarQube Analysis') {
    steps {
        withSonarQubeEnv('SonarQube') {
            sh 'mvn sonar:sonar'
        }
    }
}

stage('Quality Gate') {
    steps {
        timeout(time: 1, unit: 'HOURS') {
            waitForQualityGate abortPipeline: true
        }
    }
}
```

### Deploying to Multiple Environments

```groovy
stage('Deploy to Staging') {
    when {
        branch 'develop'
    }
    steps {
        script {
            // Update staging overlay
            sh """
                cd gitops-temp
                git checkout main

                mkdir -p envs/staging
                cat > envs/staging/kustomization.yaml <<EOF
# Staging kustomization
EOF
                git add envs/staging/
                git commit -m "Deploy to staging"
                git push origin main
            """
        }
    }
}
```

### Slack/Email Notifications

```groovy
post {
    success {
        slackSend(
            color: 'good',
            message: """
                ✅ Build Success!
                Feature: ${FEATURE_NAME}
                Build: ${BUILD_NUMBER}
                Image: ${REGISTRY_URL}/${IMAGE_NAME}:${IMAGE_TAG}
                URL: ${BUILD_URL}
            """
        )
    }
    failure {
        slackSend(
            color: 'danger',
            message: "❌ Build Failed! Feature: ${FEATURE_NAME}, Build: ${BUILD_NUMBER}"
        )
    }
}
```

## Monitoring and Troubleshooting

### Jenkins Logs

```bash
# View Jenkins logs
kubectl logs -n jenkins deployment/jenkins -f

# View specific job logs
# Jenkins UI → Job → Console Output
```

### Registry Verification

```bash
# Check if image exists in registry
curl http://10.108.98.54:80/v2/java-app/tags/list

# For Harbor
curl -u admin:password http://harbor.local/api/v2.0/projects/java-apps/repositories/java-app/artifacts
```

### ArgoCD Sync Status

```bash
# Check if ArgoCD discovered the branch
kubectl logs -n argocd deployment/argocd-applicationset-controller -f | grep payment-gateway

# Check application sync status
argocd app get preview-payment-gateway

# Force sync if needed
argocd app sync preview-payment-gateway
```

### Image Pull Issues

```bash
# If pods can't pull image from registry
# Create imagePullSecret
kubectl create secret docker-registry registry-creds \
  -n preview-feature-payment-gateway \
  --docker-server=10.108.98.54:80 \
  --docker-username=admin \
  --docker-password=password

# Update deployment to use secret (in base/deployment.yaml)
# spec:
#   imagePullSecrets:
#   - name: registry-creds
```

## Cleanup

### Manual Cleanup

```bash
# Delete feature environment
kubectl delete namespace preview-feature-payment-gateway

# Delete ArgoCD application
kubectl delete application preview-payment-gateway -n argocd

# Delete feature branch
git push origin --delete feature/payment-gateway
```

### Automated Cleanup (Add to Jenkinsfile)

```groovy
stage('Cleanup on Merge') {
    when {
        branch 'main'
    }
    steps {
        script {
            // Delete old feature environments
            sh """
                # List all preview namespaces older than 7 days
                kubectl get ns -l environment=preview \
                  -o json | jq -r '.items[] | select(.metadata.creationTimestamp | fromdateiso8601 < (now - 604800)) | .metadata.name' \
                  | xargs -r kubectl delete ns
            """
        }
    }
}
```

## Best Practices

1. **Use Semantic Versioning for Images**
   - Tag with feature name + build number: `payment-gateway-123`
   - Also tag with git commit SHA: `abc123def`

2. **Implement Image Scanning**
   - Use Trivy or Clair to scan images for vulnerabilities
   - Fail build if critical vulnerabilities found

3. **Resource Limits**
   - Set appropriate resource requests/limits in overlays
   - Prevent feature environments from consuming all cluster resources

4. **TTL for Feature Environments**
   - Auto-delete environments after 7 days
   - Add annotation with expiry date

5. **Rollback Strategy**
   - Keep previous image tags
   - Allow quick rollback via ArgoCD

6. **Monitoring**
   - Add Prometheus annotations to services
   - Monitor resource usage per feature environment

## Summary

This workflow provides:
- ✅ Automated builds on feature branch push
- ✅ Docker image creation and registry push
- ✅ GitOps-based deployment
- ✅ Isolated preview environments per feature
- ✅ Auto-discovery by ArgoCD
- ✅ Full traceability (commit → build → image → deployment)

Now you have a complete Jenkins-based CI/CD pipeline integrated with your GitOps workflow!
