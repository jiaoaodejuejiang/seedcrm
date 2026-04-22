package com.seedcrm.crm.distributor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.distributor.entity.DistributorWithdraw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DistributorWithdrawMapper extends BaseMapper<DistributorWithdraw> {

    @Select("""
            SELECT id, distributor_id, amount, status, create_time
            FROM distributor_withdraw
            WHERE id = #{id}
            FOR UPDATE
            """)
    DistributorWithdraw selectByIdForUpdate(@Param("id") Long id);
}
