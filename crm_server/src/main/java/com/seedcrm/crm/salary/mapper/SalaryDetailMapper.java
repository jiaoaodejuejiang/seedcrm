package com.seedcrm.crm.salary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalaryDetailMapper extends BaseMapper<SalaryDetail> {

    @Select("""
            SELECT id, plan_order_id, user_id, role_code, order_amount, amount, settlement_id, settlement_time, create_time
            FROM salary_detail
            WHERE user_id = #{userId}
              AND settlement_id IS NULL
              AND create_time >= #{startTime}
              AND create_time <= #{endTime}
            ORDER BY create_time ASC, id ASC
            FOR UPDATE
            """)
    List<SalaryDetail> selectUnsettledForSettlement(@Param("userId") Long userId,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);
}
