package com.herbaltea.module.product;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.herbaltea.common.result.Result;
import com.herbaltea.infrastructure.audit.AuditLog;
import com.herbaltea.infrastructure.web.RequirePermission;
import com.herbaltea.infrastructure.web.UserContext;
import com.herbaltea.module.product.dto.CategoryCreateRequest;
import com.herbaltea.module.product.dto.InventoryQuery;
import com.herbaltea.module.product.dto.InventoryRecordVO;
import com.herbaltea.module.product.dto.InventoryVO;
import com.herbaltea.module.product.dto.ProductCreateRequest;
import com.herbaltea.module.product.dto.ProductDetailVO;
import com.herbaltea.module.product.dto.ProductPageQuery;
import com.herbaltea.module.product.dto.ProductUpdateRequest;
import com.herbaltea.module.product.dto.SkuAddRequest;
import com.herbaltea.module.product.dto.StockAdjustRequest;
import com.herbaltea.module.product.dto.StoreListingRequest;
import com.herbaltea.module.product.dto.StorePriceRequest;
import com.herbaltea.module.product.dto.StoreProductVO;
import com.herbaltea.module.product.entity.Product;
import com.herbaltea.module.product.entity.ProductCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品接口（B 端后台）
 *
 * <p>路径分组：
 * <ul>
 *   <li>{@code /api/product/categories}：分类（总部）</li>
 *   <li>{@code /api/product/admin/**}：平台商品目录（总部）</li>
 *   <li>{@code /api/product/inventory/**}：库存调整与流水（总部仓管）</li>
 *   <li>{@code /api/product/store/**}：本店上架（门店，storeId 取自登录上下文）</li>
 * </ul>
 */
@Tag(name = "商品", description = "分类 / 平台商品目录（总部）/ 库存（仓管）/ 本店上架（门店）")
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ==================== 分类 ====================

    @Operation(summary = "分类列表", description = "启用优先，按 sort 升序")
    @GetMapping("/categories")
    public Result<List<ProductCategory>> listCategories() {
        return Result.ok(productService.listCategories());
    }

    @Operation(summary = "创建分类", description = "总部维护平台分类")
    @PostMapping("/categories")
    @RequirePermission("product:edit")
    @AuditLog(action = "创建商品分类")
    public Result<Long> createCategory(@Valid @RequestBody CategoryCreateRequest req) {
        return Result.ok(productService.createCategory(req));
    }

    @Operation(summary = "分类启停用", description = "0停用 / 1启用")
    @PutMapping("/categories/{id}/status")
    @RequirePermission("product:edit")
    @AuditLog(action = "分类启停用")
    public Result<Void> updateCategoryStatus(@PathVariable Long id,
                                             @RequestParam @NotNull Integer status) {
        productService.updateCategoryStatus(id, status);
        return Result.ok();
    }

    // ==================== 平台商品目录（总部） ====================

    @Operation(summary = "创建平台商品", description = "商品头 + 初始 SKU 列表（事务）；SKU 编码全局唯一")
    @PostMapping("/admin/products")
    @RequirePermission("product:edit")
    @AuditLog(action = "创建平台商品")
    public Result<Long> createProduct(@Valid @RequestBody ProductCreateRequest req) {
        return Result.ok(productService.createProduct(req));
    }

    @Operation(summary = "商品目录分页", description = "keyword 模糊（名称/副标题）/ 分类 / 状态过滤")
    @GetMapping("/admin/products")
    @RequirePermission("menu:product")
    public Result<IPage<Product>> pageProducts(@ModelAttribute ProductPageQuery query) {
        return Result.ok(productService.pageProducts(query));
    }

    @Operation(summary = "商品详情", description = "含 SKU 列表；cost_price 为敏感字段（product:cost:view 权限）")
    @GetMapping("/admin/products/{id}")
    @RequirePermission("menu:product")
    public Result<ProductDetailVO> getProductDetail(@PathVariable Long id) {
        return Result.ok(productService.getProductDetail(id));
    }

    @Operation(summary = "更新商品目录", description = "D14：更新目录 + 标记本店 catalog_dirty=1 + 发布 catalog_changed 事件（不覆盖本店定价）")
    @PutMapping("/admin/products/{id}")
    @RequirePermission("product:edit")
    @AuditLog(action = "更新平台商品目录")
    public Result<Void> updateCatalog(@PathVariable Long id,
                                      @Valid @RequestBody ProductUpdateRequest req) {
        productService.updateCatalog(id, req, UserContext.get().getAdminId());
        return Result.ok();
    }

    @Operation(summary = "商品上下架（目录层）", description = "0下架 / 1在售")
    @PutMapping("/admin/products/{id}/status")
    @RequirePermission("product:edit")
    @AuditLog(action = "商品上下架")
    public Result<Void> updateProductStatus(@PathVariable Long id,
                                            @RequestParam @NotNull Integer status) {
        productService.updateProductStatus(id, status, UserContext.get().getAdminId());
        return Result.ok();
    }

    @Operation(summary = "追加 SKU", description = "sku_code 全局唯一，冲突返回 40900")
    @PostMapping("/admin/products/{id}/skus")
    @RequirePermission("product:edit")
    @AuditLog(action = "追加 SKU")
    public Result<Long> addSku(@PathVariable Long id, @Valid @RequestBody SkuAddRequest req) {
        return Result.ok(productService.addSku(id, req));
    }

    @Operation(summary = "SKU 启停用", description = "0停用 / 1启用")
    @PutMapping("/admin/skus/{id}/status")
    @RequirePermission("product:edit")
    @AuditLog(action = "SKU 启停用")
    public Result<Void> updateSkuStatus(@PathVariable Long id,
                                        @RequestParam @NotNull Integer status) {
        productService.updateSkuStatus(id, status);
        return Result.ok();
    }

    // ==================== 库存（总部仓管） ====================

    @Operation(summary = "库存调整", description = "入库(changeType=1，qty 为正) / 盘点(changeType=3，qty 为实际差值)；行锁 + 乐观锁 + 落流水")
    @PostMapping("/inventory/adjust")
    @RequirePermission("inventory:manage")
    @AuditLog(action = "库存调整")
    public Result<Void> adjustStock(@Valid @RequestBody StockAdjustRequest req) {
        productService.adjustStock(req, UserContext.get().getAdminId());
        return Result.ok();
    }

    @Operation(summary = "库存流水分页", description = "按 SKU / 关联单号 / 变动类型过滤（出库流水由订单扣减写入）")
    @GetMapping("/inventory/records")
    @RequirePermission("inventory:manage")
    public Result<IPage<InventoryRecordVO>> pageInventoryRecords(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) Integer changeType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(productService.pageInventoryRecords(skuId, bizNo, changeType, page, size));
    }

    @Operation(summary = "库存总览分页", description = "SKU × 商品 × 分类 联表；预警行（stock<=alert_stock）优先；支持关键词/分类/状态/仅看预警")
    @GetMapping("/inventory/skus")
    @RequirePermission("menu:inventory")
    public Result<IPage<InventoryVO>> pageInventory(@ModelAttribute InventoryQuery query) {
        return Result.ok(productService.pageInventory(query));
    }

    @Operation(summary = "设置低库存预警阈值", description = "stock <= alert_stock 时在总览标记预警（仓管维护）")
    @PutMapping("/inventory/skus/{id}/alert")
    @RequirePermission("inventory:manage")
    @AuditLog(action = "设置库存预警")
    public Result<Void> setAlertStock(@PathVariable Long id,
                                      @RequestParam @NotNull Integer alertStock) {
        productService.setAlertStock(id, alertStock);
        return Result.ok();
    }

    // ==================== 本店上架（门店） ====================

    @Operation(summary = "本店上架", description = "定价须在平台建议价 80%-120%；storeId 取自登录上下文（总部账号拒绝）")
    @PostMapping("/store/listings")
    @RequirePermission("product:edit")
    @AuditLog(action = "本店上架")
    public Result<Long> createStoreProduct(@Valid @RequestBody StoreListingRequest req) {
        return Result.ok(productService.createStoreProduct(UserContext.storeId(), req));
    }

    @Operation(summary = "本店改价", description = "同样校验 80%-120% 区间")
    @PutMapping("/store/listings/{id}/price")
    @RequirePermission("product:edit")
    @AuditLog(action = "本店改价")
    public Result<Void> updateStorePrice(@PathVariable Long id,
                                         @Valid @RequestBody StorePriceRequest req) {
        productService.updateStorePrice(id, req.price(), UserContext.storeId());
        return Result.ok();
    }

    @Operation(summary = "本店上下架开关", description = "0下架 / 1上架；不被目录变更覆盖（D14）")
    @PutMapping("/store/listings/{id}/status")
    @RequirePermission("product:edit")
    @AuditLog(action = "本店上下架")
    public Result<Void> updateStoreProductStatus(@PathVariable Long id,
                                                 @RequestParam @NotNull Integer status) {
        productService.updateStoreProductStatus(id, status, UserContext.storeId());
        return Result.ok();
    }

    @Operation(summary = "本店上架列表", description = "联查商品/SKU 展示信息；catalog_dirty=1 表示目录已更新待复核")
    @GetMapping("/store/listings")
    @RequirePermission("menu:product")
    public Result<List<StoreProductVO>> listStoreProducts(
            @RequestParam(required = false) Integer status) {
        return Result.ok(productService.listStoreProducts(UserContext.storeId(), status));
    }
}
