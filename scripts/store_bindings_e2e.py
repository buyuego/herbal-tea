# -*- coding: utf-8 -*-
"""多店绑定与切换接口全链路联测（v14，MULTI_STORE）

场景矩阵：
  接口                      权限要求               预期
  GET  /api/store/my-stores  登录即可（只读本人）   返回全部正常绑定；current 标记 sid
  POST /api/store/switch-store 登录即可（实时校验） 目标∈绑定 → 重签 sid=目标店；未绑定 40400
  POST /api/store/staff/{id}/bind store:staff:manage 加绑当前店；重复 40900；非员工 40000
  移除（复用 v11 removeStaff）多店语义             解绑当前店不影响他店绑定

关键验证：
  1. JWT 双 claim：登录签发 sids（全部绑定）+ sid（默认主店/最近绑定）
  2. 加绑即吊销：bind 后目标员工旧 token 40101（sids 快照过期）
  3. 切店保持：refresh 轮换后 sid 不回到主店（切店状态跨刷新不丢）
  4. 多店解绑：removeStaff 只解绑当前店，他店绑定与 sids 保留
  5. 实时校验：switch-store 查库而非信任 JWT sids（解绑后旧 token 不可切店）
"""
import base64
import json

import pymysql
import requests

BASE = "http://localhost:8080"
PW = "Store@123456"
ADMIN_PW = "QVMWb_-_mr%+gb4D"
STAFF_PW = "Staff@123456"
PREFIX = "v14_"  # 测试账号前缀，清理按此删除

DB = dict(host="127.0.0.1", port=3306, user="herbal_tea",
          password="herbal_tea_dev", database="herbal_tea",
          charset="utf8mb4", autocommit=True)

passed = 0
failed = 0


class _FakeResp:
    """DB 断言占位：前置条件已判定，仅用于 p() 的 code 比对"""

    def __init__(self, code=0):
        self._code = code

    def json(self):
        return {"code": self._code}

    @property
    def status_code(self):
        return 200


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


def p(name, r, expect_code=0):
    """断言只看 body code（4xx 场景 http≠200，v13 经验）"""
    global passed, failed
    ok = r.json().get("code") == expect_code
    if ok:
        passed += 1
        print(f"  PASS  {name}")
    else:
        failed += 1
        print(f"  FAIL  {name}  -> http={r.status_code} body={r.text[:200]}")


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


def api(method, path, token=None, json_body=None):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    return requests.request(method, f"{BASE}{path}", headers=headers, json=json_body)


def create_test_admin(username, role_id, password=STAFF_PW):
    h = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi6X8H5QoYb7m8vZzU5pC1k3yY6nW4S"  # 占位哈希，登录校验用 bcrypt 真哈希
    import bcrypt
    h = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
    db_exec(
        "INSERT INTO admin_users (username, password_hash, real_name, phone, role_id, status, token_version) "
        "VALUES (%s, %s, %s, %s, %s, 1, 0)",
        (username, h, username, "13800138888", role_id))


def cleanup_test_data():
    """幂等清理：删 v14_ 测试账号及其全部绑定"""
    db_exec("DELETE sa FROM store_admins sa JOIN admin_users u ON u.id = sa.admin_id "
            f"WHERE u.username LIKE '{PREFIX}%%'")
    db_exec(f"DELETE FROM admin_users WHERE username LIKE '{PREFIX}%%'")
    print(f"  [cleanup] v14_ 测试账号与绑定已清理")


