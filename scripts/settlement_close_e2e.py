# -*- coding: utf-8 -*-
"""
v23 结算收尾补强联测：自动确认定时任务 + refund_approved 冲正接线

场景：
  A. 订单4(D0) 生成结算单A → auto_confirm_at 置过期 → 定时任务自动确认(20, confirm_status=1)
  B. 订单5(D-1) 生成结算单B → 手动确认 → confirm_status=2（人工确认，修 bug）
  C. 结算单A(20) 中订单4 售后退款(90→申请→审批) → outbox → 冲正：90 + refund_adjust + final 扣减 + type=7 行
  D. 订单6(D-2) 生成结算单C → confirm→review→pay(40) → 订单6 退款审批 → 不动(仍40, 告警转人工)
  E. SQL 插 orderId=999 的 refund_approved 事件 → 订阅者 no-op

口径（订单 total=176, rate=0.05, points_earned=176 门店source1）：
  final = 176 - 8.80(佣金) - 1.76(积分成本) = 165.44
  冲正后 refund_adjust=176.00, final = max(0, 165.44-176) = 0.00
"""
import sys, time, json, datetime
import requests, pymysql

BASE = 'http://localhost:8080'
DB = dict(host='127.0.0.1', port=3306, user='herbal_tea', password='herbal_tea_dev',
          database='herbal_tea', charset='utf8mb4', autocommit=True)

PASS, FAIL = 0, 0


def p(name, ok, detail=''):
    global PASS, FAIL
    tag = 'PASS' if ok else 'FAIL'
    print(f'[{tag}] {name}' + (f' | {detail}' if detail else ''))
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


def settlement_of(order_id):
    rows = q('SELECT s.id, s.settle_no, s.status, s.confirm_status, s.refund_adjust, s.final_amount '
             'FROM settlements s JOIN settlement_items si ON si.settlement_id = s.id '
             'WHERE si.order_id = %s ORDER BY s.id DESC LIMIT 1', (order_id,))
    return rows[0] if rows else None


def wait_for(cond, timeout_s, interval_s=3):
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        if cond():
            return True
        time.sleep(interval_s)
    return cond()


def cleanup():
    """前置清理：恢复订单 4/5/6 基线，删除结算/退款/事件数据（幂等）"""
    exe("DELETE FROM settlement_items")
    exe("DELETE FROM settlements")
    exe("DELETE FROM return_orders")
    exe("DELETE FROM refund_records")
    exe("DELETE FROM event_outbox WHERE event_type = 'refund_approved'")
    exe("UPDATE orders SET status=70, finished_at=NULL, refund_approved_by=NULL, refund_approved_at=NULL "
        "WHERE id IN (4,5,6)")


def main():
    cleanup()
    try:
        run()
    finally:
        cleanup()  # 结尾清理回基线，保证脚本可重复执行


