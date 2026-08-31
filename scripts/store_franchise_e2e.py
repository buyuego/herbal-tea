# -*- coding: utf-8 -*-
"""Store 模块骨架全链路联测（v9）：加盟申请 → 总部审批 → 管理员绑定 → D14 目录复核

场景矩阵：
  主体            操作                              预期
  C 端 USER       提交加盟申请                        0（返回 applicationId）
  C 端 USER       同手机号重复申请                    40900（待审核幂等）
  C 端 USER       调总部审批接口                      40300（C/B 隔离）
  无令牌          调总部审批接口                      40100
  store_admin1    调总部审批/申请列表                 40300（有 menu:store，无 store:franchise:approve）
  admin(超管)     申请列表/审批通过                   0（事务：建店 ST003 + 结算配置 + 保证金流水）
  admin          重复审批同一申请                    40900
  admin          拒绝第二条申请                      0（status=2 + reviewNote）
  admin          绑定管理员到新店                    0（首绑自动店主 is_owner=1）
  franchise_owner1 登录                             sid=3 / r=4（新店数据范围生效）
  franchise_owner1 调审批接口                        40300（无总部权限码）
  store_admin2    本店上架 + 目录变更置脏             D14 复核列表命中本店，店间隔离
"""
import base64
import json
import sys

import pymysql
import requests

BASE = "http://localhost:8080"
PW = "Store@123456"
ADMIN_PW = "QVMWb_-_mr%+gb4D"

passed = 0
failed = 0


def p(name, r, expect_code=None, expect_http=None):
    """打印并断言。expect_code: Result.code；expect_http: HTTP 状态码"""
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


def wx_login(device):
    r = requests.post(f"{BASE}/api/user/wx-login",
                      json={"code": "storev9", "deviceFingerprint": device})
    body = r.json()
    assert body.get("code") == 0, f"wx-login 失败: {body}"
    return body["data"]["accessToken"]


def jwt_claims(token):
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))


def auth(token):
    return {"Authorization": f"Bearer {token}"}


def db():
    return pymysql.connect(host="127.0.0.1", port=3306, user="herbal_tea",
                           password="herbal_tea_dev", database="herbal_tea",
                           charset="utf8mb4", autocommit=True)


def db_one(sql, args=()):
    conn = db()
    cur = conn.cursor()
    cur.execute(sql, args)
    row = cur.fetchone()
    cur.close()
    conn.close()
    return row


def ensure_owner_admin():
    """幂等创建 franchise_owner1（STORE_ADMIN 角色，密码 Store@123456），返回 adminId"""
    import bcrypt
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT id FROM admin_users WHERE username='franchise_owner1'")
    row = cur.fetchone()
    if row:
        cur.close()
        conn.close()
        return row[0]
    h = bcrypt.hashpw(PW.encode(), bcrypt.gensalt(rounds=10)).decode()
    cur.execute("""INSERT INTO admin_users (username, password_hash, real_name, phone, role_id, status,
                   token_version, created_at, updated_at)
                   VALUES ('franchise_owner1', %s, '加盟店主', '13900000009', 4, 1, 0, NOW(), NOW())""",
                (h,))
    aid = cur.lastrowid
    cur.close()
    conn.close()
    print(f"  ℹ 创建 franchise_owner1 管理员 id={aid}")
    return aid


