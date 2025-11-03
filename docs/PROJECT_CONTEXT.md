# Moqui-Marketplace 项目上下文文档

**日期**: 2025-09-30
**项目状态**: Phase 1 完成 - 核心组件已创建
**下一步**: 验证和MCP集成

---

## 📋 项目背景

### 核心定位
**AI Agent驱动的社区商业撮合平台**

用户（商家和消费者）通过Rocket.Chat与AI Agent对话，完成供需发布、智能匹配、订单记录等所有操作。类似于"用户和Claude的对话模式"，AI理解意图并调用Moqui后端服务。

### 目标场景
- **地理范围**: 1-2公里社区范围
- **目标用户**: 社区内线下实体店（菜市场商家、肉铺、水果店等）
- **核心品类**: 生鲜食材（蔬菜、肉类、水果、海鲜）
- **商业模式**:
  - 平台只做智能撮合，不涉及支付
  - 通过标签匹配供需关系
  - AI推荐后，买卖双方自行联系交易
  - 记录订单用于用户画像和粘性提升

### 关键特点
1. **零学习成本**: 像聊天一样完成所有操作
2. **智能撮合**: AI基于多维度（标签、地理、价格、用户画像）推荐
3. **轻量运营**: 不涉及支付和物流，降低平台风险
4. **数据驱动**: 用户画像越来越精准，推荐质量持续提升

---

## 🧭 2025-11 战略更新：B 端闭环体验

### 目标用户聚焦
- **客群**: 社区内的小微企业主（菜厂、农贸、餐饮、小型加工企业）
- **核心需求**: 让老板在一个受监管的闭环中掌握每一次供需匹配的进展
- **管控方式**: 将每个撮合流程映射为 HiveMind 项目，确保可追踪、可审计

### MVP 渠道策略
- **首个对话入口**: Telegram 机器人，先做最小可用“供需撮合”闭环
- **待支持能力**:
  - 文字消息：中文/英文双语
  - 语音识别：接入现有语音转写（中英文自动识别）
  - 图片识别：读取货品图片获取品类、标签信息
- **后续拓展**: 在 MVP 稳定后复用同一套交互编排，扩展到 Rocket.Chat、WeChat、Twitter 等渠道

### HiveMind 项目化管理
- **意图**: 把“沟通 → 撮合 → 跟进”的每个节点写入 HiveMind，形成真正的项目流水线
- **数据落库**: AI 会话元数据、客户需求、撮合状态同步存放到 HiveMind 数据模型
- **能力演进**: 逐步增加里程碑、责任人、提醒机制，支撑更复杂的 B 端项目协同

---

## 🏗️ 技术架构

### 整体架构图

```
┌─────────────────────────────────────────────────┐
│         用户交互层 (User Interface)              │
│  Rocket.Chat (商家/消费者通过聊天完成所有操作)   │
└────────────────┬────────────────────────────────┘
                 │ 自然语言消息
┌────────────────▼────────────────────────────────┐
│         AI Agent层 (Intelligence Layer)          │
│  ┌──────────────────────────────────────────┐   │
│  │ MCP Server (Model Context Protocol)      │   │
│  │  - Tools: 所有可调用的Moqui服务          │   │
│  │  - Resources: 业务数据访问               │   │
│  │  - Prompts: 领域专用提示词模板           │   │
│  └──────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────┐   │
│  │ Agent Orchestrator (代理编排器)          │   │
│  │  - 意图识别 (Intent Recognition)         │   │
│  │  - 上下文管理 (Context Management)       │   │
│  │  - 工具调用 (Tool Calling)               │   │
│  │  - 响应生成 (Response Generation)        │   │
│  └──────────────────────────────────────────┘   │
└────────────────┬────────────────────────────────┘
                 │ Moqui Service调用
┌────────────────▼────────────────────────────────┐
│      业务能力层 (Business Capability Layer)      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐        │
│  │ Marketplace│ │ HiveMind │ │  MinIO   │        │
│  │ 供需撮合   │ │ 内容管理 │ │ 图片存储 │        │
│  └──────────┘ └──────────┘ └──────────┘        │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│         数据层 (Data Layer)                      │
│  Moqui Framework + Entity Engine                │
└─────────────────────────────────────────────────┘
```

