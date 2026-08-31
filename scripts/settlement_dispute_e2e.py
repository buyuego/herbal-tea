# -*- coding: utf-8 -*-
"""
v24 结算异议申诉 + 复核调整单联测

场景：
  1. 造数生成结算单（订单4 → 90 → store1）
  2. 店长（V10 补 107）可见本店结算单；店2 隔离；店员无 107 → 40300
  3. 店长申诉 → confirm_status=3 + dispute_note；重复申诉拒绝
  4. auto_confirm_at 置过期 → 定时任务不吞有异议单（仍 10）
  5. 越权：店长 reconcile → 40300；临时财务（215）reconcile OK
  6. 超管 reconcile +50：原单 adjust=50、final=215.44、confirm_status=2、type=8 行；
     调整单生成（type=3、parent、final=50、status=10）
  7. 调整单复用状态机 confirm→review→pay → 40
  8. 清理回基线
"""
import sys, time, datetime
import requests, pymysql

BASE = 'http://localhost:8080'
DB = dict(host='127.0.0.1', port=3306, user='herbal_tea', password='herbal_tea_dev',
          database='herbal_tea', charset='utf8mb4', autocommit=True)

PASS, FAIL = 0, 0


def p(name, ok, detail=''):
    global PASS, FAIL
    print(f'[{"PASS" if ok else "FAIL"}] {name}' + (f' | {detail}' if detail else ''))
    if ok:
        PASS += 1
    else:
        FAIL += 1


def login(u, pw):
    r = requests.post(f'{BASE}/api/auth/admin/login', json={'username': u, 'password': pw}, timeout=10)
    b = r.json()
    assert b['code'] == 0, b
    return b['data']['accessToken']


def api(method, path, token, **kw):
    return requests.request(method, f'{BASE}{path}', headers={'Authorization': f'Bearer {token}'}, timeout=15, **kw)


def q(sql, args=None):
    conn = pymysql.connect(**DB)
    try:
        cur = conn.cursor()
        cur.execute(sql, args or ())
        return cur.fetchall()
    finally:
        conn.close()


def exe(sql, args=None):
    conn = pymysql.connect(**DB)
    try:
        cur = conn.cursor()
        cur.execute(sql, args or ())
        conn.commit()
        return cur.rowcount
    finally:
        conn.close()


def wait_for(cond, timeout_s, interval_s=3):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        if cond():
            return True
        time.sleep(interval_s)
    return cond()


def cleanup():
    exe("DELETE FROM settlement_items")
    exe("DELETE FROM settlements")
    exe("DELETE FROM admin_users WHERE username='fin_tmp2'")
    exe("UPDATE orders SET status=70, finished_at=NULL, refund_approved_by=NULL, refund_approved_at=NULL "
        "WHERE id IN (4,5,6)")


def create_finance():
    """临时财务号 role=2（107+213+215 无 214）"""
    import bcrypt
    row = q("SELECT id FROM admin_users WHERE username='fin_tmp2'")
    if row:
        return
    hashed = bcrypt.hashpw(b'Fin@123456', bcrypt.gensalt()).decode()
    exe("INSERT INTO admin_users (username, password_hash, real_name, role_id, status, created_at, updated_at) "
        "VALUES ('fin_tmp2', %s, '临时财务', 2, 1, NOW(), NOW())", (hashed,))


def main():
    cleanup()
    try:
        run()
    finally:
        cleanup()


