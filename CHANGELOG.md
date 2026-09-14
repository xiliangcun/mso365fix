# Changelog

## 1.9.4-fixed

- 加固 CI 与 Release 工作流，增加超时、并发控制、JAR 断言和手动触发保护。
- Release 手动触发只构建并上传 Artifact，只有 v* 标签才发布 Packages、Release 和 GHCR。
- Docker 构建复用 Maven 已验证的 JAR，避免容器内重复构建产生不一致。
- 明确 UTF-8 编译编码。

## 1.9.3-fixed

- 修复 MassCreateOfficeUser 中已删除 StringBuilder 后仍引用 sb 导致的编译错误。
- UsernameGenerator 移到批次循环外，确保批次内去重真正生效。
- GitHub Actions checkout/setup-java 更新到 v5。

## 1.9.2-fixed

- 批量创建允许空前缀。
- 新增 a-z 随机字母策略和1到48位长度设置。
- 随创建数量自动选择更安全的默认随机长度，并进行批次内去重。
- 新增安全正则生成策略，支持字符集、范围、\d、\w、{n} 与 {n,m}。
- 增加数量、长度、本地用户名和正则校验及生成器模拟测试。

## 1.9.1-fixed

- 修复创建用户后立即按 UPN 分配许可证导致的偶发 404。
- 改为使用创建用户响应中的 Microsoft Graph 对象 ID。
- 对 404、408、429、500、502、503、504 增加指数退避重试。
- 支持 Retry-After，校验空 userId、空 skuId 和非法 GUID。
- 批量任务逐用户、逐 SKU 记录成功与失败，避免一次失败影响整个批次。
- 增加 CI、GitHub Release、Maven Packages 和 GHCR 发布流程。
