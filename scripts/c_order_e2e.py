# -*- coding: utf-8 -*-
"""C 端订单全链路联测 v2：登录 → 下单 → 取消回滚 → 再下单 → mock支付 → 签收
状态/库存校验直接查库（pymysql），不依赖 B 端权限接口"""
import json
import uuid
import sys

import pymysql
import requests

BASE = "http://localhost:8080"
CODE = "u001"  # -> mock-openid-u001 -> userId=3 茶友小红
ADDRESS_ID = 3  # 李四（默认地址，userId=3 归属）

DB = dict(host="127.0.0.1", port=3306, user="herbal_tea", password="herbal_tea_dev",
          database="herbal_tea", charset="utf8mb4", autocommit=True)


def db():
    return pymysql.connect(**DB)


def q(sql, args=None):
    conn = db()
    cur = conn.cursor()
    cur.execute(sql, args)
    rows = cur.fetchall()
    cols = [d[0] for d in cur.description] if cur.description else []
    cur.close()
    conn.close()
    return cols, rows


def stock():
    cols, rows = q("SELECT id, product_id, stock FROM product_skus WHERE id=6")
    return rows[0][2]


def order_status(order_no):
    cols, rows = q("SELECT id, order_no, status FROM orders WHERE order_no=%s", (order_no,))
    return rows[0] if rows else None


def p(label, resp, show_keys=None):
    try:
        body = resp.json()
    except Exception:
        print(f"[{label}] HTTP {resp.status_code} RAW: {resp.text[:300]}")
        return None
    code = body.get("code")
    msg = body.get("message") or body.get("msg")
    data = body.get("data")
    print(f"[{label}] HTTP {resp.status_code} code={code} msg={msg}")
    if data is not None and show_keys:
        if isinstance(data, dict):
            print(f"    data: {json.dumps({k: data.get(k) for k in show_keys}, ensure_ascii=False, default=str)}")
        else:
            print(f"    data: {json.dumps(data, ensure_ascii=False, default=str)[:300]}")
    return body


def find_order_id(s, order_no):
    """从我的订单列表按 orderNo 找 id"""
    r = s.get(f"{BASE}/api/order/mine", params={"current": 1, "size": 20})
    body = r.json()
    if body.get("code") != 0:
        return None
    for rec in body["data"].get("records", []):
        if rec.get("orderNo") == order_no:
            return rec.get("id")
    return None


