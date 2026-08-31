-- =====================================================================
-- 养生茶小程序 数据库 Schema（对齐设计文档 v10.0）
-- MySQL 8.0 / utf8mb4 / InnoDB
-- 数据源：设计文档 v10 第 3/5/7/9/11/12/16 章
--   - 模块化单体 8 模块，单库
--   - 36 张表（7.1 清单实际列出 36 张，文档标题标注 31 为计数未更新）
--   - 5 组业务唯一索引（ADR-A2 / D1 兜底）
--   - 6 张核心写表带 version 乐观锁（16.2）
--   - event_outbox 事务性发件箱（替代 MQ，2.3）
--   - 订单状态机字段对齐 16.11 转移矩阵
--   - 积分成本双维归属（11.2 / D15）
-- =====================================================================

-- =====================================================================
-- 一、用户模块
-- =====================================================================

CREATE TABLE users (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  openid        VARCHAR(64)  NOT NULL COMMENT '微信 openid',
  unionid       VARCHAR(64)  DEFAULT NULL COMMENT '微信 unionid（可选）',
  nickname      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
  avatar_url    VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
  phone         VARCHAR(32)  DEFAULT NULL COMMENT '手机号（加密存储，异地登录强验证用）',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 / 1正常',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='C端用户';

CREATE TABLE user_addresses (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
  receiver_name VARCHAR(64)  NOT NULL COMMENT '收货人',
  phone         VARCHAR(32)  NOT NULL COMMENT '联系电话',
  province      VARCHAR(32)  NOT NULL COMMENT '省',
  city          VARCHAR(32)  NOT NULL COMMENT '市',
  district      VARCHAR(32)  NOT NULL COMMENT '区',
  detail        VARCHAR(255) NOT NULL COMMENT '详细地址',
  is_default    TINYINT      NOT NULL DEFAULT 0 COMMENT '1默认地址',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ua_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收货地址';

CREATE TABLE user_points_accounts (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
  balance       BIGINT       NOT NULL DEFAULT 0 COMMENT '当前可用积分',
  total_earned  BIGINT       NOT NULL DEFAULT 0 COMMENT '累计获得',
  total_used    BIGINT       NOT NULL DEFAULT 0 COMMENT '累计使用',
  total_expired BIGINT       NOT NULL DEFAULT 0 COMMENT '累计过期清零',
  version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_upa_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户积分账户（品牌级、全店通用）';

-- =====================================================================
-- 二、店铺模块
-- =====================================================================

CREATE TABLE stores (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  store_no        VARCHAR(16)  NOT NULL COMMENT '门店编号：ST001 直营旗舰 / ST002+ 加盟',
  store_name      VARCHAR(64)  NOT NULL COMMENT '门店名称',
  store_type      TINYINT      NOT NULL DEFAULT 2 COMMENT '1直营旗舰店 / 2加盟店',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 / 1正常 / 2待开业',
  contact_name    VARCHAR(64)  DEFAULT NULL COMMENT '联系人',
  contact_phone   VARCHAR(32)  DEFAULT NULL COMMENT '联系电话',
  province        VARCHAR(32)  DEFAULT NULL,
  city            VARCHAR(32)  DEFAULT NULL,
  district        VARCHAR(32)  DEFAULT NULL,
  address         VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
  license_url     VARCHAR(512) DEFAULT NULL COMMENT '营业执照（COS）',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stores_no (store_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店';

CREATE TABLE store_admins (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  admin_id      BIGINT UNSIGNED NOT NULL COMMENT '管理员',
  store_id      BIGINT UNSIGNED NOT NULL COMMENT '门店',
  is_owner      TINYINT      NOT NULL DEFAULT 0 COMMENT '1店主',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0移除 / 1正常',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sa_admin_store (admin_id, store_id),
  KEY idx_sa_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员-门店关联（JWT store_ids[] 数据来源，支持 MULTI_STORE）';

CREATE TABLE franchise_applications (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  applicant_name  VARCHAR(64)  NOT NULL COMMENT '申请人',
  phone           VARCHAR(32)  NOT NULL COMMENT '联系电话',
  intended_region VARCHAR(128) DEFAULT NULL COMMENT '意向区域',
  experience      VARCHAR(512) DEFAULT NULL COMMENT '从业经历',
  status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审核 / 1通过 / 2拒绝',
  review_note     VARCHAR(255) DEFAULT NULL COMMENT '审核意见',
  reviewed_by     BIGINT UNSIGNED DEFAULT NULL COMMENT '审核人',
  reviewed_at     DATETIME     DEFAULT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加盟申请';

CREATE TABLE franchise_deposits (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  store_id      BIGINT UNSIGNED NOT NULL COMMENT '门店',
  type          TINYINT      NOT NULL COMMENT '1缴纳 / 2退还',
  amount        DECIMAL(10,2) NOT NULL COMMENT '金额',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理 / 1完成',
  biz_no        VARCHAR(64)  DEFAULT NULL COMMENT '关联单号',
  paid_at       DATETIME     DEFAULT NULL COMMENT '缴纳时间',
  refunded_at   DATETIME     DEFAULT NULL COMMENT '退还时间',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_fd_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加盟保证金流水';

-- =====================================================================
-- 三、商品模块（平台目录 + 店铺上架双层模型）
-- =====================================================================

CREATE TABLE product_categories (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name          VARCHAR(64)  NOT NULL COMMENT '分类名',
  sort          INT          NOT NULL DEFAULT 0 COMMENT '排序',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 / 1启用',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类';

CREATE TABLE products (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键（平台目录层）',
  category_id     BIGINT UNSIGNED NOT NULL COMMENT '分类',
  name            VARCHAR(128) NOT NULL COMMENT '商品名（总部统一）',
  subtitle        VARCHAR(255) DEFAULT NULL COMMENT '副标题',
  formula         VARCHAR(512) DEFAULT NULL COMMENT '配方（总部维护）',
  main_image      VARCHAR(512) NOT NULL COMMENT '主图（COS）',
  images          JSON         DEFAULT NULL COMMENT '轮播图数组',
  detail          TEXT         COMMENT '富文本详情',
  suggested_price DECIMAL(10,2) NOT NULL COMMENT '建议零售价（本店定价 80%-120% 基准）',
  cost_price      DECIMAL(10,2) NOT NULL COMMENT '成本价（敏感字段，超管/财务可见）',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0下架 / 1在售（平台目录层）',
  version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁（16.2）',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_prod_category (category_id),
  KEY idx_prod_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台商品目录';

CREATE TABLE product_skus (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  product_id    BIGINT UNSIGNED NOT NULL COMMENT '所属商品',
  sku_code      VARCHAR(64)  NOT NULL COMMENT 'SKU 编码',
  specs         JSON         NOT NULL COMMENT '规格矩阵，如 {"规格":"500ml","包装":"礼盒装"}',
  price         DECIMAL(10,2) NOT NULL COMMENT 'SKU 建议价',
  cost_price    DECIMAL(10,2) NOT NULL COMMENT 'SKU 成本价',
  stock         INT          NOT NULL DEFAULT 0 COMMENT '总仓库存（总部维护，原子扣减）',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0停用 / 1启用',
  version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁（16.2）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ps_sku_code (sku_code),
  KEY idx_ps_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SKU（总部总仓）';

CREATE TABLE store_products (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键（店铺上架层）',
  store_id        BIGINT UNSIGNED NOT NULL COMMENT '门店',
  product_id      BIGINT UNSIGNED NOT NULL COMMENT '平台商品',
  sku_id          BIGINT UNSIGNED NOT NULL COMMENT 'SKU（店铺定价到 SKU 粒度）',
  price           DECIMAL(10,2) NOT NULL COMMENT '本店售价（校验：平台建议价 80%-120%，越界拒绝）',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0下架 / 1上架（店铺开关，不被目录变更覆盖）',
  catalog_dirty   TINYINT      NOT NULL DEFAULT 0 COMMENT '1目录已更新（D14：product.catalog_changed 事件标记，店铺端角标提示复核）',
  daily_quota     INT          DEFAULT NULL COMMENT '本店可售配额/日（可选，12.3；NULL=不限）',
  version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁（16.2）',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sp_store_sku (store_id, sku_id),
  KEY idx_sp_store_product (store_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺上架（选品 + 本店定价 + 上下架开关）';

CREATE TABLE inventory_records (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  sku_id        BIGINT UNSIGNED NOT NULL COMMENT 'SKU',
  change_type   TINYINT      NOT NULL COMMENT '1入库 / 2出库 / 3盘点调整 / 4退款回库',
  change_qty    INT          NOT NULL COMMENT '变动数量（正负）',
  before_stock  INT          NOT NULL COMMENT '变动前库存',
  after_stock   INT          NOT NULL COMMENT '变动后库存',
  biz_no        VARCHAR(64)  DEFAULT NULL COMMENT '关联单号（订单号/退款单号）',
  operator_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '操作仓管',
  note          VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ir_sku (sku_id),
  KEY idx_ir_biz (biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水（总部仓管）';

-- =====================================================================
-- 四、订单模块（状态机对齐 16.11）
-- status: 10待支付/20已支付/30待发货/40已发货/50已签收/
--         60退款中/70已关闭/80已退款/90已完结/95回退失败-待人工(11.3 终态)
-- =====================================================================

CREATE TABLE orders (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_no            VARCHAR(64)  NOT NULL COMMENT '订单号',
  user_id             BIGINT UNSIGNED NOT NULL COMMENT '买家',
  store_id            BIGINT UNSIGNED NOT NULL COMMENT '业绩归属门店（下单时选择）',
  status              SMALLINT     NOT NULL DEFAULT 10 COMMENT '状态机（16.11 转移矩阵）',
  warehouse_status    TINYINT      NOT NULL DEFAULT 1 COMMENT '总部发货状态：1待接单/2待发货/3已发货/4已签收（v8）',
  total_amount        DECIMAL(10,2) NOT NULL COMMENT '商品总额',
  coupon_amount       DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠券抵扣',
  points_deduct       BIGINT       NOT NULL DEFAULT 0 COMMENT '积分抵扣数量',
  points_deduct_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '积分抵扣金额',
  points_earned       BIGINT       NOT NULL DEFAULT 0 COMMENT '本单发放积分',
  points_source       TINYINT      NOT NULL DEFAULT 1 COMMENT '积分来源（D15）：1门店营销/2平台活动',
  pay_amount          DECIMAL(10,2) NOT NULL COMMENT '实付金额',
  commission_rate     DECIMAL(5,4) NOT NULL DEFAULT 0.0500 COMMENT '平台佣金比例（下单时快照，X%）',
  receiver_name       VARCHAR(64)  NOT NULL COMMENT '收货人',
  receiver_phone      VARCHAR(32)  NOT NULL,
  receiver_address    VARCHAR(255) NOT NULL COMMENT '收货地址（快照）',
  remark              VARCHAR(255) DEFAULT NULL COMMENT '买家备注',
  tracking_no         VARCHAR(64)  DEFAULT NULL COMMENT '物流单号',
  carrier             VARCHAR(32)  DEFAULT NULL COMMENT '快递公司',
  shipped_by          BIGINT UNSIGNED DEFAULT NULL COMMENT '发货仓管（v8）',
  shipped_at          DATETIME     DEFAULT NULL COMMENT '发货时间（v8）',
  paid_at             DATETIME     DEFAULT NULL,
  finished_at         DATETIME     DEFAULT NULL,
  refund_approved_by  BIGINT UNSIGNED DEFAULT NULL COMMENT '店铺退款审批人（v8）',
  refund_approved_at  DATETIME     DEFAULT NULL COMMENT '审批时间（v8）',
  expire_at           DATETIME     DEFAULT NULL COMMENT '未支付自动关单时间（下单+30min，定时任务扫描，v8）',
  auto_close_status   TINYINT      NOT NULL DEFAULT 0 COMMENT '0未触发/1已自动关单/2用户已取消（v8）',
  urge_count          INT          NOT NULL DEFAULT 0 COMMENT '催发货次数（v8）',
  urged_at            DATETIME     DEFAULT NULL COMMENT '最近催发货时间（v8）',
  ship_timeout_warned TINYINT      NOT NULL DEFAULT 0 COMMENT '总部 48h 超时预警已触发（防重复告警，v8）',
  auto_signed_at      DATETIME     DEFAULT NULL COMMENT '发货 15 天无签收自动完成时间（v8）',
  version             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁（状态流转防重入，16.11）',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_orders_no (order_no),
  UNIQUE KEY uk_orders_user_no (user_id, order_no) COMMENT '业务唯一索引兜底（D1）',
  KEY idx_orders_user (user_id, status),
  KEY idx_orders_store (store_id, status),
  KEY idx_orders_expire (expire_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单';

CREATE TABLE order_items (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id      BIGINT UNSIGNED NOT NULL,
  product_id    BIGINT UNSIGNED NOT NULL,
  sku_id        BIGINT UNSIGNED NOT NULL,
  name          VARCHAR(128) NOT NULL COMMENT '商品名快照',
  specs         JSON         DEFAULT NULL COMMENT '规格快照',
  image         VARCHAR(512) DEFAULT NULL COMMENT '主图快照',
  price         DECIMAL(10,2) NOT NULL COMMENT '成交单价（快照）',
  qty           INT          NOT NULL COMMENT '数量',
  subtotal      DECIMAL(10,2) NOT NULL COMMENT '小计',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_oi_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细';

CREATE TABLE order_shipping_logs (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id      BIGINT UNSIGNED NOT NULL,
  status        VARCHAR(32)  NOT NULL COMMENT '物流轨迹描述',
  tracking_no   VARCHAR(64)  DEFAULT NULL,
  carrier       VARCHAR(32)  DEFAULT NULL,
  operator_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人',
  note          VARCHAR(255) DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_osl_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单物流日志';

-- =====================================================================
-- 五、支付与售后模块
-- =====================================================================

CREATE TABLE payment_records (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  pay_no          VARCHAR(64)  NOT NULL COMMENT '支付单号',
  order_id        BIGINT UNSIGNED NOT NULL,
  transaction_id  VARCHAR(64)  DEFAULT NULL COMMENT '微信支付单号',
  amount          DECIMAL(10,2) NOT NULL,
  status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0待支付/1成功/2失败/3已退款',
  callback_raw    JSON         DEFAULT NULL COMMENT '回调原文（验签后存档）',
  idempotent_key  VARCHAR(64)  DEFAULT NULL COMMENT '幂等键（pay_callback）',
  paid_at         DATETIME     DEFAULT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pr_pay_no (pay_no),
  KEY idx_pr_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水';

CREATE TABLE refund_records (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  refund_no         VARCHAR(64)  NOT NULL COMMENT '退款单号',
  order_id          BIGINT UNSIGNED NOT NULL,
  user_id           BIGINT UNSIGNED NOT NULL,
  amount            DECIMAL(10,2) NOT NULL,
  reason            VARCHAR(255) DEFAULT NULL COMMENT '退款原因',
  refund_branch     TINYINT      NOT NULL COMMENT '退款分支：1未发货直退/2在途拦截/3已签收退货（申请时同事务判定，16.11）',
  status            TINYINT      NOT NULL DEFAULT 0 COMMENT '0申请中/1店铺同意/2拒绝/3退款中/4已退款/5分账回退失败-待人工（11.3 终态）',
  escalation_status TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常/1超时升级总部/2风控升级总部（v8）',
  auto_escalated_at DATETIME     DEFAULT NULL COMMENT '24h 未审批自动升级时间（v8）',
  approved_by_level TINYINT      DEFAULT NULL COMMENT '实际审批方：1店铺/2总部兜底/3超管风控（v8）',
  approved_by       BIGINT UNSIGNED DEFAULT NULL,
  approved_at       DATETIME     DEFAULT NULL,
  handled_at        DATETIME     DEFAULT NULL COMMENT '退款完成时间',
  idempotent_key    VARCHAR(64)  DEFAULT NULL COMMENT '幂等键（refund_approve / 退款原路退款）',
  version           INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rr_no (refund_no),
  UNIQUE KEY uk_rr_order_no (order_id, refund_no) COMMENT '业务唯一索引兜底（D1）',
  KEY idx_rr_user (user_id),
  KEY idx_rr_escalation (escalation_status, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款单';

CREATE TABLE return_orders (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  refund_id         BIGINT UNSIGNED NOT NULL COMMENT '关联退款单',
  order_id          BIGINT UNSIGNED NOT NULL COMMENT '关联订单',
  store_id          BIGINT UNSIGNED NOT NULL COMMENT '归属门店（Data Scope 隔离）',
  branch            TINYINT      NOT NULL COMMENT '退款分支：1未发货/2在途拦截/3已签收退货',
  return_address    VARCHAR(255) DEFAULT NULL COMMENT '总部退货寄回地址（系统给出）',
  return_tracking_no VARCHAR(64) DEFAULT NULL COMMENT '用户填写退货物流单号',
  return_carrier    VARCHAR(64)  DEFAULT NULL COMMENT '退货快递公司',
  warehouse_status  TINYINT      NOT NULL DEFAULT 1 COMMENT '总部收货状态：1待收货/2已收货/3验货通过/4验货不通过',
  received_at       DATETIME     DEFAULT NULL COMMENT '总部收货时间',
  inspected_by      BIGINT UNSIGNED DEFAULT NULL COMMENT '验货仓管',
  inspection_result VARCHAR(255) DEFAULT NULL COMMENT '验货结论：完好退全款/破损部分退款/非质量问题拒退',
  status            TINYINT      NOT NULL DEFAULT 0 COMMENT '0申请中/1待寄回/2在途/3待验货/4已完结/5已取消',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ro_refund (refund_id) COMMENT '业务唯一索引兜底（D1）：一张退款单只建一张退货单',
  KEY idx_ro_store (store_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退货单（签收后退货链路）';

-- =====================================================================
-- 六、营销模块
-- =====================================================================

CREATE TABLE promotions (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  title         VARCHAR(128) NOT NULL COMMENT '活动名',
  type          TINYINT      NOT NULL COMMENT '1满减/2折扣/3限时购',
  scope         TINYINT      NOT NULL DEFAULT 1 COMMENT '1平台/2本店',
  store_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '本店活动归属门店',
  rules         JSON         NOT NULL COMMENT '活动规则（门槛/优惠/叠加约束）',
  start_time    DATETIME     NOT NULL,
  end_time      DATETIME     NOT NULL,
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿/1进行中/2已结束',
  version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁（16.2）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_promo_store (store_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='促销活动';

CREATE TABLE coupons (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name             VARCHAR(128) NOT NULL COMMENT '券名',
  type             TINYINT      NOT NULL COMMENT '1满减券/2折扣券',
  scope            TINYINT      NOT NULL DEFAULT 1 COMMENT '1平台券（平台承担）/2本店券（店铺承担）',
  store_id         BIGINT UNSIGNED DEFAULT NULL COMMENT '本店券归属门店',
  threshold_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '使用门槛（满 X 元）',
  discount_amount  DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠金额（满减）/折扣率存 rules',
  rules            JSON         DEFAULT NULL COMMENT '扩展规则（适用商品/折扣率等）',
  total_count      INT          NOT NULL DEFAULT 0 COMMENT '发行总量',
  received_count   INT          NOT NULL DEFAULT 0 COMMENT '已领取（原子递增）',
  per_user_limit   INT          NOT NULL DEFAULT 1 COMMENT '每人限领',
  start_time       DATETIME     NOT NULL,
  end_time         DATETIME     NOT NULL,
  status           TINYINT      NOT NULL DEFAULT 0 COMMENT '0未发布/1发放中/2已停止',
  version          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁（防重复核销，16.2）',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_coupon_store (store_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板';

CREATE TABLE user_coupons (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT UNSIGNED NOT NULL,
  coupon_id     BIGINT UNSIGNED NOT NULL,
  store_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '券归属门店（平台券为 NULL）',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0未使用/1已使用/2已过期/3退款退回',
  order_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '核销订单',
  received_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  used_at       DATETIME     DEFAULT NULL,
  expire_at     DATETIME     NOT NULL COMMENT '过期时间',
  PRIMARY KEY (id),
  KEY idx_uc_user (user_id, status),
  KEY idx_uc_expire (expire_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户持券';

CREATE TABLE point_records (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT UNSIGNED NOT NULL,
  store_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '积分归属门店（平台活动积分为 NULL）',
  order_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '关联订单',
  refund_no     VARCHAR(64)  DEFAULT NULL COMMENT '关联退款单（回收）',
  change_type   TINYINT      NOT NULL COMMENT '1下单发放/2下单抵扣/3退款回收/4过期清零/5签到',
  source_type   TINYINT      NOT NULL DEFAULT 1 COMMENT '积分来源（D15 双维归属）：1门店营销/2平台活动',
  points        BIGINT       NOT NULL COMMENT '变动数量（正发放/负抵扣回收）',
  batch_no      VARCHAR(64)  DEFAULT NULL COMMENT '发放批次（过期回收单元，12.1）',
  expire_at     DATETIME     DEFAULT NULL COMMENT '该批次过期时间（默认发放后 12 个月）',
  biz_key       VARCHAR(64)  NOT NULL COMMENT '幂等键（订单号/退款单号/过期批次号，Outbox 消费防重）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ptr_biz (change_type, biz_key),
  KEY idx_ptr_user (user_id),
  KEY idx_ptr_store (store_id),
  KEY idx_ptr_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水';

CREATE TABLE banners (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  title         VARCHAR(64)  NOT NULL COMMENT '标题',
  image         VARCHAR(512) NOT NULL COMMENT '图片（COS）',
  link_type     TINYINT      NOT NULL DEFAULT 1 COMMENT '1商品/2页面/3小程序页',
  link_value    VARCHAR(255) DEFAULT NULL COMMENT '跳转目标',
  sort          INT          NOT NULL DEFAULT 0,
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0下线/1上线',
  start_time    DATETIME     DEFAULT NULL,
  end_time      DATETIME     DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Banner';

CREATE TABLE shopping_carts (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT UNSIGNED NOT NULL,
  store_id      BIGINT UNSIGNED NOT NULL,
  sku_id        BIGINT UNSIGNED NOT NULL,
  qty           INT          NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sc_user_sku (user_id, sku_id),
  KEY idx_sc_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车';

-- =====================================================================
-- 七、结算模块（11 章：T+1 日结、积分双维归属、冲正、自动确认）
-- =====================================================================

CREATE TABLE store_settlement_configs (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  store_id           BIGINT UNSIGNED NOT NULL,
  commission_rate    DECIMAL(5,4) NOT NULL DEFAULT 0.0500 COMMENT '平台佣金比例 X%（订单金额拆分）',
  cycle_type         TINYINT      NOT NULL DEFAULT 1 COMMENT '结算周期：1日结T+1/2周结（可配置）',
  auto_confirm_hours INT          NOT NULL DEFAULT 72 COMMENT '自动确认时长（小时）',
  force_catalog_sync TINYINT      NOT NULL DEFAULT 0 COMMENT '强制同步目录开关（D14，合规性修正用）',
  version            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ssc_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺结算配置';

CREATE TABLE settlements (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  settle_no           VARCHAR(64)  NOT NULL COMMENT '结算单号',
  store_id            BIGINT UNSIGNED NOT NULL,
  period              VARCHAR(16)  NOT NULL COMMENT '结算周期（日结=2026-08-30，周结=2026-W35）',
  type                TINYINT      NOT NULL DEFAULT 1 COMMENT '1日结/2周结/3调整单',
  order_count         INT          NOT NULL DEFAULT 0 COMMENT '订单数',
  total_amount        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '销售总额',
  commission_amount   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '平台佣金',
  points_deduct_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '门店营销积分抵扣（从店铺结算扣减）',
  points_cost_store   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '门店营销积分成本（计入该店）',
  points_cost_platform DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '平台活动积分成本（平台补贴，不从店铺扣减）',
  coupon_cost_store   DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '本店券成本',
  refund_adjust       DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '退款冲正',
  adjust_amount       DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '调整单（申诉复核）',
  final_amount        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '实际到账 = 总额-佣金-积分抵扣-积分成本-本店券-冲正+调整',
  confirm_status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0待确认/1自动确认/2人工确认/3有异议（v8）',
  status              TINYINT      NOT NULL DEFAULT 0 COMMENT '0生成/1已确认/2财务审核通过/3已打款/4已冲正',
  auto_confirm_at     DATETIME     DEFAULT NULL COMMENT '自动确认时间（生成后 72h）',
  confirmed_at        DATETIME     DEFAULT NULL,
  dispute_note        VARCHAR(255) DEFAULT NULL COMMENT '异议申诉说明（事后申诉，v8）',
  reviewed_by         BIGINT UNSIGNED DEFAULT NULL COMMENT '财务审核人',
  paid_at             DATETIME     DEFAULT NULL,
  payout_no           VARCHAR(64)  DEFAULT NULL COMMENT '打款流水号（幂等键组成部分）',
  version             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁（审核/打款防并发）',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_set_no (settle_no),
  UNIQUE KEY uk_set_store_period (store_id, period, type) COMMENT '业务唯一索引兜底（D1）',
  KEY idx_set_store (store_id, status),
  KEY idx_set_confirm (confirm_status, auto_confirm_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结算单';

CREATE TABLE settlement_items (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  settlement_id   BIGINT UNSIGNED NOT NULL,
  order_id        BIGINT UNSIGNED DEFAULT NULL COMMENT '关联订单（明细类必填）',
  order_no        VARCHAR(64)  DEFAULT NULL,
  item_type       TINYINT      NOT NULL COMMENT '1订单销售额/2平台佣金/3门店营销积分抵扣/4门店营销积分成本/5平台补贴积分(D15平台承担)/6本店券成本/7退款冲正/8调整单',
  direction       TINYINT      NOT NULL COMMENT '1店铺加项/2店铺减项/3平台承担项',
  amount          DECIMAL(10,2) NOT NULL,
  remark          VARCHAR(255) DEFAULT NULL COMMENT '分行说明（11.2：门店营销积分行 vs 平台补贴行，一目了然）',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_si_settlement (settlement_id),
  KEY idx_si_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='结算单明细（按积分来源分行，D15）';

-- =====================================================================
-- 八、权限模块（第 5 章：单角色 + Data Scope）
-- =====================================================================

CREATE TABLE roles (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  code          VARCHAR(32)  NOT NULL COMMENT '角色编码',
  name          VARCHAR(64)  NOT NULL COMMENT '角色名',
  data_scope    VARCHAR(16)  NOT NULL COMMENT 'GLOBAL / MULTI_STORE / SINGLE_STORE',
  level         TINYINT      NOT NULL DEFAULT 2 COMMENT '1平台级/2店铺级（权限互斥校验依据）',
  is_preset     TINYINT      NOT NULL DEFAULT 0 COMMENT '1预设角色（不可删）',
  description   VARCHAR(255) DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色（单角色+DataScope，自定义角色上限 10）';

CREATE TABLE permissions (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  code          VARCHAR(64)  NOT NULL COMMENT '权限编码（如 order:refund:approve）',
  name          VARCHAR(64)  NOT NULL COMMENT '权限名',
  module        VARCHAR(32)  NOT NULL COMMENT '所属模块（菜单权限→按钮权限→接口权限三级联动）',
  type          TINYINT      NOT NULL DEFAULT 3 COMMENT '1菜单/2按钮/3接口',
  parent_id     BIGINT UNSIGNED DEFAULT NULL COMMENT '父权限（菜单树）',
  path          VARCHAR(128) DEFAULT NULL COMMENT '菜单路由/接口路径',
  is_sensitive  TINYINT      NOT NULL DEFAULT 0 COMMENT '1敏感权限（成本价/打款确认/删除订单/角色配置，超管专属）',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_perm_code (code),
  KEY idx_perm_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限';

CREATE TABLE role_permissions (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_id       BIGINT UNSIGNED NOT NULL,
  permission_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rp (role_id, permission_id),
  KEY idx_rp_perm (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限';

CREATE TABLE admin_users (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  username      VARCHAR(32)  NOT NULL COMMENT '登录名',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
  real_name     VARCHAR(64)  NOT NULL,
  phone         VARCHAR(32)  DEFAULT NULL COMMENT '手机号（短信验证）',
  role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色（1:1 单绑定，5.1）',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用/1正常',
  token_version INT          NOT NULL DEFAULT 0 COMMENT '禁用/改密/删角色时 +1，JWT 即时吊销（R9，v8）',
  last_login_at DATETIME     DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_au_username (username),
  KEY idx_au_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台管理员';

CREATE TABLE operation_logs (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  admin_id          BIGINT UNSIGNED NOT NULL COMMENT '操作管理员',
  operator_name     VARCHAR(64)  NOT NULL COMMENT '操作人快照（JWT 登录主体服务端落库，D6）',
  device_fingerprint VARCHAR(128) DEFAULT NULL COMMENT '设备指纹（D6）',
  module            VARCHAR(32)  NOT NULL,
  action            VARCHAR(64)  NOT NULL COMMENT '操作（如 refund:approve）',
  biz_type          VARCHAR(32)  DEFAULT NULL,
  biz_key           VARCHAR(64)  DEFAULT NULL,
  request_uri       VARCHAR(255) DEFAULT NULL,
  request_params    JSON         DEFAULT NULL COMMENT '请求参数（脱敏后）',
  ip                VARCHAR(46)  DEFAULT NULL,
  region            VARCHAR(64)  DEFAULT NULL COMMENT 'IP 归属地（异地检测）',
  result            TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功/0失败',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ol_admin (admin_id, created_at),
  KEY idx_ol_biz (biz_type, biz_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志';

-- =====================================================================
-- 九、消息模块
-- =====================================================================

CREATE TABLE subscribe_messages (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT UNSIGNED NOT NULL,
  template_code VARCHAR(64)  NOT NULL COMMENT '订阅消息模板编码',
  scene         VARCHAR(32)  NOT NULL COMMENT '场景：refund_result/ship_notice/points_expire 等',
  biz_key       VARCHAR(64)  DEFAULT NULL COMMENT '业务键（防重发）',
  payload       JSON         DEFAULT NULL,
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待发送/1已发送/2失败',
  sent_at       DATETIME     DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sm_user (user_id, status),
  KEY idx_sm_scene (scene, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订阅消息';

-- =====================================================================
-- 十、基础设施：事件 / 幂等 / 设备信任
-- =====================================================================

CREATE TABLE event_outbox (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  event_id      VARCHAR(64)  NOT NULL COMMENT '事件唯一 ID（UUID）',
  event_type    VARCHAR(64)  NOT NULL COMMENT 'order.paid / refund.approved / order.auto_closed / product.catalog_changed / points.expired 等（2.3）',
  biz_key       VARCHAR(64)  NOT NULL COMMENT '业务键（订单号/退款单号，消费端幂等查重）',
  payload       JSON         NOT NULL COMMENT '事件载荷',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待投递/1已投递/2投递失败（重试中）',
  retry_count   INT          NOT NULL DEFAULT 0 COMMENT '重试次数（指数退避，上限 5 次告警转人工）',
  next_retry_at DATETIME     DEFAULT NULL COMMENT '下次重试时间',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  delivered_at  DATETIME     DEFAULT NULL COMMENT '投递完成时间（完结记录保留 7 天后清理，D11）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_eo_event_id (event_id) COMMENT '业务唯一索引兜底（D1）：重复投递只消费一次',
  KEY idx_eo_status (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事务性发件箱（替代 MQ，2.3）';

CREATE TABLE idempotency_keys (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  biz_type        VARCHAR(32)  NOT NULL COMMENT 'order_place/refund_approve/ship/payout/settlement_confirm/pay_callback/return_submit/return_inspect/order_urge',
  biz_key         VARCHAR(64)  DEFAULT NULL COMMENT '业务键（订单号/退款单号/结算单号/退货单号）',
  idempotent_key  VARCHAR(64)  NOT NULL COMMENT '客户端 UUID（请求级防重，24h 窗口）',
  request_hash    VARCHAR(64)  DEFAULT NULL COMMENT '请求体摘要（同键不同参数直接拒绝）',
  response_json   TEXT         COMMENT '首次成功响应缓存（重复请求直接返回）',
  status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0处理中/1成功/2失败（处理中+30s 视为脏数据可重试）',
  expire_at       DATETIME     NOT NULL COMMENT '重试窗口（默认 24 小时）',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ik (biz_type, idempotent_key),
  KEY idx_ik_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幂等键登记（请求级防重；业务唯一性由 5 组 UNIQUE 索引兜底，A2/D1）';

CREATE TABLE device_trusts (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  admin_id            BIGINT UNSIGNED NOT NULL COMMENT '管理员',
  device_fingerprint  VARCHAR(128) NOT NULL COMMENT '设备指纹（UA+Canvas+WebGL+字体+屏幕 五维哈希，A5/D5）',
  device_name         VARCHAR(64)  DEFAULT NULL COMMENT '设备备注名',
  trust_level         TINYINT      NOT NULL DEFAULT 0 COMMENT '0陌生/1常用（验证通过 5 次自动升级）',
  verify_count        INT          NOT NULL DEFAULT 0 COMMENT '累计验证通过次数',
  first_seen_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at        DATETIME     DEFAULT NULL,
  expires_at          DATETIME     NOT NULL COMMENT '信任有效期（默认 90 天，last_used_at 滚动续期，D5）',
  last_login_ip       VARCHAR(46)  DEFAULT NULL COMMENT '最近登录 IP（异地强制短信依据，D5）',
  last_login_region   VARCHAR(64)  DEFAULT NULL COMMENT 'IP 归属地',
  status              TINYINT      NOT NULL DEFAULT 1 COMMENT '1有效/0撤销（店主可手动吊销，D13）',
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dt_admin_fp (admin_id, device_fingerprint),
  KEY idx_dt_admin (admin_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备信任（常用设备免验证，90 天有效期）';
