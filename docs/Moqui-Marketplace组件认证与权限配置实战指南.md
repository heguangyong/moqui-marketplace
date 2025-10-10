# Moqui认证与权限配置速查手册

## 🔐 核心发现：认证属性差异

```xml
<!-- ✅ 服务: authenticate -->
<service authenticate="false" allow-remote="true">

<!-- ✅ 屏幕: require-authentication -->
<screen require-authentication="false">
```

**错误根因**: 混用属性导致 "User must be logged in to call service"

## 一、权限配置三步法

### 1. 服务权限组
```xml
<!-- data/SecurityData.xml -->
<moqui.security.ArtifactGroup artifactGroupId="APP_SERVICES"/>
<moqui.security.ArtifactGroupMember artifactGroupId="APP_SERVICES"
        artifactTypeEnumId="AT_SERVICE" artifactName="namespace.*"/>
<moqui.security.ArtifactAuthz userGroupId="_NA_" artifactGroupId="APP_SERVICES"
        authzTypeEnumId="AUTHZT_ALLOW" authzActionEnumId="AUTHZA_ALL"/>
```

### 2. 实体权限组
```xml
<moqui.security.ArtifactGroup artifactGroupId="APP_ENTITIES"/>
<moqui.security.ArtifactGroupMember artifactGroupId="APP_ENTITIES"
        artifactTypeEnumId="AT_ENTITY" artifactName="namespace.*"/>
<moqui.security.ArtifactAuthz userGroupId="_NA_" artifactGroupId="APP_ENTITIES"
        authzTypeEnumId="AUTHZT_ALLOW" authzActionEnumId="AUTHZA_ALL"/>
```

### 3. 视图实体单独授权
```xml
<!-- 针对view-entity需要单独配置 -->
<moqui.security.ArtifactAuthz userGroupId="_NA_"
        artifactTypeEnumId="AT_ENTITY" artifactName="namespace.ViewEntity"
        authzTypeEnumId="AUTHZT_ALLOW" authzActionEnumId="AUTHZA_ALL"/>
```

## 二、实体操作绕过技术

```groovy
// ✅ 查询时必须使用
def results = ec.entity.find("namespace.Entity")
    .condition("field", value)
    .disableAuthz()  // 关键
    .list()

// ✅ 创建时一般不需要（makeValue自带绕过）
ec.entity.makeValue("namespace.Entity")
    .setFields([...])
    .createOrUpdate()
```

## 三、模板错误修复

### FormConfigUser错误 → HTML表格
```xml
<!-- ❌ 权限错误 -->
<form-list name="List" list="items"/>

<!-- ✅ 替换方案 -->
<container style="table table-striped">
    <section-iterate name="Iterate" list="items" entry="item">
        <widgets>
            <container style="tr">
                <container style="td">${item.field ?: '暂无'}</container>
            </container>
        </widgets>
    </section-iterate>
</container>
```

## 四、错误诊断速查

| 错误信息 | 解决方案 |
|---------|----------|
| User must be logged in to call service | 服务改用 `authenticate="false"` |
| User [No User] is not authorized for View on Entity | 添加实体匿名权限 + `.disableAuthz()` |
| FormConfigUser 权限错误 | form-list 改为 HTML表格 |

## 五、标准模板

### 无认证服务
```xml
<service verb="action" noun="Entity" authenticate="false">
    <actions>
        <script><![CDATA[
            try {
                def result = ec.entity.find("namespace.Entity")
                    .disableAuthz().list()
                success = true
            } catch (Exception e) {
                success = false
                message = e.message
            }
        ]]></script>
    </actions>
</service>
```

### 智能匹配评分算法
```groovy
def calculateMatchScore(supply, demand) {
    def score = 0.0
    // 名称匹配40% + 类别匹配30% + 价格匹配20% + 数量匹配10%
    if (supply.name?.toLowerCase()?.contains(demand.name?.toLowerCase())) score += 0.4
    if (supply.category == demand.category) score += 0.3
    if (supply.price <= demand.budget) score += 0.2
    if (supply.quantity >= demand.quantity) score += 0.1
    return Math.max(0.0, Math.min(1.0, score))
}
```

---
**更新**: 2025-10-01 | **适用**: 认证权限、模板错误、智能匹配