### 技术栈

**后端框架**:
- Moqui Framework (Java 21)
- Gradle 8.10
- PostgreSQL (数据存储)

**AI层**:
- MCP (Model Context Protocol) - AI与应用集成标准协议
- Claude 3.5 Sonnet API 或 本地Ollama (Qwen2.5:14B)
- Node.js (MCP Server实现)

**通讯层**:
- Rocket.Chat (对话入口)
- Rocket.Chat Bot + Outgoing Webhook
- RocketChat-MCP Bridge (Node.js)

**存储**:
- MinIO (商品图片存储)
- Moqui Entity Engine (业务数据)

---

## 📦 已完成的工作

### 1. moqui-marketplace 组件结构

**位置**: `E:\moqui-framework\runtime\component\moqui-marketplace\`

```
moqui-marketplace/
├── component.xml                 ✅ 组件定义
├── build.gradle                  ✅ Gradle构建配置
├── MoquiConf.xml                 ✅ 配置参数
├── README.md                     ✅ 项目说明文档
│
├── entity/
│   └── MarketplaceEntities.xml   ✅ 完整数据模型（8个实体）
│
├── service/
│   └── MarketplaceServices.xml   ✅ 核心服务定义（20+服务）
│
├── data/
│   ├── MarketplaceSeedData.xml   ✅ 标签体系种子数据
│   └── MarketplaceDemoData.xml   ✅ 示例数据（商家/消费者/供需）
│
├── docs/
│   └── MCP_INTEGRATION_GUIDE.md  ✅ 完整MCP集成指南
│
└── src/main/java/org/moqui/marketplace/
    └── matching/
        └── SmartMatchingEngine.java  ✅ 智能匹配算法实现
```

### 2. 数据模型详解

#### **Listing** (供需信息主表)
```xml
字段:
- listingType: SUPPLY(供应) / DEMAND(需求)
- publisherId: 发布者ID
- publisherType: MERCHANT(商家) / CUSTOMER(消费者)
- title, description: 标题和描述
- category, subCategory: 品类分类
- quantity, quantityUnit: 数量和单位
- priceMin, priceMax: 价格区间
- locationDesc, geoPointId: 位置信息
- deliveryRange: 配送范围(km)
- validFrom, validThru: 有效期
- status: ACTIVE/MATCHED/EXPIRED/CANCELLED
- freshnessScore: 新鲜度评分(0-1)
- imageUrls: 商品图片(JSON)
```

#### **Tag** (标签体系)
```
品类标签 (CATEGORY):
├── 蔬菜 (CAT_VEGETABLE)
│   ├── 叶菜类 (CAT_VEG_LEAF)
│   ├── 根茎类 (CAT_VEG_ROOT)
│   ├── 瓜果类 (CAT_VEG_FRUIT)
│   └── 豆类 (CAT_VEG_BEAN)
├── 肉类 (CAT_MEAT)
│   ├── 猪肉 (CAT_MEAT_PORK)
│   ├── 牛肉 (CAT_MEAT_BEEF)
│   ├── 羊肉 (CAT_MEAT_MUTTON)
│   └── 禽类 (CAT_MEAT_POULTRY)
├── 水果 (CAT_FRUIT)
└── 海鲜 (CAT_SEAFOOD)

属性标签 (ATTRIBUTE):
- 有机 (ATTR_ORGANIC)
- 绿色 (ATTR_GREEN)
- 新鲜 (ATTR_FRESH)
- 批发 (ATTR_WHOLESALE)
- 零售 (ATTR_RETAIL)
- 可配送 (DELIVERY_YES)
- 自提 (DELIVERY_PICKUP)

