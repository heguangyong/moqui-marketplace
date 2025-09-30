# Moqui Marketplace Component

## 概述

AI Agent驱动的社区商业撮合平台，通过Rocket.Chat提供对话式交互，智能匹配社区内的供需关系。

## 核心功能

### 1. 供需发布管理
- 商家发布供应信息（生鲜、食材等）
- 消费者发布需求信息
- 智能标签自动分类

### 2. 智能撮合引擎
- 基于标签相似度匹配
- 地理位置权重计算
- 用户画像偏好分析
- AI生成推荐理由

### 3. 用户画像系统
- 商家画像（供应能力、主营品类、信用评分）
- 客户画像（购买频次、品类偏好、价格敏感度）
- 行为数据采集和分析

### 4. 订单记录
- 撮合成功记录
- 交易历史追踪
- 评价反馈系统

## 技术架构

- **对话入口**: Rocket.Chat
- **AI引擎**: MCP (Model Context Protocol) + Claude/Ollama
- **业务逻辑**: Moqui Framework Services
- **数据存储**: Moqui Entity Engine
- **文件存储**: MinIO (商品图片)

## 组件结构

```
moqui-marketplace/
├── component.xml           # 组件定义
├── build.gradle           # Gradle构建配置
├── entity/                # 实体定义
│   ├── MarketplaceEntities.xml
│   └── MarketplaceViewEntities.xml
├── service/               # 服务定义
│   ├── MarketplaceServices.xml
│   ├── MatchingServices.xml
│   └── ProfileServices.xml
├── screen/                # 屏幕定义（管理后台）
├── data/                  # 初始数据
│   └── MarketplaceDemoData.xml
└── src/
    └── main/
        ├── java/org/moqui/marketplace/
        │   ├── matching/      # 匹配算法
        │   ├── profile/       # 用户画像
        │   └── scoring/       # 评分系统
        └── groovy/org/moqui/marketplace/
            └── rest/          # REST API
```

## 使用场景

### 商家发布供应
```
商家: 我今天有20斤新鲜菠菜想卖
AI: 好的，请问价格和配送范围？
商家: 3-5块一斤，3公里内可送
AI: ✅ 已发布，找到2个匹配买家...
```

### 客户发布需求
```
客户: 我想买点新鲜排骨
AI: 大概多少斤？今天还是明天要？
客户: 2-3斤，今天下午
AI: 找到3家商家有货，推荐刘师傅肉铺...
```

## 安装

组件已在 `runtime/component/moqui-marketplace`

构建:
```bash
./gradlew :runtime:component:moqui-marketplace:build
```

运行:
```bash
./gradlew run
```

## 配置

在 `MoquiDevConf.xml` 或 `MoquiProductionConf.xml` 中配置：

```xml
<!-- Marketplace 配置 -->
<default-property name="marketplace.matching.min.score" value="0.6"/>
<default-property name="marketplace.geo.max.distance" value="5.0"/>
<default-property name="marketplace.listing.expire.hours" value="48"/>
```

## 开发状态

- [x] 组件结构创建
- [ ] 实体模型定义
- [ ] 核心服务实现
- [ ] 匹配算法开发
- [ ] MCP集成
- [ ] Rocket.Chat集成

## License

公共领域 CC0 1.0 Universal