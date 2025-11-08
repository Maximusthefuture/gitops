# Dynamic Feature Branch Discovery

Your ApplicationSet is now configured to automatically discover ALL `feature/*` branches without manual configuration!

## How It Works

```
Push to feature/xyz → GitHub → ArgoCD SCM Provider discovers branch → Creates Application → Deploys from feature/xyz
```

## Current Configuration

[argocd/applicationset-feature-branches.yaml](argocd/applicationset-feature-branches.yaml:8-19) uses **SCM Provider generator**:

```yaml
generators:
  - scmProvider:
      github:
        organization: Maximusthefuture
        api: https://api.github.com
        tokenRef:
          secretName: github-token
          key: token
        allBranches: true
      filters:
      - branchMatch: "^feature/.*"
      requeueAfterSeconds: 180
```

This configuration:
- Automatically scans your GitHub organization (Maximusthefuture)
- Discovers ALL branches matching `feature/*` pattern
- Checks every 180 seconds (3 minutes) for new branches
- No manual list maintenance required!

## Setup Required

### 1. Create GitHub Personal Access Token

You need a GitHub token with `repo` scope:

1. Go to: https://github.com/settings/tokens
2. Click "Generate new token" → "Generate new token (classic)"
3. Select scopes:
   - ✅ `repo` (Full control of private repositories)
4. Generate token and copy it

### 2. Create Kubernetes Secret

```bash
# Replace YOUR_GITHUB_TOKEN with your actual token
kubectl create secret generic github-token \
  -n argocd \
  --from-literal=token=YOUR_GITHUB_TOKEN
```

Verify secret was created:
```bash
kubectl get secret github-token -n argocd
```

### 3. Apply Updated ApplicationSet

```bash
# If updating existing ApplicationSet
kubectl apply -f argocd/applicationset-feature-branches.yaml

# If replacing completely
kubectl delete applicationset preview-feature-branches -n argocd
kubectl apply -f argocd/applicationset-feature-branches.yaml
```

## Complete Workflow

### For New Feature

```bash
# 1. Create feature branch
git checkout -b feature/my-new-feature

# 2. Make your changes to the Java app
cd java-repo
# ... edit code ...

# 3. Use the deploy script (if available) or manual steps:
# Build JAR (if Maven is available)
mvn clean package -DskipTests

# Build Docker image
docker build --platform linux/amd64 -t java-app:my-new-feature .

# Load into minikube
minikube image load java-app:my-new-feature

# Push to local registry
minikube ssh "docker tag java-app:my-new-feature localhost:5000/java-app:my-new-feature"
minikube ssh "docker push localhost:5000/java-app:my-new-feature"

# 4. Create overlay (optional, base/ will be used if no overlay exists)
mkdir -p envs/feature-my-new-feature
cat > envs/feature-my-new-feature/kustomization.yaml <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: preview-feature-my-new-feature
resources:
- ../../base
images:
- name: 10.108.98.54:80/java-app
  newTag: my-new-feature
EOF

# 5. Commit and push
git add .
git commit -m "Add my-new-feature"
git push origin feature/my-new-feature

# 6. Wait ~3 minutes for ArgoCD to discover the branch
# ArgoCD will automatically:
# - Discover feature/my-new-feature branch
# - Create Application: preview-my-new-feature
# - Deploy from feature/my-new-feature branch
# - Auto-create namespace: preview-feature-my-new-feature
```

### Simplified GitOps-Only Workflow

If you already have the image in the registry:

```bash
./scripts/deploy-feature-gitops-only.sh my-new-feature
```

This script:
1. Creates overlay in `envs/feature-my-new-feature/`
2. Commits to feature branch
3. Pushes to GitHub
4. ArgoCD discovers and deploys automatically

## Monitoring

### Check if ApplicationSet is discovering branches

```bash
# View ApplicationSet logs
kubectl logs -n argocd deployment/argocd-applicationset-controller -f

# Look for lines like:
# "generated N applications"
# "branches": ["feature/xyz", ...]
```

### List discovered applications

```bash
# All preview applications
kubectl get applications -n argocd -l type=preview

# Specific feature
kubectl get application preview-my-new-feature -n argocd

# Check which branch it's tracking
kubectl get application preview-my-new-feature -n argocd \
  -o jsonpath='{.spec.source.targetRevision}'
```

### Check deployment

```bash
# List pods in feature namespace
kubectl get pods -n preview-feature-my-new-feature

# Check pod logs
kubectl logs -n preview-feature-my-new-feature deployment/auth-service

# Port forward to access
kubectl port-forward -n preview-feature-my-new-feature svc/auth-service 8080:8080
curl http://localhost:8080/
```

## Variables Available in ApplicationSet

The SCM Provider generator provides these variables:

| Variable | Example | Description |
|----------|---------|-------------|
| `{{branch}}` | `feature/my-feature` | Full branch name |
| `{{branch_slug}}` | `my-feature` | Branch name slug (safe for K8s names) |
| `{{url}}` | `https://github.com/Maximusthefuture/gitops.git` | Repository URL |
| `{{repository}}` | `gitops` | Repository name |
| `{{organization}}` | `Maximusthefuture` | GitHub organization/user |

Current template uses:
- Application name: `preview-{{branch_slug}}`
- Namespace: `preview-feature-{{branch_slug}}`
- Deploy from: `{{branch}}`

## Advantages

✅ **Fully Automatic**: No manual list updates when creating new branches
✅ **Self-Service**: Developers just push feature branches
✅ **Clean Namespaces**: Each feature gets isolated namespace
✅ **GitOps**: Everything tracked in Git
✅ **Dynamic Discovery**: New branches discovered within 3 minutes
✅ **No Main Branch Pollution**: Features stay on feature branches

## Comparison with Previous Setup

### Before (Manual List)
```yaml
generators:
  - list:
      elements:
      - branch: feature/testik1
        branchSlug: testik1
      - branch: feature/auth
        branchSlug: auth
      # Must manually add each branch!
```

### After (SCM Provider)
```yaml
generators:
  - scmProvider:
      github:
        organization: Maximusthefuture
        allBranches: true
      filters:
      - branchMatch: "^feature/.*"
      # Automatically discovers ALL feature/* branches!
```

## Troubleshooting

### ApplicationSet not discovering branches

```bash
# Check if secret exists
kubectl get secret github-token -n argocd

# View ApplicationSet controller logs
kubectl logs -n argocd deployment/argocd-applicationset-controller --tail=100

# Common issues:
# - GitHub token missing or invalid
# - Token doesn't have 'repo' scope
# - Branch filter not matching (^feature/.*)
# - Organization name incorrect
```

### Application created but not syncing

```bash
# Check application status
argocd app get preview-my-feature

# Common issues:
# - Path doesn't exist on feature branch (using 'base' by default)
# - Image doesn't exist in registry
# - Namespace creation failed
```

### Token expired

If your token expires, update the secret:

```bash
kubectl delete secret github-token -n argocd
kubectl create secret generic github-token \
  -n argocd \
  --from-literal=token=NEW_TOKEN
```

## Next Steps

1. **Create GitHub Token** (if not done)
2. **Create Secret** in ArgoCD namespace
3. **Apply ApplicationSet** with the new configuration
4. **Test** by pushing a new feature branch
5. **Monitor** ApplicationSet logs to see branch discovery

Now when you push any `feature/*` branch, ArgoCD will automatically discover it and deploy within 3 minutes!
