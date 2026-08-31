# -*- coding: utf-8 -*-
"""门店上架联测数据准备：
1. 建门店 2（南山加盟店）+ 门店管理员1/2（STORE_ADMIN 角色）
2. store_admins 绑定：admin -> store1（店主）、admin2 -> store2（店主）
3. 密码统一 Store@123456（BCrypt rounds=10，与 Spring BCryptPasswordEncoder 同强度）
幂等：已存在则跳过
"""
import pymysql
import bcrypt

conn = pymysql.connect(host="127.0.0.1", port=3306, user="herbal_tea",
                       password="herbal_tea_dev", database="herbal_tea",
                       charset="utf8mb4", autocommit=True)
cur = conn.cursor()

PW = "Store@123456"
HASH = bcrypt.hashpw(PW.encode(), bcrypt.gensalt(rounds=10)).decode()
print("BCrypt hash:", HASH)

# 1. 门店 2（南山加盟店）
cur.execute("SELECT id FROM stores WHERE store_no='ST002'")
if not cur.fetchone():
    cur.execute("""INSERT INTO stores (store_no, store_name, store_type, status, contact_name, contact_phone,
                  province, city, district, address, created_at, updated_at)
                  VALUES ('ST002', '南山加盟店', 2, 1, '王店长', '13800138002',
                  '广东省', '深圳市', '南山区', '科技园路1号', NOW(), NOW())""")
    print("门店2 创建 OK, id =", cur.lastrowid)
else:
    print("门店2 已存在")

# 2. 门店管理员（admin_users，role_id=4 STORE_ADMIN）
def ensure_admin(username, real_name, phone):
    cur.execute("SELECT id FROM admin_users WHERE username=%s", (username,))
    row = cur.fetchone()
    if row:
        return row[0]
    cur.execute("""INSERT INTO admin_users (username, password_hash, real_name, phone, role_id, status,
                  token_version, created_at, updated_at)
                  VALUES (%s, %s, %s, %s, 4, 1, 0, NOW(), NOW())""",
                (username, HASH, real_name, phone))
    print(f"管理员 {username} 创建 OK, id =", cur.lastrowid)
    return cur.lastrowid

a1 = ensure_admin("store_admin1", "张店主", "13800138001")
a2 = ensure_admin("store_admin2", "王店长", "13800138002")

# 3. store_admins 绑定（is_owner=1）
def bind(admin_id, store_id, owner=1):
    cur.execute("SELECT id FROM store_admins WHERE admin_id=%s AND store_id=%s", (admin_id, store_id))
    if cur.fetchone():
        print(f"绑定已存在 admin={admin_id} store={store_id}")
        return
    cur.execute("INSERT INTO store_admins (admin_id, store_id, is_owner, status, created_at) VALUES (%s,%s,%s,1,NOW())",
                (admin_id, store_id, owner))
    print(f"绑定 OK admin={admin_id} -> store={store_id}")

bind(a1, 1)
bind(a2, 2)

# 汇总
cur.execute("""SELECT u.username, u.id, u.real_name, sa.store_id, sa.is_owner
               FROM admin_users u LEFT JOIN store_admins sa ON u.id=sa.admin_id
               WHERE u.username LIKE 'store_admin%' ORDER BY u.id""")
print("\n=== 账号汇总 ===")
for r in cur.fetchall():
    print(f"  {r[0]} (id={r[1]} {r[2]}) -> store_id={r[3]} owner={r[4]}  密码: {PW}")

cur.execute("SELECT id, store_no, store_name, status FROM stores ORDER BY id")
print("=== 门店 ===")
for r in cur.fetchall():
    print(" ", r)

conn.close()