时效标签 (TIMING):
- 当日 (TIME_TODAY)
- 次日 (TIME_TOMORROW)
- 预订 (TIME_PREORDER)
- 长期供应 (TIME_LONGTERM)
```

#### **Match** (撮合记录)
```xml
字段:
- supplyListingId: 供应信息ID
- demandListingId: 需求信息ID
- matchScore: 综合匹配分数(0-1)
- tagSimilarity: 标签相似度分数
- geoProximity: 地理接近度分数
- priceMatch: 价格匹配度分数
- freshnessScore: 时效性分数
- preferenceScore: 用户偏好分数
- matchReason: AI生成的匹配理由(文本)
- status: SUGGESTED/VIEWED/CONTACTED/COMPLETED/REJECTED
- [各状态对应的时间戳]
```

#### **UserProfile** (用户画像)
```xml
商家画像:
- supplyCapacity: 供应能力 (LOW/MEDIUM/HIGH)
- mainCategories: 主营品类(JSON)
- supplyFrequency: 平均供应频次(次/周)
- avgSupplyQuantity: 平均供应数量

客户画像:
- purchaseFrequency: 购买频次(次/月)
- preferredCategories: 偏好品类(JSON)
- priceSensitivity: 价格敏感度(0-1)
- avgPurchaseQuantity: 平均购买数量

通用画像:
- creditScore: 信用评分(0-1)
- responseRate: 响应率(0-1)
- matchSuccessRate: 撮合成功率(0-1)
- tagPreferences: 标签偏好权重(JSON)
- totalListings, totalMatches, totalOrders: 统计数据
```

#### **MatchOrder** (订单记录)
```xml
字段:
- matchId: 关联的撮合记录
- sellerId, buyerId: 买卖双方
- productName, quantity, agreedPrice: 交易内容
- deliveryMethod: DELIVERY(配送) / PICKUP(自提)
- status: CONFIRMED/DELIVERING/COMPLETED/CANCELLED
- sellerRating, buyerRating: 双向评分(1-5星)
- sellerComment, buyerComment: 评价内容
```

#### **UserBehavior** (行为记录)
用于用户画像分析和推荐算法优化
```xml
- behaviorType: PUBLISH/VIEW/CONTACT/ORDER/RATE/SEARCH
- targetType: LISTING/MATCH/ORDER
- behaviorData: 行为详细数据(JSON)
- sessionId: 关联的对话会话
```

### 3. 核心服务接口

#### 供需发布
```xml
<service verb="create" noun="Listing">
  入参: listingType, publisherId, title, category, quantity, price...
  出参: listingId, matchedCount (立即匹配数)
</service>

<service verb="search" noun="Listings">
  入参: listingType, category, tagIds, geoPointId, maxDistance...
  出参: listings (列表), totalCount
</service>
```

#### 智能匹配
```xml
<service verb="find" noun="Matches">
  入参: listingId, maxResults, minScore, autoNotify
  出参: matches (列表，含分数和理由)
</service>

<service verb="calculate" noun="MatchScore">
  入参: supplyListingId, demandListingId
  出参: matchScore, tagSimilarity, geoProximity, priceMatch...
</service>

<service verb="confirm" noun="Match">
  入参: matchId, confirmingPartyId
  出参: contactInfo (对方联系信息), notified
</service>
```

#### 订单记录
```xml
<service verb="create" noun="MatchOrder">
  入参: matchId, sellerId, buyerId, quantity, agreedPrice...
  出参: orderId
</service>

<service verb="rate" noun="MatchOrder">
  入参: orderId, raterPartyId, raterType, rating, comment
  作用: 评价订单并更新对方信用分
</service>
```

#### 用户画像
```xml
<service verb="get" noun="UserProfile">
  入参: partyId
  出参: profile, tagPreferences
</service>

<service verb="record" noun="UserBehavior">
  入参: partyId, behaviorType, targetId, behaviorData
  作用: 记录行为，异步更新用户画像
</service>

<service verb="rebuild" noun="UserProfile">
  入参: partyId
  作用: 基于历史行为重建用户画像