def run():
    today = datetime.date.today()
    d0, d1, d2 = today.isoformat(), (today - datetime.timedelta(days=1)).isoformat(), (today - datetime.timedelta(days=2)).isoformat()

    admin = login('admin', 'Admin@123456')

    # ---------- 造数：订单 4/5/6 置 90 已完结，三个不同周期 ----------
    exe("UPDATE orders SET status=90, finished_at=%s WHERE id=4", (d0,))
    exe("UPDATE orders SET status=90, finished_at=%s WHERE id=5", (d1,))
    exe("UPDATE orders SET status=90, finished_at=%s WHERE id=6", (d2,))
    p('造数：订单 4/5/6 置 90 已完结（三个周期）', True)

    # 生成三张结算单（同店不同周期 → 三张独立单）
    for period, label in [(d0, 'A'), (d1, 'B'), (d2, 'C')]:
        r = api('POST', '/api/settlement/admin/generate', admin, params={'period': period, 'storeId': 1}).json()
        p(f'生成结算单{label}（period={period}）', r['code'] == 0, str(r))

    rows = q('SELECT s.id, s.period, s.status, s.confirm_status, s.final_amount FROM settlements s ORDER BY s.id')
    p('三张结算单待确认(10)', len(rows) == 3 and all(r[2] == 10 for r in rows), str(rows))
    sa = settlement_of(4)
    sb = settlement_of(5)
    sc = settlement_of(6)
    p('A 金额口径 final=165.44', float(sa[5]) == 165.44, str(sa))

    # ---------- 场景 A：自动确认定时任务 ----------
    exe('UPDATE settlements SET auto_confirm_at = NOW() - INTERVAL 1 MINUTE WHERE id = %s', (sa[0],))
    p('A：auto_confirm_at 置过期', True)
    ok = wait_for(lambda: q('SELECT status FROM settlements WHERE id=%s', (sa[0],))[0][0] == 20, 90)
    row = q('SELECT status, confirm_status, confirmed_at FROM settlements WHERE id=%s', (sa[0],))[0]
    p('A：定时任务自动确认 10→20', ok and row[0] == 20, str(row))
    p('A：confirm_status=1（自动确认）', row[1] == 1, str(row))
    p('A：confirmed_at 已回填', row[2] is not None, str(row))

    # ---------- 场景 B：手动确认 confirm_status=2 ----------
    r = api('POST', f'/api/settlement/admin/{sb[0]}/confirm', admin).json()
    row = q('SELECT status, confirm_status, confirmed_at FROM settlements WHERE id=%s', (sb[0],))[0]
    p('B：手动确认 10→20', r['code'] == 0 and row[0] == 20, f'{r} {row}')
    p('B：confirm_status=2（人工确认，bug 修复）', row[1] == 2, str(row))

    # ---------- 场景 C：结算单A(20) 中订单4 售后退款 → 冲正 ----------
    final_before = float(sa[5])
    r = api('POST', '/api/refund/admin/apply', admin, json={'orderId': 4, 'reason': 'v23 冲正联测'}).json()
    p('C：订单4(90 已完结) 售后退款申请（契约补齐）', r['code'] == 0, str(r))
    refund_id = r.get('data')
    r = api('POST', f'/api/refund/admin/{refund_id}/approve', admin).json()
    p('C：退款审批通过（发布 refund_approved 事件）', r['code'] == 0, str(r))

    ok = wait_for(lambda: settlement_of(4) and settlement_of(4)[2] == 90, 30)
    sa2 = settlement_of(4)
    p('C：订阅者冲正 → 90 已冲正', ok and sa2[2] == 90, str(sa2))
    p('C：refund_adjust=176.00', float(sa2[4]) == 176.00, str(sa2))
    p('C：final 扣减且钳零（165.44-176→0.00）', float(sa2[5]) == 0.00, str(sa2))
    rows = q('SELECT item_type, direction, amount, remark FROM settlement_items '
             'WHERE settlement_id=%s AND item_type=7', (sa[0],))
    p('C：type=7 冲正明细行（direction=2 减项）',
      len(rows) == 1 and rows[0][1] == 2 and float(rows[0][2]) == 176.00, str(rows))

    # ---------- 场景 D：已打款(40) 退款不自动冲正 ----------
    for act in ('confirm', 'review', 'pay'):
        r = api('POST', f'/api/settlement/admin/{sc[0]}/{act}', admin).json()
        p(f'D：结算单C {act}', r['code'] == 0, str(r))
    row = q('SELECT status, payout_no FROM settlements WHERE id=%s', (sc[0],))[0]
    p('D：结算单C 已打款(40)', row[0] == 40 and row[1], str(row))

    r = api('POST', '/api/refund/admin/apply', admin, json={'orderId': 6, 'reason': 'v23 已打款场景'}).json()
    refund_id2 = r.get('data')
    r = api('POST', f'/api/refund/admin/{refund_id2}/approve', admin).json()
    p('D：订单6 退款审批通过', r['code'] == 0, str(r))
    time.sleep(8)  # 等 outbox worker 扫描
    sc2 = settlement_of(6)
    p('D：已打款结算单不自动冲正（仍 40，转人工）', sc2[2] == 40, str(sc2))
    p('D：refund_adjust 未变（0.00）', float(sc2[4]) == 0.00, str(sc2))

    # ---------- 场景 E：未参与结算的订单 → no-op ----------
    rows_before = q('SELECT COUNT(*) FROM settlements')[0][0]
    payload = json.dumps({'refundId': 999, 'refundNo': 'RFTEST999', 'orderId': 999, 'amount': 1.00})
    exe("INSERT INTO event_outbox (event_id, event_type, biz_key, payload, status, retry_count, next_retry_at) "
        "VALUES (%s, 'refund_approved', 'refund_approved:RFTEST999', %s, 0, 0, NOW())",
        (str(__import__('uuid').uuid4()), payload))
    time.sleep(8)
    rows_after = q('SELECT COUNT(*) FROM settlements')[0][0]
    ev = q("SELECT status FROM event_outbox WHERE biz_key='refund_approved:RFTEST999'")[0][0]
    p('E：无归属结算单的事件 no-op（结算单数不变，事件已消费）',
      rows_before == rows_after and ev == 1, f'count {rows_before}->{rows_after}, ev_status={ev}')

    print(f'\n===== 结果: {PASS} 通过 / {FAIL} 失败 =====')
    sys.exit(1 if FAIL else 0)


if __name__ == '__main__':
    main()
