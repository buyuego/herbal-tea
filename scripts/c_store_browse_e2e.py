# -*- coding: utf-8 -*-
"""C 端小程序联测（v29）：门店/分类/货架浏览 + 商品详情 + 我的积分券 + 订单归属 + C 端支付

覆盖：
  1  未登录访问 C 端接口 → 40100
  2  wx-login 静默登录
  3  门店列表（仅正常营业、无敏感字段）
  4  分类列表
  5  货架商品分页（本店在售、含本店价 SKU）
  6  货架必填校验（无 storeId → 40000）
  7  分页粒度 = 商品（size=1 时只回 1 个商品）
  8  商品详情（含 SKU；不含成本价）
  9  商品详情未上架/不存在 → 40400
 10  SKU 详情（本店价 + 库存）
 11  SKU 详情未上架门店 → 40400
 12  我的积分（无往来时零值账户）
 13  我的券包（空）
 14  下单 → 10 待支付（库存 -qty）
 15  订单详情（本人）
 16  订单详情越权（他人 token）→ 40300
 17  C 端支付 → 30 待发货；重复支付幂等
 18  支付后积分发放（outbox 异步，轮询等待）
 19  积分明细（changeType=1）
 20  券包门槛过滤（usableAmount 极小 → 无可用券）
 21  清理回基线

说明：脚本可重复运行，try/finally 兜底清理，库存与流水恢复基线。
"""
import json
import sys
import time
import uuid

import pymysql
import requests

BASE = "http://localhost:8080"
CODE = "u001"          # -> mock-openid-u001 -> userId=3 茶友小红
OTHER_CODE = "u002"    # -> mock-openid-u002 -> userId=4 茶友小红2（越权用例）
ADDRESS_ID = 3         # 李四（默认地址，userId=3 归属）
SKU_ID = 6
STORE_ID = 1

DB = dict(host="127.0.0.1", port=3306, user="herbal_tea", password="herbal_tea_dev",
          database="herbal_tea", charset="utf8mb4", autocommit=True)

PASS = FAIL = 0


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
    cur.execute(sql, args)
    n = cur.rowcount
    cur.close()
    conn.close()
    return n


