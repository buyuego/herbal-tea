package com.herbaltea.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.herbaltea.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * user_addresses 表实体（收货地址快照读取）
 *
 * <p><b>归属说明</b>：地址属用户域（User 模块落地后应迁移至 user 包）。
 * 订单模块仅做下单时的归属校验与快照读取，不维护地址数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_addresses")
public class UserAddress extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户（users.id） */
    private Long userId;

    /** 收货人 */
    private String receiverName;

    /** 联系电话 */
    private String phone;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区 */
    private String district;

    /** 详细地址 */
    private String detail;

    /** 1默认地址 */
    private Integer isDefault;
}
