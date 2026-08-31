package com.herbaltea.module.product;

import com.herbaltea.infrastructure.outbox.OutboxEventType;
import com.herbaltea.infrastructure.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 商品模块骨架实现
 *
 * <p>待实现：
 * <ol>
 *   <li>deductStock：原子 SQL（16.4 ③）——无 Redis 锁，单库原子更新足够</li>
 *   <li>updateCatalog：D14 发布 product_catalog_changed，标记本店目录已更新（不覆盖本店定价）</li>
 *   <li>商品搜索：MySQL LIKE/FULLTEXT（ADR-A7，SKU > 5000 才引入 ES）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final OutboxPublisher outboxPublisher;

    @Override
    public Long createProduct(Long adminId, String name, String categoryCode, String specs) {
        // TODO: 写 products + product_skus（初始 SKU）
        return null;
    }

    @Override
    public boolean deductStock(Long skuId, Integer qty) {
        // TODO: UPDATE product_skus SET stock = stock - #{qty}, version = version + 1
        //       WHERE sku_id = #{skuId} AND stock >= #{qty}
        //       影响行数 > 0 返回 true（零超卖，无锁）
        return false;
    }

    @Override
    public void restoreStock(Long skuId, Integer qty) {
        // TODO: UPDATE product_skus SET stock = stock + #{qty} WHERE sku_id = #{skuId}
        //       （关单/退款回滚，与关单同库本地事务）
    }

    @Override
    public void updateCatalog(Long productId, String name, Long operatorAdminId) {
        // TODO: 事务内更新 products + 发布事件（D14）
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        payload.put("name", name);
        outboxPublisher.publish(OutboxEventType.product_catalog_changed,
                "catalog:" + productId, payload);
    }
}
