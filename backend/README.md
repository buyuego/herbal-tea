# 养生茶小程序后端（herbal-tea/backend）

模块化单体工程骨架（设计文档 v10.0 / ADR-A1）。Spring Boot 3.3 + Java 17 + MyBatis-Plus + MySQL 8 + Redis 7 + Flyway。

## 技术栈（已拍板）

| 层 | 选型 |
|---|---|
| 后端 | Java 17 + Spring Boot 3.3.5 + MyBatis-Plus 3.5.7 |
| 数据库 | MySQL 8（utf8mb4，Flyway 管理 V1 schema / V2 init_data） |
| 缓存 | Redis 7（仅缓存/验证码/限流语义，非业务数据源） |
| 鉴权 | JWT（jjwt 0.12）+ token_version 即时吊销（R9）+ 设备指纹（A5） |
| 微信 | WxJava 4.6.0（miniapp 登录 + pay 服务商分账） |
| API 文档 | springdoc-openapi（/swagger-ui.html，63 API 契约随注解生成） |

## 目录结构

```
backend/
├── pom.xml                        # 依赖（含 WxJava / jjwt / MyBatis-Plus / Flyway）
├── docker-compose.yml             # 本地环境：MySQL 8 + Redis 7（端口仅绑 127.0.0.1）
├── .env.example                   # 环境变量样例（对齐部署清单 V2 第 8 章）
└── src/main/
    ├── java/com/herbaltea/
    │   ├── HerbalTeaApplication.java
    │   ├── common/                # 统一响应 / 异常 / BaseEntity
    │   ├── infrastructure/        # 基础设施（跨模块复用，禁止反向依赖业务模块）
    │   │   ├── web/               # 拦截器链：日志→鉴权→DataScope→限流（原网关下沉）
    │   │   ├── security/          # JwtUtil（2h 访问 + 30d 刷新轮换）
    │   │   ├── outbox/            # Outbox 事件机制（2.3）：Publisher / Worker / Subscriber
    │   │   ├── idempotency/       # 幂等键（16.3）：Redis SETNX 24h + @Idempotent 切面
    │   │   ├── audit/             # 审计（15.2/D6）：@AuditLog 切面
    │   │   ├── scheduler/         # 定时任务（16.12）：@ScheduledTask + SETNX 选主预留
    │   │   └── mybatis/           # 乐观锁 / 分页 / 字段填充
    │   └── module-*/              # 8 个业务模块（模块间只允许接口调用，禁止跨模块直读表）
    │       ├── auth/              # 权限账号（登录/吊销/角色）
    │       ├── user/              # C 端会员（微信登录/设备指纹）
    │       ├── store/             # 店铺/加盟/门店管理员
    │       ├── product/           # 商品/SKU/库存原子扣减（16.4）/ D14 目录变更
    │       ├── order/             # 订单 + 状态机（16.11，OrderStatus 完整实现）
    │       ├── payment/           # 支付/退款（11.3 回退失败终态，RefundStatus 完整实现）
    │       ├── marketing/         # 营销/积分（D15 双维归属，示例订阅者）
    │       └── settlement/        # 结算（SettlementStatus 完整实现）
    └── resources/
        ├── application.yml        # 公共配置（JWT/Outbox/幂等/限流参数）
        ├── application-dev.yml    # 本地 docker-compose 连接
        ├── application-prod.yml   # 生产环境变量注入
        ├── logback-spring.xml     # 按天滚动 30 天 + alert.log
        └── db/migration/          # Flyway：V1__schema.sql（36 表）+ V2__init_data.sql
```

## 快速启动

```bash
# 1. 启动本地中间件（MySQL 8 + Redis 7）
cd backend && docker compose up -d mysql redis

# 2. 启动应用（IDE 运行 HerbalTeaApplication，或）
mvn spring-boot:run

# 3. 验证
curl http://localhost:8080/actuator/health   # 健康检查（docker-compose app 探活同款）
# Swagger UI: http://localhost:8080/swagger-ui.html
```

首次启动 Flyway 自动执行 `db/migration/V1__schema.sql` + `V2__init_data.sql`（36 张表 + 初始超管 + 5 预设角色 + 旗舰店 ST001）。

## 骨架已完成 vs 待实现

**已完成（基础设施，可编译可启动）**：
- 拦截器链 4 道 + 幂等/审计 AOP（对应原网关全部功能，ADR-A1）
- Outbox 全链路：Publisher（事务内发布）/ Worker（5s 轮询、批量 100、指数退避 5 次、积压告警）/ 订阅分发
- 三大状态机完整实现：OrderStatus（16.11）/ RefundStatus（11.3）/ SettlementStatus
- 定时任务框架：@ScheduledTask + TaskRunner（SETNX 选主预留 + 错过补偿），示例 OrderCloseTask
- 8 模块包结构与接口契约 + 最小实现类（可启动）

**待实现（按里程碑 P1→P2 顺序，文件中均有 TODO 标注与设计章节指引）**：
1. AuthServiceImpl / UserServiceImpl：登录、token_version 比对器接线、设备指纹
2. OrderServiceImpl：下单核心链路（幂等 + 库存扣减 + 支付单）
3. PaymentServiceImpl：微信支付回调验签 + 退款 + 分账（需外部申请：商户号 + 分账权限）
4. 订阅者补齐：结算冲正（refund.approved）、目录复核（product_catalog_changed）、积分过期（points.expired）
5. 定时任务补齐：24h 退款升级 / 48h 发货预警 / 15 天签收 / 3 天结算确认 / 每日对账 / Outbox 清理
6. 63 API 的 Controller 补全（契约随 springdoc 注解生成）

## 约束（架构红线，来自设计文档）

- **模块间只允许接口调用**，禁止跨模块直读对方表（保持未来拆分能力）
- **写操作必须幂等**：@Idempotent + 业务唯一索引兜底（5 组 UNIQUE，V1 schema）
- **状态流转必须走状态机**：transitTo 校验 + status/version 双条件更新（16.2/16.11）
- **异步事件必须走 Outbox**：禁止绕过 Outbox 直调消费者（保证不丢不重）
- **库存/积分扣减用原子 SQL**：不用 Redis 锁（16.4/16.3，D3 移除 Redis 锁）
- **Redis 只做缓存语义**：key 前缀 cache:/sms:/limit:/idem:/lock:/lastrun:
