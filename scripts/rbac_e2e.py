# -*- coding: utf-8 -*-
"""RBAC 权限拦截器全链路联测（v8）

场景矩阵：
  账号          角色            权限码                       预期
  admin         SUPER_ADMIN    全量 29                       所有接口通过
  store_admin1  STORE_ADMIN    13（含 product:edit）          上架/改价通过，发货 40300
  staff1        STORE_STAFF    4（menu:order/refund+refund:submit） 查单通过，上架/发货/库存 40300
  warehouse1    WAREHOUSE      8（含 order:ship/inventory:manage）  发货/库存通过，上架 40300
  C 端 USER     （无 provider）                              管理接口一律 40300，公开接口放行

关键验证：
  1. JWT 携带 roleId claim（r）与 storeId claim（sid）
  2. 权限拦截先于业务校验（无权限返回 40300 而非 409/404）
  3. 总部 admin 权限层放行 → Service 层业务拒绝（分层正确）
  4. USER 主体访问标注接口 40300；未标注公开接口放行
"""
import base64
import json
import sys

import requests

BASE = "http://localhost:8080"
PW = "Store@123456"
ADMIN_PW = "QVMWb_-_mr%+gb4D"  # admin 专用密码（seed 数据）

passed = 0
failed = 0


def p(name, r, expect_code=None, expect_http=None):
    """打印并断言。expect_code: Result.code（0=成功）；expect_http: HTTP 状态码"""
    global passed, failed
    try:
        body = r.json()
        code = body.get("code")
        msg = body.get("message", body.get("msg", ""))
    except Exception:
        body, code, msg = None, None, r.text[:120]
    ok = True
    detail = f"http={r.status_code} code={code} {msg[:60]}"
    if expect_http is not None and r.status_code != expect_http:
        ok = False
        detail += f" | 期望 HTTP {expect_http}"
    if expect_code is not None and code != expect_code:
        ok = False
        detail += f" | 期望 code={expect_code}"
    mark = "✅" if ok else "❌"
    print(f"  {mark} {name}: {detail}")
    if ok:
        passed += 1
    else:
        failed += 1
    return body


def login(username, password=PW):
    r = requests.post(f"{BASE}/api/auth/admin/login",
                      json={"username": username, "password": password})
    body = r.json()
    assert body.get("code") == 0, f"{username} 登录失败: {body}"
    return body["data"]["accessToken"]


def jwt_claims(token):
    """base64url 解 JWT payload 断言 claims"""
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))


def auth(token):
    return {"Authorization": f"Bearer {token}"}


