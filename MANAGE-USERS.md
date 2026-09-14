# 批量管理用户

入口：管理用户页面，勾选一位或多位用户后点击搜索按钮前的“管理用户”（双人图标）。

支持：更换已验证域、添加或删除订阅、固定密码、逐用户随机密码、按租户策略过期、永不过期、首次登录强制改密。

建议的 Microsoft Graph 应用程序权限：
- User.ReadWrite.All
- User-PasswordProfile.ReadWrite.All
- User.ManageIdentities.All（更换 UPN 域）
- LicenseAssignment.ReadWrite.All

权限需要管理员同意。同步自本地目录的用户可能无法在云端修改部分属性。随机密码会在批量结果中返回一次，请及时安全保存。
