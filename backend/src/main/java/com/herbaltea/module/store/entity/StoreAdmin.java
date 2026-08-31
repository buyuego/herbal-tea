package com.herbaltea.module.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * store_admins 表实体（管理员-门店关联，V1__schema.sql 权威结构）
 *
 * <p>一管理员可绑定多店（JWT store_ids[] 数据来源，支持 MULTI_STORE）；
 * is_owner=1 表示店主（主店），多店时以 is_owner 优先取主店。
 */
@Data
@TableName("store_admins")
public class StoreAdmin {

    /** 状态：正常 */
    public static final int STATUS_OK = 1;
    /** 状态：已移除 */
    public static final int STATUS_REMOVED = 0;
    /** 店主标记 */
    public static final int IS_OWNER = 1;
    /** 非店主 */
    public static final int NOT_OWNER = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 管理员 id（admin_users） */
    private Long adminId;

    /** 门店 id（stores） */
    private Long storeId;

    /** 1店主（主店） */
    private Integer isOwner;

    /** 0移除 / 1正常 */
    private Integer status;

    private LocalDateTime createdAt;
}
