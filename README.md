# GitOps Demo - Ephemeral Environments with ArgoCD

Этот проект демонстрирует настройку эфемерных окружений в Minikube с использованием ArgoCD.

## Структура проекта

```
gitops-demo/
├── base/                    # Базовые Kubernetes манифесты
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   └── kustomization.yaml
├── envs/                    # Окружения (overlays)
│   ├── feature-auth/
│   │   └── kustomization.yaml
│   └── feature-login/
│       └── kustomization.yaml
├── argocd/                  # ArgoCD конфигурации
│   ├── applicationset.yaml
│   └── applicationset-github.yaml
└── scripts/                 # Скрипты настройки
    ├── setup-local-git.sh   # Для локального file:// репозитория
    └── setup-github.sh      # Для GitHub репозитория
```

## Настройка с GitHub репозиторием

### Требования

- Minikube запущен и работает
- ArgoCD установлен в namespace `argocd`
- Проект `previews` создан в ArgoCD
- Доступ к GitHub репозиторию

### Быстрый старт

1. **Создайте GitHub репозиторий** (или используйте существующий)

2. **Загрузите код в GitHub**:
   ```bash
   git init
   git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
   git add .
   git commit -m "Initial commit"
   git push -u origin main
   ```

3. **Настройте ArgoCD для работы с GitHub**:
   ```bash
   export GITHUB_REPO_URL=https://github.com/YOUR_USERNAME/YOUR_REPO.git
   export GITHUB_BRANCH=main  # опционально, по умолчанию main
   
   # Для приватного репозитория также установите токен:
   export GITHUB_TOKEN=your_github_token
   
   ./scripts/setup-github.sh
   ```

4. **Проверьте статус приложений**:
   ```bash
   argocd app list
   kubectl get applications -n argocd
   ```

### Для приватного репозитория

Если ваш репозиторий приватный, вам понадобится GitHub Personal Access Token:

1. Создайте токен на GitHub: Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Дайте токену права на чтение репозиториев
3. Экспортируйте токен:
   ```bash
   export GITHUB_TOKEN=your_token_here
   ```

## Проверка работы

После настройки приложения должны появиться в ArgoCD:

```bash
# Список приложений
argocd app list

# Детали приложения
argocd app get preview-feature-auth

# Синхронизация вручную (если нужно)
argocd app sync preview-feature-auth

# Проверка namespaces
kubectl get namespaces | grep preview

# Проверка ресурсов
kubectl get all -n preview-feature-auth
```

## Добавление нового окружения

1. Создайте новую директорию в `envs/`:
   ```bash
   mkdir -p envs/feature-new
   ```

2. Создайте `kustomization.yaml`:
   ```yaml
   apiVersion: kustomize.config.k8s.io/v1beta1
   kind: Kustomization
   
   namespace: preview-feature-new
   
   resources:
   - ../../base
   
   patches:
   - patch: |-
       - op: replace
         path: /spec/rules/0/host
         value: feature-new.local
     target:
       kind: Ingress
       name: auth-service
   
   commonLabels:
     environment: preview
     branch: feature-new
     managed-by: argocd
   ```

3. Загрузите изменения в GitHub:
   ```bash
   git add envs/feature-new
   git commit -m "Add feature-new environment"
   git push
   ```

4. ApplicationSet автоматически создаст новое приложение в ArgoCD

## Автоматическое развертывание при пуше в feature ветку

Для автоматического развертывания Java приложения при пуше в feature ветку см. [FEATURE_BRANCHES.md](./FEATURE_BRANCHES.md).

Основные возможности:
- Автоматическое обнаружение feature веток через ArgoCD ApplicationSet
- Автоматическая сборка Java приложения через GitHub Actions
- Автоматическое создание overlay для каждой feature ветки
- Развертывание в отдельном namespace для каждой ветки

## Локальная разработка (file://)

Если вы хотите использовать локальный репозиторий без GitHub:

```bash
./scripts/setup-local-git.sh
```

**Примечание**: Локальный подход работает только для тестирования и требует дополнительной настройки.

## Troubleshooting

### Приложения не синхронизируются

1. Проверьте статус репозитория в ArgoCD:
   ```bash
   kubectl get secret -n argocd -l argocd.argoproj.io/secret-type=repository
   ```

2. Проверьте логи repo-server:
   ```bash
   kubectl logs -n argocd deployment/argocd-repo-server
   ```

3. Проверьте статус приложения:
   ```bash
   argocd app get preview-feature-auth
   ```

### Ошибка доступа к репозиторию

Для приватного репозитория убедитесь, что:
- Токен установлен правильно: `echo $GITHUB_TOKEN`
- Токен имеет права на чтение репозитория
- Секрет создан в ArgoCD

### ApplicationSet не создает приложения

1. Проверьте статус ApplicationSet:
   ```bash
   kubectl get applicationset preview-environments -n argocd -o yaml
   ```

2. Проверьте логи ApplicationSet контроллера:
   ```bash
   kubectl logs -n argocd deployment/argocd-applicationset-controller
   ```

## Полезные команды

```bash
# Получить пароль ArgoCD
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d

# Порт-форвард для доступа к UI
kubectl port-forward svc/argocd-server -n argocd 8080:443

# Удалить приложение
argocd app delete preview-feature-auth

# Удалить ApplicationSet
kubectl delete applicationset preview-environments -n argocd
```