</service>
```

### 4. 智能匹配算法

**SmartMatchingEngine.java** - 核心匹配引擎

#### 匹配分数计算公式
```java
totalScore =
  tagSimilarity * 0.35      // 标签相似度 35%
  + geoProximity * 0.25     // 地理接近度 25%
  + priceMatch * 0.15       // 价格匹配度 15%
  + freshnessScore * 0.10   // 时效性 10%
  + preferenceScore * 0.15  // 用户偏好 15%
```

#### 各维度算法

**1. 标签相似度** (Jaccard相似度)
```java
similarity = |交集| / |并集|
例: 供应有标签[猪肉, 新鲜, 可配送]
    需求有标签[猪肉, 新鲜, 当日]
    交集={猪肉, 新鲜}, 并集={猪肉, 新鲜, 可配送, 当日}
    相似度 = 2/4 = 0.5
```

**2. 地理接近度** (Haversine公式 + 距离衰减)
```java
distance = Haversine(lat1, lon1, lat2, lon2)

if (distance <= deliveryRange) {
  proximity = 1.0 - (distance / deliveryRange) * 0.5
  // 范围内线性递减，最多降低50%
} else {
  proximity = 0.5 * exp(-(distance - deliveryRange) / deliveryRange)
  // 范围外指数衰减
}
```

**3. 价格匹配度** (价格差异指数衰减)
```java
avgPrice1 = (priceMin1 + priceMax1) / 2
avgPrice2 = (priceMin2 + priceMax2) / 2
diffPercent = |avgPrice1 - avgPrice2| / ((avgPrice1 + avgPrice2) / 2)
priceMatch = exp(-diffPercent * 2)
```

**4. 时效性评分** (新鲜度随时间衰减)
```java
avgAge = (age1 + age2) / 2  // 小时

if (avgAge <= 48小时) {
  freshness = 1.0 - (avgAge / 48) * 0.3  // 最多降低30%
} else {
  freshness = 0.7 * exp(-(avgAge - 48) / 48)
}
```

**5. 用户偏好评分** (基于用户画像)
```java
score = 0.5  // 基础分

// 品类偏好 +0.4
if (preferredCategories.contains(category)) {
  score += 0.2
}

// 信用评分影响 +0.1
score += avgCreditScore * 0.1

return min(score, 1.0)
```

#### 匹配理由生成
```java
public String generateMatchReason(...) {
  StringBuilder reason = new StringBuilder();

  if (tagSimilarity >= 0.7)
    reason.append("商品品类高度匹配；");

  if (geoProximity >= 0.8)
    reason.append("距离很近，配送方便；");

  if (priceMatch >= 0.8)
    reason.append("价格非常合适；");

  if (creditScore >= 0.8)
    reason.append("商家信用良好；");

  if (totalOrders > 5)
    reason.append("已完成" + totalOrders + "笔交易");

  return reason.toString();
}
```

### 5. 示例数据

**MarketplaceDemoData.xml** 已包含：

**商家**:
- MERCHANT_001: 刘师傅肉铺 (主营猪肉、牛肉，信用0.92)
- MERCHANT_002: 陈大哥菜园 (主营蔬菜，信用0.90)

**消费者**:
- CUSTOMER_001: 张女士 (家庭采购，购买频次8次/月)
- CUSTOMER_002: 李记餐厅 (餐厅采购，购买频次20次/月)

**示例供应**:
- SUPPLY_001: 新鲜排骨 30斤，25-28元/斤，3km配送

**示例需求**:
- DEMAND_001: 需要新鲜排骨 3斤，20-30元/斤

**地理位置**: 常平镇范围内（经纬度已设置）

---

## 🚀 用户交互场景示例

### 场景1: 商家发布供应

```
👨‍💼 商家: 我今天有20斤新鲜菠菜想卖

🤖 AI Agent:
好的，我帮您发布供应信息。请问：
1. 价格大概是多少？
2. 配送范围多远？
3. 需要自提还是可以配送？

👨‍💼 商家: 3-5块一斤，3公里内可以送，也可以自提

