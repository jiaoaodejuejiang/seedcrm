package com.seedcrm.crm.salary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalarySettlementMapper extends BaseMapper<SalarySettlement> {

    @Select("""
            SELECT id, user_id, total_amount, status, start_time, end_time, create_time
            FROM salary_settlement
            WHERE id = #{id}
            FOR UPDATE
            """)
    SalarySettlement selectByIdForUpdate(@Param("id") Long id);
}