def main():
    s = requests.Session()

    print(f"===== 初始库存: {stock()} =====")

    # 1. 微信登录
    r = s.post(f"{BASE}/api/user/wx-login", json={
        "code": CODE, "deviceFingerprint": "fp-" + uuid.uuid4().hex[:12], "nickname": "茶友小红"})
    body = p("1. wx-login", r, show_keys=["tokenType", "expiresIn", "firstLogin"])
    if body.get("code") != 0:
        print("登录失败，中止"); sys.exit(1)
    s.headers["Authorization"] = "Bearer " + body["data"]["accessToken"]

    # 2. 下单#1 qty=2（10 待支付）
    idem1 = uuid.uuid4().hex
    r = s.post(f"{BASE}/api/order/create", json={
        "storeId": 1, "skuId": 6, "qty": 2, "addressId": ADDRESS_ID, "remark": "联测-取消链路"},
        headers={"Idempotency-Key": idem1})
    body = p("2. 下单#1", r, show_keys=["orderNo", "payNo", "payAmount", "expireAt"])
    if body.get("code") != 0:
        print("下单失败，中止"); sys.exit(1)
    order_no1 = body["data"]["orderNo"]
    pay_no1 = body["data"]["payNo"]
    st = stock()
    print(f"    下单后库存: {st}（应 298）")
    assert st == 298, f"!! 下单后库存异常: {st}"

    # 3. 幂等重放（同 Key）→ 40901 重复请求被拦截
    r = s.post(f"{BASE}/api/order/create", json={
        "storeId": 1, "skuId": 6, "qty": 2, "addressId": ADDRESS_ID},
        headers={"Idempotency-Key": idem1})
    body = p("3. 幂等重放(同Key, 预期40901)", r)
    assert body.get("code") == 40901, "!! 幂等兜底未生效"
    st = stock()
    print(f"    重放后库存: {st}（应仍 298）")
    assert st == 298, "!! 重放导致重复扣减"

    # 4. 取消订单#1 → 库存回滚 300
    oid1 = find_order_id(s, order_no1)
    r = s.post(f"{BASE}/api/order/{oid1}/cancel")
    body = p("4. 取消订单#1", r)
    assert body.get("code") == 0, "取消失败"
    st = stock()
    print(f"    取消后库存: {st}（应 300 回滚）")
    assert st == 300, f"!! 库存未回滚: {st}"
    row = order_status(order_no1)
    print(f"    订单#1 状态: {row[2]}（应 70 已关闭）")
    assert row[2] == 70, f"!! 取消后状态异常: {row[2]}"

    # 5. 下单#2 qty=2 → mock-pay → 签收
    idem2 = uuid.uuid4().hex
    r = s.post(f"{BASE}/api/order/create", json={
        "storeId": 1, "skuId": 6, "qty": 2, "addressId": ADDRESS_ID, "remark": "联测-支付签收链路"},
        headers={"Idempotency-Key": idem2})
    body = p("5. 下单#2", r, show_keys=["orderNo", "payNo", "payAmount"])
    if body.get("code") != 0:
        print("下单失败，中止"); sys.exit(1)
    order_no2 = body["data"]["orderNo"]
    pay_no2 = body["data"]["payNo"]
    st = stock()
    print(f"    下单后库存: {st}（应 298）")
    assert st == 298

    # 6. mock 支付
    r = s.post(f"{BASE}/api/internal/mock-pay", json={"payNo": pay_no2})
    body = p("6. mock-pay", r)
    assert body.get("code") == 0, "支付失败"
    row = order_status(order_no2)
    print(f"    订单#2 支付后状态: {row[2]}（应 30 待发货）")
    assert row[2] == 30, f"!! 支付后状态异常: {row[2]}"

    # 7. 待发货状态取消 → 应拒绝
    oid2 = find_order_id(s, order_no2)
    r = s.post(f"{BASE}/api/order/{oid2}/cancel")
    body = p("7. 待发货取消(预期拒绝)", r)
    assert body.get("code") != 0, "!! 待发货订单被错误取消"
    row = order_status(order_no2)
    assert row[2] == 30, f"!! 拒绝后状态被改变: {row[2]}"

    # 8. B 端 admin 登录 + 发货（30→40）
    s2 = requests.Session()
    r = s2.post(f"{BASE}/api/auth/admin/login", json={"username": "admin", "password": "QVMWb_-_mr%+gb4D"})
    body = p("8. admin登录", r)
    if body.get("code") != 0:
        print("admin 登录失败，中止"); sys.exit(1)
    s2.headers["Authorization"] = "Bearer " + body["data"]["accessToken"]
    r = s2.post(f"{BASE}/api/order/admin/{oid2}/ship", json={
        "logisticsNo": "SF" + str(uuid.uuid4().hex[:10]).upper(),
        "carrier": "顺丰速运", "note": "联测发货"})
    body = p("8b. B端发货", r)
    assert body.get("code") == 0, "发货失败"
    row = order_status(order_no2)
    print(f"    订单#2 发货后状态: {row[2]}（应 40 已发货）")
    assert row[2] == 40, f"!! 发货后状态异常: {row[2]}"

    # 9. C 端签收（40→50）
    r = s.post(f"{BASE}/api/order/{oid2}/sign")
    body = p("9. 确认签收", r)
    assert body.get("code") == 0, "签收失败"
    row = order_status(order_no2)
    print(f"    订单#2 签收后状态: {row[2]}（应 50）")
    assert row[2] == 50, f"!! 签收后状态异常: {row[2]}"

    # 9b. 重复签收 → 应拒绝
    r = s.post(f"{BASE}/api/order/{oid2}/sign")
    body = p("9b. 重复签收(预期拒绝)", r)
    assert body.get("code") != 0, "!! 重复签收未被拦截"
    row = order_status(order_no2)
    assert row[2] == 50, f"!! 重复签收后状态被改变: {row[2]}"

    # 10. 我的订单列表（应含 2 单）
    r = s.get(f"{BASE}/api/order/mine", params={"current": 1, "size": 20})
    body = p("10. 我的订单", r)
    records = body["data"].get("records", [])
    print(f"    订单数: {len(records)}（应 ≥2）")
    for rec in records:
        print(f"      #{rec.get('id')} {rec.get('orderNo')} status={rec.get('status')}")
    nos = {rec.get("orderNo") for rec in records}
    assert order_no1 in nos and order_no2 in nos, "!! 我的订单缺失"

    print("\n===== C 端订单全链路联测通过 =====")
    print(f"订单#1 {order_no1}：10待支付 → 取消70（库存回滚 298→300）✅")
    print(f"订单#2 {order_no2}：10待支付 → 30待发货 → 40已发货 → 50已签收 ✅")
    print(f"幂等：同 Key 重放 40901 拦截 ✅；待发货取消拒绝 ✅；重复签收拒绝 ✅")
    print(f"最终库存: {stock()}（应 298）")


if __name__ == "__main__":
    main()