def ok(label, cond, detail=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  [OK] {label}" + (f"  {detail}" if detail else ""))
    else:
        FAIL += 1
        print(f"  [FAIL] {label}  {detail}")
    return cond


def body_of(label, resp, expect_code=0):
    global FAIL
    try:
        body = resp.json()
    except Exception:
        FAIL += 1
        print(f"  [FAIL] {label} HTTP {resp.status_code} 非 JSON: {resp.text[:200]}")
        return None
    code = body.get("code")
    if expect_code is not None and code != expect_code:
        FAIL += 1
        print(f"  [FAIL] {label} HTTP {resp.status_code} code={code}（期望 {expect_code}）msg={body.get('message')}")
        return body
    print(f"  [OK] {label} code={code}" + (f" msg={body.get('message')}" if code != 0 else ""))
    return body


def login(session, code, nickname):
    r = session.post(f"{BASE}/api/user/wx-login",
                     json={"code": code, "deviceFingerprint": "fp-" + uuid.uuid4().hex[:10],
                           "nickname": nickname})
    body = r.json()
    assert body.get("code") == 0, f"登录失败: {body}"
    session.headers["Authorization"] = "Bearer " + body["data"]["accessToken"]
    return body["data"]


def wait_points(user_id, timeout=30):
    """等待 outbox 事件被消费（worker 5s 轮询）：积分账户余额 > 0"""
    for _ in range(timeout):
        row = q1("SELECT balance FROM user_points_accounts WHERE user_id=%s", (user_id,))
        if row and row[0] and row[0] > 0:
            return row[0]
        time.sleep(1)
    return 0


def main():
    global PASS, FAIL
    print("=" * 62)
    print("C 端小程序联测 v29（浏览 / 积分券 / 下单 / 支付）")
    print("=" * 62)

    stock0 = q1("SELECT stock FROM product_skus WHERE id=%s", (SKU_ID,))[0]
    inv_seq0 = q1("SELECT COALESCE(MAX(id), 0) FROM inventory_records")[0]
    orders0 = q1("SELECT COUNT(*) FROM orders")[0]
    order_no = None
    order_id = None
    print(f"初始基线：SKU{SKU_ID} 库存={stock0} 订单数={orders0} 库存流水最大id={inv_seq0}")

    try:
        # ---------- 1. 未登录 ----------
        print("\n===== 1. 鉴权边界 =====")
        anon = requests.Session()
        r = anon.get(f"{BASE}/api/store/list")
        body_of("1.1 未登录访问门店列表 → 40100", r, 40100)
        r = anon.get(f"{BASE}/api/product/shelf/products", params={"storeId": STORE_ID})
        body_of("1.2 未登录访问货架 → 40100", r, 40100)
        r = anon.get(f"{BASE}/api/marketing/points/my")
        body_of("1.3 未登录访问我的积分 → 40100", r, 40100)

        # ---------- 2. 登录 ----------
        print("\n===== 2. 静默登录 =====")
        s = requests.Session()
        info = login(s, CODE, "茶友小红")
        uid = q1("SELECT id FROM users WHERE openid=%s", ("mock-openid-" + CODE,))[0]
        ok("2.1 C 端登录成功并签发双令牌", bool(info.get("accessToken") and info.get("refreshToken")),
           f"userId={uid}")

        # ---------- 3. 门店 ----------
        print("\n===== 3. 门店列表 =====")
        r = s.get(f"{BASE}/api/store/list")
        body = body_of("3.1 门店列表", r)
        stores = body["data"] if body else []
        ok("3.2 返回正常营业门店", len(stores) == 3, f"门店数={len(stores)}（基线 3）")
        ok("3.3 不含敏感字段（联系人/执照/银行）",
           stores and not ({"contactName", "contactPhone", "licenseNo", "bankAccount"} & set(stores[0].keys())),
           f"字段={sorted(stores[0].keys()) if stores else None}")
        sid = stores[0]["id"] if stores else STORE_ID

        # ---------- 4. 分类 ----------
        print("\n===== 4. 分类列表 =====")
        r = s.get(f"{BASE}/api/product/categories")
        body = body_of("4.1 分类列表", r)
        cats = body["data"] if body else []
        ok("4.2 分类非空", len(cats) > 0, f"分类数={len(cats)}")

        # ---------- 5. 货架 ----------
        print("\n===== 5. 货架商品 =====")
        r = s.get(f"{BASE}/api/product/shelf/products", params={"storeId": sid, "page": 1, "size": 10})
        body = body_of("5.1 货架分页", r)
        page = body["data"] if body else {}
        recs = page.get("records", [])
        ok("5.2 本店在售商品非空", len(recs) > 0, f"首屏 {len(recs)} 条 / total={page.get('total')}")
        first = recs[0] if recs else {}
        ok("5.3 商品含在售 SKU 与本店价",
           bool(first.get("skus")) and float(first["skus"][0].get("price") or 0) > 0,
           f"skus={len(first.get('skus') or [])} price={first['skus'][0].get('price') if first.get('skus') else None}")
        ok("5.4 列表不含成本价（敏感）", "costPrice" not in first and "cost_price" not in first)

        r = s.get(f"{BASE}/api/product/shelf/products", params={"page": 1, "size": 10})
        body_of("5.5 缺 storeId → 40000", r, 40000)

        r = s.get(f"{BASE}/api/product/shelf/products", params={"storeId": sid, "page": 1, "size": 1})
        body = body_of("5.6 分页粒度=商品（size=1）", r)
        p1 = body["data"] if body else {}
        ok("5.7 size=1 只返回 1 个商品且 total≥1",
           len(p1.get("records", [])) == 1 and (p1.get("total") or 0) >= 1,
           f"records={len(p1.get('records', []))} total={p1.get('total')}")

        # ---------- 6. 商品详情 ----------
        print("\n===== 6. 商品详情 =====")
        pid = first.get("productId")
        r = s.get(f"{BASE}/api/product/shelf/products/{pid}", params={"storeId": sid})
        body = body_of("6.1 商品详情", r)
        detail = body["data"] if body else {}
        ok("6.2 含在售 SKU 列表", bool(detail.get("skus")), f"skus={len(detail.get('skus') or [])}")
        ok("6.3 详情不含成本价", "costPrice" not in detail)

        r = s.get(f"{BASE}/api/product/shelf/products/999999", params={"storeId": sid})
        body_of("6.4 不存在/未上架商品 → 40400", r, 40400)

        # ---------- 7. SKU 详情 ----------
        print("\n===== 7. SKU 详情 =====")
        r = s.get(f"{BASE}/api/product/shelf/skus/{SKU_ID}", params={"storeId": STORE_ID})
        body = body_of("7.1 SKU 详情（本店价）", r)
        sku = body["data"] if body else {}
        ok("7.2 价格与库存有效",
           float(sku.get("price") or 0) > 0 and sku.get("stock") is not None,
           f"price={sku.get('price')} stock={sku.get('stock')}")

        # 找一家未上架该 SKU 的门店（动态，避免硬编码）
        row = q1("""SELECT s.id FROM stores s
                    WHERE s.status=1 AND NOT EXISTS (
                        SELECT 1 FROM store_products sp
                        WHERE sp.store_id=s.id AND sp.sku_id=%s AND sp.status=1)
                    LIMIT 1""", (SKU_ID,))
        if row:
            r = s.get(f"{BASE}/api/product/shelf/skus/{SKU_ID}", params={"storeId": row[0]})
            body_of(f"7.3 未上架门店({row[0]})取 SKU → 40400", r, 40400)
        else:
            print("  [skip] 7.3 全部门店均已上架该 SKU，跳过未上架用例")

        # ---------- 8. 我的积分 / 券包（下单前） ----------
        print("\n===== 8. 我的积分与券包（下单前） =====")
        r = s.get(f"{BASE}/api/marketing/points/my")
        body = body_of("8.1 我的积分", r)
        pts0 = body["data"] if body else {}
        ok("8.2 无积分往来时返回零值账户", int(pts0.get("balance") or 0) == 0,
           f"balance={pts0.get('balance')} earned={pts0.get('totalEarned')}")

        r = s.get(f"{BASE}/api/marketing/coupons/my", params={"page": 1, "size": 10})
        body = body_of("8.3 我的券包", r)
        ok("8.4 券包为空", (body["data"].get("total") or 0) == 0, f"total={body['data'].get('total')}")

        # ---------- 9. 下单 ----------
        print("\n===== 9. 下单（C 端） =====")
        idem = uuid.uuid4().hex
        r = s.post(f"{BASE}/api/order/create",
                   json={"storeId": STORE_ID, "skuId": SKU_ID, "qty": 1,
                         "addressId": ADDRESS_ID, "remark": "v29-C端联测"},
                   headers={"Idempotency-Key": idem})
        body = body_of("9.1 下单成功", r)
        order_no = body["data"]["orderNo"]
        pay_amount = float(body["data"]["payAmount"])
        order_id = q1("SELECT id FROM orders WHERE order_no=%s", (order_no,))[0]
        ok("9.2 订单初始状态 10 待支付",
           q1("SELECT status FROM orders WHERE id=%s", (order_id,))[0] == 10)
        st = q1("SELECT stock FROM product_skus WHERE id=%s", (SKU_ID,))[0]
        ok("9.3 库存原子扣减", st == stock0 - 1, f"{stock0} → {st}")

        # ---------- 10. 订单详情与归属 ----------
        print("\n===== 10. 订单详情与归属校验 =====")
        r = s.get(f"{BASE}/api/order/{order_id}")
        body = body_of("10.1 本人订单详情", r)
        d = body["data"] if body else {}
        ok("10.2 含商品明细与应付金额",
           bool(d.get("items")) and float(d.get("payAmount") or 0) == pay_amount,
           f"items={len(d.get('items') or [])} payAmount={d.get('payAmount')}")

        s2 = requests.Session()
        login(s2, OTHER_CODE, "茶友小红2")
        r = s2.get(f"{BASE}/api/order/{order_id}")
        body_of("10.3 他人订单详情 → 40300", r, 40300)
        r = s2.post(f"{BASE}/api/order/{order_id}/pay")
        body_of("10.4 他人发起支付 → 40300", r, 40300)
        ok("10.5 越权支付未改变订单状态",
           q1("SELECT status FROM orders WHERE id=%s", (order_id,))[0] == 10)

        # ---------- 11. C 端支付 ----------
        print("\n===== 11. C 端支付 =====")
        r = s.post(f"{BASE}/api/order/{order_id}/pay")
        body_of("11.1 发起支付（dev 直通）", r)
        st = q1("SELECT status FROM orders WHERE id=%s", (order_id,))[0]
        ok("11.2 状态推进到 30 待发货", st == 30, f"status={st}")
        r = s.post(f"{BASE}/api/order/{order_id}/pay")
        body_of("11.3 重复支付幂等（仍 0）", r)
        ok("11.4 状态仍为 30", q1("SELECT status FROM orders WHERE id=%s", (order_id,))[0] == 30)

        # ---------- 12. 支付后积分发放 ----------
        print("\n===== 12. 支付后积分发放（outbox 异步） =====")
        expect_points = int(pay_amount)  # 实付向下取整（1 元 = 1 积分）
        balance = wait_points(uid)
        ok("12.1 赠送积分已入账", balance == expect_points,
           f"balance={balance} 期望={expect_points}（实付 ¥{pay_amount}）")

        r = s.get(f"{BASE}/api/marketing/points/my")
        body = body_of("12.2 我的积分（支付后）", r)
        ok("12.3 余额与累计获得一致",
           int(body["data"]["balance"]) == expect_points and int(body["data"]["totalEarned"]) == expect_points,
           f"balance={body['data']['balance']} earned={body['data']['totalEarned']}")

        r = s.get(f"{BASE}/api/marketing/points/my/records", params={"changeType": 1, "page": 1, "size": 5})
        body = body_of("12.4 积分明细（changeType=1 发放）", r)
        recs = body["data"].get("records", [])
        ok("12.5 发放流水关联本单",
           len(recs) == 1 and recs[0].get("orderNo") == order_no,
           f"orderNo={recs[0].get('orderNo') if recs else None} points={recs[0].get('points') if recs else None}")

        # ---------- 13. 券包门槛过滤 ----------
        print("\n===== 13. 券包门槛过滤 =====")
        r = s.get(f"{BASE}/api/marketing/coupons/my",
                  params={"status": 0, "storeId": STORE_ID, "usableAmount": 0.01, "page": 1, "size": 10})
        body = body_of("13.1 极小金额过滤可用券", r)
        ok("13.2 无可叠加到 1 分钱的券（无券场景 total=0）",
           (body["data"].get("total") or 0) == 0, f"total={body['data'].get('total')}")

        print("\n===== 联测结果 =====")
        print(f"通过 {PASS} / {PASS + FAIL}")
        return FAIL == 0
    finally:
        # ---------- 清理回基线 ----------
        print("\n===== 清理 =====")
        if order_id:
            for t in ("payment_records", "order_shipping_logs", "order_items", "refund_records"):
                x(f"DELETE FROM {t} WHERE order_id=%s", (order_id,))
            x("DELETE FROM orders WHERE id=%s", (order_id,))
            x("DELETE FROM event_outbox WHERE biz_key LIKE %s", (f"%{order_no}%",))
            print(f"  已删除订单 {order_no}")
        # 积分（本脚本只在 user 3 上产生积分）
        x("DELETE FROM point_records WHERE user_id=%s", (3,))
        x("DELETE FROM user_points_accounts WHERE user_id=%s", (3,))
        # 库存与流水还原
        x("UPDATE product_skus SET stock=%s WHERE id=%s", (stock0, SKU_ID))
        x("DELETE FROM inventory_records WHERE id > %s", (inv_seq0,))
        print(f"  库存还原 {SKU_ID} → {stock0}，清理新增库存流水")
        print(f"  最终：订单数={q1('SELECT COUNT(*) FROM orders')[0]}"
              f"（基线 {orders0}） 积分账户={q1('SELECT COUNT(*) FROM user_points_accounts')[0]}"
              f" 积分流水={q1('SELECT COUNT(*) FROM point_records')[0]}"
              f" pending_outbox={q1('SELECT COUNT(*) FROM event_outbox WHERE status=0')[0]}")


if __name__ == "__main__":
    sys.exit(0 if main() else 1)
