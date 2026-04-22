package com.seedcrm.crm.distributor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.distributor.entity.DistributorSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DistributorSettlementMapper extends BaseMapper<DistributorSettlement> {

    @Select("""
            SELECT id, distributor_id, total_amount, status, start_time, end_time, create_time
            FROM distributor_settlement
            WHERE id = #{id}
            FOR UPDATE
            """)
    DistributorSettlement selectByIdForUpdate(@Param("id") Long id);
}
