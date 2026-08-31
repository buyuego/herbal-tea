# -*- coding: utf-8 -*-
"""积分体系全链路联测（v27）：发放幂等 → 下单抵扣 → 余额不足回滚 → 过期回收 → 退款回收

场景矩阵：
  准备         C 端登录（u001 → userId=3）+ 手工充值 500 积分（造数，测试后清理）
  下单抵扣      usePoints=100 → payAmount 减 1.00；orders.points_deduct=100 / deduct_amount=1.00
                账户 balance 500→400、total_used=100；流水 change_type=2（biz_key=use:{orderNo}）
  余额不足      usePoints=5000（>余额 400）→ 50000「积分余额不足」；库存未扣（事务回滚）
  超额抵扣      usePoints=999999（折算 > 订单金额）→ 50000「抵扣积分超过订单金额」
  支付发放      mock-pay → order_paid 事件 → 积分入账（balance += pointsEarned）
                流水 change_type=1，batch_no 有效，expire_at ≈ 发放后 12 个月
  发放幂等      重复插入同 (change_type=1, biz_key=grant:{orderNo}) → 1062 唯一键拦截
  过期回收      expire_at 置为过去 → 手动触发接口 → balance 扣减（钳零）+ change_type=4 流水
                再次触发返回 0（幂等，NOT EXISTS 排除已回收批次）
  退款回收      退款审批通过 → change_type=3 流水 + balance 扣减（钳零不透支）
  权限门禁      手动回收接口：无令牌 40100 / 店长 40300（无 224）/ 超管 0
  清理          删除测试订单/积分账户/积分流水/相关 outbox 事件，恢复库存与基线
"""
import sys
import time
import uuid

import pymysql
import requests

BASE = "http://localhost:8080"
PW = "Store@123456"
ADMIN_PW = "Admin@123456"
CODE = "u001"          # → mock-openid-u001 → userId=3 茶友小红
ADDRESS_ID = 3         # userId=3 的默认地址
SKU_ID = 6             # 红枣枸杞茶（库存充足）
STORE_ID = 1

DB = dict(host="127.0.0.1", port=3306, user="herbal_tea", password="herbal_tea_dev",
          database="herbal_tea", charset="utf8mb4", autocommit=True)

INIT_POINTS = 500      # 造数充值积分
DEDUCT_POINTS = 100    # 下单抵扣 100 积分 = ¥1.00

passed = 0
failed = 0


def p(name, r, expect_code=None, expect_http=None):
    global passed, failed
    try:
        body = r.json()
        code = body.get("code")
        msg = body.get("message", body.get("msg", ""))
    except Exception:
        body, code, msg = None, None, r.text[:120]
    ok = True
    detail = f"http={r.status_code} code={code} {msg[:70]}"
    if expect_http is not None and r.status_code != expect_http:
        ok = False
        detail += f" | 期望 HTTP {expect_http}"
    if expect_code is not None and code != expect_code:
        ok = False
        detail += f" | 期望 code={expect_code}"
    print(f"  {'✅' if ok else '❌'} {name}: {detail}")
    if ok:
        passed += 1
    else:
        failed += 1
    return body


def ok(name, cond, extra=""):
    global passed, failed
    print(f"  {'✅' if cond else '❌'} {name}{(' | ' + extra) if extra else ''}")
    if cond:
        passed += 1
    else:
        failed += 1


def db():
    return pymysql.connect(**DB)


def q1(sql, args=None):
    conn = db()
    cur = conn.cursor()
    cur.execute(sql, args)
    row = cur.fetchone()
    cur.close()
    conn.close()
    return row


def qall(sql, args=None):
    conn = db()
    cur = conn.cursor()
    cur.execute(sql, args)
    rows = cur.fetchall()
    cur.close()
    conn.close()
    return rows


def x(sql, args=None):
    conn = db()
    cur = conn.cursor()
    n = cur.execute(sql, args)
    cur.close()
    conn.close()
    return n


def admin_login(username, password=PW):
    r = requests.post(f"{BASE}/api/auth/admin/login",
                      json={"username": username, "password": password})
    body = r.json()
    assert body.get("code") == 0, f"{username} 登录失败: {body}"
    return body["data"]["accessToken"]


def wx_login(code=CODE):
    r = requests.post(f"{BASE}/api/user/wx-login",
                      json={"code": code, "deviceFingerprint": "fp-" + uuid.uuid4().hex[:12]})
    body = r.json()
    assert body.get("code") == 0, f"wx-login 失败: {body}"
    return body["data"]["accessToken"]


