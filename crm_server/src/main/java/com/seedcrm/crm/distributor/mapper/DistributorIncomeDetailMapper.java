package com.seedcrm.crm.distributor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DistributorIncomeDetailMapper extends BaseMapper<DistributorIncomeDetail> {

    @Select("""
            SELECT id, distributor_id, order_id, order_amount, income_amount, settlement_id, settlement_time, create_time
            FROM distributor_income_detail
            WHERE order_id = #{orderId}
              AND distributor_id = #{distributorId}
            LIMIT 1
            """)
    DistributorIncomeDetail selectByOrderAndDistributor(@Param("orderId") Long orderId,
                                                        @Param("distributorId") Long distributorId);

    @Select("""
            SELECT id, distributor_id, order_id, order_amount, income_amount, settlement_id, settlement_time, create_time
            FROM distributor_income_detail
            WHERE distributor_id = #{distributorId}
              AND settlement_id IS NULL
              AND create_time >= #{startTime}
              AND create_time <= #{endTime}
            ORDER BY create_time ASC, id ASC
            FOR UPDATE
            """)
    List<DistributorIncomeDetail> selectUnsettledForSettlement(@Param("distributorId") Long distributorId,
                                                               @Param("startTime") LocalDateTime startTime,
                                                               @Param("endTime") LocalDateTime endTime);
}
