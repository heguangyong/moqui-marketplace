# MCP集成指南 - Moqui Marketplace

## 概述

本文档描述如何将Moqui Marketplace与MCP (Model Context Protocol)集成，实现AI Agent驱动的社区商业撮合平台。

## 架构图

```
┌─────────────────────────────────────────────────────────┐
│                    Rocket.Chat                          │
│              (用户对话界面)                               │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP Webhook
┌────────────────────▼────────────────────────────────────┐
│         RocketChat-MCP Bridge (Node.js)                 │
│  - 接收Rocket.Chat消息                                   │
│  - 转发给MCP Client                                      │
│  - 返回AI响应到Rocket.Chat                               │
└────────────────────┬────────────────────────────────────┘
                     │ MCP Protocol (stdio/HTTP)
┌────────────────────▼────────────────────────────────────┐
│            MCP Server (Node.js/Python)                  │
│                                                         │
│  Tools:                                                 │
│  ├─ marketplace_create_supply                           │
│  ├─ marketplace_create_demand                           │
│  ├─ marketplace_find_matches                            │
│  ├─ marketplace_confirm_match                           │
│  ├─ marketplace_create_order                            │
│  └─ ...                                                 │
│                                                         │
│  Resources:                                             │
│  ├─ marketplace://listings/{id}                         │
│  ├─ marketplace://profiles/{partyId}                    │
│  └─ marketplace://matches/{id}                          │
│                                                         │
│  Prompts:                                               │
│  ├─ merchant_onboarding                                 │
│  ├─ generate_match_reason                               │
│  └─ ...                                                 │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP REST API
┌────────────────────▼────────────────────────────────────┐
│         Moqui Framework REST API                        │
│  /rest/s1/marketplace/                                  │
│  ├─ listing/create                                      │
│  ├─ listing/search                                      │
│  ├─ match/find                                          │
│  └─ ...                                                 │
└─────────────────────────────────────────────────────────┘
```

## 实施步骤

### Phase 1: MCP Server开发（2周）

#### 1.1 创建MCP Server项目

```bash
mkdir moqui-mcp-server
cd moqui-mcp-server
npm init -y
npm install @modelcontextprotocol/sdk express axios
```

#### 1.2 MCP Server核心代码

```typescript
// src/index.ts
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import axios from 'axios';

const MOQUI_BASE_URL = process.env.MOQUI_URL || 'http://localhost:8080';
const MOQUI_AUTH_TOKEN = process.env.MOQUI_TOKEN || '';

// 创建MCP Server
const server = new Server(
  {
    name: 'moqui-marketplace',
    version: '1.0.0',
  },
  {
    capabilities: {
      tools: {},
      resources: {},
      prompts: {},
    },
  }
);

// 定义Tools
server.setRequestHandler('tools/list', async () => {
  return {
    tools: [
      {
        name: 'marketplace_create_supply',
        description: '商家发布供应信息',
        inputSchema: {
          type: 'object',
          properties: {
            publisherId: { type: 'string', description: '发布者ID' },
            title: { type: 'string', description: '标题' },
            category: { type: 'string', description: '品类' },
            quantity: { type: 'number', description: '数量' },
            priceMin: { type: 'number', description: '最低价' },
            priceMax: { type: 'number', description: '最高价' },
            locationDesc: { type: 'string', description: '位置描述' },
            deliveryRange: { type: 'number', description: '配送范围(km)' },
          },
          required: ['publisherId', 'title', 'category'],
        },
      },
      {
        name: 'marketplace_create_demand',
        description: '消费者发布需求信息',
        inputSchema: {
          type: 'object',
          properties: {
            publisherId: { type: 'string' },
            title: { type: 'string' },
            category: { type: 'string' },
            quantity: { type: 'number' },
            priceMin: { type: 'number' },
            priceMax: { type: 'number' },
          },
          required: ['publisherId', 'title', 'category'],
        },
      },
      {
        name: 'marketplace_find_matches',
        description: '查找匹配的供需信息',
        inputSchema: {
          type: 'object',
          properties: {
            listingId: { type: 'string' },
            maxResults: { type: 'number', default: 10 },
            minScore: { type: 'number', default: 0.6 },
          },
          required: ['listingId'],
        },
      },
      {
        name: 'marketplace_confirm_match',
        description: '确认撮合（授权联系）',
        inputSchema: {
          type: 'object',
          properties: {
            matchId: { type: 'string' },
            confirmingPartyId: { type: 'string' },
          },
          required: ['matchId', 'confirmingPartyId'],
        },
      },
    ],
  };
});

// 实现Tool调用
server.setRequestHandler('tools/call', async (request) => {
  const { name, arguments: args } = request.params;

  try {
    let result;
    switch (name) {
      case 'marketplace_create_supply':
        result = await callMoquiService('marketplace.MarketplaceServices.create#Listing', {
          ...args,
          listingType: 'SUPPLY',
          publisherType: 'MERCHANT',
        });
        break;

      case 'marketplace_create_demand':
        result = await callMoquiService('marketplace.MarketplaceServices.create#Listing', {
          ...args,
          listingType: 'DEMAND',
          publisherType: 'CUSTOMER',
        });
        break;

      case 'marketplace_find_matches':
        result = await callMoquiService('marketplace.MarketplaceServices.find#Matches', args);
        break;

      case 'marketplace_confirm_match':
        result = await callMoquiService('marketplace.MarketplaceServices.confirm#Match', args);
        break;

      default:
        throw new Error(`Unknown tool: ${name}`);
    }

    return {
      content: [
        {
          type: 'text',
          text: JSON.stringify(result, null, 2),
        },
      ],
    };
  } catch (error) {
    return {
      content: [
        {
          type: 'text',
          text: `Error: ${error.message}`,
        },
      ],
      isError: true,
    };
  }
});

// 调用Moqui REST API
async function callMoquiService(serviceName: string, params: any) {
  const response = await axios.post(
    `${MOQUI_BASE_URL}/rest/s1/moqui/service/${serviceName}`,
    params,
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${MOQUI_AUTH_TOKEN}`,
      },
    }
  );
  return response.data;
}