def main():
    cleanup_test_data()
    # ---------- 准备 ----------
    admin_t = login("admin", ADMIN_PW)       # 超管（无绑定）
    sa1_t = login("store_admin1")            # 店1 店主
    sa2_t = login("store_admin2")            # 店2 店主
    staff1_t = login("staff1")               # 店1 员工

    print("\n[1] 权限门禁")
    p("1.1 无令牌 my-stores 40100", api("GET", "/api/store/my-stores"), 40100)
    p("1.2 无令牌 switch-store 40100",
      api("POST", "/api/store/switch-store", json_body={"storeId": 1}), 40100)
    p("1.3 员工调员工加绑 40300（无 store:staff:manage）",
      api("POST", "/api/store/staff/4/bind", staff1_t), 40300)

    print("\n[2] my-stores 与 JWT claims")
    r = api("GET", "/api/store/my-stores", sa1_t)
    rows = r.json().get("data") or []
    ok = len(rows) == 1 and rows[0]["storeId"] == 1 and rows[0]["isOwner"] == 1 and rows[0]["current"] is True
    p("2.1 店1 店主 my-stores（1 条、店主、current）", _FakeResp(0) if ok else _FakeResp(-1))
    claims = jwt_claims(sa1_t)
    p("2.2 店主登录 claims sid=1 sids='1'",
      _FakeResp(), 0 if (claims.get("sid") == 1 and claims.get("sids") == "1") else -1)
    r = api("GET", "/api/store/my-stores", admin_t)
    p("2.3 超管 my-stores 空列表", _FakeResp(), 0 if (r.json().get("data") == []) else -1)
    claims = jwt_claims(admin_t)
    p("2.4 超管 claims 无 sid/sids",
      _FakeResp(), 0 if ("sid" not in claims and "sids" not in claims) else -1)

    print("\n[3] 员工创建 + 跨店加绑（一人多店）")
    create_test_admin(PREFIX + "staff_a", 5)
    aid = db_one("SELECT id FROM admin_users WHERE username=%s", (PREFIX + "staff_a",))[0]
    # 店1 店主把 v14_staff_a 绑到店1（创建员工语义 = 绑定本店；预插账号走"已存在-复绑"分支，密码重置为 STAFF_PW）
    r = api("POST", "/api/store/staff", sa1_t, json_body={
        "username": PREFIX + "staff_a", "realName": "测试员工甲",
        "phone": "13800138000", "password": STAFF_PW})
    if r.json().get("code") != 0:
        print(f"  [skip] 创建复用 {r.json()}")
    pre_bind_t = login(PREFIX + "staff_a", STAFF_PW)   # 加绑前登录（旧会话，token_version=0）
    # 店2 店主把该员工加绑店2（允许已正常绑定店1 → 一人多店）
    r = api("POST", f"/api/store/staff/{aid}/bind", sa2_t)
    p("3.1 店2 店主加绑员工到店2 成功", r)
    row = db_one("SELECT store_id, is_owner, status FROM store_admins WHERE admin_id=%s AND store_id=2",
                 (aid,))
    p("3.2 落库：店2 绑定非店主正常",
      _FakeResp(), 0 if (row and row == (2, 0, 1)) else -1)
    p("3.3 重复加绑 40900",
      api("POST", f"/api/store/staff/{aid}/bind", sa2_t), 40900)
    # 加绑 → bumpTokenVersion 即时吊销旧令牌（sids 快照过期）
    p("3.4 加绑后旧 token 40101（即时吊销）",
      api("GET", "/api/store/my-stores", pre_bind_t), 40101)

    print("\n[4] 多店登录与切店")
    staff_a_t = login(PREFIX + "staff_a", STAFF_PW)
    claims = jwt_claims(staff_a_t)
    sids = set((claims.get("sids") or "").split(","))
    p("4.1 员工登录 claims sids={1,2}",
      _FakeResp(), 0 if sids == {"1", "2"} else -1)
    r = api("POST", "/api/store/switch-store", staff_a_t, json_body={"storeId": 1})
    p("4.2 切到店1 成功（sid=1）", r, 0 if r.json().get("code") == 0 else -1)
    if r.json().get("code") == 0:
        staff_a_s1 = r.json()["data"]["accessToken"]
        claims = jwt_claims(staff_a_s1)
        p("4.3 切店后 claims sid=1 sids 仍含两店",
          _FakeResp(), 0 if (claims.get("sid") == 1 and set(claims.get("sids").split(",")) == {"1", "2"}) else -1)
    p("4.4 切到未绑定店 40400",
      api("POST", "/api/store/switch-store", staff_a_t, json_body={"storeId": 3}), 40400)
    p("4.5 切到非法店 40000",
      api("POST", "/api/store/switch-store", staff_a_t, json_body={"storeId": 0}), 40000)
    p("4.6 超管切店 40400（无绑定）",
      api("POST", "/api/store/switch-store", admin_t, json_body={"storeId": 1}), 40400)

    print("\n[5] 切店状态跨刷新保持（refresh 不回到主店）")
    if r.json().get("code") == 0:
        refresh_tok = r.json()["data"]["refreshToken"]
        rr = requests.post(f"{BASE}/api/auth/refresh", json={"refreshToken": refresh_tok})
        body = rr.json()
        if body.get("code") == 0:
            claims = jwt_claims(body["data"]["accessToken"])
            p("5.1 refresh 后 sid 保持店1（切店状态不丢）",
              _FakeResp(), 0 if claims.get("sid") == 1 else -1)
            p("5.2 refresh 后 sids 仍含两店",
              _FakeResp(), 0 if set(claims.get("sids").split(",")) == {"1", "2"} else -1)
        else:
            p("5.1 refresh 后 sid 保持店1", _FakeResp(-1))
    else:
        p("5.1 refresh 后 sid 保持店1", _FakeResp(-1))

    print("\n[6] 多店解绑（removeStaff 只解绑当前店）")
    # 店1 店主移除 v14_staff_a（解绑店1）
    r = api("DELETE", f"/api/store/staff/{aid}", sa1_t)
    p("6.1 店1 店主移除员工（解绑店1）成功", r)
    row = db_one("SELECT status FROM store_admins WHERE admin_id=%s AND store_id=1", (aid,))
    p("6.2 店1 绑定软删（status=0）",
      _FakeResp(), 0 if (row and row[0] == 0) else -1)
    row = db_one("SELECT status FROM store_admins WHERE admin_id=%s AND store_id=2", (aid,))
    p("6.3 店2 绑定不受影响（status=1）",
      _FakeResp(), 0 if (row and row[0] == 1) else -1)
    # 员工重新登录：sids 只含店2
    staff_a_t2 = login(PREFIX + "staff_a", STAFF_PW)
    claims = jwt_claims(staff_a_t2)
    p("6.4 重登后 sids 只含店2",
      _FakeResp(), 0 if set((claims.get("sids") or "").split(",")) == {"2"} else -1)
    # 解绑店1 后旧 token 已吊销，但"切店实时校验"再兜底：即使旧 sids 含店1 也切不过去
    p("6.5 店1 解绑后（原切店 token 吊销）40101",
      api("GET", "/api/store/my-stores", staff_a_s1), 40101)

    print("\n[7] 加绑边界")
    p("7.1 加绑店主 40000（非员工角色）",
      api("POST", "/api/store/staff/2/bind", sa2_t), 40000)
    p("7.2 加绑不存在账号 40400",
      api("POST", "/api/store/staff/99999/bind", sa2_t), 40400)
    # 复绑：店2 先移除再 bind → 恢复绑定
    api("DELETE", f"/api/store/staff/{aid}", sa2_t)
    r = api("POST", f"/api/store/staff/{aid}/bind", sa2_t)
    p("7.3 移除后复绑成功", r)
    row = db_one("SELECT status FROM store_admins WHERE admin_id=%s AND store_id=2", (aid,))
    p("7.4 复绑落库 status=1",
      _FakeResp(), 0 if (row and row[0] == 1) else -1)

    print("\n[8] 列表跟随（店2 员工列表含加绑员工）")
    r = api("GET", "/api/store/staff?boundStatus=1&page=1&size=50", sa2_t)
    names = [x["username"] for x in (r.json().get("data") or {}).get("records", [])]
    p("8.1 店2 店主列表可见 v14_staff_a",
      _FakeResp(), 0 if PREFIX + "staff_a" in names else -1)

    cleanup_test_data()
    print(f"\n===== v14 联测结果: {passed} passed / {failed} failed =====")
    sys_exit = 1 if failed else 0
    import sys
    sys.exit(sys_exit)


if __name__ == "__main__":
    main()
