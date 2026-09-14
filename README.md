# o365
O365管理系统是一个以java语言开发的基于Microsoft Graph Restful API的多全局管理系统，理论上支持任何Office全局的管理(A1,A3,A1P,E3,E5等)，你可以很方便的使用它来批量添加，批量删除，批量启用，批量禁用，搜索和查看用户，绑定解绑域名，生成邀请码，邀请朋友注册，提升和收回管理员权限，更新密钥，查看订阅，分配订阅(创新用户时)，查看单全局或多全局报告，登录同时需要微信许可（此功能默认关闭）

## 最低环境需求
| 类型 | - |
| ---- | ----|
| CPU | 1C |
| RAM | 0.75G |
| 硬盘 | 5GB |

## heroku已不支持免费部署，故而删除体验网址  
## ~~体验o365 in heroku~~  
~~o365已部署于heroku,你可以访问以下路径体验最新版的o365,你也可以将工程fork到自己的仓库用自己的heroku账号进行部署（推荐）~~  
~~https://oo365.herokuapp.com~~  
  
~~**特别提示**~~  
~~heroku 超过30分钟不被访问数据就会被销毁，所以仅能用来体验o365的功能，有需求的话还是建议部署到自己的VPS，群晖或者杜甫上~~  


# mso365fix 项目说明、权限配置与 Docker 部署建议

> 本文档用于说明项目来源、维护分支、Microsoft Graph 应用程序权限、Docker 数据持久化、升级与发布注意事项。

## 1. 项目来源与链接

本项目基于原开源项目继续维护和修复。

- 原作者项目：https://github.com/vanyouseea/o365

原项目以 Java、Spring Boot 和 Microsoft Graph REST API 开发，用于管理 Microsoft 365 用户、许可证、域名、应用密钥、管理员角色及报告等功能。原项目采用 MIT License，二次修改和发布时应保留原项目作者、许可证文件及版权说明。

建议在项目首页保留以下说明：

```markdown
## 项目来源

本项目基于 [vanyouseea/o365](https://github.com/vanyouseea/o365) 继续维护和修复。

- 原项目：https://github.com/vanyouseea/o365
- 当前维护版本：https://github.com/xiliangcun/mso365fix
- License：MIT
```

## 2. Microsoft Graph 权限配置

### 2.1 必须选择的权限类型

在 Microsoft Entra 管理中心添加 API 权限时，应选择：

```text
Microsoft Graph
→ Application permissions
```

本项目使用客户端凭据方式获取令牌，不应只添加 Delegated permissions。

添加权限后必须执行：

```text
Grant admin consent for <租户名称>
```

只有状态显示为“Granted”后，新签发的访问令牌才会包含对应应用程序权限。添加权限后一般不需要重新创建 Client Secret，但应用应重新获取访问令牌。

### 2.2 原项目使用的权限

```text
Application.ReadWrite.All
Application.ReadWrite.OwnedBy
Directory.ReadWrite.All
RoleManagement.ReadWrite.Directory
User.ManageIdentities.All
User.ReadWrite.All
Reports.Read.All
Sites.FullControl.All
Domain.ReadWrite.All
```

用途说明：

- `Application.ReadWrite.All`：管理应用程序对象及新增密钥等功能。
- `Application.ReadWrite.OwnedBy`：管理当前应用拥有的应用程序对象和密钥。
- `Directory.ReadWrite.All`：目录对象的广泛读写权限，用于部分用户、目录、订阅和域相关操作。
- `RoleManagement.ReadWrite.Directory`：分配、撤销和管理 Microsoft Entra 目录角色。
- `User.ManageIdentities.All`：修改用户标识相关属性，例如用户主体名称和登录标识。
- `User.ReadWrite.All`：创建、读取、更新和删除用户。
- `Reports.Read.All`：读取 Microsoft 365 使用情况报告。
- `Sites.FullControl.All`：SharePoint 站点完全控制，用于站点检查相关功能。
- `Domain.ReadWrite.All`：读取、添加、验证和删除租户域名。

### 2.3 当前维护版本建议新增的权限

```text
User-PasswordProfile.ReadWrite.All
LicenseAssignment.ReadWrite.All
User.EnableDisableAccount.All
User.Read.All
```

用途说明：

