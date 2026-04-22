package com.seedcrm.crm.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.finance.entity.Ledger;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LedgerMapper extends BaseMapper<Ledger> {

    @Select("""
            SELECT id, account_id, change_amount, balance_after, biz_type, biz_id, direction, create_time
            FROM ledger
            WHERE account_id = #{accountId}
              AND biz_type = #{bizType}
              AND biz_id = #{bizId}
            LIMIT 1
            """)
    Ledger selectByAccountAndBiz(@Param("accountId") Long accountId,
                                 @Param("bizType") String bizType,
                                 @Param("bizId") Long bizId);

    @Select("""
            SELECT id, account_id, change_amount, balance_after, biz_type, biz_id, direction, create_time
            FROM ledger
            WHERE biz_type = #{bizType}
              AND biz_id = #{bizId}
            LIMIT 1
            """)
    Ledger selectByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    @Select("""
            SELECT COALESCE(SUM(change_amount), 0)
            FROM ledger
            WHERE account_id = #{accountId}
            """)
    BigDecimal sumChangeAmountByAccountId(@Param("accountId") Long accountId);

    @Select("""
            SELECT COALESCE(SUM(change_amount), 0)
            FROM ledger
            WHERE account_id = #{accountId}
              AND biz_type = #{bizType}
              AND biz_id = #{bizId}
            """)
    BigDecimal sumChangeAmountByAccountAndBiz(@Param("accountId") Long accountId,
                                              @Param("bizType") String bizType,
                                              @Param("bizId") Long bizId);
}