// 启动Server
const transport = new StdioServerTransport();
await server.connect(transport);
```

#### 1.3 测试MCP Server

```bash
# 安装MCP Inspector工具
npm install -g @modelcontextprotocol/inspector

# 测试MCP Server
npx @modelcontextprotocol/inspector node dist/index.js
```

### Phase 2: Rocket.Chat集成（1-2周）

#### 2.1 创建Rocket.Chat Bot

1. 登录Rocket.Chat管理后台
2. Administration → Users → New User
3. 创建Bot用户：`marketplace-bot`
4. 获取Personal Access Token

#### 2.2 配置Outgoing Webhook

1. Administration → Integrations → Outgoing WebHook
2. 设置:
   - Event Trigger: Message Sent
   - Enabled: Yes
   - Channel: all_public_channels
   - URLs: `http://your-server:3002/rocketchat/webhook`
   - Script: (可选)自定义过滤逻辑

#### 2.3 RocketChat-MCP Bridge

```typescript
// rocketchat-bridge/src/index.ts
import express from 'express';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import axios from 'axios';

const app = express();
app.use(express.json());

const ROCKETCHAT_URL = process.env.ROCKETCHAT_URL || 'http://localhost:3000';
const ROCKETCHAT_BOT_TOKEN = process.env.ROCKETCHAT_BOT_TOKEN || '';
const MCP_SERVER_CMD = 'node';
const MCP_SERVER_ARGS = ['../moqui-mcp-server/dist/index.js'];

// 初始化MCP Client
const transport = new StdioClientTransport({
  command: MCP_SERVER_CMD,
  args: MCP_SERVER_ARGS,
});

const mcpClient = new Client(
  {
    name: 'rocketchat-bridge',
    version: '1.0.0',
  },
  {
    capabilities: {},
  }
);

await mcpClient.connect(transport);

// Rocket.Chat Webhook处理
app.post('/rocketchat/webhook', async (req, res) => {
  const { user_name, text, channel_name, user_id } = req.body;

  // 忽略Bot自己的消息
  if (user_name === 'marketplace-bot') {
    return res.json({ text: '' });
  }

  console.log(`Received message from ${user_name}: ${text}`);

  try {
    // 获取或创建用户会话
    const sessionId = await getOrCreateSession(user_id);

    // 调用AI Agent处理消息
    const aiResponse = await processMessageWithAI(sessionId, text, user_id);

    // 发送响应回Rocket.Chat
    res.json({
      text: aiResponse.text,
      attachments: aiResponse.attachments || [],
    });
  } catch (error) {
    console.error('Error processing message:', error);
    res.json({
      text: '抱歉，处理您的消息时出现了问题，请稍后再试。',
    });
  }
});

// 使用AI处理消息
async function processMessageWithAI(sessionId: string, message: string, userId: string) {
  // 这里可以调用Claude API或其他LLM
  // 示例：使用Claude API

  const claudeResponse = await axios.post(
    'https://api.anthropic.com/v1/messages',
    {
      model: 'claude-3-5-sonnet-20241022',
      max_tokens: 1024,
      messages: [
        {
          role: 'user',
          content: message,
        },
      ],
      tools: await getMCPTools(), // 从MCP Server获取可用工具
    },
    {
      headers: {
        'x-api-key': process.env.ANTHROPIC_API_KEY,
        'anthropic-version': '2023-06-01',
        'Content-Type': 'application/json',
      },
    }
  );

  // 处理tool_use响应
  if (claudeResponse.data.stop_reason === 'tool_use') {
    const toolUses = claudeResponse.data.content.filter((c) => c.type === 'tool_use');

    for (const toolUse of toolUses) {
      // 调用MCP Tool
      const toolResult = await mcpClient.callTool({
        name: toolUse.name,
        arguments: toolUse.input,
      });

      // 可以将结果再次发送给Claude进行总结
      // ...
    }
  }

  // 提取文本响应
  const textContent = claudeResponse.data.content.find((c) => c.type === 'text');

  return {
    text: textContent?.text || '收到',
    attachments: [], // 可以添加按钮等交互元素
  };
}

// 获取MCP Tools
async function getMCPTools() {
  const toolsResponse = await mcpClient.listTools();
  return toolsResponse.tools.map((tool) => ({
    name: tool.name,
    description: tool.description,
    input_schema: tool.inputSchema,
  }));
}

// 会话管理
const sessions = new Map();

async function getOrCreateSession(userId: string) {
  if (!sessions.has(userId)) {
    // 调用Moqui创建会话
    const response = await axios.post(
      `${process.env.MOQUI_URL}/rest/s1/mcp/dialog/createSession`,
      { customerId: userId }
    );
    sessions.set(userId, response.data.sessionId);
  }
  return sessions.get(userId);
}

const PORT = process.env.PORT || 3002;
app.listen(PORT, () => {
  console.log(`RocketChat-MCP Bridge listening on port ${PORT}`);
});
```

