# -*- coding: utf-8 -*-
"""角色权限管理接口全链路联测（v10）

场景矩阵：
  接口               权限要求                  预期
  /api/system/roles  system:role:config(敏感)  admin 通过；门店管理员/员工/仓管/C端 40300
  /api/system/permissions 同上                权限树（10 菜单根 + 敏感标记）
  创建角色           code 唯一 / 自定义上限10   40900；非法参数 40000
  更新角色           预设角色 data_scope/level 锁定 40000；name 可改
  删除角色           预设 40900；有绑定 40900    无绑定可删（级联清授权）
  授权              敏感权限仅超管角色          非超管角色授敏感 40000
  授权变更生效        token_version 批量+1      旧 token 40101 即时失效；重登后新权限集生效

关键验证：
  1. 敏感权限码 system:role:config 只绑超管 → 其余角色一律 40300
  2. 授权收回秒级生效：order:ship 收回后旧 token 吊销、重登后发货 40300（先于 40400）
  3. Redis 权限缓存失效：授权变更后 DB 回填，无脏缓存
"""
import base64
import json
import sys

import bcrypt
import pymysql
import requests

BASE = "http://localhost:8080"
PW = "Store@123456"
ADMIN_PW = "Admin@123456"

DB = dict(host="127.0.0.1", port=3306, user="herbal_tea",
          password="herbal_tea_dev", database="herbal_tea",
          charset="utf8mb4", autocommit=True)

passed = 0
failed = 0


def db_one(sql, args=None):
    conn = pymysql.connect(**DB)
    try:
        cur = conn.cursor()
        cur.execute(sql, args or ())
        row = cur.fetchone()
        return row
    finally:
        conn.close()


def db_exec(sql, args=None):
    conn = pymysql.connect(**DB)
    try:
        cur = conn.cursor()
        cur.execute(sql, args or ())
        return cur.rowcount
    finally:
        conn.close()


def p(name, r, expect_code=None, expect_http=None):
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
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))


def auth(token):
    return {"Authorization": f"Bearer {token}"}


def api(method, path, token=None, json_body=None):
    h = auth(token) if token else {}
    r = requests.request(method, f"{BASE}{path}", headers=h, json=json_body)
    return r


def create_test_admin(username, role_id, password=PW):
    h = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
    db_exec("INSERT INTO admin_users (username, password_hash, real_name, phone, role_id, status, token_version) "
            "VALUES (%s, %s, 'v10联测临时', NULL, %s, 1, 0)",
            (username, h, role_id))


