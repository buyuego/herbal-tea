# -*- coding: utf-8 -*-
"""本地联调数据库查询工具（pymysql）"""
import pymysql

conn = pymysql.connect(
    host="127.0.0.1", port=3306, user="herbal_tea", password="herbal_tea_dev",
    database="herbal_tea", charset="utf8mb4", autocommit=True
)
cur = conn.cursor()

def show(title, sql):
    print(f"\n=== {title} ===")
    cur.execute(sql)
    cols = [d[0] for d in cur.description]
    rows = cur.fetchall()
    print(" | ".join(cols))
    for r in rows:
        print(" | ".join(str(x) for x in r))
    print(f"({len(rows)} rows)")

show("store_products", "SELECT id, store_id, product_id, sku_id, price, status, daily_quota FROM store_products")
show("stores", "SELECT id, store_name, status FROM stores")
show("users", "SELECT id, openid, nickname, status, token_version FROM users")
show("product_skus", "SELECT id, product_id, spec, price, stock FROM product_skus")
show("user_addresses", "SELECT id, user_id, receiver_name, phone, is_default FROM user_addresses")
cur.close()
conn.close()
