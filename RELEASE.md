# 发布说明

1. 将代码推送到 GitHub 仓库。
2. 在仓库 Settings > Actions > General 中允许工作流读写权限。
3. 创建并推送标签，例如 `git tag v1.9.1-fixed && git push origin v1.9.1-fixed`。
4. Actions 将构建 JAR、创建 Release、发布 Maven Package，并推送 Docker 镜像到 GHCR。

## 参数

- `graph.license.retry.max-attempts=6`
- `graph.license.retry.initial-delay-ms=500`
- `graph.license.retry.max-delay-ms=8000`