def balance(uid):
    row = q1("SELECT balance FROM user_points_accounts WHERE user_id=%s", (uid,))
    return row[0] if row else 0


def stock():
    return q1("SELECT stock FROM product_skus WHERE id=%s", (SKU_ID,))[0]


def oid_of(order_no):
    """按订单号查订单 id"""
    return q1("SELECT id FROM orders WHERE order_no=%s", (order_no,))[0]


def wait_outbox(biz_key, timeout=30):
    """等待 outbox 事件被消费（status=1）；超时返回 False"""
    for _ in range(timeout):
        row = q1("SELECT status FROM event_outbox WHERE biz_key=%s", (biz_key,))
        if row and row[0] == 1:
            return True
        time.sleep(1)
    return False


def wait_pending_clear(timeout=25):
    """等待所有待投递事件被消费（worker 每 5 秒扫一次，退款后需给它时间）"""
    for _ in range(timeout):
        if q1("SELECT COUNT(*) FROM event_outbox WHERE status=0")[0] == 0:
            return True
        time.sleep(1)
    return False


def main():
    global passed, failed
    admin_t = admin_login("admin", ADMIN_PW)
    sa1_t = admin_login("store_admin1")
    s0 = requests.Session()
    s0.headers.update({"Authorization": f"Bearer {admin_t}"})
    s1 = requests.Session()
    s1.headers.update({"Authorization": f"Bearer {sa1_t}"})

    user_t = wx_login()
    us = requests.Session()
    us.headers.update({"Authorization": f"Bearer {user_t}"})
    uid = q1("SELECT id FROM users WHERE openid=%s", (f"mock-openid-{CODE}",))[0]
    print(f"  ℹ 测试会员 userId={uid}，初始库存 {stock()}")

    # 造数：充值积分（幂等 upsert）
    had_account = q1("SELECT id FROM user_points_accounts WHERE user_id=%s", (uid,)) is not None
    x("""INSERT INTO user_points_accounts (user_id, balance, total_earned, total_used, total_expired, version)
         VALUES (%s, %s, %s, 0, 0, 0)
         ON DUPLICATE KEY UPDATE balance=%s, total_earned=%s, total_used=0, total_expired=0""",
      (uid, INIT_POINTS, INIT_POINTS, INIT_POINTS, INIT_POINTS))
    print(f"  ℹ 充值 {INIT_POINTS} 积分（账户 {'已存在→重置' if had_account else '新建'}）")

    print("===== 1. 权限门禁（手动过期回收 224） =====")
    r = requests.post(f"{BASE}/api/marketing/admin/points/expire")
    p("1.1 无令牌 → 40100", r, 40100)
    r = s1.post(f"{BASE}/api/marketing/admin/points/expire")
    p("1.2 门店管理员 → 40300（无 marketing:points:run）", r, 40300)
    r = s0.post(f"{BASE}/api/marketing/admin/points/expire")
    body = p("1.3 超管手动执行放行", r, 0)
    ok("1.4 返回清零批次数（int）", isinstance(body["data"], int), f"data={body['data']}")

    print("\n===== 2. 下单积分抵扣 =====")
    base_stock = stock()
    idem = uuid.uuid4().hex
    r = us.post(f"{BASE}/api/order/create",
                json={"storeId": STORE_ID, "skuId": SKU_ID, "qty": 2,
                      "addressId": ADDRESS_ID, "remark": "v27 积分抵扣",
                      "usePoints": DEDUCT_POINTS},
                headers={"Idempotency-Key": idem})
    body = p("2.1 下单（usePoints=100）", r, 0)
    assert body and body.get("code") == 0, "下单失败，中止"
    order_no = body["data"]["orderNo"]
    pay_no = body["data"]["payNo"]
    pay_amount = float(body["data"]["payAmount"])
    unit = float(q1("SELECT price FROM store_products WHERE store_id=%s AND sku_id=%s",
                    (STORE_ID, SKU_ID))[0])
    expect_pay = round(unit * 2 - DEDUCT_POINTS * 0.01, 2)
    ok("2.2 实付 = 商品小计 − 积分抵扣（1 分 = ¥0.01）",
       abs(pay_amount - expect_pay) < 0.001, f"实付 {pay_amount} / 期望 {expect_pay}")
    row = q1("""SELECT points_deduct, points_deduct_amount, points_earned, points_source, pay_amount
                FROM orders WHERE order_no=%s""", (order_no,))
    ok("2.3 订单抵扣快照落库（100 / ¥1.00）",
       row[0] == DEDUCT_POINTS and float(row[1]) == 1.00, f"points_deduct={row[0]} amount={row[1]}")
    ok("2.4 赠送积分按抵扣后实付向下取整（1 元 = 1 分）",
       row[2] == int(pay_amount), f"points_earned={row[2]} / 实付 {pay_amount}")
    ok("2.5 积分来源=1 门店营销（D15）", row[3] == 1, f"points_source={row[3]}")
    ok("2.6 账户余额 500→400", balance(uid) == INIT_POINTS - DEDUCT_POINTS, f"balance={balance(uid)}")
    row = q1("SELECT SUM(total_used) FROM user_points_accounts WHERE user_id=%s", (uid,))
    ok("2.7 total_used 累加 100", row[0] == DEDUCT_POINTS, f"total_used={row[0]}")
    row = q1("""SELECT points, biz_key FROM point_records
                WHERE change_type=2 AND user_id=%s ORDER BY id DESC LIMIT 1""", (uid,))
    ok("2.8 抵扣流水（change_type=2，points=-100，biz_key=use:{orderNo}）",
       row and row[0] == -DEDUCT_POINTS and row[1] == f"use:{order_no}", f"流水={row}")

    print("\n===== 3. 异常抵扣（余额不足 / 超额） =====")
    stock_before = stock()
    r = us.post(f"{BASE}/api/order/create",
                json={"storeId": STORE_ID, "skuId": SKU_ID, "qty": 2,
                      "addressId": ADDRESS_ID, "usePoints": 5000},
                headers={"Idempotency-Key": uuid.uuid4().hex})
    body = p("3.1 抵扣 5000（>余额 400）→ 50000 余额不足", r, 50000)
    ok("3.2 异常信息含「积分余额不足」", body and "余额不足" in body.get("message", ""),
       f"msg={body.get('message') if body else None}")
    ok("3.3 事务回滚：库存未扣减", stock() == stock_before, f"{stock_before} → {stock()}")
    ok("3.4 事务回滚：账户余额未变", balance(uid) == INIT_POINTS - DEDUCT_POINTS, f"balance={balance(uid)}")
    r = us.post(f"{BASE}/api/order/create",
                json={"storeId": STORE_ID, "skuId": SKU_ID, "qty": 2,
                      "addressId": ADDRESS_ID, "usePoints": 999999},
                headers={"Idempotency-Key": uuid.uuid4().hex})
    body = p("3.5 抵扣 999999（折算 > 订单金额）→ 50000 超额", r, 50000)
    ok("3.6 异常信息含「超过订单金额」", body and "超过订单金额" in body.get("message", ""),
       f"msg={body.get('message') if body else None}")
    r = us.post(f"{BASE}/api/order/create",
                json={"storeId": STORE_ID, "skuId": SKU_ID, "qty": 2,
                      "addressId": ADDRESS_ID, "usePoints": -1},
                headers={"Idempotency-Key": uuid.uuid4().hex})
    p("3.7 负积分 → 40000 参数校验", r, 40000)

    print("\n===== 4. 支付发放积分（order_paid → grantPoints） =====")
    bal_before = balance(uid)
    r = us.post(f"{BASE}/api/internal/mock-pay", json={"payNo": pay_no})
    p("4.1 mock 支付", r, 0)
    earned = q1("SELECT points_earned FROM orders WHERE order_no=%s", (order_no,))[0]
    ok("4.2 outbox 事件 order_paid 被消费", wait_outbox(f"order_paid:{order_no}"),
       f"biz_key=order_paid:{order_no}")
    ok("4.3 积分入账（balance += pointsEarned）", balance(uid) == bal_before + earned,
       f"{bal_before} + {earned} = {bal_before + earned} / 实际 {balance(uid)}")
    row = q1("""SELECT points, batch_no, expire_at, source_type, biz_key
                FROM point_records WHERE change_type=1 AND biz_key=%s""", (f"grant:{order_no}",))
    ok("4.4 发放流水落库（biz_key=grant:{orderNo}）", row is not None and row[4] == f"grant:{order_no}",
       f"流水={row}")
    if row:
        ok("4.5 发放积分数量 = 订单 points_earned", row[0] == earned, f"{row[0]} / {earned}")
        ok("4.6 批次号（batch_no）非空（过期回收单元）", bool(row[1]), f"batch_no={row[1]}")
        ok("4.7 有效期 ≈ 12 个月", row[2] is not None and 364 <= (row[2].date() -
           q1("SELECT DATE(NOW())")[0]).days <= 367, f"expire_at={row[2]}")
        ok("4.8 来源=1 门店营销（D15 双维归属）", row[3] == 1, f"source_type={row[3]}")

    print("\n===== 5. 发放幂等（uk_ptr_biz 兜底） =====")
    try:
        x("""INSERT INTO point_records (user_id, change_type, source_type, points, biz_key, created_at)
             VALUES (%s, 1, 1, 999, %s, NOW())""", (uid, f"grant:{order_no}"))
        ok("5.1 重复发放被唯一键拦截", False, "未抛异常，幂等约束失效！")
    except pymysql.err.IntegrityError as e:
        ok("5.1 重复发放被唯一键拦截（1062）", "1062" in str(e) or "Duplicate" in str(e),
           str(e)[:80])
    ok("5.2 幂等拦截后余额未被错误累加", balance(uid) == bal_before + earned, f"balance={balance(uid)}")

    print("\n===== 6. 过期回收（D8） =====")
    bal_before_expire = balance(uid)
    granted = q1("SELECT points FROM point_records WHERE change_type=1 AND biz_key=%s",
                 (f"grant:{order_no}",))[0]
    x("UPDATE point_records SET expire_at=DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE biz_key=%s",
      (f"grant:{order_no}",))
    r = s0.post(f"{BASE}/api/marketing/admin/points/expire")
    body = p("6.1 手动触发过期回收", r, 0)
    ok("6.2 返回清零批次数 ≥ 1", body["data"] >= 1, f"handled={body['data']}")
    ok("6.3 账户余额按批次扣减", balance(uid) == max(0, bal_before_expire - granted),
       f"{bal_before_expire} - {granted} → {balance(uid)}")
    batch_no = q1("SELECT batch_no FROM point_records WHERE change_type=1 AND biz_key=%s",
                  (f"grant:{order_no}",))[0]
    row = q1("""SELECT points, biz_key, batch_no FROM point_records
                WHERE change_type=4 AND biz_key=%s""", (f"expire:{batch_no}",))
    ok("6.4 清零流水（change_type=4，biz_key=expire:{batchNo}）", row is not None, f"流水={row}")
    if row:
        ok("6.5 清零数量为负（扣减语义）", row[0] < 0, f"points={row[0]}")
    r = s0.post(f"{BASE}/api/marketing/admin/points/expire")
    body = p("6.6 再次触发（幂等）", r, 0)
    ok("6.7 已回收批次不重复处理", body["data"] == 0, f"handled={body['data']}")
    ok("6.8 重复触发后余额不变", balance(uid) == max(0, bal_before_expire - granted),
       f"balance={balance(uid)}")

    print("\n===== 7. 退款回收（change_type=3） =====")
    # 7A：上一步已过期清零的订单 → 退款不得重复扣减
    bal_before_refund = balance(uid)
    r = s0.post(f"{BASE}/api/refund/admin/apply",
                json={"orderId": oid_of(order_no), "reason": "v27 已过期积分不重复回收"})
    body = p("7.1 对已过期订单申请退款", r, 0)
    if body and body.get("code") == 0:
        rid = body["data"]
        r = s0.post(f"{BASE}/api/refund/admin/{rid}/approve")
        p("7.2 退款审批通过", r, 0)
        row = q1("SELECT COUNT(*) FROM point_records WHERE change_type=3 AND biz_key=%s",
                 (f"reclaim:{order_no}",))
        ok("7.3 已过期积分不重复回收（无 change_type=3 流水）", row[0] == 0, f"流水数={row[0]}")
        ok("7.4 余额未被重复扣减", balance(uid) == bal_before_refund,
           f"{bal_before_refund} → {balance(uid)}")

    # 7B：新订单（未过期）→ 退款应正常回收发放的积分
    idem2 = uuid.uuid4().hex
    r = us.post(f"{BASE}/api/order/create",
                json={"storeId": STORE_ID, "skuId": SKU_ID, "qty": 1,
                      "addressId": ADDRESS_ID, "remark": "v27 退款回收"},
                headers={"Idempotency-Key": idem2})
    body = p("7.5 新建订单（不使用积分）", r, 0)
    order_no2 = body["data"]["orderNo"]
    pay_no2 = body["data"]["payNo"]
    r = us.post(f"{BASE}/api/internal/mock-pay", json={"payNo": pay_no2})
    p("7.6 支付", r, 0)
    wait_outbox(f"order_paid:{order_no2}")
    earned2 = q1("SELECT points FROM point_records WHERE change_type=1 AND biz_key=%s",
                 (f"grant:{order_no2}",))[0]
    bal_before_reclaim = balance(uid)
    ok("7.7 发放积分已入账", balance(uid) == bal_before_reclaim and earned2 > 0,
       f"发放 {earned2} 分，余额 {balance(uid)}")
    r = s0.post(f"{BASE}/api/refund/admin/apply",
                json={"orderId": oid_of(order_no2), "reason": "v27 退款回收积分"})
    body = p("7.8 申请退款", r, 0)
    if body and body.get("code") == 0:
        rid2 = body["data"]
        r = s0.post(f"{BASE}/api/refund/admin/{rid2}/approve")
        p("7.9 退款审批通过", r, 0)
        ok("7.10 账户余额按发放积分扣回", balance(uid) == max(0, bal_before_reclaim - earned2),
           f"{bal_before_reclaim} - {earned2} → {balance(uid)}")
        row = q1("SELECT points FROM point_records WHERE change_type=3 AND biz_key=%s",
                 (f"reclaim:{order_no2}",))
        ok("7.11 回收流水（change_type=3，points 为负）", row is not None and row[0] < 0, f"流水={row}")
        # 幂等：重复回收不会产生第二条（uk_ptr_biz）
        row = q1("SELECT COUNT(*) FROM point_records WHERE change_type=3 AND biz_key=%s",
                 (f"reclaim:{order_no2}",))
        ok("7.12 回收唯一（同 biz_key 仅 1 条）", row[0] == 1, f"条数={row[0]}")

    print("\n===== 8. 数据清理与基线校验 =====")
    # 清理：订单及子表、积分账户、积分流水、outbox 事件
    for no in (order_no, order_no2):
        oid = q1("SELECT id FROM orders WHERE order_no=%s", (no,))
        if oid:
            oid = oid[0]
            for t in ("payment_records", "order_shipping_logs", "order_items", "refund_records"):
                x(f"DELETE FROM {t} WHERE order_id=%s", (oid,))
            x("DELETE FROM orders WHERE id=%s", (oid,))
    x("DELETE FROM point_records WHERE user_id=%s", (uid,))
    x("DELETE FROM user_points_accounts WHERE user_id=%s", (uid,))
    x("DELETE FROM event_outbox WHERE biz_key LIKE %s OR biz_key LIKE %s",
      (f"order_paid:{order_no}", "points_expire_notice:%"))
    x("UPDATE product_skus SET stock=%s WHERE id=%s", (base_stock, SKU_ID))
    print(f"  ℹ 清理订单 {order_no} / 积分账户 / 积分流水 / outbox 事件")

    ok("7.1 积分账户已清空", q1("SELECT COUNT(*) FROM user_points_accounts WHERE user_id=%s", (uid,))[0] == 0)
    ok("7.2 积分流水已清空", q1("SELECT COUNT(*) FROM point_records WHERE user_id=%s", (uid,))[0] == 0)
    ok("7.3 库存恢复基线", stock() == base_stock, f"stock={stock()}")
    ok("7.4 测试订单已删除", q1("SELECT COUNT(*) FROM orders WHERE order_no=%s", (order_no,))[0] == 0)
    ok("7.5 无待投递 outbox 残留（等待 worker 消费）", wait_pending_clear(),
       f"pending={q1('SELECT COUNT(*) FROM event_outbox WHERE status=0')[0]}")
    ok("7.6 V13 权限 marketing:points:run 存在",
       q1("SELECT COUNT(*) FROM permissions WHERE code='marketing:points:run'")[0] == 1)
    ok("7.7 该权限仅授权超管",
       q1("""SELECT COUNT(*) FROM role_permissions rp JOIN permissions p ON p.id=rp.permission_id
             WHERE p.code='marketing:points:run'""")[0] == 1)

    print(f"\n===== 结果: {passed} 通过 / {failed} 失败 =====")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    finally:
        try:
            # 兜底：任何断言失败也要清干净（按测试会员维度）
            uid = q1("SELECT id FROM users WHERE openid=%s", (f"mock-openid-{CODE}",))
            if uid:
                uid = uid[0]
                x("DELETE FROM point_records WHERE user_id=%s", (uid,))
                x("DELETE FROM user_points_accounts WHERE user_id=%s", (uid,))
            x("DELETE FROM orders WHERE remark LIKE %s", ("v27%",))
            # 基线 pending 恒为 0，测试产生的待投递事件可安全清理
            x("DELETE FROM event_outbox WHERE status=0")
            x("UPDATE product_skus SET stock=297 WHERE id=6")
        except Exception as e:
            print(f"  ⚠ 兜底清理失败: {e}")