- `User-PasswordProfile.ReadWrite.All`：更新其他用户的 `passwordProfile`，包括设置固定密码、设置随机密码，以及配置 `forceChangePasswordNextSignIn`。这是批量更改密码功能最关键的新权限。
- `LicenseAssignment.ReadWrite.All`：为用户添加或删除许可证，是 `assignLicense` 接口的最低特权应用程序权限。虽然 `Directory.ReadWrite.All` 和 `User.ReadWrite.All` 可能覆盖该操作，仍建议添加专用权限，便于后续按最小权限收敛。
- `User.EnableDisableAccount.All` 与 `User.Read.All`：用于更新用户的 `accountEnabled`，也就是批量启用或禁用账户。官方最低权限组合要求同时具备这两项。

### 2.4 推荐的完整权限清单

```text
Application.ReadWrite.All
Application.ReadWrite.OwnedBy
Directory.ReadWrite.All
RoleManagement.ReadWrite.Directory
User.ManageIdentities.All
User.ReadWrite.All
User.Read.All
User-PasswordProfile.ReadWrite.All
User.EnableDisableAccount.All
LicenseAssignment.ReadWrite.All
Reports.Read.All
Sites.FullControl.All
Domain.ReadWrite.All
```

### 2.5 密码修改的额外角色要求

仅添加 Microsoft Graph API 权限不一定足以修改所有用户的密码。

对于 app-only 场景，修改普通用户的 `passwordProfile` 时，调用应用的服务主体还需要合适的 Microsoft Entra 管理角色。建议使用满足实际需要的最低角色，例如“用户管理员”。如果要修改管理员账户的密码，通常需要更高角色，例如“特权身份验证管理员”。

不要为了绕过 `403 Insufficient privileges` 直接给应用分配全局管理员，应先检查：

1. 权限类型是否为 Application permissions。
2. 是否已完成管理员同意。
3. 新令牌中是否包含新增权限。
4. 服务主体是否被授予合适的 Microsoft Entra 角色。
5. 被修改用户是否为管理员、同步用户或联合身份用户。

联合身份用户不能通过相同方式修改 `passwordProfile`；同步自本地 Active Directory 的用户，部分属性应在本地授权源修改。

### 2.6 域名与许可证限制

- 更换 UPN 域名时，目标域必须已经在租户中验证。
- 用户主体名称一般采用 `alias@verified-domain` 格式。
- 向用户分配许可证前，用户必须具有有效的两字母 `usageLocation`。
- 添加或删除许可证使用 `POST /users/{id}/assignLicense`。
- 请求正文必须同时包含 `addLicenses` 和 `removeLicenses`，即使其中一项为空数组。
- 同一个 SKU 不应在同一次请求中同时添加和删除。

## 3. Docker 部署建议

### 3.1 当前镜像运行路径

当前 Docker 镜像使用：

```dockerfile
WORKDIR /app
COPY target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

应用默认 H2 配置为：

```properties
spring.datasource.url=jdbc:h2:./data/o365;DB_CLOSE_DELAY=-1
```

因为 `./data` 是相对路径，所以在当前镜像中会解析为：

```text
/app/data/o365.mv.db
```

### 3.2 推荐方案：固定数据库绝对路径

为了避免以后修改 `WORKDIR` 后再次找不到旧数据库，推荐继续把宿主机数据挂载到容器 `/data`，同时通过环境变量把 JDBC 地址固定为绝对路径。

推荐的 `docker-compose.yml`：

```yaml
services:
  mso365fix:
    image: ghcr.io/xiliangcun/mso365fix:v1.9.5-fixed
    container_name: mso365fix
    restart: unless-stopped

    ports:
      - "9527:9527"
      - "8443:8443"

    volumes:
      - "./data:/data"

    environment:
      SPRING_DATASOURCE_URL: "jdbc:h2:file:/data/o365;DB_CLOSE_DELAY=-1"
      TZ: "Asia/Shanghai"
```

路径对应关系：

```text
宿主机 ./data/o365.mv.db
          ↓
容器 /data/o365.mv.db
          ↓
jdbc:h2:file:/data/o365
```

这种方式不依赖容器工作目录，后续镜像即使把 `WORKDIR` 从 `/app` 改成其他路径，也不会影响数据库读取。

### 3.3 兼容当前相对路径的方案

如果不想设置 `SPRING_DATASOURCE_URL`，可以把挂载目标改成：

```yaml
volumes:
  - "./data:/app/data"
