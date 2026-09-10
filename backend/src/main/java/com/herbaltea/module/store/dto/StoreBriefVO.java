package com.herbaltea.module.store.dto;

import lombok.Data;

/**
 * C 端门店摘要（v29：小程序选店用，仅暴露必要字段，不含联系人与执照信息）
 */
@Data
public class StoreBriefVO {

    private Long id;

    private String storeNo;

    private String storeName;

    /** 1直营旗舰店 / 2加盟店 */
    private Integer storeType;

    private String province;

    private String city;

    private String district;

    private String address;

    /** 完整地址（省市区 + 详细） */
    private String fullAddress;
}
