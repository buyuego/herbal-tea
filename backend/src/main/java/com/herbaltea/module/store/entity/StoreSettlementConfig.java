package com.herbaltea.module.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * store_settlement_configs 表实体（门店结算配置，对齐 V1__schema.sql 权威结构）
 *
 * <p>加盟审批通过时以默认值创建：佣金 5%（0.0500）、T+1 日结、72h 自动确认。
 * 后续由结算模块按配置生成结算单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("store_settlement_configs")
public class StoreSettlementConfig extends BaseEntity {

    /** 结算周期：日结 T+1 */
    public static final int CYCLE_DAILY = 1;
    /** 结算周期：周结 */
    public static final int CYCLE_WEEKLY = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 门店（stores.id，唯一） */
    private Long storeId;

    /** 平台佣金比例 X%（订单金额拆分） */
    private BigDecimal commissionRate;

    /** 结算周期：1日结T+1 / 2周结 */
    private Integer cycleType;

    /** 自动确认时长（小时） */
    private Integer autoConfirmHours;

    /** 强制同步目录开关（D14，合规性修正用） */
    private Integer forceCatalogSync;

    /** 乐观锁 */
    @Version
    private Integer version;
}
