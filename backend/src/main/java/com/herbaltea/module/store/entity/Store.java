package com.herbaltea.module.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * stores 表实体（门店，对齐 V1__schema.sql 权威结构）
 *
 * <p>store_no 生成规则：直营旗舰 ST001，加盟店按 {@code MAX(id)+1} 顺延
 * （ST002、ST003…），由审批事务内生成，保证唯一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("stores")
public class Store extends BaseEntity {

    /** 门店类型：直营旗舰店 */
    public static final int TYPE_DIRECT = 1;
    /** 门店类型：加盟店 */
    public static final int TYPE_FRANCHISE = 2;

    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;
    /** 状态：正常 */
    public static final int STATUS_OK = 1;
    /** 状态：待开业 */
    public static final int STATUS_PENDING_OPEN = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 门店编号：ST001 直营旗舰 / ST002+ 加盟 */
    private String storeNo;

    private String storeName;

    /** 1直营旗舰店 / 2加盟店 */
    private Integer storeType;

    /** 0停用 / 1正常 / 2待开业 */
    private Integer status;

    private String contactName;

    private String contactPhone;

    private String province;

    private String city;

    private String district;

    private String address;

    /** 营业执照（COS） */
    private String licenseUrl;
}
