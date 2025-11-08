# Complete Setup Guide: Minikube + ArgoCD + GitOps

Complete step-by-step guide for setting up the entire GitOps environment from scratch with Minikube (Docker driver) and ArgoCD.

## Table of Contents

- [Prerequisites](#prerequisites)
- [1. Install Required Tools](#1-install-required-tools)
- [2. Start Minikube with Docker Driver](#2-start-minikube-with-docker-driver)
- [3. Install ArgoCD](#3-install-argocd)
- [4. Setup Local Docker Registry](#4-setup-local-docker-registry)
- [5. Configure Repository](#5-configure-repository)
- [6. Verification](#6-verification)
- [7. Daily Operations](#7-daily-operations)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### System Requirements

- **OS**: macOS, Linux, or Windows with WSL2
- **RAM**: Minimum 4GB, Recommended 8GB+
- **CPU**: 2+ cores
- **Disk**: 20GB+ free space
- **Internet**: Required for downloading images and packages

### Required Software

- Docker Desktop (or Docker Engine)
- kubectl
- minikube
- Git

---

## 1. Install Required Tools

### macOS

#### Install Docker Desktop

```bash
# Download from https://www.docker.com/products/docker-desktop
# Or using Homebrew:
brew install --cask docker

# Start Docker Desktop from Applications
# Verify installation
docker --version
docker ps
```

#### Install kubectl

```bash
# Using Homebrew
brew install kubectl

# Verify installation
kubectl version --client
```

#### Install minikube

```bash
# Using Homebrew
brew install minikube

# Verify installation
minikube version
```

#### Install Git (if not already installed)

```bash
brew install git
git --version
```

### Linux

#### Install Docker

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install -y docker.io

# Start Docker
sudo systemctl start docker
sudo systemctl enable docker

# Add user to docker group (to run without sudo)
sudo usermod -aG docker $USER
newgrp docker

# Verify
docker --version
docker ps
```

#### Install kubectl

```bash
# Download latest release
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"

# Install
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# Verify
kubectl version --client
```

#### Install minikube

```bash
# Download binary
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64

# Install
sudo install minikube-linux-amd64 /usr/local/bin/minikube

# Verify
minikube version
```

### Windows (with WSL2)

#### Install WSL2

```powershell
# Run in PowerShell as Administrator
wsl --install
wsl --set-default-version 2
```

#### Install Docker Desktop

- Download from: https://www.docker.com/products/docker-desktop
- Enable WSL2 integration in Docker Desktop settings
- Verify in WSL2 terminal: `docker --version`

#### Install kubectl and minikube in WSL2

```bash
# Follow Linux instructions above in WSL2 terminal
```

---

## 2. Start Minikube with Docker Driver

### Initial Cluster Creation

```bash
# Start minikube with Docker driver
minikube start --driver=docker

# Configure resource allocation (recommended)
minikube start \
  --driver=docker \
  --cpus=4 \
  --memory=8192 \
  --disk-size=20g \
  --kubernetes-version=v1.28.0

# If you get permission errors on Linux
sudo usermod -aG docker $USER && newgrp docker
```

### Verify Minikube is Running

```bash
# Check minikube status
minikube status

# Should show:
# minikube
# type: Control Plane
# host: Running
# kubelet: Running
# apiserver: Running
# kubeconfig: Configured

# Check cluster info
kubectl cluster-info

# Check nodes
kubectl get nodes

# Should show:
# NAME       STATUS   ROLES           AGE   VERSION
# minikube   Ready    control-plane   1m    v1.28.0
```

### Configure kubectl Context

```bash
# Minikube automatically configures kubectl context
# Verify you're using minikube context
kubectl config current-context
# Should output: minikube

# View all contexts
kubectl config get-contexts
```

### Enable Required Addons

```bash
# Enable metrics server (for resource monitoring)
minikube addons enable metrics-server

# Enable ingress controller (for Ingress resources)
minikube addons enable ingress

# Enable registry (for local Docker registry)
minikube addons enable registry

# List enabled addons
minikube addons list
```

---

## 3. Install ArgoCD

### Install ArgoCD in Cluster

```bash
# Create namespace for ArgoCD
kubectl create namespace argocd

# Install ArgoCD using official manifest
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Wait for ArgoCD pods to be ready (this may take 2-3 minutes)
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=argocd-server -n argocd --timeout=300s

# Check all ArgoCD pods are running
kubectl get pods -n argocd

# Expected output:
# NAME                                  READY   STATUS    RESTARTS   AGE
# argocd-application-controller-...     1/1     Running   0          2m
# argocd-applicationset-controller-...  1/1     Running   0          2m
# argocd-dex-server-...                 1/1     Running   0          2m
# argocd-notifications-controller-...   1/1     Running   0          2m
# argocd-redis-...                      1/1     Running   0          2m
# argocd-repo-server-...                1/1     Running   0          2m
# argocd-server-...                     1/1     Running   0          2m
```

### Access ArgoCD UI

```bash
# Get initial admin password
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d; echo

# Port forward to access UI
kubectl port-forward svc/argocd-server -n argocd 8080:443

# Open browser to: https://localhost:8080
# Username: admin
# Password: (output from command above)

# IMPORTANT: Accept the self-signed certificate warning in browser
```

### Install ArgoCD CLI (Optional but Recommended)

```bash
# macOS
brew install argocd

# Linux
curl -sSL -o argocd https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
sudo install -m 555 argocd /usr/local/bin/argocd

# Login via CLI
argocd login localhost:8080 --username admin --password <password-from-above> --insecure

# Change admin password (recommended)
argocd account update-password
```

### Create ArgoCD AppProject for Previews

```bash
# Create previews project (required for feature environments)
kubectl apply -f - <<EOF
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: previews
  namespace: argocd
spec:
  description: Preview environments for feature branches
  sourceRepos:
  - '*'
  destinations:
  - namespace: 'preview-*'
    server: https://kubernetes.default.svc
  - namespace: argocd
    server: https://kubernetes.default.svc
  clusterResourceWhitelist:
  - group: '*'
    kind: '*'
  namespaceResourceWhitelist:
  - group: '*'
    kind: '*'
EOF

# Verify project created
kubectl get appproject -n argocd
```

---

## 4. Setup Local Docker Registry

### Enable and Configure Registry

```bash
# Registry addon should already be enabled from step 2
# If not, enable it:
minikube addons enable registry

# Get registry service IP
export REGISTRY_IP=$(kubectl get svc registry -n kube-system -o jsonpath='{.spec.clusterIP}')
echo "Registry IP: $REGISTRY_IP:80"

# Expected output: Registry IP: 10.108.98.54:80 (IP may vary)

# Verify registry is running
kubectl get pods -n kube-system | grep registry

# Test registry connectivity from within minikube
minikube ssh "curl -v http://localhost:5000/v2/"
# Should return: {}
```

### Configure Registry Access

```bash
# The registry is accessible from within minikube at:
# - localhost:5000 (from inside minikube)
# - 10.108.98.54:80 (from pods in the cluster)

# To push images, you need to:
# 1. Build image on host
# 2. Load into minikube
# 3. Tag and push from inside minikube

# Example workflow:
docker build -t java-app:latest ./java-repo
minikube image load java-app:latest
minikube ssh "docker tag java-app:latest localhost:5000/java-app:latest"
minikube ssh "docker push localhost:5000/java-app:latest"

# Verify image in registry
curl http://$REGISTRY_IP:80/v2/_catalog
# Should show: {"repositories":["java-app"]}
```

### Registry Port Forwarding (Optional)

```bash
# If you want to access registry from host machine
kubectl port-forward -n kube-system svc/registry 5000:80 &

# Now you can push directly from host:
docker tag java-app:latest localhost:5000/java-app:latest
docker push localhost:5000/java-app:latest
```

---

## 5. Configure Repository

### Clone GitOps Repository

```bash
# Clone repository
git clone https://github.com/Maximusthefuture/gitops.git
cd gitops

# Check current branch
git branch

# Checkout main branch (if not already there)
git checkout main
```

### Setup GitHub Token (for ArgoCD SCM Provider)

```bash
# Create GitHub Personal Access Token
# 1. Go to: https://github.com/settings/tokens
# 2. Generate new token (classic)
# 3. Select scope: repo (Full control of private repositories)
# 4. Copy the token

# Create secret in ArgoCD namespace
kubectl create secret generic github-token \
  -n argocd \
  --from-literal=token=YOUR_GITHUB_TOKEN_HERE

# Verify secret created
kubectl get secret github-token -n argocd
```

### Apply ApplicationSet

```bash
# Apply the ApplicationSet for feature branch discovery
kubectl apply -f argocd/applicationset-feature-branches.yaml

# Verify ApplicationSet created
kubectl get applicationset -n argocd

# Check ApplicationSet logs for branch discovery
kubectl logs -n argocd deployment/argocd-applicationset-controller -f
```

### Build and Deploy Initial Application

```bash
# Build Java application (requires Maven)
cd java-repo
mvn clean package -DskipTests
cd ..

# Build Docker image
cd java-repo
docker build --platform linux/amd64 -t java-app:latest .
cd ..

# Load into minikube
minikube image load java-app:latest

# Push to local registry
minikube ssh "docker tag java-app:latest localhost:5000/java-app:latest"
minikube ssh "docker push localhost:5000/java-app:latest"

# Verify image in registry
export REGISTRY_IP=$(kubectl get svc registry -n kube-system -o jsonpath='{.spec.clusterIP}')
curl http://$REGISTRY_IP:80/v2/java-app/tags/list
# Should show: {"name":"java-app","tags":["latest"]}
```

---

## 6. Verification

### Check All Components

```bash
# 1. Minikube status
minikube status

# 2. Cluster info
kubectl cluster-info

# 3. All pods running
kubectl get pods -A

# 4. ArgoCD running
kubectl get pods -n argocd

# 5. Registry running
kubectl get pods -n kube-system | grep registry

# 6. ApplicationSet exists
kubectl get applicationset -n argocd

# 7. GitHub token secret exists
kubectl get secret github-token -n argocd
```

### Test Feature Branch Workflow

```bash
# Create a test feature branch
git checkout -b feature/test-setup

# Create overlay for this feature
mkdir -p envs/feature-test-setup

cat > envs/feature-test-setup/kustomization.yaml <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: preview-feature-test-setup

resources:
- ../../base

images:
- name: 10.108.98.54:80/java-app
  newTag: latest

patches:
- patch: |-
    - op: replace
      path: /spec/rules/0/host
      value: feature-test-setup.local
  target:
    kind: Ingress
    name: auth-service

commonLabels:
  environment: preview
  branch: feature-test-setup
  managed-by: argocd
EOF

# Commit and push
git add envs/feature-test-setup/
git commit -m "Test setup verification"
git push origin feature/test-setup

# Wait 3 minutes for ArgoCD to discover branch
# Check if Application was created
kubectl get applications -n argocd | grep test-setup

# Check namespace created
kubectl get namespaces | grep preview-feature-test-setup

# Check pods deployed
kubectl get pods -n preview-feature-test-setup

# Access application
kubectl port-forward -n preview-feature-test-setup svc/auth-service 8081:8080
curl http://localhost:8081/
```

---

## 7. Daily Operations

### Starting the Environment

```bash
# Start minikube (if stopped)
minikube start

# Verify all systems running
kubectl get pods -A

# Start port forwards (if needed)
kubectl port-forward svc/argocd-server -n argocd 8080:443 &
```

### Stopping the Environment

```bash
# Stop port forwards
pkill -f "kubectl port-forward"

# Stop minikube
minikube stop

# Or pause (faster restart)
minikube pause
```

### Cleanup and Reset

```bash
# Delete specific namespace
kubectl delete namespace preview-feature-xyz

# Delete all preview namespaces
kubectl get ns -l environment=preview -o name | xargs kubectl delete

# Delete minikube cluster completely
minikube delete

# Start fresh
minikube start --driver=docker
```

### Useful Commands

```bash
# View minikube dashboard
minikube dashboard

# SSH into minikube node
minikube ssh

# View minikube logs
minikube logs

# Check resource usage
kubectl top nodes
kubectl top pods -A

# View ArgoCD applications
kubectl get applications -n argocd
argocd app list

# Sync an application manually
argocd app sync preview-feature-xyz

# View application details
argocd app get preview-feature-xyz

# Access services via minikube
minikube service auth-service -n preview-feature-xyz --url
```

---

## Troubleshooting

### Minikube Won't Start

```bash
# Check Docker is running
docker ps

# Delete and recreate cluster
minikube delete
minikube start --driver=docker

# Check system resources
docker info | grep -i memory
docker info | grep -i cpu

# View minikube logs
minikube logs --follow
```

### ArgoCD Pods CrashLooping

```bash
# Check pod logs
kubectl logs -n argocd <pod-name>

# Check resource constraints
kubectl describe pod -n argocd <pod-name>

# Increase minikube resources and restart
minikube delete
minikube start --driver=docker --cpus=4 --memory=8192
```

### Applications Not Syncing

```bash
# Check ApplicationSet controller logs
kubectl logs -n argocd deployment/argocd-applicationset-controller

# Check if GitHub token is valid
kubectl get secret github-token -n argocd -o jsonpath='{.data.token}' | base64 -d

# Force refresh ApplicationSet
kubectl delete applicationset preview-feature-branches -n argocd
kubectl apply -f argocd/applicationset-feature-branches.yaml

# Manually sync application
argocd app sync preview-feature-xyz --force
```

### Registry Issues

```bash
# Check registry is running
kubectl get pods -n kube-system | grep registry

# Restart registry
kubectl rollout restart deployment/registry -n kube-system

# Verify connectivity
minikube ssh "curl http://localhost:5000/v2/"

# Check images in registry
export REGISTRY_IP=$(kubectl get svc registry -n kube-system -o jsonpath='{.spec.clusterIP}')
curl http://$REGISTRY_IP:80/v2/_catalog
```

### Image Pull Errors

```bash
# Check if image exists in registry
curl http://10.108.98.54:80/v2/java-app/tags/list

# Verify imagePullPolicy in deployment
kubectl get deployment -n preview-feature-xyz auth-service -o yaml | grep imagePullPolicy

# Check pod events
kubectl describe pod -n preview-feature-xyz <pod-name>

# Rebuild and push image
docker build -t java-app:latest ./java-repo
minikube image load java-app:latest
minikube ssh "docker tag java-app:latest localhost:5000/java-app:latest && docker push localhost:5000/java-app:latest"
```

### Pods Stuck in Pending

```bash
# Check node resources
kubectl describe node minikube

# Check pod events
kubectl describe pod -n preview-feature-xyz <pod-name>

# Check for resource limits
kubectl get pod -n preview-feature-xyz <pod-name> -o yaml | grep -A 5 resources
```

### Cannot Access Services

```bash
# Check service exists
kubectl get svc -n preview-feature-xyz

# Check endpoints
kubectl get endpoints -n preview-feature-xyz

# Check pod is running
kubectl get pods -n preview-feature-xyz

# Test from within cluster
kubectl run -it --rm debug --image=busybox --restart=Never -- wget -O- http://auth-service.preview-feature-xyz:8080/
```

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                       Host Machine                          │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────┐        │
│  │   Docker   │  │   kubectl   │  │  ArgoCD CLI  │        │
│  └─────┬──────┘  └──────┬──────┘  └──────┬───────┘        │
└────────┼─────────────────┼─────────────────┼───────────────┘
         │                 │                 │
    ┌────▼─────────────────▼─────────────────▼──────────────┐
    │              Minikube (Docker Driver)                  │
    │  ┌──────────────────────────────────────────────────┐ │
    │  │           Kubernetes Cluster                     │ │
    │  │  ┌────────────────────────────────────────────┐  │ │
    │  │  │  Namespace: argocd                         │  │ │
    │  │  │  - ApplicationSet Controller               │  │ │
    │  │  │  - ArgoCD Server                           │  │ │
    │  │  │  - Repo Server                             │  │ │
    │  │  └────────────────────────────────────────────┘  │ │
    │  │  ┌────────────────────────────────────────────┐  │ │
    │  │  │  Namespace: kube-system                    │  │ │
    │  │  │  - Registry (10.108.98.54:80)              │  │ │
    │  │  └────────────────────────────────────────────┘  │ │
    │  │  ┌────────────────────────────────────────────┐  │ │
    │  │  │  Namespace: preview-feature-*              │  │ │
    │  │  │  - Java Application                        │  │ │
    │  │  │  - Service                                 │  │ │
    │  │  │  - Ingress                                 │  │ │
    │  │  └────────────────────────────────────────────┘  │ │
    │  └──────────────────────────────────────────────────┘ │
    └─────────────────────────────────────────────────────────┘
```

---

## Next Steps

After completing this setup:

1. **Explore Documentation**:
   - [DYNAMIC_FEATURE_BRANCHES.md](DYNAMIC_FEATURE_BRANCHES.md) - Dynamic branch discovery
   - [JENKINS_WORKFLOW.md](JENKINS_WORKFLOW.md) - Jenkins CI/CD integration
   - [LOCAL_REGISTRY_GUIDE.md](LOCAL_REGISTRY_GUIDE.md) - Registry usage

2. **Test Feature Workflows**:
   - Use `./scripts/deploy-feature-gitops-only.sh` for quick feature deployment
   - Test automatic discovery by pushing feature branches

3. **Set Up CI/CD**:
   - Configure Jenkins or GitHub Actions
   - Automate image builds and deployments

4. **Monitor and Scale**:
   - Use ArgoCD UI to monitor applications
   - Scale minikube resources as needed

---

## Summary

You now have a complete GitOps environment with:
- ✅ Minikube running with Docker driver
- ✅ ArgoCD installed and configured
- ✅ Local Docker registry
- ✅ Dynamic feature branch discovery
- ✅ Automated namespace creation
- ✅ Complete deployment workflow

Push a feature branch and watch ArgoCD automatically discover and deploy it! 🚀