### Phase 3: Moqui REST API扩展（1周）

需要在Moqui中创建REST API端点供MCP Server调用：

```xml
<!-- service/marketplace.rest.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<resource xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:noNamespaceSchemaLocation="http://moqui.org/xsd/rest-api-3.xsd"
          name="marketplace" displayName="Marketplace API">

    <resource name="listing">
        <method type="post">
            <service name="marketplace.MarketplaceServices.create#Listing"/>
        </method>
        <method type="get">
            <service name="marketplace.MarketplaceServices.search#Listings"/>
        </method>

        <resource name="{listingId}">
            <method type="get">
                <entity name="marketplace.listing.Listing" operation="one"/>
            </method>
        </resource>
    </resource>

    <resource name="match">
        <resource name="find">
            <method type="post">
                <service name="marketplace.MarketplaceServices.find#Matches"/>
            </method>
        </resource>

        <resource name="confirm">
            <method type="post">
                <service name="marketplace.MarketplaceServices.confirm#Match"/>
            </method>
        </resource>
    </resource>

    <resource name="order">
        <method type="post">
            <service name="marketplace.MarketplaceServices.create#MatchOrder"/>
        </method>
    </resource>

</resource>
```

## 部署架构

### 开发环境

```yaml
# docker-compose.yml
version: '3.8'

services:
  moqui:
    image: moqui-framework:latest
    ports:
      - "8080:8080"
    environment:
      - entity_ds_host=moqui-database
      - entity_ds_user=moqui
      - entity_ds_password=moqui
    depends_on:
      - moqui-database

  moqui-database:
    image: postgres:15
    environment:
      - POSTGRES_DB=moqui
      - POSTGRES_USER=moqui
      - POSTGRES_PASSWORD=moqui
    volumes:
      - moqui-db-data:/var/lib/postgresql/data

  rocketchat:
    image: rocket.chat:latest
    ports:
      - "3000:3000"
    environment:
      - ROOT_URL=http://localhost:3000
      - MONGO_URL=mongodb://mongo:27017/rocketchat
    depends_on:
      - mongo

  mongo:
    image: mongo:6
    volumes:
      - mongo-data:/data/db

  mcp-server:
    build: ./moqui-mcp-server
    environment:
      - MOQUI_URL=http://moqui:8080
      - MOQUI_TOKEN=${MOQUI_TOKEN}

  rocketchat-bridge:
    build: ./rocketchat-bridge
    ports:
      - "3002:3002"
    environment:
      - ROCKETCHAT_URL=http://rocketchat:3000
      - ROCKETCHAT_BOT_TOKEN=${ROCKETCHAT_BOT_TOKEN}
      - MOQUI_URL=http://moqui:8080
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
    depends_on:
      - rocketchat
      - mcp-server

volumes:
  moqui-db-data:
  mongo-data:
```