def main():
    global passed, failed
    print("===== 1. 登录与 JWT claims 断言 =====")
    admin_t = login("admin", ADMIN_PW)
    sa1_t = login("store_admin1")
    staff_t = login("staff1")
    wh_t = login("warehouse1")

    c = jwt_claims(admin_t)
    assert c.get("r") == 1 and "sid" not in c, f"admin claims 异常: {c}"
    print("  ✅ admin claims:", {k: c.get(k) for k in ("type", "r", "sid")})

    c = jwt_claims(sa1_t)
    assert c.get("r") == 4 and c.get("sid") == 1, f"store_admin1 claims 异常: {c}"
    print("  ✅ store_admin1 claims:", {k: c.get(k) for k in ("type", "r", "sid")})

    c = jwt_claims(staff_t)
    assert c.get("r") == 5 and c.get("sid") == 1, f"staff1 claims 异常: {c}"
    print("  ✅ staff1 claims:", {k: c.get(k) for k in ("type", "r", "sid")})

    c = jwt_claims(wh_t)
    assert c.get("r") == 3 and "sid" not in c, f"warehouse1 claims 异常: {c}"
    print("  ✅ warehouse1 claims:", {k: c.get(k) for k in ("type", "r", "sid")})
    passed += 4

    print("\n===== 2. STORE_STAFF（staff1：仅查单/退款） =====")
    s = requests.Session()
    s.headers.update(auth(staff_t))
    r = s.get(f"{BASE}/api/order/admin/page")
    p("2.1 查订单列表 menu:order", r, 0)
    r = s.post(f"{BASE}/api/product/store/listings",
               json={"productId": 3, "skuId": 6, "price": 90.00})
    p("2.2 上架 product:edit → 拒绝", r, 40300)
    r = s.post(f"{BASE}/api/order/admin/999999/ship",
               json={"expressCompany": "顺丰", "trackingNo": "SF1234567890"})
    p("2.3 发货 order:ship → 拒绝", r, 40300)
    r = s.post(f"{BASE}/api/product/inventory/adjust",
               json={"skuId": 6, "changeType": 1, "qty": 10})
    p("2.4 库存调整 inventory:manage → 拒绝", r, 40300)

    print("\n===== 3. WAREHOUSE（warehouse1：发货/库存，不可上架） =====")
    s = requests.Session()
    s.headers.update(auth(wh_t))
    r = s.get(f"{BASE}/api/product/inventory/records")
    p("3.1 库存流水 inventory:manage", r, 0)
    r = s.post(f"{BASE}/api/order/admin/999999/ship",
               json={"expressCompany": "顺丰", "trackingNo": "SF1234567890"})
    body = p("3.2 发货 order:ship → 权限放行", r)
    assert body.get("code") != 40300 and body.get("code") != 0, \
        f"发货应权限放行后到业务层报错: {body}"
    print("      ↑ 非40300且非0=权限通过后订单不存在（分层正确）")
    r = s.post(f"{BASE}/api/product/store/listings",
               json={"productId": 3, "skuId": 6, "price": 90.00})
    p("3.3 上架 product:edit → 拒绝", r, 40300)

    print("\n===== 4. STORE_ADMIN（store_admin1：本店经营，不可发货） =====")
    s = requests.Session()
    s.headers.update(auth(sa1_t))
    r = s.put(f"{BASE}/api/product/store/listings/2/price", json={"price": 90.00})
    p("4.1 本店改价 product:edit", r, 0)
    r = s.get(f"{BASE}/api/product/store/listings")
    body = p("4.2 本店上架列表 menu:product", r, 0)
    assert body and body.get("data"), "本店上架列表应为空非空"
    r = s.post(f"{BASE}/api/order/admin/999999/ship",
               json={"expressCompany": "顺丰", "trackingNo": "SF1234567890"})
    p("4.3 发货 order:ship → 拒绝（店铺管理员不发货）", r, 40300)

    print("\n===== 5. SUPER_ADMIN（admin：全量放行 + 分层验证） =====")
    s = requests.Session()
    s.headers.update(auth(admin_t))
    r = s.get(f"{BASE}/api/order/admin/page")
    p("5.1 查订单列表 menu:order", r, 0)
    r = s.post(f"{BASE}/api/product/store/listings",
               json={"productId": 3, "skuId": 6, "price": 90.00})
    body = p("5.2 总部上架 product:edit 权限放行", r)
    assert body.get("code") != 40300 and body.get("code") != 0, \
        f"总部上架应权限放行后由 Service 拒绝: {body}"
    print("      ↑ 非40300且非0=权限通过后总部业务拒绝（分层正确）")

    print("\n===== 6. C 端 USER 主体 =====")
    r = requests.post(f"{BASE}/api/user/wx-login",
                      json={"code": "rbactest", "deviceFingerprint": "rbac-device"})
    body = p("6.1 C端微信登录", r, 0)
    user_t = body["data"]["accessToken"]
    s = requests.Session()
    s.headers.update(auth(user_t))
    r = s.get(f"{BASE}/api/order/admin/page")
    p("6.2 C端令牌访问管理接口 → 拒绝", r, 40300)
    r = s.get(f"{BASE}/api/product/categories")
    p("6.3 C端令牌访问公开分类接口 → 放行", r, 0)

    print("\n===== 7. 无令牌访问 =====")
    r = requests.get(f"{BASE}/api/order/admin/page")
    p("7.1 无令牌访问管理接口 → 40100", r, 40100)

    print(f"\n===== 结果: {passed} 通过 / {failed} 失败 =====")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