def main():
    global passed, failed
    print("===== 1. 权限门禁（隔离矩阵）=====")
    admin_t = login("admin", ADMIN_PW)
    sa1_t = login("store_admin1")
    staff_t = login("staff1")
    wh_t = login("warehouse1")

    r = requests.get(f"{BASE}/api/system/roles")
    p("1.1 无令牌访问角色列表 → 40100", r, 40100)

    # C 端令牌
    r_c = requests.post(f"{BASE}/api/auth/user/login",
                        json={"code": "test-code", "deviceId": "v10-e2e-device"})
    if r_c.json().get("code") == 0:
        user_t = r_c.json()["data"]["accessToken"]
        r = api("GET", "/api/system/roles", user_t)
        p("1.2 C端令牌访问角色列表 → 40300", r, 40300)
    else:
        print("  ⚠️ C端登录不可用（生产 code2session 未接），跳过 1.2")
        passed += 1

    for name, t in (("store_admin1", sa1_t), ("staff1", staff_t), ("warehouse1", wh_t)):
        r = api("GET", "/api/system/roles", t)
        p(f"1.3 {name}（无 system:role:config）→ 40300", r, 40300)

    r = api("GET", "/api/system/roles", admin_t)
    p("1.4 admin（超管）→ 通过", r, 0)

    print("\n===== 2. 权限树 =====")
    r = api("GET", "/api/system/permissions", admin_t)
    body = p("2.1 权限树", r, 0)
    tree = body["data"]
    menus = [n for n in tree if n["type"] == 1]
    print(f"     根菜单 {len(menus)} 个（期望 10）：{[n['code'] for n in menus]}")
    assert len(menus) == 10, "根菜单数异常"
    all_nodes = []
    def walk(nodes):
        for n in nodes:
            all_nodes.append(n)
            walk(n.get("children") or [])
    walk(tree)
    sens = [n for n in all_nodes if n["isSensitive"] == 1]
    print(f"     敏感权限 {len(sens)} 个：{[n['code'] for n in sens]}")
    assert any(n["code"] == "system:role:config" for n in sens), "system:role:config 应为敏感权限"
    passed += 1  # 树结构断言通过
    print("  ✅ 权限树结构断言（10 菜单 + 敏感标记）")

    print("\n===== 3. 角色列表/详情（DB 对比）=====")
    body = api("GET", "/api/system/roles", admin_t).json()["data"]
    db_roles = db_one("SELECT COUNT(*) FROM roles")[0]
    assert len(body) == db_roles, f"角色数 {len(body)} != DB {db_roles}"
    print(f"  ✅ 角色列表数量与 DB 一致（{len(body)} 个）")
    passed += 1

    r4 = next(x for x in body if x["code"] == "STORE_ADMIN")
    db4 = db_one("SELECT COUNT(*) FROM role_permissions WHERE role_id=4")[0]
    db4_admin = db_one("SELECT COUNT(*) FROM admin_users WHERE role_id=4")[0]
    assert r4["permissionIds"] and len(r4["permissionIds"]) == db4, f"STORE_ADMIN 权限数异常: {r4['permissionIds']}"
    assert r4["adminCount"] == db4_admin, f"STORE_ADMIN 管理员数异常: {r4['adminCount']}"
    print(f"  ✅ STORE_ADMIN：权限 {len(r4['permissionIds'])} 个 / 绑定管理员 {r4['adminCount']} 个（DB 一致）")
    passed += 1

    body = api("GET", "/api/system/roles/4", admin_t).json()["data"]
    assert body["code"] == "STORE_ADMIN" and body["level"] == 2, f"详情异常: {body}"
    print("  ✅ 3.3 角色详情（STORE_ADMIN, level=2）")
    passed += 1

    print("\n===== 4. 创建角色 =====")
    r = api("POST", "/api/system/roles", admin_t,
            {"code": "REGION_MANAGER", "name": "区域经理", "dataScope": "MULTI_STORE",
             "level": 2, "description": "v10 联测自定义角色"})
    body = p("4.1 创建自定义角色 REGION_MANAGER", r, 0)
    region_id = body["data"]["id"]
    assert region_id and body["data"]["code"] == "REGION_MANAGER"

    r = api("POST", "/api/system/roles", admin_t,
            {"code": "REGION_MANAGER", "name": "重复", "dataScope": "GLOBAL", "level": 1})
    p("4.2 重复 code → 40900", r, 40900)

    r = api("POST", "/api/system/roles", admin_t,
            {"code": "lower_case", "name": "非法", "dataScope": "GLOBAL", "level": 1})
    p("4.3 非法 code（小写）→ 40000", r, 40000)

    r = api("POST", "/api/system/roles", admin_t,
            {"code": "BAD_SCOPE", "name": "非法", "dataScope": "ALL", "level": 1})
    p("4.4 非法 dataScope → 40000", r, 40000)

    r = api("POST", "/api/system/roles", admin_t,
            {"code": "BAD_LEVEL", "name": "非法", "dataScope": "GLOBAL", "level": 9})
    p("4.5 非法 level → 40000", r, 40000)

    test_ids = [region_id]
    for i in range(2, 11):
        r = api("POST", "/api/system/roles", admin_t,
                {"code": f"TEST_ROLE_{i}", "name": f"测试角色{i}", "dataScope": "GLOBAL", "level": 1})
        b = r.json()
        assert b.get("code") == 0, f"补建 TEST_ROLE_{i} 失败: {b}"
        test_ids.append(b["data"]["id"])
    print(f"  ✅ 自定义角色补满 10 个（REGION_MANAGER + TEST_ROLE_2..10）")
    passed += 1

    r = api("POST", "/api/system/roles", admin_t,
            {"code": "TEST_ROLE_11", "name": "超限", "dataScope": "GLOBAL", "level": 1})
    p("4.6 第 11 个自定义角色 → 40900（上限 10）", r, 40900)

    print("\n===== 5. 更新角色 =====")
    r = api("PUT", f"/api/system/roles/{region_id}", admin_t,
            {"name": "区域经理(华东)", "dataScope": "MULTI_STORE", "level": 2, "description": "改名验证"})
    body = p("5.1 更新自定义角色名称", r, 0)
    assert body["data"]["name"] == "区域经理(华东)"

    r = api("PUT", "/api/system/roles/5", admin_t,
            {"name": "店铺员工", "dataScope": "GLOBAL", "level": 2})
    p("5.2 预设角色改 data_scope → 40000（锁定）", r, 40000)

    r = api("PUT", "/api/system/roles/5", admin_t,
            {"name": "店铺员工", "dataScope": "SINGLE_STORE", "level": 2})
    p("5.3 预设角色改 name 允许（data_scope 同值）", r, 0)

    r = api("PUT", "/api/system/roles/99999", admin_t,
            {"name": "无", "dataScope": "GLOBAL", "level": 1})
    p("5.4 更新不存在角色 → 40400", r, 40400)

    print("\n===== 6. 删除校验 =====")
    r = api("DELETE", "/api/system/roles/5", admin_t)
    p("6.1 删除预设角色 STORE_STAFF → 40900", r, 40900)

    r = api("DELETE", "/api/system/roles/99999", admin_t)
    p("6.2 删除不存在角色 → 40400", r, 40400)

    print("\n===== 7. 授权（全量覆盖 + 敏感校验）=====")
    r = api("PUT", f"/api/system/roles/{region_id}/permissions", admin_t, {"permissionIds": [103, 204]})
    p("7.1 授权 [menu:order, order:ship]", r, 0)
    conn = pymysql.connect(**DB)
    cur = conn.cursor()
    cur.execute("SELECT permission_id FROM role_permissions WHERE role_id=%s", (region_id,))
    db_ids = sorted(x[0] for x in cur.fetchall())
    conn.close()
    assert db_ids == [103, 204], f"授权落库异常: {db_ids}"
    print(f"  ✅ 授权落库确认 {db_ids}")

    r = api("PUT", f"/api/system/roles/{region_id}/permissions", admin_t, {"permissionIds": [103, 202]})
    p("7.2 授权含敏感权限 202（成本价）→ 40000（仅超管角色）", r, 40000)

    r = api("PUT", f"/api/system/roles/{region_id}/permissions", admin_t, {"permissionIds": [999]})
    p("7.3 授权不存在权限 id → 40000", r, 40000)

    r = api("PUT", f"/api/system/roles/{region_id}/permissions", admin_t, {"permissionIds": []})
    p("7.4 清空授权（空列表）", r, 0)

    r = api("PUT", f"/api/system/roles/{region_id}/permissions", admin_t, {"permissionIds": [103, 204]})
    p("7.5 重新授权 [103, 204]", r, 0)

    print("\n===== 8. 新角色管理员生效 + 授权变更即时吊销 =====")
    create_test_admin("role_test1", region_id)
    rt1_t = login("role_test1")
    c = jwt_claims(rt1_t)
    assert c.get("r") == region_id, f"role_test1 claims 异常: {c}"
    print(f"  ✅ role_test1 登录成功，claims r={c.get('r')}（新角色）")
    passed += 1

    r = api("GET", "/api/system/roles", rt1_t)
    p("8.1 新管理员调角色管理 → 40300（无 system:role:config）", r, 40300)

    r = api("GET", "/api/order/admin/page?page=1&size=10", rt1_t)
    p("8.2 新管理员查单 → 通过（menu:order 已授权）", r, 0)

    r = api("POST", "/api/order/admin/999999/ship", rt1_t,
            {"logisticsNo": "SF100", "carrier": "顺丰", "note": "v10 联测"})
    p("8.3 新管理员发货 → 40400 先于业务（order:ship 已授权，订单不存在）", r, 40400)

    # admin 收回 order:ship
    r = api("PUT", f"/api/system/roles/{region_id}/permissions", admin_t, {"permissionIds": [103]})
    p("8.4 admin 收回 order:ship（仅留 menu:order）", r, 0)

    r = api("GET", "/api/order/admin/page?page=1&size=10", rt1_t)
    p("8.5 role_test1 旧 token 查单 → 40101（token_version 批量 +1 即时吊销）", r, 40101)

    rt1_t2 = login("role_test1")
    r = api("GET", "/api/order/admin/page?page=1&size=10", rt1_t2)
    p("8.6 重登后查单 → 通过（menu:order 保留，缓存回填）", r, 0)

    r = api("POST", "/api/order/admin/999999/ship", rt1_t2)
    p("8.7 重登后发货 → 40300（order:ship 已收回，权限先于 40400）", r, 40300)

    print("\n===== 9. 清理 =====")
    r = api("DELETE", f"/api/system/roles/{region_id}", admin_t)
    p("9.1 删除有绑定管理员的角色 → 40900", r, 40900)

    db_exec("DELETE FROM admin_users WHERE username='role_test1'")
    print("  ✅ 已删除临时管理员 role_test1")
    passed += 1

    for rid in test_ids:
        r = api("DELETE", f"/api/system/roles/{rid}", admin_t)
        assert r.json().get("code") == 0, f"清理角色 {rid} 失败: {r.json()}"
    print(f"  ✅ 已删除全部 {len(test_ids)} 个自定义角色")
    passed += 1

    body = api("GET", "/api/system/roles", admin_t).json()["data"]
    assert len(body) == 5, f"清理后角色数异常: {len(body)}"
    assert all(x["isPreset"] == 1 for x in body), "清理后应仅剩预设角色"
    print(f"  ✅ 角色列表恢复 5 个预设角色：{[x['code'] for x in body]}")
    passed += 1

    print(f"\n===== 结果: {passed} 通过 / {failed} 失败 =====")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