def main():
    global passed, failed
    admin_t = login("admin", ADMIN_PW)
    sa1_t = login("store_admin1")
    sa2_t = login("store_admin2")

    print("===== 1. C 端加盟申请 =====")
    user_t = wx_login("store-v9-device-1")
    s = requests.Session()
    s.headers.update(auth(user_t))
    r = s.post(f"{BASE}/api/store/franchise/apply",
               json={"applicantName": "陈加盟", "phone": "13900000001",
                     "intendedRegion": "广州市天河区", "experience": "5 年餐饮经营"})
    body = p("1.1 提交加盟申请", r, 0)
    app_id = body["data"]
    print(f"      ↑ applicationId={app_id}")

    r = s.post(f"{BASE}/api/store/franchise/apply",
               json={"applicantName": "陈加盟", "phone": "13900000001"})
    p("1.2 同手机号重复申请 → 40900", r, 40900)

    r = s.post(f"{BASE}/api/store/franchise/apply",
               json={"applicantName": "陈加盟", "phone": "12345"})
    p("1.3 手机号格式错误 → 40000", r, 40000)

    print("\n===== 2. 越权与主体隔离 =====")
    r = s.post(f"{BASE}/api/store/admin/franchise/applications/{app_id}/approve")
    p("2.1 C 端令牌调总部审批 → 40300", r, 40300)
    r = requests.post(f"{BASE}/api/store/admin/franchise/applications/{app_id}/approve")
    p("2.2 无令牌调审批 → 40100", r, 40100)

    s1 = requests.Session()
    s1.headers.update(auth(sa1_t))
    r = s1.get(f"{BASE}/api/store/admin/franchise/applications")
    p("2.3 门店管理员查申请列表 → 40300（有 menu:store 但无 store:franchise:approve）", r, 40300)
    r = s1.post(f"{BASE}/api/store/admin/franchise/applications/{app_id}/approve")
    p("2.4 门店管理员调审批 → 40300", r, 40300)

    print("\n===== 3. 总部审批通过（事务） =====")
    s0 = requests.Session()
    s0.headers.update(auth(admin_t))
    r = s0.get(f"{BASE}/api/store/admin/franchise/applications?status=0")
    body = p("3.1 待审核申请列表", r, 0)
    ids = [row["id"] for row in body["data"]["records"]]
    assert app_id in ids, f"申请 {app_id} 不在待审核列表: {ids}"
    print(f"      ↑ 待审核列表含 {app_id}")

    r = s0.post(f"{BASE}/api/store/admin/franchise/applications/{app_id}/approve")
    body = p("3.2 审批通过", r, 0)
    new_store_id = body["data"]
    print(f"      ↑ 新门店 id={new_store_id}")

    row = db_one("SELECT status, reviewed_by, reviewed_at IS NOT NULL FROM franchise_applications WHERE id=%s",
                 (app_id,))
    assert row == (1, 1, 1), f"申请状态异常: {row}"
    print("  ✅ 3.3 DB: 申请 status=1 / reviewed_by=1 / reviewed_at 落库")
    passed += 1

    row = db_one("SELECT store_no, store_name, store_type, status, contact_phone FROM stores WHERE id=%s",
                 (new_store_id,))
    import re as _re
    assert row and _re.fullmatch(r"ST\d{3}", row[0]) and row[2] == 2 and row[3] == 1, f"门店异常: {row}"
    print(f"  ✅ 3.4 DB: 门店 {row[0]} 加盟店(2)/正常(1)/联系人 {row[4]}")
    passed += 1

    row = db_one("SELECT commission_rate, cycle_type, auto_confirm_hours FROM store_settlement_configs WHERE store_id=%s",
                 (new_store_id,))
    assert float(row[0]) == 0.05 and row[1] == 1 and row[2] == 72, f"结算配置异常: {row}"
    print("  ✅ 3.5 DB: 结算配置 佣金5%/T+1日结/72h 自动确认")
    passed += 1

    row = db_one("SELECT type, amount, status, biz_no FROM franchise_deposits WHERE store_id=%s", (new_store_id,))
    assert row and row[0] == 1 and float(row[1]) == 20000.0 and row[2] == 0 and row[3] == f"FR-{app_id}", \
        f"保证金流水异常: {row}"
    print(f"  ✅ 3.6 DB: 保证金缴纳流水 ¥20000 待处理 biz_no={row[3]}")
    passed += 1

    r = s0.post(f"{BASE}/api/store/admin/franchise/applications/{app_id}/approve")
    p("3.7 重复审批同一申请 → 40900", r, 40900)

    print("\n===== 4. 总部审批拒绝 =====")
    user2_t = wx_login("store-v9-device-2")
    s2 = requests.Session()
    s2.headers.update(auth(user2_t))
    r = s2.post(f"{BASE}/api/store/franchise/apply",
                json={"applicantName": "刘先生", "phone": "13900000002", "intendedRegion": "佛山"})
    body = p("4.1 第二条加盟申请", r, 0)
    app2 = body["data"]
    r = s0.post(f"{BASE}/api/store/admin/franchise/applications/{app2}/reject",
                params={"reviewNote": "意向区域与现有网点重叠"})
    p("4.2 审批拒绝", r, 0)
    row = db_one("SELECT status, review_note, reviewed_by FROM franchise_applications WHERE id=%s", (app2,))
    assert row == (2, "意向区域与现有网点重叠", 1), f"拒绝落库异常: {row}"
    print("  ✅ 4.3 DB: 申请 status=2 / review_note / reviewed_by 落库")
    passed += 1
    r = s0.post(f"{BASE}/api/store/admin/franchise/applications/{app2}/reject",
                params={"reviewNote": "again"})
    p("4.4 重复拒绝 → 40900", r, 40900)

    print("\n===== 5. 门店管理员绑定（首绑自动店主） =====")
    owner_id = ensure_owner_admin()
    r = s0.post(f"{BASE}/api/store/admin/admins/bind",
                json={"adminId": owner_id, "storeId": new_store_id})
    p("5.1 总部绑定管理员到新店", r, 0)
    row = db_one("SELECT is_owner, status FROM store_admins WHERE admin_id=%s AND store_id=%s",
                 (owner_id, new_store_id))
    assert row == (1, 1), f"首绑应为店主: {row}"
    print("  ✅ 5.2 DB: 首绑自动店主 is_owner=1 / status=1")
    passed += 1

    r = s0.post(f"{BASE}/api/store/admin/admins/bind",
                json={"adminId": owner_id, "storeId": new_store_id})
    p("5.3 重复绑定（复绑幂等）", r, 0)
    row = db_one("SELECT COUNT(*) FROM store_admins WHERE admin_id=%s AND store_id=%s",
                 (owner_id, new_store_id))
    assert row[0] == 1, f"复绑不应产生新行: {row}"
    print("  ✅ 5.4 DB: 复绑未产生重复行（upsert）")
    passed += 1

    r = s0.get(f"{BASE}/api/store/admin/admins", params={"storeId": new_store_id})
    body = p("5.5 新店管理员列表", r, 0)
    assert any(x["adminId"] == owner_id and x["isOwner"] == 1 for x in body["data"]), \
        f"列表缺少新店主: {body['data']}"
    print(f"      ↑ 列表 {len(body['data'])} 条，含店主 franchise_owner1")

    r = s0.post(f"{BASE}/api/store/admin/admins/bind",
                json={"adminId": 999999, "storeId": new_store_id})
    p("5.6 绑定不存在管理员 → 40400", r, 40400)

    owner_t = login("franchise_owner1")
    c = jwt_claims(owner_t)
    assert c.get("sid") == new_store_id and c.get("r") == 4, f"新店主 claims 异常: {c}"
    print(f"  ✅ 5.7 JWT claims: sid={c.get('sid')} r={c.get('r')}（新店数据范围生效）")
    passed += 1

    so = requests.Session()
    so.headers.update(auth(owner_t))
    r = so.get(f"{BASE}/api/store/pending-catalog-review")
    p("5.8 新店主查本店目录复核（menu:product 放行）", r, 0)
    r = so.post(f"{BASE}/api/store/admin/franchise/applications/{app2}/approve")
    p("5.9 新店主调总部审批 → 40300", r, 40300)

    print("\n===== 6. D14 目录变更复核（店间隔离） =====")
    s2a = requests.Session()
    s2a.headers.update(auth(sa2_t))
    r = s2a.post(f"{BASE}/api/product/store/listings",
                 json={"productId": 3, "skuId": 6, "price": 88.00})
    p("6.1 店2 本店上架（product3/sku6）", r, 0)

    conn = db()
    cur = conn.cursor()
    cur.execute("UPDATE store_products SET catalog_dirty=1 WHERE store_id=2 AND sku_id=6")
    print(f"  ℹ 模拟目录变更事件：店2 sku6 置 catalog_dirty=1（{cur.rowcount} 行）")
    cur.close()
    conn.close()

    r = s2a.get(f"{BASE}/api/store/pending-catalog-review")
    body = p("6.2 店2 查目录变更复核", r, 0)
    hit = [x for x in body["data"] if x["storeId"] == 2 and x["skuId"] == 6]
    assert hit, f"店2 复核列表未命中置脏记录: {body['data']}"
    print(f"      ↑ 命中 {len(hit)} 条：product={hit[0]['productName']} sku={hit[0]['skuCode']}")

    r = s1.get(f"{BASE}/api/store/pending-catalog-review")
    body = p("6.3 店1 查目录变更复核", r, 0)
    assert all(x["storeId"] == 1 for x in body["data"]), "店1 复核列表应仅含本店数据"
    print("  ✅ 6.4 DB/接口: 店间数据隔离（店2 的置脏记录不出现在店1）")
    passed += 1

    print(f"\n===== 结果: {passed} 通过 / {failed} 失败 =====")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