🤖 AI Agent: [调用 marketplace.create#Listing]
✅ 已为您发布供应信息：
   - 品类：蔬菜 - 叶菜类 - 菠菜
   - 数量：20斤
   - 价格：3-5元/斤
   - 配送：3公里内配送 + 自提

我会帮您匹配有需求的客户，有意向的买家我会立即通知您！

[10分钟后，系统自动推送]
🤖 AI Agent:
📢 找到2个匹配的买家：

1️⃣ 张女士（社区团购）
   - 需要：15斤菠菜
   - 距离：1.2公里
   - 信用：⭐⭐⭐⭐⭐
   - 匹配度：92分
   - 推荐理由：她经常采购叶菜，价格敏感度低，过去成交5次

   [授权联系] [查看详情] [暂不考虑]

2️⃣ 李先生（餐厅采购）
   - 需要：蔬菜类每日采购
   - 距离：2.5公里
   - 信用：⭐⭐⭐⭐
   - 匹配度：85分
   - 推荐理由：餐厅日常需求，但价格敏感

   [授权联系] [查看详情] [暂不考虑]

👨‍💼 商家: [点击"授权联系"按钮1]

🤖 AI Agent: [调用 marketplace.confirm#Match]
✅ 已将您的联系方式发送给张女士，同时也把她的联系信息发给您：
- 微信：zhang_xxxx
- 电话：138****1234

您可以直接联系她洽谈细节。如果交易成功，记得告诉我哦！
```

### 场景2: 客户发布需求

```
👩 客户: 我想买点新鲜的排骨

🤖 AI Agent:
好的！请问：
1. 大概需要多少斤？
2. 今天就要还是明天？
3. 对价格有要求吗？

👩 客户: 2-3斤，今天下午要，价格不要太贵

🤖 AI Agent: [调用 marketplace.create#Listing + find#Matches]

明白了！已为您寻找附近的商家，找到3家有货：

1️⃣ 刘师傅肉铺（推荐⭐）
   - 距离：500米
   - 价格：28元/斤
   - 库存：充足
   - 信用：⭐⭐⭐⭐⭐（完成订单52次）
   - 匹配度：95分
   - 特点：本地养殖，当天新鲜

   [联系商家] [查看图片] [看评价]

2️⃣ 旺旺生鲜超市
   - 距离：1.2公里
   - 价格：25元/斤
   - 库存：有货
   - 信用：⭐⭐⭐⭐
   - 匹配度：88分

   [联系商家] [查看图片]

3️⃣ 社区菜市场-王老板
   - 距离：800米
   - 价格：30元/斤
   - 库存：少量
   - 匹配度：82分

   [联系商家]

👩 客户: 第一家

🤖 AI Agent: [调用 marketplace.confirm#Match]
✅ 已为您联系刘师傅肉铺，这是他的联系方式：
- 电话：139****5678
- 店铺位置：[地图链接]

刘师傅也收到了您的联系方式，他可能会直接联系您。

💡 小提示：交易完成后记得评价，这样我能更好地为您推荐！
```

### 场景3: 智能推荐（主动推送）

```
🤖 AI Agent: [基于用户画像主动推送]
👩 李女士，早上好！

根据您的购买习惯，今天有几条信息可能您感兴趣：

🥬 **周末特供蔬菜包**
陈大哥菜园发布了新鲜蔬菜组合：
- 包含：菠菜、生菜、西红柿、黄瓜
- 价格：38元/份（比平时便宜15%）
- 配送：今天下午4点前
- 匹配理由：您过去买过陈大哥的菜3次，都很满意

[我要] [看看] [不需要]

---

📢 **您关注的排骨降价了**
刘师傅肉铺今天排骨特价：
- 价格：25元/斤（原价28元）
- 理由：今天进货多了
- 您上次问过排骨价格，今天正好降价

[感兴趣] [提醒我明天]

---

💡 基于您的购买记录，我发现您每周五会买菜做周末大餐。
需要我每周四晚上给您推荐当天新鲜特价商品吗？

[好的，请推荐] [不用了]
```

---

## 📝 MCP集成方案

详细集成指南见: `docs/MCP_INTEGRATION_GUIDE.md`

### MCP Tools清单

```typescript
// 已定义的Tools
[
  "marketplace_create_supply",      // 发布供应
  "marketplace_create_demand",      // 发布需求
  "marketplace_search_listings",    // 搜索供需
  "marketplace_find_matches",       // 查找匹配
  "marketplace_confirm_match",      // 确认撮合（授权联系）
  "marketplace_create_order",       // 记录订单
  "marketplace_rate_order",         // 评价订单
  "marketplace_get_profile",        // 获取用户画像
  "marketplace_upload_image",       // 上传商品图片(MinIO)
]
```

### MCP Resources

```typescript
// 可访问的资源
[
  "marketplace://listings/{listingId}",      // 供需详情
  "marketplace://matches/{listingId}",       // 匹配列表
  "marketplace://profiles/{partyId}",        // 用户画像
  "marketplace://orders/{orderId}",          // 订单详情
]
```

### MCP Prompts

```typescript
// 领域专用提示词模板
[
  "merchant_onboarding",         // 商家首次使用引导
  "customer_onboarding",         // 消费者首次使用引导
  "generate_match_reason",       // 生成匹配推荐理由
  "extract_tags_from_text",      // 从描述中提取标签
  "optimize_listing_title",      // 优化供需信息标题
]
```

---

## ⚙️ 配置参数

**MoquiConf.xml** 中的关键配置：

```xml
<!-- 匹配算法参数 -->
<default-property name="marketplace.matching.min.score" value="0.6"/>
<default-property name="marketplace.matching.max.results" value="10"/>
<default-property name="marketplace.geo.max.distance" value="5.0"/>
<default-property name="marketplace.listing.expire.hours" value="48"/>

<!-- AI配置 -->
<default-property name="marketplace.ai.provider" value="CLAUDE"/>
<default-property name="marketplace.ai.model" value="claude-3-5-sonnet-20241022"/>
<default-property name="marketplace.ai.enable.match.reason" value="true"/>

<!-- Rocket.Chat配置 -->
<default-property name="rocketchat.server.url" value="${ROCKETCHAT_URL:http://localhost:3000}"/>
<default-property name="rocketchat.bot.username" value="${ROCKETCHAT_BOT_USER:marketplace-bot}"/>

<!-- MCP Server配置 -->
<default-property name="mcp.server.host" value="${MCP_SERVER_HOST:localhost}"/>
<default-property name="mcp.server.port" value="${MCP_SERVER_PORT:3001}"/>
```

---

## 🎯 下一步行动计划

### Sprint 0：Telegram MVP 闭环（当前迭代焦点）

1. **后端稳定性自检**
   - 确认 `MarketplaceServices`/`MatchingServices` 等核心接口可通过 REST 调用
   - 利用 `MarketplaceDemoData.xml` 导入基础样例，验证 `find#Matches`、`create#Listing`、`confirm#Match` 正常返回

2. **Telegram 机器人对接**
   - 复用既有 `testing-tools/test_multimodal_telegram.sh` 流程，梳理命令 → 服务映射
   - 实现关键指令/意图：
     - `/supply` 发布供应 → `marketplace.SupplyDemandServices.create#SupplyListing`
     - `/demand` 发布需求 → `marketplace.SupplyDemandServices.create#DemandListing`
     - `/match {listingId}` 查找匹配 → `marketplace.MarketplaceServices.find#Matches`
     - `/confirm {matchId}` 确认撮合 → `marketplace.MarketplaceServices.confirm#Match`
   - 设计失败提示与重试机制，确保老板能理解每一步状态

3. **多模态输入兜底方案**
   - **语音识别**: 调用 `Speech_To_Text_Integration_Report.md` 中的识别链路，将转写结果回填到对话
   - **图像识别**: 使用 `Image_Recognition_Integration_Report.md` 中的识别流程，为图片打标签并提示可发布供应
   - 在机器人消息中标注“系统自动识别结果 + 人工确认指引”，保持透明度

4. **HiveMind 项目同步（基础版）**
   - 每次供需撮合在 Telegram 内形成闭环时，调用 HiveMind API 创建/更新项目节点：
     - `阶段`：沟通中 / 匹配中 / 已确认 / 已完成
     - `关键数据`：listingId、matchId、联系人、图片/语音附件链接
   - 先使用占位字段写入，自定义字段映射待后续细化

### Sprint 1：体验提升 & 数据固化

1. **对话模版化**
   - 梳理 B 端老板常见问法，沉淀成意图 + 引导模版（中文为主，英文备用）
   - 引入“项目化提示”，让 AI 主动告知“已为您开一个 HiveMind 项目跟踪此次撮合”

2. **多模态自动触发**
   - 语音/图片上传后自动解释并建议下一步操作，比如自动生成 supply 草稿
   - 对无法识别的情况输出 fallback：请求用户输入文本补充

3. **HiveMind 数据模型映射**
   - 定义 HiveMind 中的项目结构：阶段、负责人、动作日志、所需文件
   - 将 Moqui 内部实体（Listing/Match/Order）与 HiveMind 项目标记联动，确保双向追踪

### 后续通道拓展（Rocket.Chat / WeChat / Twitter）

1. **抽象对话编排层**
   - 将 Telegram 中的命令解析、上下文存储、错误处理封装为通用模块
   - 未来各渠道只需实现适配器，将消息转换为统一意图格式

2. **渠道特性适配**
   - Rocket.Chat：支持企业内多成员协同
   - WeChat：侧重语音、图片输入，适配小程序或企业微信
   - Twitter：面向公开运营，可能只暴露部分咨询/订阅功能

3. **监管合规与审计**
   - 随着渠道扩展，强化 HiveMind 审计：记录渠道来源、对话摘要、关键决策节点
   - 为 B 端企业提供“项目汇总报表”及“历史撮合归档”

---

## 🔑 关键决策点

需要明确以下选择：

### 1. AI模型选择

**选项A: Claude API（推荐）**
- ✅ 对话能力最强
- ✅ 工具调用精准
- ✅ 多轮上下文管理好
- ❌ 费用：约$3/百万输入token
- 建议：商用首选

**选项B: 本地Ollama**
- ✅ 完全免费
- ✅ 数据隐私
- ✅ 离线可用
- ❌ 效果一般（需要14B+模型）
- ❌ 需要GPU（至少8GB显存）
- 建议：原型验证或成本敏感场景

**选项C: 混合方案（平衡）**
- 简单任务（意图识别、标签提取）→ Ollama
- 复杂推理（匹配理由生成、对话）→ Claude API
- 建议：生产环境推荐

### 2. Rocket.Chat部署

**选项A: Docker本地部署（推荐开发）**
- 快速启动
- 完全控制
- 适合开发测试

**选项B: Rocket.Chat Cloud托管**
- 无需维护
- 自动扩容
- 适合快速上线

**选项C: 自建生产集群**
- 高可用
- 数据隐私
- 适合大规模部署

### 3. 试点范围

**需要确认**:
- 具体社区位置：______
- 商家数量：约 ____ 家
- 商家类型：菜市场 / 肉铺 / 水果店 / 超市
- 预计消费者：约 ____ 人
- 试点时长：____ 周

---

## 📚 相关文档

**项目文档**:
- `README.md` - 组件概述
- `docs/MCP_INTEGRATION_GUIDE.md` - MCP完整集成指南

**Moqui官方文档**:
- https://www.moqui.org/docs/framework
- https://www.moqui.org/m/docs/mantle

**MCP协议**:
- https://modelcontextprotocol.io/
- https://github.com/modelcontextprotocol/specification

**Rocket.Chat**:
- https://docs.rocket.chat/
- https://developer.rocket.chat/

**Claude API**:
- https://docs.anthropic.com/
- https://console.anthropic.com/

---

## 🐛 已知问题和注意事项

### 需要补充的代码

1. **REST API定义** (`service/marketplace.rest.xml`)
   - 目前只有Service定义，未暴露REST接口
   - 需要定义URL路径和HTTP方法映射

2. **Groovy服务实现** (`src/main/groovy/`)
   - Service XML中引用了Groovy实现
   - 需要实现业务逻辑调用Java匹配引擎

3. **AI集成代码**
   - 标签自动提取
   - 匹配理由生成
   - 需要调用AI API

4. **通知发送实现**
   - Rocket.Chat消息推送
   - 短信/邮件通知（可选）

### 环境依赖

**必需**:
- ✅ Java 21
- ✅ Gradle 8.10
- ✅ PostgreSQL (或其他数据库)
- ✅ Moqui Framework (已升级)

**MCP/AI功能需要**:
- Node.js 18+ (MCP Server)
- Rocket.Chat服务器
- Claude API Key 或 本地Ollama

**图片存储需要**:
- MinIO组件 (已在runtime/component/moqui-minio)

---

## 💬 沟通记录要点

### 项目演进历程

1. **初始想法**: 社区电商撮合平台
2. **核心定位**: AI Agent驱动，去中心化撮合
3. **技术选型**: Moqui + MCP + Rocket.Chat + Claude
4. **已完成**: 数据模型、服务定义、匹配算法、集成指南

### 设计亮点

1. **零学习成本**: 像聊天一样完成所有操作
2. **智能匹配**: 多维度算法 + AI理由生成
3. **用户画像**: 越用越懂用户，推荐越精准
4. **轻量运营**: 只撮合不涉及支付，降低风险
5. **技术前瞻**: MCP协议让AI能力可插拔

### 待讨论问题

1. AI模型选择（Claude API vs Ollama）
2. 试点社区和商家范围
3. 是否先做REST API验证，再做AI Agent
4. 部署架构（本地 vs 云端）

---

## 📞 联系方式

**项目位置**: `E:\moqui-framework\runtime\component\moqui-marketplace\`

**GitHub**: https://github.com/heguangyong/moqui-framework

**关键文件**:
- 数据模型: `entity/MarketplaceEntities.xml`
- 服务定义: `service/MarketplaceServices.xml`
- 匹配算法: `src/main/java/.../SmartMatchingEngine.java`
- 集成指南: `docs/MCP_INTEGRATION_GUIDE.md`

---

## ✅ 检查清单

**已完成**:
- [x] 组件结构创建
- [x] 数据模型设计（8个实体）
- [x] 服务接口定义（20+服务）
- [x] 智能匹配算法实现
- [x] 种子数据和示例数据
- [x] MCP集成指南文档
- [x] 配置参数定义

**待完成**:
- [ ] REST API定义文件
- [ ] Groovy服务实现
- [ ] AI标签提取实现
- [ ] AI匹配理由生成
- [ ] Rocket.Chat通知实现
- [ ] MCP Server开发
- [ ] RocketChat Bridge开发
- [ ] 端到端测试
- [ ] 管理后台Screen（可选）

**验证清单**:
- [ ] Moqui启动成功
- [ ] 数据表创建成功
- [ ] 示例数据加载成功
- [ ] 匹配算法运行正常
- [ ] REST API可调用
- [ ] Rocket.Chat部署成功
- [ ] MCP Server运行成功
- [ ] 端到端对话流程跑通

---

**文档版本**: v1.0
**创建日期**: 2025-09-30
**最后更新**: 2025-09-30

---

## 💡 快速开始命令

```bash
# 1. 进入项目目录
cd E:\moqui-framework

# 2. 构建marketplace组件
./gradlew :runtime:component:moqui-marketplace:classes

# 3. 启动Moqui
./gradlew run

# 4. 加载初始数据
./gradlew load -Ptypes=seed,demo

# 5. 访问管理界面
# 浏览器打开: http://localhost:8080
# 登录: john.doe / moqui

# 6. 测试匹配服务
# Tools → Service → Run Service
# 服务名: marketplace.MarketplaceServices.find#Matches
# 参数: {"listingId": "SUPPLY_001"}
```

---

**祝项目顺利！有任何问题随时沟通。** 🚀
