# -*- coding: utf-8 -*-
"""促销活动全链路联测（v30）：活动 CRUD → 发布/结束 → C 端活动列表 → 下单自动计价 → 券/积分叠加 → 结算成本归属

场景矩阵：
  权限门禁    活动列表无令牌 40100 / 店员 40300 / 店长 0（105 menu:marketing 已含）
             店长建平台活动 → 50000（门店账号只能建本店活动）；店长建活动自动归属本店
             店长无 212 时写入接口 40300（由 212 授权驱动，本脚本断言店长可建本店活动）
  活动 CRUD   创建（草稿）→ 编辑 → 发布（0→1）→ 再编辑被拒 40900 → 重复发布 40900 → 结束（1→2）
             参数校验：结束早于开始 / 类型非法 / 折扣缺 discountRate / 满减金额≤0 / 归属非法 → 50000
  规则摘要    满减 → 「满 ¥100 减 ¥15」；折扣 → 「满 ¥50 打 9 折（最高减 ¥30）」
  C 端列表    按门店返回 平台活动 + 本店活动；不传 storeId 仅平台活动；草稿/已结束不出现在列表
  下单计价    活动 → 券 → 积分 顺序：subtotal 180
              本店满减(满100减15) → pay 165, promotion_scope=2
              平台满减(满100减20) 与本店并存 → 取最优 20（不叠加）, promotion_scope=1
              叠加本店券(满100减10) → coupon_amount=10；再叠加 500 积分 → 抵扣 5.00，实付 145
              未达门槛（小计 90 < 100）→ 不命中，promotion_discount=0
              折扣活动（9 折）→ 优惠 = 小计 × 10%
              活动结束后不再命中
  结算归属    本店活动 → promotion_cost_store（店铺减项，item_type=10, direction=2），final 扣减
              平台活动 → promotion_cost_platform（平台承担，item_type=11, direction=3），final 不变
  迁移校验    V15 列存在 + flyway 版本 15 成功
  清理        按 id 水位精确删除活动/券/订单/结算/流水，恢复库存，回到基线
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
STORE_ID = 1

DB = dict(host="127.0.0.1", port=3306, user="herbal_tea", password="herbal_tea_dev",
          database="herbal_tea", charset="utf8mb4", autocommit=True)

passed = 0
failed = 0


def p(name, r, expect_code=None, expect_http=None):
    global passed, failed
    try:
        body = r.json()
        code = body.get("code")
        msg = body.get("message", body.get("msg", "")) or ""
    except Exception:
        body, code, msg = None, None, r.text[:120]
    ok_flag = True
    detail = f"http={r.status_code} code={code} {msg[:70]}"
    if expect_http is not None and r.status_code != expect_http:
        ok_flag = False
        detail += f" | 期望 HTTP {expect_http}"
    if expect_code is not None and code != expect_code:
        ok_flag = False
        detail += f" | 期望 code={expect_code}"
    print(f"  {'✅' if ok_flag else '❌'} {name}: {detail}")
    if ok_flag:
        passed += 1
    else:
        failed += 1
    return body


def data_of(body, *path, default=None):
    """安全取 data 路径（后端 data 可能为 null）"""
    cur = body.get("data") if isinstance(body, dict) else None
    for k in path:
        if not isinstance(cur, dict):
            return default if default is not None else []
        cur = cur.get(k)
    return cur if cur is not None else (default if default is not None else [])


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


def watermark(table):
    """id 水位：清理时按 id > 水位 精确删除，避免误伤历史数据"""
    return q1(f"SELECT COALESCE(MAX(id), 0) FROM {table}")[0] or 0


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


def create_order(sess, qty, remark, **kw):
    payload = {"storeId": STORE_ID, "skuId": SKU_ID, "qty": qty,
               "addressId": ADDRESS_ID, "remark": remark}
    payload.update(kw)
    return sess.post(f"{BASE}/api/order/create", json=payload,
                     headers={"Idempotency-Key": uuid.uuid4().hex})


def mk_promo(sess, title, ptype, scope, rules, store_id=None, start=None, end=None):
    body = {"title": title, "type": ptype, "scope": scope,
            "rules": rules, "startTime": start or ts(-1), "endTime": end or ts(2)}
    if store_id is not None:
        body["storeId"] = store_id
    return sess.post(f"{BASE}/api/marketing/admin/promotions", json=body)


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
    subtotal = round(unit * 2, 2)
    print(f"  ℹ 会员 userId={uid} / 门店 {STORE_ID} / 单价 ¥{unit} / qty=2 小计 ¥{subtotal} / 库存 {base_stock}")

    # id 水位（清理用）
    w_promo = watermark("promotions")
    w_coupon = watermark("coupons")
    w_uc = watermark("user_coupons")
    w_order = watermark("orders")
    w_settle = watermark("settlements")
    w_settle_item = watermark("settlement_items")
    w_outbox = watermark("event_outbox")
    w_ptr = watermark("point_records")
    c0 = {t: q1(f"SELECT COUNT(*) FROM {t}")[0]
          for t in ("promotions", "coupons", "orders", "settlements")}
    had_points_account = q1("SELECT id FROM user_points_accounts WHERE user_id=%s", (uid,)) is not None

    promo_ids = []
    coupon_ids = []

    # ==================== 1. 权限门禁 ====================
    print("\n===== 1. 权限门禁 =====")
    p("1.1 活动列表无令牌 → 40100",
      requests.get(f"{BASE}/api/marketing/admin/promotions"), 40100)
    p("1.2 店员（无 menu:marketing）→ 40300",
      s_staff.get(f"{BASE}/api/marketing/admin/promotions"), 40300)
    p("1.3 店长（105 菜单权限）可查列表", s_store.get(f"{BASE}/api/marketing/admin/promotions"), 0)
    p("1.4 C 端活动列表无令牌 → 40100",
      requests.get(f"{BASE}/api/marketing/promotions/active", params={"storeId": STORE_ID}), 40100)

    # ==================== 2. 创建与参数校验 ====================
    print("\n===== 2. 创建活动与参数校验 =====")
    r = mk_promo(s_store, "联测-店长越权平台活动", 1, 1, '{"thresholdAmount":0,"discountAmount":5}')
    body = p("2.1 店长建平台活动 → 50000（只能建本店活动）", r, 50000)
    ok("2.2 提示含「本店活动」", body and "本店活动" in (body.get("message") or ""),
       f"msg={body.get('message') if body else None}")

    cash_store_id = None
    r = mk_promo(s_store, "联测-本店满减", 1, 2, '{"thresholdAmount":100,"discountAmount":15}')
    body = p("2.3 店长建本店活动（满100减15）", r, 0)
    if body and body.get("code") == 0:
        cash_store_id = body["data"]
        promo_ids.append(cash_store_id)
    row = q1("SELECT scope, store_id, status, type FROM promotions WHERE id=%s", (cash_store_id,))
    ok("2.4 自动归属本店（scope=2 store_id=1 草稿）",
       row and row[0] == 2 and row[1] == STORE_ID and row[2] == 0 and row[3] == 1, f"row={row}")

    cash_platform_id = None
    r = mk_promo(s0, "联测-平台满减", 1, 1, '{"thresholdAmount":100,"discountAmount":20}')
    body = p("2.5 超管建平台活动（满100减20）", r, 0)
    if body and body.get("code") == 0:
        cash_platform_id = body["data"]
        promo_ids.append(cash_platform_id)
    row = q1("SELECT scope, store_id FROM promotions WHERE id=%s", (cash_platform_id,))
    ok("2.6 平台活动 store_id 为空", row and row[0] == 1 and row[1] is None, f"row={row}")

    disc_id = None
    r = mk_promo(s0, "联测-平台折扣", 2, 1, '{"thresholdAmount":150,"discountRate":0.9,"maxDiscount":30}')
    body = p("2.7 超管建平台折扣活动（满150打9折封顶30）", r, 0)
    if body and body.get("code") == 0:
        disc_id = body["data"]
        promo_ids.append(disc_id)

    p("2.8 结束时间早于开始 → 50000",
      mk_promo(s0, "联测-时间非法", 1, 1, '{"discountAmount":5}', start=ts(3), end=ts(1)), 50000)
    p("2.9 活动类型非法（type=9）→ 50000",
      mk_promo(s0, "联测-类型非法", 9, 1, '{"discountAmount":5}'), 50000)
    p("2.10 折扣活动缺 discountRate → 50000",
      mk_promo(s0, "联测-折扣缺率", 2, 1, '{"thresholdAmount":0}'), 50000)
    p("2.11 满减优惠金额 ≤ 0 → 50000",
      mk_promo(s0, "联测-金额非法", 1, 1, '{"thresholdAmount":10,"discountAmount":0}'), 50000)
    p("2.12 归属非法（scope=9）→ 50000",
      mk_promo(s0, "联测-归属非法", 1, 9, '{"discountAmount":5}'), 50000)
    p("2.13 规非 JSON → 50000",
      mk_promo(s0, "联测-规则非法", 1, 1, 'not-a-json'), 50000)
    p("2.14 本店活动未指定门店（超管）→ 50000",
      mk_promo(s0, "联测-本店缺门店", 1, 2, '{"discountAmount":5}'), 50000)

    # ==================== 3. 列表、详情与规则摘要 ====================
    print("\n===== 3. 列表、详情与规则摘要 =====")
    r = s0.get(f"{BASE}/api/marketing/admin/promotions",
               params={"keyword": "联测-本店满减", "page": 1, "size": 10})
    body = p("3.1 关键词筛选", r, 0)
    recs = data_of(body, "records") if body else []
    ok("3.2 命中 1 条", len(recs) == 1 and recs[0]["id"] == cash_store_id, f"n={len(recs)}")
    ok("3.3 满减规则摘要「满 ¥100 减 ¥15」",
       recs and recs[0]["ruleDesc"] == "满 ¥100 减 ¥15", f"ruleDesc={recs[0]['ruleDesc'] if recs else None}")
    ok("3.4 草稿活动 active=false",
       recs and recs[0]["active"] is False, f"active={recs[0]['active'] if recs else None}")

    r = s0.get(f"{BASE}/api/marketing/admin/promotions/{disc_id}")
    body = p("3.5 活动详情", r, 0)
    d = (body or {}).get("data") or {}
    ok("3.6 折扣规则摘要「满 ¥150 打 9 折（最高减 ¥30）」",
       d.get("ruleDesc") == "满 ¥150 打 9 折（最高减 ¥30）", f"ruleDesc={d.get('ruleDesc')}")

    r = s0.get(f"{BASE}/api/marketing/admin/promotions", params={"scope": 1, "page": 1, "size": 50})
    body = p("3.7 按归属筛选（平台活动）", r, 0)
    ids = [it["id"] for it in data_of(body, "records")]
    ok("3.8 结果仅含平台活动种子", cash_platform_id in ids and cash_store_id not in ids, f"ids={ids}")

    # ==================== 4. 发布 / 编辑 / 结束 ====================
    print("\n===== 4. 发布 / 编辑 / 结束 =====")
    p("4.1 编辑草稿本店活动 → 0",
      s_store.put(f"{BASE}/api/marketing/admin/promotions/{cash_store_id}",
                  json={"title": "联测-本店满减", "type": 1, "scope": 2,
                        "rules": '{"thresholdAmount":100,"discountAmount":15}',
                        "startTime": ts(-1), "endTime": ts(2)}), 0)
    p("4.2 发布本店活动（0→1）",
      s_store.post(f"{BASE}/api/marketing/admin/promotions/{cash_store_id}/publish"), 0)
    ok("4.3 状态已置为进行中",
       q1("SELECT status FROM promotions WHERE id=%s", (cash_store_id,))[0] == 1)
    p("4.4 发布后再编辑 → 40900",
      s_store.put(f"{BASE}/api/marketing/admin/promotions/{cash_store_id}",
                  json={"title": "联测-本店满减改", "type": 1, "scope": 2,
                        "rules": '{"thresholdAmount":100,"discountAmount":18}',
                        "startTime": ts(-1), "endTime": ts(2)}), 40900)
    p("4.5 重复发布 → 40900",
      s_store.post(f"{BASE}/api/marketing/admin/promotions/{cash_store_id}/publish"), 40900)
    p("4.6 发布平台满减", s0.post(f"{BASE}/api/marketing/admin/promotions/{cash_platform_id}/publish"), 0)
    p("4.7 发布平台折扣", s0.post(f"{BASE}/api/marketing/admin/promotions/{disc_id}/publish"), 0)

    # ==================== 5. C 端活动列表 ====================
    print("\n===== 5. C 端活动列表 =====")
    r = us.get(f"{BASE}/api/marketing/promotions/active", params={"storeId": STORE_ID})
    body = p("5.1 按门店查生效活动", r, 0)
    ids = [it["id"] for it in (body or {}).get("data") or []]
    ok("5.2 含平台活动与本店活动",
       cash_platform_id in ids and cash_store_id in ids, f"ids={ids}")
    ok("5.3 折扣活动也在列表", disc_id in ids, f"ids={ids}")

    r = us.get(f"{BASE}/api/marketing/promotions/active")
    body = p("5.4 不传 storeId → 仅平台活动", r, 0)
    ids2 = [it["id"] for it in (body or {}).get("data") or []]
    ok("5.5 不含本店活动", cash_store_id not in ids2, f"ids={ids2}")

    # 新草稿活动不应出现在 C 端列表
    r = mk_promo(s0, "联测-未发布草稿", 1, 1, '{"thresholdAmount":0,"discountAmount":99}')
    draft_id = (r.json().get("data") if r.json().get("code") == 0 else None)
    if draft_id:
        promo_ids.append(draft_id)
    r = us.get(f"{BASE}/api/marketing/promotions/active", params={"storeId": STORE_ID})
    ids3 = [it["id"] for it in (r.json().get("data") or [])]
    ok("5.6 草稿活动不出现在 C 端列表", draft_id not in ids3, f"draft={draft_id} ids={ids3}")

    # ==================== 6. 下单自动计价 ====================
    print("\n===== 6. 下单自动计价（活动 → 券 → 积分） =====")

    # 6.1 未达门槛：qty=1 → 小计 90（满减需 ≥100，折扣需 ≥150）
    r = create_order(us, 1, "v30 未达门槛")
    body = p("6.1 下单 qty=1（小计 90，未达任何活动门槛）", r, 0)
    if body and body.get("code") == 0:
        ono = body["data"]["orderNo"]
        row = q1("SELECT promotion_id, promotion_discount, promotion_scope, pay_amount FROM orders WHERE order_no=%s", (ono,))
        ok("6.2 未命中活动（promotion_discount=0）",
           row and row[0] is None and float(row[1]) == 0.0 and row[2] == 0 and float(row[3]) == round(unit, 2),
           f"row={row}")

    # 6.3 双活动并存 → 取优惠最大者（平台 20 > 本店 15）
    r = create_order(us, 2, "v30 活动取最优")
    body = p("6.3 下单 qty=2（小计 180，平台20 vs 本店15）", r, 0)
    order_pay = body["data"]["orderNo"] if body and body.get("code") == 0 else None
    if order_pay:
        row = q1("""SELECT promotion_id, promotion_discount, promotion_scope, pay_amount,
                           coupon_amount, points_deduct_amount, points_earned
                    FROM orders WHERE order_no=%s""", (order_pay,))
        ok("6.4 命中平台活动（优惠 20，不叠加本店 15）",
           row and row[0] == cash_platform_id and float(row[1]) == 20.0 and row[2] == 1, f"row={row}")
        ok("6.5 实付 = 180 − 20 = 160", row and float(row[3]) == 160.0, f"pay={row[3] if row else None}")
        ok("6.6 无券无积分时 coupon/points 均为 0",
           row and float(row[4]) == 0.0 and float(row[5]) == 0.0)
        ok("6.7 赠送积分按活动后实付取整（160）", row and row[6] == 160, f"earned={row[6] if row else None}")

    # 6.8 活动 + 券 + 积分 叠加
    r = s0.post(f"{BASE}/api/marketing/admin/coupons",
                json={"name": "联测-活动叠加券", "type": 1, "scope": 2, "storeId": STORE_ID,
                      "thresholdAmount": 100, "discountAmount": 10, "totalCount": 10,
                      "perUserLimit": 1, "startTime": ts(-1), "endTime": ts(2)})
    body = p("6.8 建本店券（满100减10）", r, 0)
    if body and body.get("code") == 0:
        coupon_ids.append(body["data"])
    # 发布平台券需 211；店长建本店券后由超管发布（超管拥有全部权限）
    if coupon_ids:
        p("6.9 发布券", s0.post(f"{BASE}/api/marketing/admin/coupons/{coupon_ids[-1]}/publish"), 0)
    uc = None
    if coupon_ids:
        body = p("6.10 发券给会员",
                 s0.post(f"{BASE}/api/marketing/admin/coupons/{coupon_ids[-1]}/grant",
                         params={"userId": uid}), 0)
        uc = body["data"] if body and body.get("code") == 0 else None

    if not had_points_account:
        x("""INSERT INTO user_points_accounts (user_id, balance, total_earned, total_used, total_expired, version)
             VALUES (%s, 5000, 5000, 0, 0, 0)""", (uid,))

    r = create_order(us, 2, "v30 活动+券+积分", userCouponId=uc, usePoints=500)
    body = p("6.11 下单（活动+券+积分）", r, 0)
    order_stack = body["data"]["orderNo"] if body and body.get("code") == 0 else None
    if order_stack:
        row = q1("""SELECT promotion_discount, coupon_amount, points_deduct_amount, pay_amount, points_earned
                    FROM orders WHERE order_no=%s""", (order_stack,))
        ok("6.12 活动优惠 20（活动先于券）", row and float(row[0]) == 20.0, f"promo={row[0] if row else None}")
        ok("6.13 券门槛按活动后 160 判定，抵扣 10",
           row and float(row[1]) == 10.0, f"coupon={row[1] if row else None}")
        ok("6.14 积分 500 抵扣 5.00（1 分 = 0.01 元）",
           row and float(row[2]) == 5.0, f"points={row[2] if row else None}")
        ok("6.15 实付 = 180 − 20 − 10 − 5 = 145", row and float(row[3]) == 145.0, f"pay={row[3] if row else None}")
        ok("6.16 赠送积分 = 145", row and row[4] == 145, f"earned={row[4] if row else None}")

    # 6.17 结束满减活动 → 仅折扣活动生效
    p("6.17 结束本店满减", s0.post(f"{BASE}/api/marketing/admin/promotions/{cash_store_id}/stop"), 0)
    p("6.18 结束平台满减", s0.post(f"{BASE}/api/marketing/admin/promotions/{cash_platform_id}/stop"), 0)
    ok("6.19 结束后状态为已结束",
       q1("SELECT status FROM promotions WHERE id=%s", (cash_platform_id,))[0] == 2)
    r = create_order(us, 2, "v30 折扣活动")
    body = p("6.20 下单（仅折扣活动生效）", r, 0)
    order_disc = body["data"]["orderNo"] if body and body.get("code") == 0 else None
    if order_disc:
        row = q1("SELECT promotion_id, promotion_discount, pay_amount FROM orders WHERE order_no=%s", (order_disc,))
        expect = round(subtotal * 0.1, 2)
        ok("6.21 折扣 9 折 → 优惠 = 小计 × 10% = 18",
           row and row[0] == disc_id and float(row[1]) == expect and float(row[2]) == round(subtotal - expect, 2),
           f"row={row} expect={expect}")

    # 6.22 全部活动结束 → 不再命中
    p("6.23 结束折扣活动", s0.post(f"{BASE}/api/marketing/admin/promotions/{disc_id}/stop"), 0)
    r = create_order(us, 2, "v30 无活动")
    body = p("6.24 下单（无活动）", r, 0)
    order_none = body["data"]["orderNo"] if body and body.get("code") == 0 else None
    if order_none:
        row = q1("SELECT promotion_discount, pay_amount FROM orders WHERE order_no=%s", (order_none,))
        ok("6.25 无活动命中，实付 = 小计",
           row and float(row[0]) == 0.0 and float(row[1]) == subtotal, f"row={row}")

    # ==================== 7. 结算成本归属 ====================
    print("\n===== 7. 结算成本归属 =====")
    today = datetime.now().strftime("%Y-%m-%d")

    def settle_once(order_no, label):
        o = q1("SELECT id FROM orders WHERE order_no=%s", (order_no,))
        if not o:
            ok(f"{label} 订单存在", False, f"orderNo={order_no}")
            return None
        oid = o[0]
        x("UPDATE orders SET status=90, finished_at=NOW() WHERE id=%s", (oid,))
        r = s0.post(f"{BASE}/api/settlement/admin/generate",
                    params={"storeId": STORE_ID, "period": today})
        p(f"{label} 生成结算单", r, 0)
        row_s = q1("""SELECT id FROM settlements WHERE store_id=%s AND period=%s
                      ORDER BY id DESC LIMIT 1""", (STORE_ID, today))
        return row_s[0] if row_s else None

    sid = settle_once(order_pay, "7.1")
    if sid:
        row = q1("""SELECT promotion_cost_store, promotion_cost_platform, points_cost_store,
                           final_amount, total_amount, commission_amount
                    FROM settlements WHERE id=%s""", (sid,))
        ok("7.1b 平台活动 → promotion_cost_platform=20，店铺成本为 0",
           float(row[0]) == 0.0 and float(row[1]) == 20.0, f"store={row[0]} platform={row[1]}")
        item = q1("""SELECT item_type, direction, amount FROM settlement_items
                     WHERE settlement_id=%s AND item_type=11""", (sid,))
        ok("7.1c 明细出现「平台活动补贴」（item_type=11, direction=3）",
           item and item[1] == 3 and float(item[2]) == 20.0, f"item={item}")
        # 平台活动不减店铺应付：final 只受总额、佣金、积分成本影响
        expect_final = round(float(row[4]) - float(row[5]) - float(row[2]), 2)
        ok("7.1d 平台活动不减店铺应付（final = 总额 − 佣金 − 积分成本）",
           abs(float(row[3]) - expect_final) < 0.001, f"final={row[3]} expect={expect_final}")
        # 清理该结算单并把订单还原为已完结之外的态，避免污染下一场景的结算生成
        x("DELETE FROM settlement_items WHERE settlement_id=%s", (sid,))
        x("DELETE FROM settlements WHERE id=%s", (sid,))
        x("UPDATE orders SET status=70 WHERE order_no=%s", (order_pay,))

    # 本店活动结算场景：已结束的活动不可重新发布，新建一张同规则的本店活动
    r = mk_promo(s_store, "联测-本店满减(结算)", 1, 2, '{"thresholdAmount":100,"discountAmount":15}')
    body = p("7.2 新建本店满减活动（结算场景）", r, 0)
    store_promo2 = body["data"] if body and body.get("code") == 0 else None
    if store_promo2:
        promo_ids.append(store_promo2)
    if store_promo2:
        p("7.2b 发布本店活动", s_store.post(f"{BASE}/api/marketing/admin/promotions/{store_promo2}/publish"), 0)

    r = create_order(us, 2, "v30 本店活动结算")
    body = p("7.3 下单（仅本店活动生效）", r, 0)
    order_store = body["data"]["orderNo"] if body and body.get("code") == 0 else None
    if order_store:
        row = q1("SELECT promotion_discount, promotion_scope FROM orders WHERE order_no=%s", (order_store,))
        ok("7.4 命中本店活动（优惠 15，scope=2）",
           row and float(row[0]) == 15.0 and row[1] == 2, f"row={row}")

    if order_store:
        x("DELETE FROM settlement_items WHERE settlement_id IN (SELECT id FROM settlements WHERE store_id=%s AND period=%s)",
          (STORE_ID, today))
        x("DELETE FROM settlements WHERE store_id=%s AND period=%s", (STORE_ID, today))
        sid2 = settle_once(order_store, "7.5")
        if sid2:
            row = q1("""SELECT promotion_cost_store, promotion_cost_platform, points_cost_store,
                               final_amount, total_amount, commission_amount
                        FROM settlements WHERE id=%s""", (sid2,))
            ok("7.6 本店活动 → promotion_cost_store=15，平台补贴为 0",
               float(row[0]) == 15.0 and float(row[1]) == 0.0, f"store={row[0]} platform={row[1]}")
            item = q1("""SELECT item_type, direction, amount FROM settlement_items
                         WHERE settlement_id=%s AND item_type=10""", (sid2,))
            ok("7.7 明细出现「本店活动成本」（item_type=10, direction=2）",
               item and item[1] == 2 and float(item[2]) == 15.0, f"item={item}")
            expect_final = round(float(row[4]) - float(row[5]) - float(row[2]) - 15.0, 2)
            ok("7.8 本店活动从店铺应付扣减",
               abs(float(row[3]) - expect_final) < 0.001, f"final={row[3]} expect={expect_final}")

    # ==================== 8. 迁移与基线校验 ====================
    print("\n===== 8. 迁移与基线校验 =====")
    cols = q1("""SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='herbal_tea'
                 AND TABLE_NAME='orders' AND COLUMN_NAME IN ('promotion_id','promotion_discount','promotion_scope')""")[0]
    ok("8.1 V15：orders 三个活动列存在", cols == 3, f"count={cols}")
    cols = q1("""SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='herbal_tea'
                 AND TABLE_NAME='settlements' AND COLUMN_NAME IN ('promotion_cost_store','promotion_cost_platform')""")[0]
    ok("8.2 V15：settlements 两个活动成本列存在", cols == 2, f"count={cols}")
    row = q1("SELECT success FROM flyway_schema_history WHERE version='15'")
    ok("8.3 Flyway V15 记录成功", row is not None and row[0] == 1, f"row={row}")

    # ==================== 9. 清理 ====================
    print("\n===== 9. 清理 =====")
    x("DELETE FROM settlement_items WHERE id > %s", (w_settle_item,))
    x("DELETE FROM settlements WHERE id > %s", (w_settle,))
    x("DELETE FROM user_coupons WHERE id > %s", (w_uc,))
    x("DELETE FROM coupons WHERE id > %s", (w_coupon,))
    x("DELETE FROM promotions WHERE id > %s", (w_promo,))
    for t in ("payment_records", "order_items", "order_shipping_logs", "refund_records"):
        x(f"DELETE FROM {t} WHERE order_id > %s", (w_order,))
    x("DELETE FROM orders WHERE id > %s", (w_order,))
    x("DELETE FROM point_records WHERE id > %s", (w_ptr,))
    if not had_points_account:
        x("DELETE FROM user_points_accounts WHERE user_id=%s", (uid,))
    x("DELETE FROM event_outbox WHERE id > %s", (w_outbox,))
    x("UPDATE product_skus SET stock=%s WHERE id=%s", (base_stock, SKU_ID))
    time.sleep(3)  # 给 outbox worker 收敛

    ok("9.1 测试活动已清空", q1("SELECT COUNT(*) FROM promotions")[0] == c0["promotions"],
       f"count={q1('SELECT COUNT(*) FROM promotions')[0]} 基线={c0['promotions']}")
    ok("9.2 测试券已清空", q1("SELECT COUNT(*) FROM coupons")[0] == c0["coupons"])
    ok("9.3 测试订单已清空", q1("SELECT COUNT(*) FROM orders")[0] == c0["orders"],
       f"count={q1('SELECT COUNT(*) FROM orders')[0]} 基线={c0['orders']}")
    ok("9.4 结算单已清空", q1("SELECT COUNT(*) FROM settlements")[0] == c0["settlements"])
    ok("9.5 库存恢复基线", stock() == base_stock, f"stock={stock()} 基线={base_stock}")
    ok("9.6 无待投递 outbox 残留",
       q1("SELECT COUNT(*) FROM event_outbox WHERE status=0")[0] == 0)

    print(f"\n===== 结果: {passed} 通过 / {failed} 失败 =====")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    w0 = {"promotions": 0, "coupons": 0, "user_coupons": 0, "orders": 0,
          "settlements": 0, "settlement_items": 0, "event_outbox": 0, "point_records": 0}
    try:
        for k in w0:
            w0[k] = watermark(k)
        main()
    finally:
        try:
            x("DELETE FROM settlement_items WHERE id > %s", (w0["settlement_items"],))
            x("DELETE FROM settlements WHERE id > %s", (w0["settlements"],))
            x("DELETE FROM user_coupons WHERE id > %s", (w0["user_coupons"],))
            x("DELETE FROM coupons WHERE id > %s", (w0["coupons"],))
            x("DELETE FROM promotions WHERE id > %s", (w0["promotions"],))
            for t in ("payment_records", "order_items", "order_shipping_logs", "refund_records"):
                x(f"DELETE FROM {t} WHERE order_id > %s", (w0["orders"],))
            x("DELETE FROM orders WHERE id > %s", (w0["orders"],))
            x("DELETE FROM point_records WHERE id > %s", (w0["point_records"],))
            x("DELETE FROM event_outbox WHERE id > %s", (w0["event_outbox"],))
        except Exception as e:
            print(f"  ⚠ 兜底清理失败: {e}")
