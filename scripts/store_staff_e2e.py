# -*- coding: utf-8 -*-
"""员工账号管理接口全链路联测（v11，store:staff:manage）

场景矩阵：
  接口                         权限要求              预期
  /api/store/staff             store:staff:manage   店主管通；员工/仓管 40300；超管无门店 40000
  创建员工                     强制 STORE_STAFF      登录 claims r=5 sid=1；重复 username 40900
  更新/重置/移除               目标必须本店+员工角色  跨店 40400；操作店主 40000
  禁用/改密/移除               即时吊销（R9）       旧 token 40101；禁用登录 40300
  移除后复绑                   账号复用             复绑成功可登录

关键验证：
  1. 门店自治边界：storeId 取登录上下文，跨店操作一律 40400（不暴露他店员工存在性）
  2. 提权面收敛：创建不接收 roleId，操作目标限 STORE_STAFF，店主/管理员账号 40000
  3. 状态变更秒级生效：禁用/改密/移除后旧令牌 40101，重登后按新状态执行
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
STAFF_PW = "Staff@123456"
STAFF_NEW_PW = "NewPass@2026"
PREFIX = "v11_"  # 测试账号前缀，清理按此删除

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
        return cur.fetchone()
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
            "VALUES (%s, %s, 'v11联测临时', NULL, %s, 1, 0)",
            (username, h, role_id))


def cleanup_test_data():
    """幂等清理：v11_ 前缀测试账号及其绑定，保证可重复执行"""
    db_exec(f"DELETE sa FROM store_admins sa JOIN admin_users u ON u.id = sa.admin_id "
            f"WHERE u.username LIKE '{PREFIX}%%'")
    db_exec(f"DELETE FROM admin_users WHERE username LIKE '{PREFIX}%%'")


def main():
    global passed, failed
    print("===== 0. 数据准备与基线清理 =====")
    cleanup_test_data()

    admin_t = login("admin", ADMIN_PW)
    sa1_t = login("store_admin1")   # 店1 店主（role=4）
    sa2_t = login("store_admin2")   # 店2 店主（role=4）
    staff1_t = login("staff1")      # 店1 员工（role=5）
    wh_t = login("warehouse1")      # 总部仓管（role=3）

    # ============ 1. 权限门禁 ============
    print("===== 1. 权限门禁（隔离矩阵）=====")
    p("1.1 无令牌调员工列表", api("GET", "/api/store/staff"), expect_code=40100)
    p("1.2 员工调员工列表", api("GET", "/api/store/staff", staff1_t), expect_code=40300)
    p("1.3 员工创建员工", api("POST", "/api/store/staff", staff1_t,
                             json_body={"username": PREFIX + "x", "realName": "x",
                                        "phone": "13800138000", "password": STAFF_PW}),
      expect_code=40300)
    p("1.4 仓管调员工列表", api("GET", "/api/store/staff", wh_t), expect_code=40300)
    p("1.5 超管（无绑定门店）调员工列表", api("GET", "/api/store/staff", admin_t), expect_code=40000)

    claims = jwt_claims(sa1_t)
    print(f"      claims r={claims.get('r')} sid={claims.get('sid')}")
    p("1.6 店主登录 claims（r=4 sid=1）",
      _FakeResp(), 0 if (claims.get("r") == 4 and claims.get("sid") == 1) else -1)

    # ============ 2. 创建员工 ============
    print("===== 2. 创建员工 =====")
    r = api("POST", "/api/store/staff", sa1_t, json_body={
        "username": PREFIX + "staff1", "realName": "测试员工甲",
        "phone": "13800138000", "password": STAFF_PW})
    body = p("2.1 店1店主创建员工", r, expect_code=0)
    staff_admin_id = body["data"] if body and body.get("code") == 0 else None

    row = db_one("SELECT role_id, status, token_version, password_hash FROM admin_users WHERE id = %s",
                 (staff_admin_id,))
    p("2.2 落库：员工角色/启用/BCrypt",
      _FakeResp(), 0) if (row and row[0] == 5 and row[1] == 1 and row[2] == 0
                          and row[3].startswith("$2")) else p("2.2 落库：员工角色/启用/BCrypt", _FakeResp(), -1)
    row2 = db_one("SELECT store_id, is_owner, status FROM store_admins WHERE admin_id = %s",
                  (staff_admin_id,))
    p("2.3 落库：绑定本店非店主",
      _FakeResp(), 0) if (row2 and row2 == (1, 0, 1)) else p("2.3 落库：绑定本店非店主", _FakeResp(), -1)

    staff_t = login(PREFIX + "staff1", STAFF_PW)
    claims = jwt_claims(staff_t)
    p("2.4 新员工登录（r=5 sid=1）",
      _FakeResp(), 0) if (claims.get("r") == 5 and claims.get("sid") == 1) else p("2.4 新员工登录", _FakeResp(), -1)
    p("2.5 新员工可查订单（menu:order 放行）", api("GET", "/api/order/admin/page?page=1&size=1", staff_t),
      expect_code=0)
    p("2.6 新员工调员工管理 40300", api("GET", "/api/store/staff", staff_t), expect_code=40300)

    p("2.7 重复 username 创建 40900", api("POST", "/api/store/staff", sa1_t, json_body={
        "username": PREFIX + "staff1", "realName": "x", "phone": "13800138000", "password": STAFF_PW}),
      expect_code=40900)
    p("2.8 非法 username（数字开头）40000", api("POST", "/api/store/staff", sa1_t, json_body={
        "username": "1abc", "realName": "x", "phone": "13800138000", "password": STAFF_PW}),
      expect_code=40000)
    p("2.9 非法手机号 40000", api("POST", "/api/store/staff", sa1_t, json_body={
        "username": PREFIX + "badphone", "realName": "x", "phone": "123", "password": STAFF_PW}),
      expect_code=40000)
    p("2.10 密码过短 40000", api("POST", "/api/store/staff", sa1_t, json_body={
        "username": PREFIX + "badpw", "realName": "x", "phone": "13800138000", "password": "123"}),
      expect_code=40000)
    p("2.11 店2店主创建店1已有员工（跨店占用）40900", api("POST", "/api/store/staff", sa2_t, json_body={
        "username": PREFIX + "staff1", "realName": "x", "phone": "13800138000", "password": STAFF_PW}),
      expect_code=40900)

    # ============ 3. 列表 ============
    print("===== 3. 列表 =====")
    r = api("GET", "/api/store/staff?page=1&size=10", sa1_t)
    body = p("3.1 店1员工列表（含新员工）", r, expect_code=0)
    if body and body.get("code") == 0:
        records = body["data"]["records"]
        names = [x["username"] for x in records]
        new = next((x for x in records if x["username"] == PREFIX + "staff1"), None)
        ok = (body["data"]["total"] >= 2 and "staff1" in names
              and new is not None and new["roleName"] == "店铺员工"
              and new["isOwner"] == 0 and new["adminStatus"] == 1)
        p("3.2 列表字段（roleName/isOwner/adminStatus）",
          _FakeResp(), 0) if ok else p("3.2 列表字段", _FakeResp(), -1)
    r = api("GET", "/api/store/staff?boundStatus=0", sa1_t)
    body = p("3.3 已移除过滤（当前无移除）", r, expect_code=0)
    if body and body.get("code") == 0:
        names = [x["username"] for x in body["data"]["records"]]
        p("3.4 正常员工不在已移除列表",
          _FakeResp(), 0) if PREFIX + "staff1" not in names else p("3.4 已移除过滤", _FakeResp(), -1)

    # ============ 4. 更新 ============
    print("===== 4. 更新 =====")
    p("4.1 更新姓名/手机号", api("PUT", f"/api/store/staff/{staff_admin_id}", sa1_t, json_body={
        "realName": "测试员工甲改", "phone": "13900139000", "status": 1}), expect_code=0)
    row = db_one("SELECT real_name, phone FROM admin_users WHERE id = %s", (staff_admin_id,))
    p("4.2 更新落库断言",
      _FakeResp(), 0) if (row and row == ("测试员工甲改", "13900139000")) else p("4.2 更新落库", _FakeResp(), -1)
    p("4.3 更新不存在员工 40400", api("PUT", "/api/store/staff/999999", sa1_t, json_body={
        "realName": "x", "phone": None, "status": 1}), expect_code=40400)
    p("4.4 跨店更新（店2店主改店1员工）40400", api("PUT", f"/api/store/staff/{staff_admin_id}", sa2_t, json_body={
        "realName": "x", "phone": None, "status": 1}), expect_code=40400)
    p("4.5 操作店主（非员工角色）40000", api("PUT", "/api/store/staff/2", sa1_t, json_body={
        "realName": "x", "phone": None, "status": 1}), expect_code=40000)

    # ============ 5. 禁用/启用 ============
    print("===== 5. 禁用/启用（即时吊销）=====")
    p("5.1 禁用员工", api("PUT", f"/api/store/staff/{staff_admin_id}", sa1_t, json_body={
        "realName": "测试员工甲改", "phone": "13900139000", "status": 0}), expect_code=0)
    row = db_one("SELECT status, token_version FROM admin_users WHERE id = %s", (staff_admin_id,))
    p("5.2 落库：禁用 + token_version+1",
      _FakeResp(), 0) if (row and row[0] == 0 and row[1] == 1) else p("5.2 禁用落库", _FakeResp(), -1)
    p("5.3 旧 token 调列表 40101", api("GET", "/api/store/staff", staff_t), expect_code=40101)
    r = requests.post(f"{BASE}/api/auth/admin/login",
                      json={"username": PREFIX + "staff1", "password": STAFF_PW})
    p("5.4 禁用后登录 40300", r, expect_code=40300)
    p("5.5 重新启用", api("PUT", f"/api/store/staff/{staff_admin_id}", sa1_t, json_body={
        "realName": "测试员工甲改", "phone": "13900139000", "status": 1}), expect_code=0)
    staff_t = login(PREFIX + "staff1", STAFF_PW)  # 启用后重新登录
    p("5.6 启用后登录成功", _FakeResp(), 0)

    # ============ 6. 重置密码 ============
    print("===== 6. 重置密码（即时吊销）=====")
    p("6.1 店主重置员工密码", api("PUT", f"/api/store/staff/{staff_admin_id}/password", sa1_t,
                               json_body={"newPassword": STAFF_NEW_PW}), expect_code=0)
    p("6.2 旧 token 40101", api("GET", "/api/store/staff", staff_t), expect_code=40101)
    r = requests.post(f"{BASE}/api/auth/admin/login",
                      json={"username": PREFIX + "staff1", "password": STAFF_PW})
    p("6.3 旧密码登录 40100", r, expect_code=40100)
    staff_t = login(PREFIX + "staff1", STAFF_NEW_PW)
    p("6.4 新密码登录成功", _FakeResp(), 0)

    # ============ 7. 移除/复绑 ============
    print("===== 7. 移除/复绑 =====")
    p("7.1 移除员工", api("DELETE", f"/api/store/staff/{staff_admin_id}", sa1_t), expect_code=0)
    row = db_one("SELECT sa.status, u.token_version FROM store_admins sa "
                 "JOIN admin_users u ON u.id = sa.admin_id WHERE sa.admin_id = %s", (staff_admin_id,))
    p("7.2 落库：绑定移除 + 令牌吊销",
      _FakeResp(), 0) if (row and row[0] == 0 and row[1] >= 2) else p("7.2 移除落库", _FakeResp(), -1)
    p("7.3 旧 token 40101", api("GET", "/api/store/staff", staff_t), expect_code=40101)
    p("7.4 重复移除幂等（200）", api("DELETE", f"/api/store/staff/{staff_admin_id}", sa1_t), expect_code=0)
    r = api("GET", "/api/store/staff?boundStatus=0", sa1_t)
    body = p("7.5 已移除列表含该员工", r, expect_code=0)
    if body and body.get("code") == 0:
        names = [x["username"] for x in body["data"]["records"]]
        p("7.6 移除记录可见",
          _FakeResp(), 0) if PREFIX + "staff1" in names else p("7.6 移除记录可见", _FakeResp(), -1)
    r = api("POST", "/api/store/staff", sa1_t, json_body={
        "username": PREFIX + "staff1", "realName": "测试员工甲复绑",
        "phone": "13800138000", "password": STAFF_PW})
    body = p("7.7 复绑（复用账号）", r, expect_code=0)
    if body and body.get("code") == 0 and body["data"] == staff_admin_id:
        row = db_one("SELECT sa.status, u.real_name FROM store_admins sa "
                     "JOIN admin_users u ON u.id = sa.admin_id WHERE sa.admin_id = %s", (staff_admin_id,))
        p("7.8 复绑落库：绑定恢复、原账号保留",
          _FakeResp(), 0) if (row and row[0] == 1 and row[1] == "测试员工甲复绑") else p("7.8 复绑落库", _FakeResp(), -1)
        staff_t = login(PREFIX + "staff1", STAFF_PW)  # 复绑用初始密码登录
        p("7.9 复绑后员工可登录（初始密码）", _FakeResp(), 0)
    else:
        p("7.8/7.9 复绑失败", _FakeResp(), -1)

    # ============ 8. 清理回归 ============
    print("===== 8. 清理与回归 =====")
    cleanup_test_data()
    row = db_one("SELECT COUNT(*) FROM roles WHERE is_preset = 1")
    p("8.1 预设 5 角色回归",
      _FakeResp(), 0) if row and row[0] == 5 else p("8.1 预设角色回归", _FakeResp(), -1)
    row = db_one("SELECT COUNT(*) FROM admin_users WHERE username IN "
                 "('admin','store_admin1','store_admin2','staff1','warehouse1')")
    p("8.2 基线账号回归",
      _FakeResp(), 0) if row and row[0] == 5 else p("8.2 基线账号回归", _FakeResp(), -1)
    row = db_one("SELECT COUNT(*) FROM admin_users WHERE username LIKE 'v11_%%'")
    p("8.3 v11 测试数据已清理",
      _FakeResp(), 0) if row and row[0] == 0 else p("8.3 v11 测试数据清理", _FakeResp(), -1)

    print(f"\n===== 结果: {passed} 通过 / {failed} 失败 =====")
    if failed > 0:
        sys.exit(1)


class _FakeResp:
    """占位响应（用于已由前置条件判定的纯断言）"""

    def json(self):
        return {"code": 0 if True else -1}

    @property
    def status_code(self):
        return 200


if __name__ == "__main__":
    main()