### 生产环境建议

1. **高可用部署**
   - Moqui: 多实例 + Nginx负载均衡
   - Rocket.Chat: 多实例 + MongoDB Replica Set
   - MCP Server: PM2集群模式

2. **安全加固**
   - 使用HTTPS (Let's Encrypt)
   - JWT Token过期策略
   - API Rate Limiting
   - Webhook签名验证

3. **监控告警**
   - Prometheus + Grafana
   - 日志聚合 (ELK Stack)
   - 错误追踪 (Sentry)

## 测试场景

### 端到端测试流程

1. **用户在Rocket.Chat发送消息**
   ```
   用户: 我今天有20斤新鲜菠菜想卖
   ```

2. **RocketChat Bridge接收并转发给Claude**
   - Claude识别意图：发布供应信息
   - Claude调用Tool: `marketplace_create_supply`

3. **MCP Server调用Moqui REST API**
   - POST /rest/s1/marketplace/listing
   - 创建Listing记录

4. **Moqui自动触发匹配**
   - 调用SmartMatchingEngine
   - 查找匹配的需求

5. **返回结果给用户**
   ```
   Bot: ✅ 已为您发布供应信息！
   找到2个匹配的买家：
   1. 张女士（距离1.2km，信用⭐⭐⭐⭐⭐）
   [授权联系] [查看详情]
   ```

## 下一步开发计划

### Week 1-2: MCP Server基础
- [ ] 实现核心Tools（create/search/match）
- [ ] 实现Resources访问
- [ ] 单元测试

### Week 3: Rocket.Chat集成
- [ ] 搭建私有Rocket.Chat服务器
- [ ] 创建Bot和Webhook
- [ ] 实现RocketChat Bridge

### Week 4: AI Agent开发
- [ ] 集成Claude API
- [ ] 实现多轮对话管理
- [ ] 意图识别和工具调用

### Week 5-6: 业务逻辑完善
- [ ] 标签自动提取
- [ ] AI生成匹配理由
- [ ] 用户画像更新逻辑

### Week 7: 试点准备
- [ ] 选择2-3个商家
- [ ] 用户培训
- [ ] 监控和日志

### Week 8-10: 灰度测试和迭代
- [ ] 收集反馈
- [ ] 优化匹配算法
- [ ] 性能调优

## 常见问题

### Q: 为什么要用MCP而不是直接调用Moqui API?
**A:** MCP提供了标准化的AI-应用集成协议，让AI可以"理解"业务能力。未来可以无缝切换不同的AI模型，也可以让其他AI工具（如Claude Desktop）直接连接使用。

### Q: 本地部署Ollama够用吗？
**A:** 对于简单的意图识别和标签提取，Ollama的Qwen2.5:14B足够。但复杂的推理和生成（如匹配理由），建议用Claude API获得更好效果。可以混合使用。

### Q: 如何保证对话上下文的连续性？
**A:** 使用McpDialogSession记录会话历史，每次调用AI时将历史消息作为上下文传入。RocketChat Bridge维护userId到sessionId的映射。

### Q: 匹配推荐的实时性如何保证？
**A:**
1. 新Listing创建时立即触发匹配
2. 高分匹配立即推送通知到Rocket.Chat
3. 定时任务扫描ACTIVE状态的Listing更新匹配

---

**作者**: Moqui Marketplace Team
**最后更新**: 2025-09-30
**版本**: 1.0.0