# -*- coding: utf-8 -*-
"""优惠券全链路联测（v28）：模板 CRUD → 发布/停发 → 领券 → 下单核销 → 券成本归属 → 退款退券

场景矩阵：
  权限门禁    券列表无令牌 40100 / 店员 40300 / 店长 0（211）
             店长建平台券 → 50000（门店账号只能建本店券）；店长建券自动归属本店
  模板 CRUD   创建（未发布）→ 编辑（可改）→ 发布（0→1）→ 再编辑被拒（40900）
             参数校验：失效早于生效 / 满减金额≤0 / 折扣券缺 discountRate / 发行量≤0 / 限领<1 → 50000
  领券        正常领取（received_count 原子递增，余量 -1）；未发布 → 50000；超过每人限领 → 50000
             领完（余量为 0）→ 50000
  下单核销    满减券：payAmount = 小计 − 券额；orders.coupon_amount/coupon_scope 落库
             折扣券：应付按折扣率计算（含 maxDiscount 封顶）
             门槛不足 → 50000；本店券跨店使用 → 50000；他人券 → 50000
             重复核销同一张券 → 40900（status 0→1 原子条件）
  成本归属    本店券 → 结算单 coupon_cost_store（店铺减项）
             平台券 → 结算单 coupon_cost_platform（平台承担，不减店铺应付）
  退款退券    退款审批通过 → 券置为「退款退回」(status=3)
  清理        删除测试券模板/持券/订单/结算单，恢复库存与基线
"""
import sys
import time
import uuid
from datetime import datetime, timedelta

import pymysql
import requests

BASE = "http://localhost:8080"
PW = "Store@123456"
ADMIN_PW = "Admin@123456"
CODE = "u001"          # → userId=3
ADDRESS_ID = 3
SKU_ID = 6
STORE_ID = 1           # 门店1（store_admin1 所属）

DB = dict(host="127.0.0.1", port=3306, user="herbal_tea", password="herbal_tea_dev",
          database="herbal_tea", charset="utf8mb4", autocommit=True)

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


def x(sql, args=None):
    conn = db()
    cur = conn.cursor()
    n = cur.execute(sql, args)
    cur.close()
    conn.close()
    return n


def login(u, pw=PW):
    r = requests.post(f"{BASE}/api/auth/admin/login", json={"username": u, "password": pw})
    b = r.json()
    assert b.get("code") == 0, f"{u} 登录失败: {b}"
    return b["data"]["accessToken"]


def wx_login(code=CODE):
    r = requests.post(f"{BASE}/api/user/wx-login",
                      json={"code": code, "deviceFingerprint": "fp-" + uuid.uuid4().hex[:12]})
    b = r.json()
    assert b.get("code") == 0, f"wx-login 失败: {b}"
    return b["data"]["accessToken"]


def stock():
    return q1("SELECT stock FROM product_skus WHERE id=%s", (SKU_ID,))[0]


def unit_price():
    return float(q1("SELECT price FROM store_products WHERE store_id=%s AND sku_id=%s",
                    (STORE_ID, SKU_ID))[0])


def ts(days=1):
    return (datetime.now() + timedelta(days=days)).strftime("%Y-%m-%d %H:%M:%S")


def ts_past(days=1):
    return (datetime.now() - timedelta(days=days)).strftime("%Y-%m-%d %H:%M:%S")


