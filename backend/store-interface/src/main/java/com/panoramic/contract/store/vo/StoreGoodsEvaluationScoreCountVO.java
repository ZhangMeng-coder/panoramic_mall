package com.panoramic.contract.store.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 某一星级的评价条数（星级分布的一项）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreGoodsEvaluationScoreCountVO {

    /**
     * 星级：1 ~ 5
     */
    private Integer score;

    /**
     * 该星级的评价条数
     */
    private Long count;
}