def run():
    today = datetime.date.today().isoformat()
    admin = login('admin', 'Admin@123456')
    sa1 = login('store_admin1', 'Store@123456')
    sa2 = login('store_admin2', 'Store@123456')
    staff1 = login('staff1', 'Store@123456')

    # ---------- 1. 造数生成 ----------
    exe("UPDATE orders SET status=90, finished_at=%s WHERE id=4", (today,))
    r = api('POST', '/api/settlement/admin/generate', admin, params={'period': today, 'storeId': 1}).json()
    p('生成结算单', r['code'] == 0, str(r))
    row = q("SELECT id, status, confirm_status, final_amount FROM settlements WHERE store_id=1")[0]
    sid, final0 = row[0], float(row[3])
    p('final=165.44', final0 == 165.44, str(row))

    # ---------- 2. 店长可见（V10 补 107）+ 隔离 + 店员越权 ----------
    r = api('GET', '/api/settlement/admin/page', sa1, params={'page': 1, 'size': 10}).json()
    p('店长可见本店结算单（V10 补 107）', r['code'] == 0 and r['data']['total'] >= 1, str(r.get('data', {}).get('total')))
    store_ids = {rec['storeId'] for rec in r['data']['records']}
    p('店长仅见本店（store 1）', store_ids == {1}, str(store_ids))
    r = api('GET', '/api/settlement/admin/page', sa2, params={'page': 1, 'size': 10}).json()
    p('店2 不可见店1 结算单（隔离）', r['code'] == 0 and r['data']['total'] == 0, str(r.get('data', {}).get('total')))
    r = api('GET', '/api/settlement/admin/page', staff1, params={'page': 1, 'size': 10}).json()
    p('店员无 107 → 40300', r['code'] == 40300, str(r.get('code')))
    r = api('GET', f'/api/settlement/admin/{sid}', sa2).json()
    p('店2 看店1 详情 → 40300', r['code'] == 40300, str(r.get('code')))

    # ---------- 3. 店长申诉 ----------
    r = api('POST', f'/api/settlement/admin/{sid}/dispute', sa1, json={'note': '佣金计算有误，少计一笔订单'}).json()
    row = q('SELECT confirm_status, dispute_note FROM settlements WHERE id=%s', (sid,))[0]
    p('申诉成功 confirm_status=3', r['code'] == 0 and row[0] == 3, f'{r} {row}')
    p('dispute_note 落库', '佣金计算有误' in (row[1] or ''), str(row))
    r = api('POST', f'/api/settlement/admin/{sid}/dispute', sa1, json={'note': '再次申诉'}).json()
    p('重复申诉拒绝', r['code'] != 0, str(r))
    r = api('POST', f'/api/settlement/admin/{sid}/dispute', sa2, json={'note': '越店申诉'}).json()
    p('店2 越店申诉 → 40300', r['code'] == 40300, str(r.get('code')))

    # ---------- 4. 定时任务不吞异议单 ----------
    exe('UPDATE settlements SET auto_confirm_at = NOW() - INTERVAL 1 MINUTE WHERE id=%s', (sid,))
    time.sleep(70)  # 等 cron 至少一轮
    row = q('SELECT status, confirm_status FROM settlements WHERE id=%s', (sid,))[0]
    p('有异议单不被自动确认（仍 10）', row[0] == 10 and row[1] == 3, str(row))

    # ---------- 5. 越权 + 复核 ----------
    r = api('POST', f'/api/settlement/admin/{sid}/reconcile', sa1, json={'adjustAmount': 50, 'remark': '越权'}).json()
    p('店长 reconcile → 40300（无 215）', r['code'] == 40300, str(r.get('code')))
    create_finance()
    fin = login('fin_tmp2', 'Fin@123456')
    r = api('POST', f'/api/settlement/admin/{sid}/reconcile', fin, json={'adjustAmount': 50, 'remark': '核实漏计订单，补偿差价'}).json()
    p('财务 reconcile 成功（215）', r['code'] == 0, str(r))
    adj_id = r.get('data')

    row = q('SELECT adjust_amount, final_amount, confirm_status FROM settlements WHERE id=%s', (sid,))[0]
    p('原单 adjust=50 / final=215.44 / confirm_status 复位 2',
      float(row[0]) == 50.0 and float(row[1]) == 215.44 and row[2] == 2, str(row))
    rows = q('SELECT item_type, direction, amount FROM settlement_items WHERE settlement_id=%s AND item_type=8', (sid,))
    p('原单 type=8 调整行（direction=1 加项）', len(rows) == 1 and rows[0][1] == 1 and float(rows[0][2]) == 50.0, str(rows))
    adj = q('SELECT type, parent_settlement_id, adjust_amount, final_amount, status, store_id FROM settlements WHERE id=%s', (adj_id,))[0]
    p('调整单生成（type=3 / parent 关联 / final=50 / status=10）',
      adj[0] == 3 and adj[1] == sid and float(adj[3]) == 50.0 and adj[4] == 10 and adj[5] == 1, str(adj))
    r = api('POST', f'/api/settlement/admin/{sid}/reconcile', fin, json={'adjustAmount': 10, 'remark': '重复'}).json()
    p('非异议单重复 reconcile 拒绝', r['code'] != 0, str(r))

    # ---------- 6. 调整单复用状态机 ----------
    r = api('POST', f'/api/settlement/admin/{adj_id}/confirm', admin).json()
    r2 = api('POST', f'/api/settlement/admin/{adj_id}/review', fin).json()
    r3 = api('POST', f'/api/settlement/admin/{adj_id}/pay', admin).json()
    row = q('SELECT status, payout_no FROM settlements WHERE id=%s', (adj_id,))[0]
    p('调整单 confirm→review→pay → 40', r['code'] == 0 and r2['code'] == 0 and r3['code'] == 0 and row[0] == 40 and row[1],
      f'{r} {r2} {r3} {row}')
    r = api('POST', f'/api/settlement/admin/{adj_id}/pay', fin).json()
    p('财务 pay → 40300（无 214 敏感）', r['code'] == 40300, str(r.get('code')))

    print(f'\n===== 结果: {PASS} 通过 / {FAIL} 失败 =====')
    sys.exit(1 if FAIL else 0)


if __name__ == '__main__':
    main()
