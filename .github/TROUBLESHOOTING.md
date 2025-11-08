# Troubleshooting GitHub Actions

## Проблема: Permission denied при push

Если вы видите ошибку:
```
remote: Permission to USER/REPO.git denied to github-actions[bot].
fatal: unable to access 'https://github.com/USER/REPO/': The requested URL returned error: 403
```

### Решение 1: Проверьте настройки репозитория

1. Перейдите в Settings → Actions → General
2. В разделе "Workflow permissions" выберите:
   - ✅ "Read and write permissions"
   - ✅ "Allow GitHub Actions to create and approve pull requests"

3. Нажмите "Save"

### Решение 2: Используйте Personal Access Token (PAT)

Если настройки репозитория не помогают, создайте Personal Access Token:

1. Перейдите в Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Создайте новый токен с правами:
   - `repo` (полный доступ к репозиторию)
3. Добавьте токен как секрет в репозитории:
   - Settings → Secrets and variables → Actions → New repository secret
   - Имя: `GITHUB_TOKEN_WRITE`
   - Значение: ваш токен
4. Обновите workflow:

```yaml
- name: Checkout repository
  uses: actions/checkout@v4
  with:
    fetch-depth: 0
    token: ${{ secrets.GITHUB_TOKEN_WRITE }}
    ref: ${{ github.ref }}

- name: Commit and push changes
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN_WRITE }}
  run: |
    git config --local user.email "github-actions[bot]@users.noreply.github.com"
    git config --local user.name "github-actions[bot]"
    git add envs/feature-${{ steps.branch.outputs.feature_name }}/
    git commit -m "Auto-create overlay for feature-${{ steps.branch.outputs.feature_name }}" || exit 0
    git remote set-url origin https://x-access-token:${GITHUB_TOKEN}@github.com/${{ github.repository }}
    git push origin ${{ github.ref }}
```

### Решение 3: Используйте GitHub App (для организаций)

Для организаций рекомендуется использовать GitHub App вместо PAT:

1. Создайте GitHub App с правами на запись
2. Установите App в репозиторий
3. Используйте токен App в workflow

## Проблема: Бесконечный цикл workflow

Если workflow запускается снова и снова:

1. Убедитесь, что используется `paths-ignore` вместо `paths`:
```yaml
on:
  push:
    branches:
      - 'feature/**'
    paths-ignore:
      - 'envs/**'
```

2. Добавьте проверку в commit message:
```yaml
if: "!contains(github.event.head_commit.message, '[skip ci]')"
```

3. Используйте специальный префикс в commit message:
```yaml
git commit -m "[skip ci] Auto-create overlay for feature-$FEATURE_NAME"
```

## Проблема: Workflow не запускается

1. Проверьте, что файл находится в `.github/workflows/`
2. Проверьте синтаксис YAML
3. Убедитесь, что ветка существует и содержит изменения
4. Проверьте логи в Actions tab

## Проблема: Overlay создается, но ArgoCD не видит

1. Проверьте, что ApplicationSet настроен правильно:
```bash
kubectl get applicationset -n argocd
kubectl describe applicationset preview-feature-branches -n argocd
```

2. Проверьте логи ApplicationSet контроллера:
```bash
kubectl logs -n argocd deployment/argocd-applicationset-controller
```

3. Убедитесь, что репозиторий добавлен в ArgoCD:
```bash
kubectl get secret -n argocd -l argocd.argoproj.io/secret-type=repository
```

4. Проверьте, что директория существует в репозитории:
```bash
# В репозитории
ls -la envs/feature-*
```

