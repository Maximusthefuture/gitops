# Быстрый старт - Настройка ArgoCD с GitHub

## Шаг 1: Подготовка GitHub репозитория

Если у вас еще нет репозитория на GitHub:

```bash
# Инициализируйте git репозиторий (если еще не сделано)
git init
git add .
git commit -m "Initial commit"

# Создайте репозиторий на GitHub и добавьте remote
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git branch -M main
git push -u origin main
```

## Шаг 2: Настройка ArgoCD

### Для публичного репозитория:

```bash
export GITHUB_REPO_URL=https://github.com/YOUR_USERNAME/YOUR_REPO.git
./scripts/setup-github.sh
```

### Для приватного репозитория:

1. Создайте GitHub Personal Access Token:
   - Перейдите на https://github.com/settings/tokens
   - Нажмите "Generate new token (classic)"
   - Выберите права: `repo` (для доступа к приватным репозиториям)
   - Скопируйте токен

2. Запустите скрипт с токеном:

```bash
export GITHUB_REPO_URL=https://github.com/YOUR_USERNAME/YOUR_REPO.git
export GITHUB_TOKEN=your_token_here
./scripts/setup-github.sh
```

## Шаг 3: Проверка

После выполнения скрипта подождите 30-60 секунд и проверьте:

```bash
# Список приложений
argocd app list

# Детали приложения
argocd app get preview-feature-auth

# Проверка namespaces
kubectl get namespaces | grep preview

# Проверка ресурсов
kubectl get all -n preview-feature-auth
```

## Шаг 4: Синхронизация (если нужно)

Приложения должны синхронизироваться автоматически. Если нет:

```bash
argocd app sync preview-feature-auth
argocd app sync preview-feature-login
```

## Устранение неполадок

### Приложения не появляются

1. Проверьте статус ApplicationSet:
   ```bash
   kubectl get applicationset preview-environments -n argocd
   ```

2. Проверьте логи:
   ```bash
   kubectl logs -n argocd deployment/argocd-applicationset-controller
   ```

### Ошибка доступа к репозиторию

1. Проверьте секрет репозитория:
   ```bash
   kubectl get secret -n argocd -l argocd.argoproj.io/secret-type=repository
   ```

2. Для приватного репозитория убедитесь, что токен установлен:
   ```bash
   echo $GITHUB_TOKEN
   ```

### Добавление репозитория через ArgoCD CLI

Альтернативный способ добавления репозитория:

```bash
# Получите пароль ArgoCD
ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d)

# Войдите в ArgoCD
argocd login localhost:8080 --username admin --password $ARGOCD_PASSWORD --insecure

# Добавьте репозиторий
argocd repo add https://github.com/YOUR_USERNAME/YOUR_REPO.git

# Для приватного репозитория
argocd repo add https://github.com/YOUR_USERNAME/YOUR_REPO.git --username git --password $GITHUB_TOKEN
```

## Следующие шаги

После успешной настройки:

1. Изменения в GitHub будут автоматически синхронизироваться с кластером
2. При добавлении новых директорий в `envs/` они автоматически появятся в ArgoCD
3. Используйте ArgoCD UI для мониторинга и управления приложениями:
   ```bash
   kubectl port-forward svc/argocd-server -n argocd 8080:443
   # Откройте https://localhost:8080 в браузере
   ```

