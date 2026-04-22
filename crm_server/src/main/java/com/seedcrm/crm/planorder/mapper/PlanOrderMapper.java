package com.seedcrm.crm.planorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlanOrderMapper extends BaseMapper<PlanOrder> {

    @Select("""
            SELECT id, order_id, status, arrive_time, start_time, finish_time, create_time
            FROM plan_order
            WHERE id = #{id}
            FOR UPDATE
            """)
    PlanOrder selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT id, order_id, status, arrive_time, start_time, finish_time, create_time
            FROM plan_order
            WHERE order_id = #{orderId}
            LIMIT 1
            FOR UPDATE
            """)
    PlanOrder selectByOrderIdForUpdate(@Param("orderId") Long orderId);
}