```

这会与当前默认配置 `jdbc:h2:./data/o365` 对应，但它依赖 `WORKDIR /app`，长期维护不如绝对路径稳定。

### 3.4 升级镜像前必须备份

在 `docker-compose.yml` 所在目录执行：

```bash
docker compose down
cp -a ./data ./data-backup-$(date +%Y%m%d-%H%M%S)
```

确认数据库文件存在：

```bash
ls -lh ./data/o365.mv.db
```

然后更新镜像并启动：

```bash
docker compose pull
docker compose up -d
```

查看日志：

```bash
docker compose logs -f --tail=200
```

不要在没有备份的情况下删除 `data` 目录，也不要把新容器生成的空数据库覆盖到旧数据库上。

### 3.5 检查实际挂载

```bash
docker inspect mso365fix \
  --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}'
```

使用推荐方案时，应看到类似：

```text
/opt/mso365fix/data -> /data
```

检查容器中的数据库：

```bash
docker exec mso365fix ls -lh /data
```

应当包含：

```text
o365.mv.db
```

### 3.6 拉取和运行 GHCR 镜像

公开镜像：

```bash
docker pull ghcr.io/xiliangcun/mso365fix:v1.9.5-fixed
```

私有镜像需要先登录：

```bash
echo "你的 GitHub Token" | docker login ghcr.io \
  -u xiliangcun \
  --password-stdin
```

Token 至少需要 `read:packages`。不要把 Token 写入 Dockerfile、Compose 文件或提交到 GitHub。

### 3.7 Docker 安全建议

- 使用固定版本标签，例如 `v1.9.5-fixed`，生产环境不要只使用 `latest`。
- 数据目录必须定期备份。
- 不要把 Client Secret、Token 或固定密码写入公开仓库。
- 对外只开放实际需要的端口。
- 如通过反向代理提供服务，建议启用 HTTPS。
- 限制 H2 控制台对外访问，生产环境保持 `spring.h2.console.enabled=false`。
- 对容器日志进行轮转，避免日志无限增长。
- 升级前先在测试环境验证数据库兼容性和批量用户操作。

可选的日志轮转配置：

```yaml
services:
  mso365fix:
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "5"
```

## 4. GitHub Actions、Release 与 Packages

项目包含：

```text
.github/workflows/ci.yml
.github/workflows/release.yml
```

CI 用于编译、测试和上传 JAR Artifact。正式发布应通过推送以 `v` 开头的标签触发，例如：

```text
v1.9.5-fixed
```

手动运行 `Release and Packages` 默认只进行构建验证和 Artifact 上传，不正式发布 Release、Maven Package 或 GHCR 镜像。

标签触发成功后将生成：

- GitHub Release
- JAR 和 SHA-256 附件
- GitHub Maven Package
- GHCR Docker 镜像

仓库设置中应启用：

```text
Settings
→ Actions
→ General
→ Workflow permissions
→ Read and write permissions
```

## 5. 使用与安全注意事项

- 批量管理功能会对多位用户执行真实目录变更，操作前应先小批量测试。
- 批量删除订阅可能立即影响 Exchange、OneDrive、SharePoint 等服务。
- 更换 UPN 可能影响用户登录、客户端缓存及部分服务地址。
- 随机密码只应在结果中短暂显示一次，并通过安全途径交付给用户。
- 不要在普通日志中输出密码、Client Secret、访问令牌或完整认证请求。
- 对管理员账号执行密码修改和禁用操作前，应确认存在可用的紧急访问账号。
- 对同步或联合用户，应先确认属性授权来源，避免云端修改失败或随后被本地同步覆盖。

## 6. 参考资料

- 原项目：https://github.com/vanyouseea/o365
- 当前维护项目：https://github.com/xiliangcun/mso365fix
- Microsoft Graph 更新用户：https://learn.microsoft.com/zh-cn/graph/api/user-update?view=graph-rest-1.0
- Microsoft Graph 分配许可证：https://learn.microsoft.com/zh-cn/graph/api/user-assignlicense?view=graph-rest-1.0
- Microsoft Graph 修改本人密码：https://learn.microsoft.com/en-us/graph/api/user-changepassword?view=graph-rest-1.0

## 7. 免责声明

本项目涉及 Microsoft 365 租户的用户、许可证、域名、应用凭据及管理员角色管理。请仅在你有权管理的租户中使用，并在批量操作前完成备份、权限审查和小范围验证。维护版本不代表原作者对新增功能、修改内容或部署结果承担责任。