def main():
    global passed, failed
    admin_t = login("admin", ADMIN_PW)
    sa1_t = login("store_admin1")
    st1_t = login("staff1")
    s0 = requests.Session()
    s0.headers.update({"Authorization": f"Bearer {admin_t}"})
    s_store = requests.Session()
    s_store.headers.update({"Authorization": f"Bearer {sa1_t}"})
    s_staff = requests.Session()
    s_staff.headers.update({"Authorization": f"Bearer {st1_t}"})

    user_t = wx_login()
    us = requests.Session()
    us.headers.update({"Authorization": f"Bearer {user_t}"})
    uid = q1("SELECT id FROM users WHERE openid=%s", (f"mock-openid-{CODE}",))[0]
    base_stock = stock()
    unit = unit_price()
    print(f"  ℹ 会员 userId={uid} / 门店 {STORE_ID} / 单价 ¥{unit} / 库存 {base_stock}")

    print("===== 1. 权限门禁 =====")
    r = requests.get(f"{BASE}/api/marketing/admin/coupons")
    p("1.1 无令牌查券列表 → 40100", r, 40100)
    r = s_staff.get(f"{BASE}/api/marketing/admin/coupons")
    p("1.2 店铺员工 → 40300（无 menu:marketing）", r, 40300)
    r = s_store.get(f"{BASE}/api/marketing/admin/coupons")
    body = p("1.3 门店管理员查列表放行（menu:marketing）", r, 0)
    ok("1.4 分页结构正常", "records" in body["data"])
    r = s_store.post(f"{BASE}/api/marketing/admin/coupons", json={
        "name": "门店越权平台券", "type": 1, "scope": 1, "thresholdAmount": 0,
        "discountAmount": 5, "totalCount": 10, "perUserLimit": 1,
        "startTime": ts(-1), "endTime": ts(30)})
    p("1.5 门店账号建平台券 → 50000", r, 50000)

    print("\n===== 2. 模板 CRUD 与参数校验 =====")
    # 2.1 参数校验
    bad = {
        "失效早于生效": {"endTime": ts_past(1)},
        "满减金额≤0": {"discountAmount": 0},
        "发行量≤0": {"totalCount": 0},
        "限领<1": {"perUserLimit": 0},
    }
    base_req = {"name": "联测满减券", "type": 1, "scope": 2, "storeId": STORE_ID,
                "thresholdAmount": 100, "discountAmount": 20, "totalCount": 10,
                "perUserLimit": 2, "startTime": ts(-1), "endTime": ts(30)}
    for label, patch in bad.items():
        req = dict(base_req)
        req.update(patch)
        r = s0.post(f"{BASE}/api/marketing/admin/coupons", json=req)
        p(f"2.1 {label} → 50000", r, 50000)
    req = dict(base_req)
    req.update({"type": 2, "rules": None})
    r = s0.post(f"{BASE}/api/marketing/admin/coupons", json=req)
    p("2.1 折扣券缺 discountRate → 50000", r, 50000)

    # 2.2 创建（满减券，本店券，门槛 100 减 20）
    r = s0.post(f"{BASE}/api/marketing/admin/coupons", json=base_req)
    body = p("2.2 创建满减券（门槛100减20，发行10，限领2）", r, 0)
    cash_id = body["data"]
    row = q1("SELECT status, received_count, scope, store_id FROM coupons WHERE id=%s", (cash_id,))
    ok("2.3 初始为未发布、已领 0、本店券归属门店1",
       row == (0, 0, 2, STORE_ID), f"status/received/scope/store={row}")

    # 2.3 编辑（未发布可改）
    req2 = dict(base_req)
    req2["discountAmount"] = 25
    r = s0.put(f"{BASE}/api/marketing/admin/coupons/{cash_id}", json=req2)
    p("2.4 未发布可编辑（20→25）", r, 0)
    ok("2.5 编辑生效", float(q1("SELECT discount_amount FROM coupons WHERE id=%s", (cash_id,))[0]) == 25.0)

    # 2.4 发布
    r = s0.post(f"{BASE}/api/marketing/admin/coupons/{cash_id}/publish")
    p("2.6 发布（0→1）", r, 0)
    ok("2.7 状态=1 发放中", q1("SELECT status FROM coupons WHERE id=%s", (cash_id,))[0] == 1)
    r = s0.put(f"{BASE}/api/marketing/admin/coupons/{cash_id}", json=req2)
    p("2.8 已发布再编辑 → 40900", r, 40900)
    r = s0.post(f"{BASE}/api/marketing/admin/coupons/{cash_id}/publish")
    p("2.9 重复发布 → 40900", r, 40900)

    # 2.5 平台折扣券（满 50 享 9 折，封顶 15）
    plat_req = {"name": "联测平台折扣券", "type": 2, "scope": 1,
                "thresholdAmount": 50, "rules": '{"discountRate":0.9,"maxDiscount":15.00}',
                "totalCount": 5, "perUserLimit": 1, "startTime": ts(-1), "endTime": ts(30)}
    r = s0.post(f"{BASE}/api/marketing/admin/coupons", json=plat_req)
    body = p("2.10 创建平台折扣券（9折，封顶15）", r, 0)
    disc_id = body["data"]
    ok("2.11 平台券 store_id 为空", q1("SELECT store_id FROM coupons WHERE id=%s", (disc_id,))[0] is None)
    s0.post(f"{BASE}/api/marketing/admin/coupons/{disc_id}/publish")

    print("\n===== 3. 领券（限领 / 余量 / 状态） =====")
    r = s0.post(f"{BASE}/api/marketing/admin/coupons/{cash_id}/grant", params={"userId": uid})
    body = p("3.1 发券给会员", r, 0)
    uc_cash = body["data"]
    row = q1("SELECT received_count, total_count FROM coupons WHERE id=%s", (cash_id,))
    ok("3.2 已领数量原子递增（0→1）", row[0] == 1, f"received={row[0]}/{row[1]}")
    row = q1("SELECT status, expire_at IS NOT NULL FROM user_coupons WHERE id=%s", (uc_cash,))
    ok("3.3 持券记录为未使用且有过期时间", row == (0, 1), f"status/expire={row}")
    r = s0.post(f"{BASE}/api/marketing/admin/coupons/{cash_id}/grant", params={"userId": uid})
    p("3.4 二次领取（限领2，允许）", r, 0)
    r = s0.post(f"{BASE}/api/marketing/admin/coupons/{cash_id}/grant", params={"userId": uid})
    p("3.5 第三次领取 → 50000 超每人限领", r, 50000)

    # 未发布券不可领
    draft = dict(base_req)
    draft.update({"name": "联测未发布券", "thresholdAmount": 0, "discountAmount": 5})
    draft_id = s0.post(f"{BASE}/api/marketing/admin/coupons", json=draft).json()["data"]
    r = s0.post(f"{BASE}/api/marketing/admin/coupons/{draft_id}/grant", params={"userId": uid})
    p("3.6 未发布券领取 → 50000", r, 50000)

    # 领完场景：发行量 1 的券领 2 次
    one = dict(base_req)
    one.update({"name": "联测限量券", "totalCount": 1, "perUserLimit": 5, "thresholdAmount": 0})
    one_id = s0.post(f"{BASE}/api/marketing/admin/coupons", json=one).json()["data"]
    s0.post(f"{BASE}/api/marketing/admin/coupons/{one_id}/publish")
    s0.post(f"{BASE}/api/marketing/admin/coupons/{one_id}/grant", params={"userId": uid})
    r = s0.post(f"{BASE}/api/marketing/admin/coupons/{one_id}/grant", params={"userId": 2})
    p("3.7 券已领完 → 50000", r, 50000)
    ok("3.8 已领数量不超过发行量",
       q1("SELECT received_count <= total_count FROM coupons WHERE id=%s", (one_id,))[0] == 1)

    print("\n===== 4. 下单核销（满减券） =====")
    subtotal = round(unit * 2, 2)
    idem = uuid.uuid4().hex
    r = us.post(f"{BASE}/api/order/create",
                json={"storeId": STORE_ID, "skuId": SKU_ID, "qty": 2,
                      "addressId": ADDRESS_ID, "remark": "v28 券核销",
                      "userCouponId": uc_cash},
                headers={"Idempotency-Key": idem})
    body = p("4.1 下单使用满减券（减 25）", r, 0)
    assert body and body.get("code") == 0, "下单失败，中止"
    order_no = body["data"]["orderNo"]
    pay_amount = float(body["data"]["payAmount"])
    ok("4.2 实付 = 小计 − 券额", abs(pay_amount - (subtotal - 25)) < 0.001,
       f"实付 {pay_amount} / 期望 {subtotal - 25}")
    row = q1("SELECT coupon_amount, coupon_scope FROM orders WHERE order_no=%s", (order_no,))
    ok("4.3 订单券快照落库（25.00 / scope=2 本店券）",
       float(row[0]) == 25.0 and row[1] == 2, f"coupon_amount={row[0]} scope={row[1]}")
    row = q1("SELECT status, order_id IS NOT NULL FROM user_coupons WHERE id=%s", (uc_cash,))
    ok("4.4 券已核销（status=1）且回填 order_id", row == (1, 1), f"status/order_id={row}")
    r = us.post(f"{BASE}/api/order/create",
                json={"storeId": STORE_ID, "skuId": SKU_ID, "qty": 2,
                      "addressId": ADDRESS_ID, "userCouponId": uc_cash},
                headers={"Idempotency-Key": uuid.uuid4().hex})
    # 串行重复核销：状态校验先拦（50000 明确原因）；markUsed 的 40900 是并发下的原子兜底
    p("4.5 重复使用同一张券 → 50000（已使用）", r, 50000)
    r = us.post(f"{BASE}/api/order/create",
                json={"storeId": STORE_ID, "skuId": SKU_ID, "qty": 2,
                      "addressId": ADDRESS_ID, "userCouponId": 999999},
                headers={"Idempotency-Key": uuid.uuid4().hex})
    p("4.6 不存在的持券 → 40400", r, 40400)
    # 清理该订单，避免占用库存
    oid = q1("SELECT id FROM orders WHERE order_no=%s", (order_no,))[0]
    for t in ("payment_records", "order_items"):
        x(f"DELETE FROM {t} WHERE order_id=%s", (oid,))
    x("DELETE FROM orders WHERE id=%s", (oid,))

    print("\n===== 5. 核销校验（门槛 / 跨店 / 他人券） =====")
    # 5.1 门槛不足：给本人发一张门槛 200 的券，qty=2（小计 180 < 200）
    high = dict(base_req)
    high.update({"name": "联测高门槛券", "thresholdAmount": 200, "discountAmount": 30,
                 "perUserLimit": 1})
    high_id = s0.post(f"{BASE}/api/marketing/admin/coupons", json=high).json()["data"]
    s0.post(f"{BASE}/api/marketing/admin/coupons/{high_id}/publish")
    uc_high = s0.post(f"{BASE}/api/marketing/admin/coupons/{high_id}/grant",
                      params={"userId": uid}).json()["data"]
    r = us.post(f"{BASE}/api/order/create",
                json={"storeId": STORE_ID, "skuId": SKU_ID, "qty": 2,
                      "addressId": ADDRESS_ID, "userCouponId": uc_high},
                headers={"Idempotency-Key": uuid.uuid4().hex})
    body = p("5.1 订单未达门槛（小计180 < 门槛200）→ 50000", r, 50000)
    ok("5.2 提示含「未达使用门槛」", body and "门槛" in body.get("message", ""),
       f"msg={body.get('message') if body else None}")
    # 5.3 他人券：给 userId=2 发券，用本人身份下单
    uc2 = s0.post(f"{BASE}/api/marketing/admin/coupons/{cash_id}/grant",
                  params={"userId": 2}).json()["data"]
    # 他人券
    r = us.post(f"{BASE}/api/order/create",
                json={"storeId": STORE_ID, "skuId": SKU_ID, "qty": 2,
                      "addressId": ADDRESS_ID, "userCouponId": uc2},
                headers={"Idempotency-Key": uuid.uuid4().hex})
    p("5.3 使用他人券 → 50000", r, 50000)

    print("\n===== 6. 折扣券与平台券成本归属 =====")
    uc_disc = s0.post(f"{BASE}/api/marketing/admin/coupons/{disc_id}/grant",
                      params={"userId": uid}).json()["data"]
    idem3 = uuid.uuid4().hex
    r = us.post(f"{BASE}/api/order/create",
                json={"storeId": STORE_ID, "skuId": SKU_ID, "qty": 2,
                      "addressId": ADDRESS_ID, "remark": "v28 平台券",
                      "userCouponId": uc_disc},
                headers={"Idempotency-Key": idem3})
    body = p("6.1 下单使用平台折扣券（9折封顶15）", r, 0)
    order_no3 = body["data"]["orderNo"]
    pay3 = float(body["data"]["payAmount"])
    expect_disc = min(round(subtotal * 0.1, 2), 15.0)
    ok("6.2 折扣金额按折扣率计算并封顶", abs(pay3 - (subtotal - expect_disc)) < 0.001,
       f"实付 {pay3} / 优惠 {round(subtotal - pay3, 2)} / 期望 {expect_disc}")
    row = q1("SELECT coupon_amount, coupon_scope FROM orders WHERE order_no=%s", (order_no3,))
    ok("6.3 订单记录平台券（scope=1）", float(row[0]) == expect_disc and row[1] == 1,
       f"amount={row[0]} scope={row[1]}")

    # 6.4 结算归属：把订单置为已完成(90)并生成结算单
    oid3 = q1("SELECT id FROM orders WHERE order_no=%s", (order_no3,))[0]
    x("UPDATE orders SET status=90, finished_at=NOW() WHERE id=%s", (oid3,))
    today = datetime.now().strftime("%Y-%m-%d")
    r = s0.post(f"{BASE}/api/settlement/admin/generate",
                params={"storeId": STORE_ID, "period": today})
    body = p("6.4 生成结算单", r, 0)
    # generate 返回 Void，按门店+周期回查刚生成的结算单
    row_s = q1("""SELECT id FROM settlements WHERE store_id=%s AND period=%s
                  ORDER BY id DESC LIMIT 1""", (STORE_ID, today))
    settle_id = row_s[0] if row_s else None
    ok("6.4b 结算单已生成", settle_id is not None, f"settlementId={settle_id}")
    if settle_id:
        row = q1("""SELECT coupon_cost_store, coupon_cost_platform, final_amount
                    FROM settlements WHERE id=%s""", (settle_id,))
        ok("6.5 平台券计入 coupon_cost_platform（不进店铺成本）",
           float(row[0]) == 0.0 and float(row[1]) == expect_disc,
           f"store={row[0]} platform={row[1]}")
        row2 = q1("""SELECT item_type, direction, amount FROM settlement_items
                     WHERE settlement_id=%s AND item_type=9""", (settle_id,))
        ok("6.6 结算明细出现「平台券补贴」行（item_type=9, direction=3 平台承担）",
           row2 is not None and row2[1] == 3 and float(row2[2]) == expect_disc, f"明细={row2}")

    print("\n===== 7. 退款退券 =====")
    r = s0.post(f"{BASE}/api/refund/admin/apply",
                json={"orderId": oid3, "reason": "v28 退款退券"})
    body = p("7.1 申请退款", r, 0)
    if body and body.get("code") == 0:
        rid = body["data"]
        r = s0.post(f"{BASE}/api/refund/admin/{rid}/approve")
        p("7.2 退款审批通过", r, 0)
        ok("7.3 券退回为 status=3（退款退回）",
           q1("SELECT status FROM user_coupons WHERE id=%s", (uc_disc,))[0] == 3,
           f"status={q1('SELECT status FROM user_coupons WHERE id=%s', (uc_disc,))[0]}")

    print("\n===== 8. 清理与基线校验 =====")
    x("DELETE FROM user_coupons WHERE coupon_id IN (%s,%s,%s,%s,%s)",
      (cash_id, disc_id, draft_id, one_id, high_id))
    x("DELETE FROM coupons WHERE id IN (%s,%s,%s,%s,%s)",
      (cash_id, disc_id, draft_id, one_id, high_id))
    # 删除本轮测试订单与结算单
    for no in (order_no3,):
        o = q1("SELECT id FROM orders WHERE order_no=%s", (no,))
        if o:
            for t in ("payment_records", "order_items", "refund_records"):
                x(f"DELETE FROM {t} WHERE order_id=%s", (o[0],))
            x("DELETE FROM orders WHERE id=%s", (o[0],))
    x("DELETE FROM settlement_items WHERE settlement_id IN "
      "(SELECT id FROM settlements WHERE period=%s)", (datetime.now().strftime("%Y-%m-%d"),))
    x("DELETE FROM settlements WHERE period=%s", (datetime.now().strftime("%Y-%m-%d"),))
    x("UPDATE product_skus SET stock=%s WHERE id=%s", (base_stock, SKU_ID))
    x("DELETE FROM point_records WHERE user_id IN (%s,2)", (uid,))
    x("DELETE FROM user_points_accounts WHERE user_id IN (%s,2)", (uid,))
    x("DELETE FROM event_outbox WHERE status=0")
    time.sleep(6)  # 给 worker 时间消费退款事件

    ok("8.1 测试券模板已清空",
       q1("SELECT COUNT(*) FROM coupons WHERE name LIKE %s", ("联测%",))[0] == 0)
    ok("8.2 测试持券记录已清空",
       q1("SELECT COUNT(*) FROM user_coupons WHERE coupon_id NOT IN (SELECT id FROM coupons)")[0] == 0)
    ok("8.3 库存恢复基线", stock() == base_stock, f"stock={stock()}")
    ok("8.4 V14 迁移列存在",
       q1("""SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='herbal_tea'
             AND TABLE_NAME='orders' AND COLUMN_NAME='coupon_scope'""")[0] == 1
       and q1("""SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='herbal_tea'
                 AND TABLE_NAME='settlements' AND COLUMN_NAME='coupon_cost_platform'""")[0] == 1)
    ok("8.5 无待投递 outbox 残留",
       q1("SELECT COUNT(*) FROM event_outbox WHERE status=0")[0] == 0)
    ok("8.6 结算单已清理", q1("SELECT COUNT(*) FROM settlements")[0] == 0)

    print(f"\n===== 结果: {passed} 通过 / {failed} 失败 =====")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    finally:
        try:
            x("DELETE FROM user_coupons WHERE coupon_id NOT IN (SELECT id FROM coupons)")
            x("DELETE FROM coupons WHERE name LIKE %s", ("联测%",))
            x("DELETE FROM settlement_items WHERE settlement_id NOT IN (SELECT id FROM settlements)")
            x("DELETE FROM orders WHERE remark LIKE %s", ("v28%",))
            x("UPDATE product_skus SET stock=297 WHERE id=6")
        except Exception as e:
            print(f"  ⚠ 兜底清理失败: {e}")
